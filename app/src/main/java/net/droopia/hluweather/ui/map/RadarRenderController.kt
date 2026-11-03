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

/** In speed-first mode, displayed means submitted to the native map, not confirmed drawn. */
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
    /** Cache-only from creation onward; false or an exception must leave no source or lease. */
    fun createOptional(slot: RadarRenderSlot): Boolean = false
    /** Check every required tile in the current cache, including freshness and cache-control. */
    suspend fun hasCachedCoverage(slot: RadarRenderSlot): Boolean = false
    /** Transfer this exact optional registration to real demand only inside the supplied guard. */
    suspend fun adoptOptional(slot: RadarRenderSlot, guard: RadarMutationGuard): Boolean = false
    /** Wait for a render after this call's native barrier, not a previously observed event. */
    suspend fun awaitFullRender(slot: RadarRenderSlot, viewport: RadarRenderRequest, guard: RadarMutationGuard)
    suspend fun remove(slot: RadarRenderSlot)
}

/**
 * Two slots, latest request wins. By default, coverage and accepted paint commands suffice;
 * optional conservative render barriers remain available for diagnostics. All continuations run on
 * the owning UI scope; the lock also arbitrates with the native owner thread when it applies a command.
 */
internal class RadarRenderController(
    private val scope: CoroutineScope,
    private val backend: RadarRenderBackend,
    private val timeoutMillis: Long = 45_000,
    private val waitForFullRender: Boolean = false,
    timing: RadarHandoffTiming? = null
) {
    // The existing renderer remains on its established path until the later integration enables
    // timed handoff and implements the optional-source backend contract.
    private val buffered = timing?.let {
        require(!waitForFullRender) { "Timed handoff and render-barrier diagnostics are separate modes" }
        RadarBufferedRenderController(scope, backend, timeoutMillis, it)
    }
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
    // Promotion may throw after applying some native properties but before its guard completes.
    private var promoting: Promotion? = null
    private var unacknowledged: Promotion? = null
    private var frontVisible = false
    private var nextSlot = 0L
    private val mutableState = MutableStateFlow(RadarDisplayState())
    val state: StateFlow<RadarDisplayState> = buffered?.state ?: mutableState.asStateFlow()

    fun update(request: RadarRenderRequest?, provider: RadarProvider?) {
        if (buffered != null) return buffered.update(request, provider)
        update(request, provider, force = false)
    }

    fun retry() {
        if (buffered != null) buffered.retry() else update(input, selectedProvider, force = true)
    }

    /** A new opportunity may retry a prior cache miss; repeating the same opportunity cannot. */
    fun prepare(request: RadarRenderRequest?, revision: Long = 0) {
        buffered?.prepare(request, revision)
    }

    /** Invalid viewport or metadata invalidates pending work without erasing a same-style front. */
    fun reject(identity: RadarFrameIdentity, provider: RadarProvider?, styleEpoch: Long, message: String) {
        if (buffered != null) return buffered.reject(identity, provider, styleEpoch, message)
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
                        promoting?.let { restoreAttempted(it, token, it.opacity) }
                        staging?.let { backend.remove(it); staging = null }
                        unacknowledged?.let { rollback(it, token, it.opacity) }
                        staging?.let { backend.remove(it); staging = null }
                        if (!waitForFullRender) retired?.let { backend.remove(it); retired = null }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                synchronized(lock) {
                    if (serial.get() == token) {
                        mutableState.value = mutableState.value.copy(error = "$message; restore failed: ${error.message}")
                    }
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
                // A native callback can synchronously invalidate this request on the owner thread.
                if (serial.get() != token) false else {
                    onApplied()
                    true
                }
            }
        }
    }

    private suspend fun reconcile(token: Long, request: RadarRenderRequest?, provider: RadarProvider?) {
        // Written under the same guard as the native swap. A timeout/cancellation can happen after
        // the owner thread applied the swap but before its coroutine continuation resumes.
        var accepted = false
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

                if (request == null || serial.get() != token) return
                val opacity = request.config.opacity.coerceIn(0f, 1f)

                // A failed or cancelled mutation may have changed either opacity before throwing.
                // Restore with the latest generation (at zero when hiding) before removing staging.
                promoting?.let { restoreAttempted(it, token, opacity) }
                staging?.let { backend.remove(it); staging = null }
                if (opacity == 0f) {
                    // A hidden, unacknowledged promotion must not later be acknowledged by a
                    // Full render of an invisible layer. Restore it before the next visible request.
                    synchronized(lock) {
                        if (serial.get() == token) unacknowledged?.failed = true
                    }
                    front?.let { backend.paint(it, 0f, guard(token)) }
                    retired?.let { backend.paint(it, 0f, guard(token)) }
                    synchronized(lock) {
                        if (serial.get() == token) {
                            frontVisible = false
                            mutableState.value = mutableState.value.copy(pending = false)
                        }
                    }
                    if (!waitForFullRender && serial.get() == token) {
                        retired?.let { backend.remove(it); retired = null }
                    }
                    return
                }

                // A timed-out promotion stays failed even if a newer request interrupted rollback.
                // The latest generation, never the obsolete one, must finish restoring the front.
                unacknowledged?.takeIf { it.failed }?.let { rollback(it, token, opacity) }
                staging?.let { backend.remove(it); staging = null }

                // A cancelled selection may have applied a swap even when there was no old slot.
                // Conservative diagnostics require its later render before recycling either slot.
                if (unacknowledged != null || retired != null) {
                    val current = requireNotNull(front)
                    if (waitForFullRender) {
                        withTimeout(timeoutMillis) { backend.awaitFullRender(current, request, guard(token)) }
                    }
                    val stillCurrent = synchronized(lock) {
                        if (serial.get() != token) false else {
                            if (unacknowledged != null) {
                                unacknowledged = null
                                mutableState.value = mutableState.value.copy(displayed = current.request.identity)
                            }
                            true
                        }
                    }
                    if (!stillCurrent) return
                    retired?.let { backend.remove(it); retired = null }
                }

                val current = front
                if (current != null && frontVisible) {
                    val sameFrame = current.request.identity == request.identity &&
                        current.request.coordinates == request.coordinates &&
                        current.request.styleEpoch == request.styleEpoch
                    backend.paint(current, opacity, guard(token) {
                        if (sameFrame) mutableState.value = mutableState.value.copy(pending = false, error = null)
                    })
                    if (serial.get() != token || sameFrame) return
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
                    if (waitForFullRender) backend.awaitFullRender(next, request, guard(token))
                    synchronized(lock) {
                        if (serial.get() != token) throw CancellationException("Obsolete radar promotion")
                        promoting = Promotion(next, current, opacity)
                    }
                    backend.promote(next, current, opacity, guard(token) {
                        promoting = null
                        front = next
                        staging = null
                        retired = current
                        frontVisible = true
                        if (waitForFullRender) {
                            unacknowledged = Promotion(next, current, opacity)
                        } else {
                            // Acknowledge in the native mutation guard so cancellation cannot leave
                            // an accepted promotion recorded as the old frame. This is not draw proof.
                            accepted = true
                            mutableState.value = mutableState.value.copy(
                                displayed = request.identity, pending = false, error = null
                            )
                        }
                    })
                    if (waitForFullRender) backend.awaitFullRender(next, request, guard(token))
                }
                val stillCurrent = synchronized(lock) {
                    if (serial.get() != token) false else {
                        if (waitForFullRender) {
                            unacknowledged = null
                            mutableState.value = mutableState.value.copy(
                                displayed = request.identity, pending = false, error = null
                            )
                        }
                        true
                    }
                }
                if (!stillCurrent) return
                retired?.let { backend.remove(it); retired = null }
            }
        } catch (error: CancellationException) {
            if (error is kotlinx.coroutines.TimeoutCancellationException) {
                val outcome = failureOutcome(token) { accepted }
                if (outcome != null) {
                    if (outcome == token) cleanupAccepted(token)
                    else fail(outcome, request, "Radar rendering timed out")
                }
            } else {
                // Usually a new request/close owns cleanup. If only this child was cancelled,
                // finish it in a sibling job instead of retaining an accepted predecessor forever.
                val outcome = failureOutcome(token) { accepted }
                if (outcome != null) scope.launch {
                    if (outcome == token) cleanupAccepted(token)
                    else fail(outcome, request, "Radar rendering cancelled")
                }
                throw error
            }
        } catch (error: Exception) {
            val outcome = failureOutcome(token) { accepted }
            if (outcome != null) {
                if (outcome == token) cleanupAccepted(token)
                else fail(outcome, request, error.message ?: "Radar unavailable")
            }
        }
    }

    /** Return the original token for an accepted swap, a new failure token, or null if obsolete. */
    private fun failureOutcome(token: Long, accepted: () -> Boolean): Long? = synchronized(lock) {
        if (serial.get() != token) null else if (accepted()) token else serial.incrementAndGet()
    }

    private suspend fun cleanupAccepted(token: Long) {
        try {
            mutex.withLock {
                if (serial.get() != token) return
                retired?.let { backend.remove(it); retired = null }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Retain the lease if native removal failed; the next request or close retries it.
        }
    }

    /** Repair a possibly partial swap before releasing its staging source. */
    private suspend fun restoreAttempted(promotion: Promotion, token: Long, opacity: Float) {
        backend.rollback(promotion.next, promotion.previous, opacity, guard(token) { promoting = null })
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
                promoting?.let { restoreAttempted(it, token, it.opacity) }
                unacknowledged?.let { rollback(it, token, it.opacity) }
                staging?.let { backend.remove(it); staging = null }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                synchronized(lock) {
                    if (serial.get() == token) {
                        mutableState.value = mutableState.value.copy(pending = false, error = "$message; restore failed: ${error.message}")
                    }
                }
                return
            }
            synchronized(lock) {
                if (serial.get() != token) return
                mutableState.value = mutableState.value.copy(pending = false, error = message)
            }

            // Diagnostic mode retains the failed slot until the restored front has rendered.
            // Otherwise cleanup needs no render events; failed removals are retried later.
            val restored = front
            val failed = retired
            if (restored != null && failed != null && request != null) {
                try {
                    if (waitForFullRender) {
                        withTimeout(timeoutMillis) { backend.awaitFullRender(restored, request, guard(token)) }
                    }
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
        if (buffered != null) return buffered.close()
        synchronized(lock) {
            serial.incrementAndGet()
            input = null
            selectedProvider = null
            mutableState.value = RadarDisplayState()
        }
        work?.cancel()
        mutex.withLock { removeAll() }
    }

    private suspend fun removeAll() {
        listOfNotNull(staging, retired, front).distinctBy { it.id }.forEach { backend.remove(it) }
        staging = null
        retired = null
        front = null
        frontVisible = false
        promoting = null
        unacknowledged = null
    }

    companion object {
        const val STAGING_OPACITY = 0.001f
    }
}
