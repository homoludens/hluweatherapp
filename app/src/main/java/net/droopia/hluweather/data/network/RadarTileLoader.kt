package net.droopia.hluweather.data.network

import java.io.IOException
import java.util.LinkedHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

class RadarTileNotCachedException : IOException("Radar tile is not cached")

/** Invalidated synchronously when a speculative plan is replaced or stopped. */
class RadarSpeculativeOwner {
    @Volatile
    internal var active = true
        private set

    fun cancel() { active = false }
}

/** Compressed-byte LRU and consumer-owned in-flight requests shared by renderer and prefetch. */
class RadarTileLoader(
    private val scope: CoroutineScope,
    private val scheduler: RadarNetworkScheduler,
    private val transport: RadarTileTransport,
    private val wallMillis: () -> Long = System::currentTimeMillis,
    private val maxCacheBytes: Long = MAX_CACHE_BYTES,
    private val maxCacheEntries: Int = MAX_CACHE_ENTRIES
) {
    private class Entry(
        val result: CompletableDeferred<RadarTilePayload>,
        val priority: RadarRequestPriority,
        var foregroundConsumers: Int,
        @Volatile var speculativeOwners: List<RadarSpeculativeOwner>,
        var job: Job? = null
    )

    private val lock = Any()
    private val cache = LinkedHashMap<RadarTileKey, RadarTilePayload>(16, 0.75f, true)
    private val inflight = mutableMapOf<RadarTileKey, Entry>()
    private var bytes = 0L

    init { require(maxCacheBytes >= 0 && maxCacheEntries >= 0) }

    internal val cachedBytes: Long get() = synchronized(lock) { bytes }
    internal val cachedEntries: Int get() = synchronized(lock) { cache.size }

    suspend fun load(
        key: RadarTileKey,
        speculative: Boolean = false,
        cacheOnly: Boolean = false,
        forceNetwork: Boolean = false,
        speculativeOwner: RadarSpeculativeOwner? = null
    ): RadarTilePayload {
        val owner = if (speculative) speculativeOwner ?: RadarSpeculativeOwner() else null
        if (owner?.active == false) throw CancellationException("Obsolete radar speculation")
        var start: Job? = null
        var priorityChanged = false
        val entry = synchronized(lock) {
            val cached = cache[key]
            if (!forceNetwork && cached != null && cached.expiresAtMillis > wallMillis()) return cached
            if (cacheOnly) throw RadarTileNotCachedException()
            val existing = inflight[key]
            if (existing != null) {
                if (owner != null) existing.speculativeOwners = existing.speculativeOwners + owner
                else existing.foregroundConsumers++
                if (!speculative && existing.priority.speculative) {
                    existing.priority.speculative = false
                    priorityChanged = true
                }
                existing
            } else {
                val created = Entry(
                    result = CompletableDeferred(),
                    priority = RadarRequestPriority(speculative),
                    foregroundConsumers = if (speculative) 0 else 1,
                    speculativeOwners = listOfNotNull(owner)
                )
                created.priority.mayRun = {
                    !created.priority.speculative || created.speculativeOwners.any { it.active }
                }
                inflight[key] = created
                val job = scope.launch(start = CoroutineStart.LAZY) {
                    try {
                        val payload = fetchWithRetry(key, created.priority, cached)
                        synchronized(lock) {
                            // A cancelled, superseded consumer may complete non-cooperatively.
                            // It must not overwrite a newer in-flight request's cached result.
                            if (inflight[key] === created && created.job?.isActive == true) {
                                storeLocked(key, payload)
                            }
                        }
                        created.result.complete(payload)
                    } catch (error: Exception) {
                        created.result.completeExceptionally(error)
                    }
                }
                created.job = job
                job.invokeOnCompletion { failure ->
                    if (failure != null) created.result.completeExceptionally(failure)
                    synchronized(lock) {
                        if (inflight[key] === created) inflight.remove(key)
                    }
                }
                start = job
                created
            }
        }
        if (priorityChanged) scheduler.priorityChanged(entry.priority)
        start?.start()
        var retryForeground = false
        val payload = try {
            entry.result.await()
        } catch (error: RadarObsoleteSpeculationException) {
            // A display consumer can join after the scheduler invalidated queued speculation but
            // before that cancellation reaches the shared entry. Start fresh for that consumer.
            if (speculative) throw error
            currentCoroutineContext().ensureActive()
            retryForeground = true
            null
        } finally {
            var demoted = false
            var cancel: Job? = null
            synchronized(lock) {
                if (owner != null) entry.speculativeOwners = entry.speculativeOwners - owner
                else entry.foregroundConsumers--
                if (entry.foregroundConsumers == 0 && !entry.priority.speculative) {
                    entry.priority.speculative = true
                    demoted = true
                }
                if (entry.result.isCompleted && inflight[key] === entry) inflight.remove(key)
                if (entry.foregroundConsumers == 0 && entry.speculativeOwners.isEmpty() && !entry.result.isCompleted) {
                    if (inflight[key] === entry) inflight.remove(key)
                    cancel = entry.job
                }
            }
            if (demoted) scheduler.priorityChanged(entry.priority)
            cancel?.cancel()
        }
        return if (retryForeground) load(key, cacheOnly = cacheOnly, forceNetwork = forceNetwork)
        else requireNotNull(payload)
    }

    /** A failure or cancellation rejects the entire coverage; transparent/204 tiles remain valid. */
    suspend fun awaitCoverage(keys: List<RadarTileKey>): List<RadarTilePayload> = coroutineScope {
        keys.distinct().map { key -> async { load(key) } }.awaitAll()
    }

    private suspend fun fetchWithRetry(
        key: RadarTileKey,
        priority: RadarRequestPriority,
        initialPrior: RadarTilePayload?
    ): RadarTilePayload {
        var retries = 0
        var retryDelay = 1_000L
        while (true) {
            try {
                val response = scheduler.execute(key.provider, priority) {
                    val result = withTimeoutOrNull(REQUEST_TIMEOUT_MILLIS) { transport.fetch(key, initialPrior) }
                        ?: throw IOException("Radar tile request timed out")
                    if (result.status !in listOf(200, 204, 304)) {
                        throw RadarApiException(
                            "Radar tile service returned HTTP ${result.status}", result.status, result.retryAfter
                        )
                    }
                    result
                }
                val payload = when (response.status) {
                    200 -> response.payload.also {
                        if (it.bytes == null) throw IOException("Radar tile response has no body")
                    }
                    204 -> response.payload.copy(bytes = null)
                    304 -> {
                        val previous = initialPrior ?: throw IOException("Radar tile returned 304 without a cached body")
                        if (previous.etag == null && previous.modifiedAtMillis == null) {
                            throw IOException("Radar tile returned 304 without a validator")
                        }
                        response.payload.copy(
                            bytes = previous.bytes,
                            etag = response.payload.etag ?: previous.etag,
                            modifiedAtMillis = response.payload.modifiedAtMillis ?: previous.modifiedAtMillis,
                            // A 304 without cache directives must not turn an earlier no-cache
                            // response into a fresh five-minute entry. Conservatively revalidate.
                            expiresAtMillis = if (previous.mustRevalidate) wallMillis() else response.payload.expiresAtMillis,
                            mustRevalidate = response.payload.mustRevalidate || previous.mustRevalidate
                        )
                    }
                    else -> throw RadarApiException("Radar tile service returned HTTP ${response.status}", response.status)
                }
                if ((payload.bytes?.size ?: 0) > KtorRadarTileTransport.MAX_TILE_BYTES) throw RadarTileTooLargeException()
                return payload
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (!error.isRetryable() || retries >= MAX_RETRIES) throw error
                delay(retryDelay)
                retryDelay *= 2
                retries++
            }
        }
    }

    private fun Exception.isRetryable(): Boolean = when (this) {
        is RadarSpeculativeQueueFullException, is RadarTileTooLargeException -> false
        is RadarApiException -> statusCode == 408 || statusCode == 429 || statusCode in 500..599
        is IOException -> true
        else -> false
    }

    private fun storeLocked(key: RadarTileKey, payload: RadarTilePayload) {
        cache.remove(key)?.let { bytes -= weight(it) }
        val weight = weight(payload)
        if (!payload.cacheable || weight > maxCacheBytes) return
        cache[key] = payload
        bytes += weight
        val iterator = cache.entries.iterator()
        while ((bytes > maxCacheBytes || cache.size > maxCacheEntries) && iterator.hasNext()) {
            bytes -= weight(iterator.next().value)
            iterator.remove()
        }
    }

    // Also bound successful no-content entries, which have no compressed body.
    private fun weight(payload: RadarTilePayload): Long = (payload.bytes?.size ?: 0).toLong().coerceAtLeast(1)

    companion object {
        const val MAX_CACHE_BYTES = 16L * 1024 * 1024
        // No-content/very small payloads must not turn the byte limit into unbounded map overhead.
        private const val MAX_CACHE_ENTRIES = 4_096
        private const val MAX_RETRIES = 2
        private const val REQUEST_TIMEOUT_MILLIS = 15_000L
    }
}
