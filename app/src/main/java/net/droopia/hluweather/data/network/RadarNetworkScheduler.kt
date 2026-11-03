package net.droopia.hluweather.data.network

import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import net.droopia.hluweather.data.model.RadarProvider
import net.droopia.hluweather.data.repository.RadarRequestBudget
import kotlin.coroutines.cancellation.CancellationException

/** Shared by every consumer of one in-flight tile; display can promote an existing prefetch. */
class RadarRequestPriority(speculative: Boolean) {
    @Volatile
    var speculative: Boolean = speculative
        internal set
    @Volatile
    internal var mayRun: () -> Boolean = { true }
}

class RadarSpeculativeQueueFullException : IOException("Radar prefetch queue is full")
internal class RadarObsoleteSpeculationException : CancellationException("Obsolete radar speculation")

/**
 * Schedules individual HTTP attempts, not whole retry loops. Metadata and tiles use the same
 * scheduler, so an in-progress request holds one of the eight slots until its response is read.
 */
class RadarNetworkScheduler(
    private val scope: CoroutineScope,
    private val monotonicMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    private val wallMillis: () -> Long = System::currentTimeMillis
) : RadarRequestBudget {
    private class Work(
        val provider: RadarProvider,
        val priority: RadarRequestPriority,
        val run: suspend () -> Unit,
        val fail: (Throwable) -> Unit
    ) {
        var job: Job? = null
    }

    private val lock = Any()
    private val pending = mutableListOf<Work>()
    private val active = mutableSetOf<Work>()
    private val rainAttempts = ArrayDeque<Long>()
    private var cooldownUntil: Long? = null
    private var rateLimitStreak = 0
    private var wake: Job? = null
    private var wakeGeneration = 0L
    private val mutableRateLimitedUntil = MutableStateFlow<Long?>(null)

    init {
        scope.coroutineContext[Job]?.invokeOnCompletion {
            synchronized(lock) {
                val abandoned = pending.toList()
                pending.clear()
                abandoned.forEach { it.fail(CancellationException("Radar scheduler is stopped")) }
            }
        }
    }

    /** Wall-clock deadline for a RainViewer 429 or exhausted foreground budget, if known. */
    val rateLimitedUntil: StateFlow<Long?> = mutableRateLimitedUntil

    override suspend fun acquire(provider: RadarProvider) {
        execute(provider) { Unit }
    }

    override suspend fun <T> execute(provider: RadarProvider, request: suspend () -> T): T =
        execute(provider, RadarRequestPriority(speculative = false), request)

    suspend fun <T> execute(
        provider: RadarProvider,
        priority: RadarRequestPriority,
        request: suspend () -> T
    ): T {
        val result = CompletableDeferred<T>()
        val work = Work(provider, priority, run = {
            if (!priority.mayRun()) {
                result.cancel(RadarObsoleteSpeculationException())
            } else try {
                result.complete(request())
            } catch (error: CancellationException) {
                result.cancel(error)
            } catch (error: Throwable) {
                if (provider == RadarProvider.RAINVIEWER && error is RadarApiException && error.statusCode == 429) {
                    synchronized(lock) { recordRateLimitLocked(error.retryAfter) }
                }
                result.completeExceptionally(error)
            }
        }, fail = { result.completeExceptionally(it) })
        result.invokeOnCompletion {
            if (result.isCancelled) synchronized(lock) {
                pending.remove(work)
                work.job?.cancel()
                pumpLocked()
            }
        }
        synchronized(lock) {
            if (scope.coroutineContext[Job]?.isActive == false) {
                result.cancel(CancellationException("Radar scheduler is stopped"))
            } else if (priority.speculative && pending.count { it.priority.speculative } >= MAX_SPECULATIVE_QUEUE) {
                result.completeExceptionally(RadarSpeculativeQueueFullException())
            } else {
                pending.add(work)
                pumpLocked()
            }
        }
        return try {
            result.await()
        } finally {
            // await cancellation does not automatically cancel an independent CompletableDeferred.
            if (!result.isCompleted) result.cancel()
        }
    }

    /** Promote immediately; if a running request is demoted, preserve the speculative cap. */
    fun priorityChanged(priority: RadarRequestPriority) = synchronized(lock) {
        if (priority.speculative && active.count { it.priority.speculative } > MAX_SPECULATIVE_CONCURRENT) {
            // The former foreground consumer left while other speculative slots were filled.
            // Speculation is best effort: cancel this work rather than exceed the hard cap.
            active.firstOrNull { it.priority === priority }?.job?.cancel()
        }
        // A demand consumer can leave a formerly promoted tile with only speculative owners.
        val excess = pending.filter { it.priority.speculative }.drop(MAX_SPECULATIVE_QUEUE)
        excess.forEach {
            pending.remove(it)
            it.fail(RadarSpeculativeQueueFullException())
        }
        pumpLocked()
    }

    private fun pumpLocked() {
        wake?.cancel()
        wake = null
        wakeGeneration++
        val obsolete = pending.filter { !it.priority.mayRun() }
        pending.removeAll(obsolete.toSet())
        obsolete.forEach { it.fail(RadarObsoleteSpeculationException()) }
        while (active.size < MAX_CONCURRENT && pending.isNotEmpty()) {
            val now = monotonicMillis()
            expireAttemptsLocked(now)
            val hasForeground = pending.any { !it.priority.speculative }
            val candidates = pending.filter {
                it.priority.speculative != hasForeground &&
                    (!it.priority.speculative || active.count { running -> running.priority.speculative } < MAX_SPECULATIVE_CONCURRENT)
            }
            val ready = candidates.firstOrNull { waitMillisLocked(it.provider, it.priority.speculative, now) == 0L }
            if (ready == null) {
                val wait = candidates.minOfOrNull { waitMillisLocked(it.provider, it.priority.speculative, now) }
                if (wait != null && wait > 0) {
                    if (hasForeground && candidates.any { it.provider == RadarProvider.RAINVIEWER }) {
                        mutableRateLimitedUntil.value = saturatingAdd(wallMillis(), wait)
                    }
                    scheduleWakeLocked(wait)
                }
                return
            }
            pending.remove(ready)
            if (ready.provider == RadarProvider.RAINVIEWER) rainAttempts.addLast(now)
            active.add(ready)
            val job = scope.launch(start = CoroutineStart.LAZY) { ready.run() }
            ready.job = job
            job.invokeOnCompletion { failure ->
                if (failure != null) ready.fail(failure)
                synchronized(lock) {
                    active.remove(ready)
                    pumpLocked()
                }
            }
            job.start()
        }
        val now = monotonicMillis()
        expireAttemptsLocked(now)
        val cooldown = cooldownUntil?.let { remainingUntil(it, now) } ?: 0L
        if (cooldown == 0L && rainAttempts.size < MAX_RAIN_REQUESTS) {
            cooldownUntil = null
            mutableRateLimitedUntil.value = null
        } else if (pending.isEmpty() && mutableRateLimitedUntil.value != null) {
            // Clear the status when the deadline passes even if no further work is enqueued.
            val budgetWait = if (rainAttempts.size >= MAX_RAIN_REQUESTS) {
                remainingUntil(saturatingAdd(rainAttempts.first, RAIN_WINDOW_MILLIS), now)
            } else 0L
            val wait = maxOf(cooldown, budgetWait)
            if (wait > 0) scheduleWakeLocked(wait)
        }
    }

    private fun scheduleWakeLocked(wait: Long) {
        val generation = ++wakeGeneration
        wake = scope.launch {
            delay(wait)
            synchronized(lock) {
                if (wakeGeneration == generation) {
                    wake = null
                    pumpLocked()
                }
            }
        }
    }

    private fun expireAttemptsLocked(now: Long) {
        while (rainAttempts.isNotEmpty() && remainingUntil(saturatingAdd(rainAttempts.first, RAIN_WINDOW_MILLIS), now) == 0L) {
            rainAttempts.removeFirst()
        }
    }

    private fun waitMillisLocked(provider: RadarProvider, speculative: Boolean, now: Long): Long {
        if (provider != RadarProvider.RAINVIEWER) return 0
        val limit = if (speculative) MAX_RAIN_REQUESTS - FOREGROUND_RESERVE else MAX_RAIN_REQUESTS
        val budgetWait = if (rainAttempts.size >= limit) {
            remainingUntil(saturatingAdd(rainAttempts.elementAt(rainAttempts.size - limit), RAIN_WINDOW_MILLIS), now)
        } else 0L
        val cooldown = cooldownUntil?.let { remainingUntil(it, now) } ?: 0L
        return maxOf(cooldown, budgetWait)
    }

    private fun recordRateLimitLocked(retryAfter: String?) {
        val now = monotonicMillis()
        val retryMillis = parseRetryAfterMillis(retryAfter, wallMillis()) ?: run {
            rateLimitStreak = (rateLimitStreak + 1).coerceAtMost(7)
            (1_000L shl (rateLimitStreak - 1)).coerceAtMost(MAX_BACKOFF_MILLIS)
        }
        val until = maxOf(cooldownUntil ?: Long.MIN_VALUE, saturatingAdd(now, retryMillis))
        cooldownUntil = until
        mutableRateLimitedUntil.value = saturatingAdd(wallMillis(), remainingUntil(until, now))
    }

    companion object {
        const val MAX_CONCURRENT = 8
        const val MAX_SPECULATIVE_CONCURRENT = 4
        const val MAX_SPECULATIVE_QUEUE = 64
        const val MAX_RAIN_REQUESTS = 90
        const val FOREGROUND_RESERVE = 20
        const val RAIN_WINDOW_MILLIS = 60_000L
        private const val MAX_BACKOFF_MILLIS = 60_000L

        internal fun parseRetryAfterMillis(value: String?, wallMillis: Long): Long? {
            if (value.isNullOrBlank()) return null
            val trimmed = value.trim()
            trimmed.toLongOrNull()?.let { seconds ->
                if (seconds < 0) return null
                return if (seconds > Long.MAX_VALUE / 1_000) Long.MAX_VALUE else seconds * 1_000
            }
            return try {
                (ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - wallMillis)
                    .coerceAtLeast(0)
            } catch (_: Exception) {
                null
            }
        }

        private fun saturatingAdd(a: Long, b: Long): Long =
            if (b > 0 && a > Long.MAX_VALUE - b) Long.MAX_VALUE else a + b

        private fun remainingUntil(deadline: Long, now: Long): Long = when {
            deadline <= now -> 0L
            now < 0 && deadline > Long.MAX_VALUE + now -> Long.MAX_VALUE
            else -> deadline - now
        }
    }
}
