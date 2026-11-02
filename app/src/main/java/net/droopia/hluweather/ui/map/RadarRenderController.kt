package net.droopia.hluweather.ui.map

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import net.droopia.hluweather.data.model.RadarFrameIdentity
import net.droopia.hluweather.data.model.RadarProvider
import net.droopia.hluweather.data.model.RadarTileCoordinate

/** The tile set, not every subpixel camera movement, defines a viewport revision. */
internal data class RadarRenderRequest(
    val config: WeatherMapRadarConfig,
    val coordinates: List<RadarTileCoordinate>,
    val styleEpoch: Long
) {
    val identity: RadarFrameIdentity get() = config.frame.identity(config.host)
}

/** Renderer state only; playback/UI acknowledgment policy is owned by the caller. */
data class RadarDisplayState(
    val requested: RadarFrameIdentity? = null,
    val displayed: RadarFrameIdentity? = null,
    val pending: Boolean = false,
    val error: String? = null
)

internal data class RadarRenderSlot(val id: Long, val request: RadarRenderRequest)

/**
 * A guard runs the native mutation and the controller's bookkeeping under the same short lock.
 * It must be invoked on the native owner thread, not checked before a queued native operation.
 */
internal typealias RadarMutationGuard = ((() -> Unit) -> Boolean)

internal interface RadarRenderBackend {
    /** Creates an unreferenced volatile source and retains its registry lease. */
    fun create(slot: RadarRenderSlot)
    suspend fun install(slot: RadarRenderSlot, opacity: Float, guard: RadarMutationGuard)
    suspend fun paint(slot: RadarRenderSlot, opacity: Float, guard: RadarMutationGuard)
    suspend fun promote(
        next: RadarRenderSlot,
        previous: RadarRenderSlot?,
        opacity: Float,
        guard: RadarMutationGuard
    )
    /** Restore the previously displayed slot and hide the unacknowledged promotion atomically. */
    suspend fun rollback(
        failed: RadarRenderSlot,
        previous: RadarRenderSlot?,
        opacity: Float,
        guard: RadarMutationGuard
    )
    suspend fun awaitCoverage(slot: RadarRenderSlot)
    /** Wait for a render after this call's native barrier, not a previously observed event. */
    suspend fun awaitFullRender(slot: RadarRenderSlot, viewport: RadarRenderRequest, guard: RadarMutationGuard)
    suspend fun remove(slot: RadarRenderSlot)
}

/**
 * Two slots, latest request wins. A retained old slot cannot be recycled until the promoted
 * revision has rendered. All public calls and coroutine continuations run on the owning UI scope;
 * the lock also arbitrates with the native owner thread at the instant it applies a command.
 */
internal class RadarRenderController(
    private val scope: CoroutineScope,
    private val backend: RadarRenderBackend,
    private val timeoutMillis: Long = 45_000
) {
    private val lock = Any()
    private val mutex = Mutex()
    private val serial = AtomicLong()
    private var input: RadarRenderRequest? = null
    private var selectedProvider: RadarProvider? = null
    private var work: Job? = null
    private var front: RadarRenderSlot? = null
    private var staging: RadarRenderSlot? = null
    private var retired: RadarRenderSlot? = null
    private data class Promotion(
        val next: RadarRenderSlot,
        val previous: RadarRenderSlot?,
        val opacity: Float,
        var failed: Boolean = false
    )
    private var unacknowledged: Promotion? = null
    private var frontVisible = false
    private var nextSlot = 0L
    private val mutableState = MutableStateFlow(RadarDisplayState())
    val state: StateFlow<RadarDisplayState> = mutableState.asStateFlow()

    fun update(request: RadarRenderRequest?, provider: RadarProvider?) {
        update(request, provider, force = false)
    }

    fun retry() = update(input, selectedProvider, force = true)

    /** Invalid viewport or metadata invalidates pending work without erasing a same-style front. */
    fun reject(identity: RadarFrameIdentity, provider: RadarProvider?, styleEpoch: Long, message: String) {
        val token = synchronized(lock) {
            input = null
            selectedProvider = provider
            val retain = front?.let { it.request.styleEpoch == styleEpoch && it.request.identity.provider == provider } == true
            if (retain) unacknowledged?.failed = true
            mutableState.value = RadarDisplayState(identity, if (retain) mutableState.value.displayed else null, false, message)
            serial.incrementAndGet()
        }
        work?.cancel()
        work = scope.launch {
            try {
                mutex.withLock {
                    if (serial.get() != token) return@withLock
                    val retain = front?.let { it.request.styleEpoch == styleEpoch && it.request.identity.provider == provider } == true
                    if (!retain) removeAll()
                    else {
                        staging?.let { backend.remove(it); staging = null }
                        unacknowledged?.let { rollback(it, token, it.opacity) }
                        staging?.let { backend.remove(it); staging = null }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (serial.get() == token) {
                    mutableState.value = mutableState.value.copy(error = "$message; restore failed: ${error.message}")
                }
            }
        }
    }

    private fun update(request: RadarRenderRequest?, provider: RadarProvider?, force: Boolean) {
        if (!force && request == input && provider == selectedProvider) return
        val token = synchronized(lock) {
            input = request
            selectedProvider = provider
            val clear = request == null || provider != request.config.frame.provider ||
                front?.let { it.request.styleEpoch != request.styleEpoch || it.request.identity.provider != provider } == true
            mutableState.value = RadarDisplayState(
                requested = request?.identity?.takeIf { provider == it.provider },
                displayed = if (clear) null else mutableState.value.displayed,
                pending = request != null && provider == request.config.frame.provider && request.config.opacity > 0f
            )
            serial.incrementAndGet()
        }
        work?.cancel()
        work = scope.launch { reconcile(token, request, provider) }
    }

    private fun guard(token: Long, onApplied: () -> Unit = {}): RadarMutationGuard = { mutation ->
        synchronized(lock) {
            if (serial.get() != token) false else {
                mutation()
                onApplied()
                true
            }
        }
    }

    private suspend fun reconcile(token: Long, request: RadarRenderRequest?, provider: RadarProvider?) {
        try {
            mutex.withLock {
                if (serial.get() != token) return
                val incompatible = request == null || request.config.frame.provider != provider ||
                    front?.let { it.request.styleEpoch != request.styleEpoch || it.request.identity.provider != provider } == true ||
                    staging?.let { it.request.styleEpoch != request.styleEpoch || it.request.identity.provider != provider } == true ||
                    retired?.let { it.request.styleEpoch != request.styleEpoch || it.request.identity.provider != provider } == true
                if (incompatible) {
                    removeAll()
                    if (request == null || request.config.frame.provider != provider) return
                }

                // A cancelled request may have left an installed staging slot, but never removes
                // the current front. Unique slot IDs make removal safe even after supersession.
                staging?.let { backend.remove(it); staging = null }
                if (request == null || serial.get() != token) return

                val opacity = request.config.opacity.coerceIn(0f, 1f)
                if (opacity == 0f) {
                    // A hidden, unacknowledged promotion must not later be acknowledged by a
                    // Full render of an invisible layer. Restore it before the next visible request.
                    synchronized(lock) {
                        if (serial.get() == token) unacknowledged?.failed = true
                    }
                    front?.let { backend.paint(it, 0f, guard(token)) }
                    retired?.let { backend.paint(it, 0f, guard(token)) }
                    frontVisible = false
                    mutableState.value = mutableState.value.copy(pending = false)
                    return
                }

                // A timed-out promotion stays failed even if a newer request interrupted rollback.
                // The latest generation, never the obsolete one, must finish restoring the front.
                unacknowledged?.takeIf { it.failed }?.let { rollback(it, token, opacity) }
                staging?.let { backend.remove(it); staging = null }

                // A cancelled selection may have applied a swap even when there was no old slot.
                // Require its later render before acknowledging it or recycling either slot.
                if (unacknowledged != null || retired != null) {
                    val current = requireNotNull(front)
                    withTimeout(timeoutMillis) { backend.awaitFullRender(current, request, guard(token)) }
                    if (serial.get() != token) return
                    if (unacknowledged != null) {
                        unacknowledged = null
                        mutableState.value = mutableState.value.copy(displayed = current.request.identity)
                    }
                    retired?.let { backend.remove(it); retired = null }
                }

                val current = front
                if (current != null && frontVisible) {
                    backend.paint(current, opacity, guard(token))
                    if (serial.get() != token) return
                    if (current.request.identity == request.identity &&
                        current.request.coordinates == request.coordinates &&
                        current.request.styleEpoch == request.styleEpoch
                    ) {
                        mutableState.value = mutableState.value.copy(pending = false, error = null)
                        return
                    }
                }

                val next = RadarRenderSlot(++nextSlot, request)
                backend.create(next)
                staging = next
                withTimeout(timeoutMillis) {
                    coroutineScope {
                        val coverage = async { backend.awaitCoverage(next) }
                        backend.install(next, minOf(opacity, STAGING_OPACITY), guard(token))
                        coverage.await()
                    }
                    backend.awaitFullRender(next, request, guard(token))
                    backend.promote(next, current, opacity, guard(token) {
                        front = next
                        staging = null
                        retired = current
                        frontVisible = true
                        unacknowledged = Promotion(next, current, opacity)
                    })
                    // Do not report a displayed frame or recycle its predecessor merely because
                    // the paint commands were accepted; wait for the promoted revision to render.
                    backend.awaitFullRender(next, request, guard(token))
                }
                if (serial.get() != token) return
                unacknowledged = null
                mutableState.value = mutableState.value.copy(displayed = request.identity, pending = false, error = null)
                retired?.let { backend.remove(it); retired = null }
            }
        } catch (error: CancellationException) {
            if (error is kotlinx.coroutines.TimeoutCancellationException && serial.get() == token) {
                fail(token, request, "Radar rendering timed out")
            } else {
                throw error
            }
        } catch (error: Exception) {
            if (serial.get() == token) {
                fail(token, request, error.message ?: "Radar unavailable")
            }
        }
    }

    private suspend fun rollback(promotion: Promotion, token: Long, opacity: Float) {
        backend.rollback(promotion.next, promotion.previous, opacity, guard(token) {
            front = promotion.previous
            frontVisible = promotion.previous != null
            retired = promotion.next.takeIf { promotion.previous != null }
            staging = promotion.next.takeIf { promotion.previous == null }
            unacknowledged = null
        })
    }

    private suspend fun fail(token: Long, request: RadarRenderRequest?, message: String) {
        synchronized(lock) {
            if (serial.get() != token) return
            unacknowledged?.failed = true
        }
        mutex.withLock {
            if (serial.get() != token) return
            try {
                unacknowledged?.let { rollback(it, token, it.opacity) }
                staging?.let { backend.remove(it); staging = null }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (serial.get() == token) {
                    mutableState.value = mutableState.value.copy(pending = false, error = "$message; restore failed: ${error.message}")
                }
                return
            }
            if (serial.get() != token) return
            mutableState.value = mutableState.value.copy(pending = false, error = message)

            // Keep the failed slot hidden until the restored front has actually rendered. If that
            // render also fails, the next request resumes this barrier before reusing the slot.
            val restored = front
            val failed = retired
            if (restored != null && failed != null && request != null) {
                try {
                    withTimeout(timeoutMillis) { backend.awaitFullRender(restored, request, guard(token)) }
                    if (serial.get() == token) {
                        backend.remove(failed)
                        retired = null
                    }
                } catch (error: CancellationException) {
                    if (error !is kotlinx.coroutines.TimeoutCancellationException) throw error
                } catch (_: Exception) {
                    // Retain both slots; a later request may retry the guarded cleanup.
                }
            }
        }
    }

    /** Invalidate queued native commands immediately; cleanup may finish after the map closes. */
    suspend fun close() {
        synchronized(lock) { serial.incrementAndGet() }
        work?.cancel()
        mutex.withLock { removeAll() }
    }

    private suspend fun removeAll() {
        listOfNotNull(staging, retired, front).distinctBy { it.id }.forEach { backend.remove(it) }
        staging = null
        retired = null
        front = null
        frontVisible = false
        unacknowledged = null
    }

    companion object {
        const val STAGING_OPACITY = 0.001f
    }
}
