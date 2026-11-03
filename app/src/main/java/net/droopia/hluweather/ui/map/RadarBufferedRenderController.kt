package net.droopia.hluweather.ui.map

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import net.droopia.hluweather.data.model.RadarFrameIdentity
import net.droopia.hluweather.data.model.RadarProvider

/** Timers are heuristics, not native-render readiness. The scope supplies the delay scheduler. */
internal data class RadarHandoffTiming(
    val warmupMillis: Long = 50,
    val retirementMillis: Long = 50,
    val nowMillis: () -> Long = { System.nanoTime() / 1_000_000L }
) {
    init {
        require(warmupMillis >= 0 && retirementMillis >= 0)
    }
}

/** Opt-in implementation; all native slots, including hidden ones, count toward the two-slot cap. */
internal class RadarBufferedRenderController(
    private val scope: CoroutineScope,
    private val backend: RadarRenderBackend,
    private val timeoutMillis: Long,
    private val timing: RadarHandoffTiming
) {
    private enum class Owner { OPTIONAL, DEMAND }
    private class Back(val slot: RadarRenderSlot, var owner: Owner, val wasOptional: Boolean) {
        var installedAt: Long? = null
        var coveredAt: Long? = null
        var adopted = false
        var removing = false
        var reusable = true
        val readyAt: Long? get() = installedAt?.let { installed -> coveredAt?.let { maxOf(installed, it) } }
    }
    private data class Promotion(val next: Back, val previous: RadarRenderSlot?, val opacity: Float)
    private data class Opportunity(val request: RadarRenderRequest, val revision: Long)

    private val lock = Any()
    // Foreground operations and source removal/allocation are serialized. Optional byte and install
    // waits run outside this mutex, so cancellation of speculation cannot hold demand behind them.
    private val mutex = Mutex()
    @Volatile private var generation = 0L
    private var preparationGeneration = 0L
    private var attemptedPreparation = -1L
    private var nextSlot = 0L
    private var input: RadarRenderRequest? = null
    private var provider: RadarProvider? = null
    private var front: RadarRenderSlot? = null
    private var frontVisible = false
    private var back: Back? = null
    private var retired: RadarRenderSlot? = null
    private var promoting: Promotion? = null
    private var opportunity: Opportunity? = null
    private var work: Job? = null
    private var preparation: Job? = null
    private var retirement: Job? = null
    private val mutableState = MutableStateFlow(RadarDisplayState())
    val state = mutableState.asStateFlow()

    fun update(request: RadarRenderRequest?, selectedProvider: RadarProvider?) =
        update(request, selectedProvider, force = false)

    fun retry() {
        val (request, selectedProvider) = synchronized(lock) { input to provider }
        update(request, selectedProvider, force = true)
    }

    private fun update(request: RadarRenderRequest?, selectedProvider: RadarProvider?, force: Boolean) {
        val token: Long
        val oldWork: Job?
        synchronized(lock) {
            if (!force && request == input && selectedProvider == provider) return
            val previousInput = input
            input = request
            provider = selectedProvider
            token = ++generation
            val valid = request != null && request.identity.provider == selectedProvider
            val clear = !valid || front?.let { !sameLifetime(it.request, request!!) } == true
            mutableState.value = RadarDisplayState(
                requested = request?.identity?.takeIf { valid },
                displayed = if (clear) null else mutableState.value.displayed,
                pending = valid && request!!.config.opacity > 0f
            )

            // Preserve preparation for opacity-only updates of the displayed frame. Otherwise a
            // fully installed matching optional slot transfers synchronously to actual demand.
            val sameSelection = valid && previousInput != null && sameFrame(previousInput, request!!) &&
                request.config.opacity > 0f && selectedProvider == previousInput.identity.provider
            if (!sameSelection) {
                val candidate = back
                if (valid && request!!.config.opacity > 0f && candidate?.owner == Owner.OPTIONAL &&
                    candidate.reusable && !candidate.removing &&
                    candidate.readyAt != null && sameFrame(candidate.slot.request, request)) {
                    candidate.owner = Owner.DEMAND
                }
                invalidatePreparationLocked()
            }
            oldWork = work
            work = scope.launch(start = CoroutineStart.LAZY) { reconcile(token, request, selectedProvider) }
        }
        oldWork?.cancel()
        synchronized(lock) { work }?.start()
    }

    /** Null withdraws speculation. A simultaneous real selection may claim an installed slot. */
    fun prepare(request: RadarRenderRequest?, revision: Long = 0) {
        val cleanup: Back?
        synchronized(lock) {
            val next = request?.let { Opportunity(it, revision) }
            if (next == opportunity) return
            val prepared = back
            if (next != null && prepared?.owner == Owner.OPTIONAL && prepared.reusable && !prepared.removing &&
                prepared.readyAt != null && sameFrame(prepared.slot.request, next.request)) {
                // A newer byte-prefetch notification does not invalidate an already covered,
                // installed slot or restart its compatible warm-up.
                opportunity = next
                return
            }
            opportunity = next
            preparationGeneration++
            preparation?.cancel()
            preparation = null
            cleanup = back?.takeIf { it.owner == Owner.OPTIONAL }
        }
        if (cleanup != null) scope.launch { discardOptional(cleanup) } else startPreparation()
    }

    private fun invalidatePreparationLocked() {
        opportunity = null
        preparationGeneration++
        preparation?.cancel()
        preparation = null
    }

    private fun eligibleOptional(request: RadarRenderRequest): Boolean {
        val current = front ?: return false
        val selected = input ?: return false
        return frontVisible && selected.config.opacity > 0f &&
            provider == selected.identity.provider && sameFrame(current.request, selected) &&
            sameLifetime(selected, request) && sameTiles(selected, request) && selected.identity != request.identity
    }

    private fun startPreparation() {
        val job: Job
        synchronized(lock) {
            val target = opportunity ?: return
            val token = preparationGeneration
            if (attemptedPreparation == token || back != null || retired != null || !eligibleOptional(target.request)) return
            attemptedPreparation = token
            job = scope.launch(start = CoroutineStart.LAZY) { prepareSlot(token, target.request) }
            preparation = job
        }
        job.start()
    }

    private fun optionalGuard(token: Long, item: Back, onApplied: () -> Unit = {}): RadarMutationGuard = { mutation ->
        synchronized(lock) {
            if (preparationGeneration != token || back !== item || item.owner != Owner.OPTIONAL || !item.reusable || item.removing ||
                !eligibleOptional(item.slot.request)) false else {
                mutation()
                if (preparationGeneration != token || back !== item || item.owner != Owner.OPTIONAL || !item.reusable || item.removing ||
                    !eligibleOptional(item.slot.request)) false else {
                    onApplied()
                    true
                }
            }
        }
    }

    private suspend fun prepareSlot(token: Long, request: RadarRenderRequest) {
        var item: Back? = null
        var keep = false
        try {
            mutex.withLock {
                val slot = synchronized(lock) {
                    if (preparationGeneration != token || back != null || retired != null || !eligibleOptional(request)) null
                    else RadarRenderSlot(++nextSlot, request)
                } ?: return
                // This hook must never create an ordinary network-capable source as a fallback.
                if (!backend.createOptional(slot)) return
                item = Back(slot, Owner.OPTIONAL, wasOptional = true)
                synchronized(lock) { back = item }
            }
            val created = requireNotNull(item)
            if (!backend.hasCachedCoverage(created.slot)) return
            synchronized(lock) {
                if (back !== created || created.owner != Owner.OPTIONAL || !created.reusable || preparationGeneration != token) return
                created.coveredAt = timing.nowMillis()
            }
            val opacity = synchronized(lock) { minOf(input?.config?.opacity ?: 0f, RadarRenderController.STAGING_OPACITY) }
            backend.install(created.slot, opacity, optionalGuard(token, created) { created.installedAt = timing.nowMillis() })
            keep = synchronized(lock) {
                back === created && created.owner == Owner.OPTIONAL && created.reusable &&
                    preparationGeneration == token && created.readyAt != null
            }
        } catch (_: CancellationException) {
            // Demand or lifecycle changes own cleanup; never report speculative failure to the UI.
        } catch (_: Exception) {
            // Cache miss, installation failure and unsupported optional sources are silent.
        } finally {
            if (!keep && item != null) scope.launch { discardOptional(item) }
        }
    }

    private suspend fun discardOptional(expected: Back? = null) {
        try {
            mutex.withLock {
                val item = synchronized(lock) { back?.takeIf { it.owner == Owner.OPTIONAL && (expected == null || it === expected) } }
                if (item != null) removeBack(item, optionalOnly = true)
            }
        } catch (_: CancellationException) {
            throw CancellationException("Optional radar cleanup cancelled")
        } catch (_: Exception) {
            // A failed removal keeps the slot/lease and prevents allocating a third source.
        }
        startPreparation()
    }

    fun reject(identity: RadarFrameIdentity, selectedProvider: RadarProvider?, styleEpoch: Long, message: String) {
        val token: Long
        val oldWork: Job?
        synchronized(lock) {
            input = null
            provider = selectedProvider
            token = ++generation
            invalidatePreparationLocked()
            val retain = front?.let { it.request.styleEpoch == styleEpoch && it.request.identity.provider == selectedProvider } == true
            mutableState.value = RadarDisplayState(identity, if (retain) mutableState.value.displayed else null, false, message)
            oldWork = work
            work = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    mutex.withLock {
                        if (!current(token)) return@withLock
                        if (!retain) removeAll(token) else {
                            restoreAttempted(token, frontOpacity())
                            back?.let { removeBack(it, token) }
                            retired?.let { removeRetired(it) }
                        }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    synchronized(lock) {
                        if (current(token)) mutableState.value = mutableState.value.copy(error = "$message; restore failed: ${error.message}")
                    }
                }
            }
        }
        oldWork?.cancel()
        synchronized(lock) { work }?.start()
    }

    private fun current(token: Long) = generation == token

    private fun guard(token: Long, onApplied: () -> Unit = {}): RadarMutationGuard = { mutation ->
        synchronized(lock) {
            if (!current(token)) false else {
                mutation()
                if (!current(token)) false else {
                    onApplied()
                    true
                }
            }
        }
    }

    private suspend fun reconcile(token: Long, request: RadarRenderRequest?, selectedProvider: RadarProvider?) {
        var accepted = false
        var tearingDown = false
        try {
            withTimeout(timeoutMillis) {
                mutex.withLock {
                    if (!current(token)) return@withLock
                    val valid = request != null && request.identity.provider == selectedProvider
                    val incompatible = !valid || listOfNotNull(front, back?.slot, retired).any { !sameLifetime(it.request, request!!) }
                    if (incompatible) {
                        tearingDown = true
                        removeAll(token)
                        tearingDown = false
                        if (!valid || !current(token)) return@withLock
                    }
                    val wanted = request ?: return@withLock
                    val opacity = wanted.config.opacity.coerceIn(0f, 1f)

                    if (opacity == 0f) {
                        // A failed partial swap may have left the staging layer visible. Hide both
                        // before attempting rollback, which may itself fail or suspend.
                        hideSlots(token, requireFrontHidden = true)
                        restoreAttempted(token, 0f)
                        back?.let { removeBack(it, token) }
                        retired?.let { removeRetired(it) }
                        synchronized(lock) { if (current(token)) mutableState.value = mutableState.value.copy(pending = false) }
                        return@withLock
                    }
                    restoreAttempted(token, opacity)
                    if (!current(token)) return@withLock

                    val currentFront = front
                    if (currentFront != null && sameFrame(currentFront.request, wanted)) {
                        backend.paint(currentFront, opacity, guard(token) {
                            frontVisible = true
                            mutableState.value = mutableState.value.copy(pending = false, error = null)
                        })
                        back?.takeIf { it.owner != Owner.OPTIONAL || opportunity == null }?.let { removeBack(it, token) }
                        back?.takeIf { it.owner == Owner.OPTIONAL && it.installedAt != null }?.let {
                            backend.paint(it.slot, minOf(opacity, RadarRenderController.STAGING_OPACITY), guard(token))
                        }
                        return@withLock
                    }
                    if (currentFront != null && frontVisible) backend.paint(currentFront, opacity, guard(token))
                    if (!current(token)) return@withLock

                    // New demand preempts a cleanup timer and any optional slot it cannot adopt.
                    retired?.let { removeRetired(it) }
                    var item = back
                    if (item != null && (item.owner != Owner.DEMAND || !item.reusable ||
                            !sameFrame(item.slot.request, wanted) || item.readyAt == null)) {
                        removeBack(item, token)
                        item = null
                    }
                    if (item?.wasOptional == true && !validateAdoption(token, item)) {
                        removeBack(item, token)
                        item = null
                    }
                    if (item == null) item = createDemand(token, wanted)
                    val next = item
                    if (next.readyAt == null) installAndCover(token, next, opacity)
                    if (!current(token)) return@withLock

                    if (frontVisible) {
                        val age = (timing.nowMillis() - requireNotNull(next.readyAt)).coerceAtLeast(0)
                        delay((timing.warmupMillis - age).coerceAtLeast(0))
                    }
                    // Cache-control may expire even during the remaining warm-up.
                    if (next.wasOptional && !reusableCoverage(next.slot)) {
                        removeBack(next, token)
                        val replacement = createDemand(token, wanted)
                        installAndCover(token, replacement, opacity)
                        if (frontVisible) {
                            val age = (timing.nowMillis() - requireNotNull(replacement.readyAt)).coerceAtLeast(0)
                            delay((timing.warmupMillis - age).coerceAtLeast(0))
                        }
                        promote(token, replacement, opacity) { accepted = true }
                    } else {
                        promote(token, next, opacity) { accepted = true }
                    }
                }
            }
            startPreparation()
        } catch (error: CancellationException) {
            val outcome = failureOutcome(token, accepted)
            if (outcome != null) {
                if (error is TimeoutCancellationException) fail(outcome, "Radar rendering timed out", restore = !tearingDown)
                else scope.launch { fail(outcome, "Radar rendering cancelled", restore = !tearingDown) }
            }
            if (error !is TimeoutCancellationException) throw error
        } catch (error: Exception) {
            failureOutcome(token, accepted)?.let { fail(it, error.message ?: "Radar unavailable", restore = !tearingDown) }
        }
    }

    private suspend fun validateAdoption(token: Long, item: Back): Boolean {
        if (!reusableCoverage(item.slot)) return false
        if (!item.adopted) {
            val adopted = try {
                backend.adoptOptional(item.slot, guard(token) { item.adopted = true })
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                false
            }
            if (!adopted || !item.adopted) return false
        }
        return true
    }

    private suspend fun reusableCoverage(slot: RadarRenderSlot): Boolean = try {
        backend.hasCachedCoverage(slot)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        false
    }

    private fun createDemand(token: Long, request: RadarRenderRequest): Back {
        synchronized(lock) {
            if (!current(token)) throw CancellationException("Obsolete radar allocation")
            check(back == null && retired == null && listOfNotNull(front).size < 2) { "No free radar slot" }
            val item = Back(RadarRenderSlot(++nextSlot, request), Owner.DEMAND, wasOptional = false)
            backend.create(item.slot)
            back = item
            return item
        }
    }

    private suspend fun installAndCover(token: Long, item: Back, opacity: Float) = coroutineScope {
        val coverage = async {
            backend.awaitCoverage(item.slot)
            synchronized(lock) { if (current(token) && back === item) item.coveredAt = timing.nowMillis() }
        }
        backend.install(item.slot, minOf(opacity, RadarRenderController.STAGING_OPACITY), guard(token) {
            item.installedAt = timing.nowMillis()
        })
        coverage.await()
        if (!current(token) || item.readyAt == null) throw CancellationException("Obsolete radar installation")
    }

    private suspend fun promote(token: Long, item: Back, opacity: Float, onAccepted: () -> Unit) {
        val previous = front
        synchronized(lock) {
            if (!current(token) || back !== item) throw CancellationException("Obsolete radar promotion")
            item.reusable = false
            promoting = Promotion(item, previous, opacity)
        }
        backend.promote(item.slot, previous, opacity, guard(token) {
            promoting = null
            front = item.slot
            back = null
            retired = previous
            frontVisible = true
            onAccepted()
            mutableState.value = mutableState.value.copy(displayed = item.slot.request.identity, pending = false, error = null)
            if (previous != null) scheduleRetirement(previous)
        })
    }

    private fun scheduleRetirement(item: RadarRenderSlot) {
        retirement?.cancel()
        val acceptedAt = timing.nowMillis()
        retirement = scope.launch {
            val elapsed = (timing.nowMillis() - acceptedAt).coerceAtLeast(0)
            delay((timing.retirementMillis - elapsed).coerceAtLeast(0))
            try {
                mutex.withLock { if (retired?.id == item.id) removeRetired(item, cancelTimer = false) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Retain the lease and retry on demand or close.
            }
            startPreparation()
        }
    }

    private fun failureOutcome(token: Long, accepted: Boolean): Long? = synchronized(lock) {
        if (!current(token) || accepted) null else ++generation
    }

    private suspend fun fail(token: Long, message: String, restore: Boolean) {
        mutex.withLock {
            if (!current(token)) return
            try {
                if (restore) restoreAttempted(token, frontOpacity())
                back?.let { removeBack(it, token) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                synchronized(lock) {
                    if (current(token)) mutableState.value = mutableState.value.copy(pending = false, error = "$message; restore failed: ${error.message}")
                }
                return
            }
            synchronized(lock) { if (current(token)) mutableState.value = mutableState.value.copy(pending = false, error = message) }
        }
    }

    private fun frontOpacity(): Float = input?.config?.opacity?.coerceIn(0f, 1f) ?: promoting?.opacity ?: 0f

    private suspend fun restoreAttempted(token: Long, opacity: Float) {
        promoting?.let { attempt ->
            backend.rollback(attempt.next.slot, attempt.previous, opacity, guard(token) {
                promoting = null
                frontVisible = attempt.previous != null && opacity > 0f
            })
            // A partially applied promotion is no longer a reusable prepared source, even when
            // its byte coverage and original installation were successful.
            if (promoting == null && back === attempt.next) removeBack(attempt.next, token)
        }
    }

    private suspend fun removeBack(item: Back, token: Long? = null, optionalOnly: Boolean = false) {
        synchronized(lock) {
            if (token != null && !current(token)) throw CancellationException("Obsolete radar cleanup")
            if (back !== item || (optionalOnly && item.owner != Owner.OPTIONAL)) return
            item.removing = true
            item.reusable = false
        }
        try {
            // Removal can fail or suspend. Do not leave a speculative/failed staging layer
            // faintly visible in that case, and never adopt it after it has been hidden.
            try {
                backend.paint(item.slot, 0f) { mutation ->
                    synchronized(lock) {
                        if (back !== item || !item.removing || (token != null && !current(token))) false else {
                            mutation()
                            back === item && item.removing && (token == null || current(token))
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // A source whose installation failed may have no layer; removal is still required.
            }
            backend.remove(item.slot)
            synchronized(lock) {
                if (back === item) back = null
                if (promoting?.next === item) promoting = null
            }
        } finally {
            synchronized(lock) { item.removing = false }
        }
    }

    private suspend fun removeRetired(item: RadarRenderSlot, cancelTimer: Boolean = true) {
        if (cancelTimer) retirement?.cancel()
        retirement = null
        backend.remove(item)
        synchronized(lock) { if (retired?.id == item.id) retired = null }
    }

    private suspend fun removeAll(token: Long) {
        retirement?.cancel()
        retirement = null
        // Hide the visible layer even if removing another resource fails. Continue attempting all
        // removals, and retain each failed lease so it still counts against the cap.
        var failure: Exception? = null
        val slots = listOfNotNull(front, back?.slot, retired).distinctBy { it.id }
        hideSlots(token, requireFrontHidden = false)
        for (slot in slots) {
            try {
                val staged = back?.takeIf { it.slot.id == slot.id }
                if (staged != null) {
                    removeBack(staged, token)
                    continue
                }
                backend.remove(slot)
                synchronized(lock) {
                    if (front?.id == slot.id) { front = null; frontVisible = false }
                    if (back?.slot?.id == slot.id) back = null
                    if (retired?.id == slot.id) retired = null
                    if (promoting?.next?.slot?.id == slot.id || promoting?.previous?.id == slot.id) promoting = null
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (failure == null) failure = error
            }
        }
        failure?.let { throw it }
    }

    private suspend fun hideSlots(token: Long, requireFrontHidden: Boolean) {
        var frontFailure: Exception? = null
        listOfNotNull(front, back?.slot, retired).distinctBy { it.id }.forEach { slot ->
            try {
                backend.paint(slot, 0f, guard(token) { if (front?.id == slot.id) frontVisible = false })
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // Staging may not have been installed, and a style change may have removed layers.
                if (front?.id == slot.id) frontFailure = error
            }
        }
        if (requireFrontHidden) frontFailure?.let { throw it }
    }

    suspend fun close() {
        val token: Long
        val oldWork: Job?
        synchronized(lock) {
            token = ++generation
            input = null
            provider = null
            invalidatePreparationLocked()
            mutableState.value = RadarDisplayState()
            oldWork = work
        }
        oldWork?.cancel()
        mutex.withLock { removeAll(token) }
    }

    private fun sameLifetime(a: RadarRenderRequest, b: RadarRenderRequest) =
        a.styleEpoch == b.styleEpoch && a.identity.provider == b.identity.provider

    private fun sameTiles(a: RadarRenderRequest, b: RadarRenderRequest) = a.coordinates.toSet() == b.coordinates.toSet()

    private fun sameFrame(a: RadarRenderRequest, b: RadarRenderRequest) =
        sameLifetime(a, b) && a.identity == b.identity && sameTiles(a, b)
}
