package net.droopia.hluweather.data.network

import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.supervisorScope
import kotlinx.io.files.Path
import org.maplibre.compose.map.MapRuntimeOptions
import org.maplibre.compose.resource.MapResourceError
import org.maplibre.compose.resource.MapResourceKind
import org.maplibre.compose.resource.MapResourceLoad
import org.maplibre.compose.resource.MapResourceLoadRequest
import org.maplibre.compose.resource.MapResourceProvider
import org.maplibre.compose.resource.MapResourceRequest

/** Public MapLibre hook. A malformed/unregistered radar alias fails closed rather than going HTTP. */
class RadarMapResourceProvider(
    private val registry: RadarTileRegistry,
    private val loader: RadarTileLoader,
    private val scheduler: RadarNetworkScheduler
) : MapResourceProvider {
    private val observationLock = Any()
    private val observations = mutableSetOf<RadarNativeTileObservation>()

    /** Records native demand failures separately from app-side coverage and memory-cache hits. */
    fun observeNativeTiles(source: RegisteredRadarSource, keys: Collection<RadarTileKey>): RadarNativeTileObservation {
        require(keys.all { source.tile(it.coordinate) == it }) { "Radar observation does not match its source" }
        return RadarNativeTileObservation(source, keys.toSet()) { observation ->
            synchronized(observationLock) {
                observations.remove(observation)
                observation.deactivate()
            }
        }.also { synchronized(observationLock) { observations.add(it) } }
    }

    override fun accepts(request: MapResourceRequest): Boolean =
        (request.kind == MapResourceKind.Tile || request.kind == MapResourceKind.Unknown) &&
            registry.isRadarUrl(request.url)

    override suspend fun load(request: MapResourceLoadRequest): MapResourceLoad = supervisorScope {
        if (!accepts(MapResourceRequest(request.url, request.kind)) || request.range != null) {
            return@supervisorScope MapResourceLoad.Failed(MapResourceError.Other, "Unsupported radar resource request")
        }
        // The native API cannot mark individual responses no-store. Never provide radar bytes to
        // a source that could write them to the native ambient cache, even on a cache hit.
        if (request.storagePolicy != MapResourceLoadRequest.StoragePolicy.Volatile) {
            return@supervisorScope MapResourceLoad.Failed(MapResourceError.Other, "Radar source must be volatile")
        }
        val native = registry.beginRequest(request.url,
            request.loadingMethod == MapResourceLoadRequest.LoadingMethod.CacheOnly)
            ?: return@supervisorScope staleResource()

        // A private consumer is cancelled when its own registration is released, without
        // cancelling another registration's consumer of the same remote in-flight tile.
        val load = async(start = CoroutineStart.LAZY) { loader.load(native.key, cacheOnly = native.cacheOnly) }
        if (!registry.attachRequest(native, load)) {
            load.cancel()
            registry.finishRequest(native, failed = true, cancelled = true)
            return@supervisorScope staleResource()
        }
        var finished = false
        try {
            var cancellation: CancellationException? = null
            // Native priorData cannot establish no-store policy. Only the app LRU supplies
            // fresh bytes and validators. Optional registrations always force cache-only here.
            val result = try {
                val payload = load.await()
                val modified = payload.modifiedAtMillis?.let(Instant::fromEpochMilliseconds)
                val expires = Instant.fromEpochMilliseconds(if (payload.cacheable) payload.expiresAtMillis else 0)
                payload.bytes?.let {
                    MapResourceLoad.Bytes(it, payload.etag, payload.mustRevalidate || !payload.cacheable, modified, expires)
                } ?: MapResourceLoad.NoContent(modified, expires)
            } catch (error: CancellationException) {
                cancellation = error
                null
            } catch (error: Exception) {
                val reason = when {
                    error is RadarApiException && error.statusCode == 429 -> MapResourceError.RateLimit
                    error is RadarApiException && error.statusCode == 404 -> MapResourceError.NotFound
                    error is RadarApiException && error.statusCode in 500..599 -> MapResourceError.Server
                    error is IOException -> MapResourceError.Connection
                    else -> MapResourceError.Other
                }
                MapResourceLoad.Failed(
                    reason = reason,
                    message = error.message ?: "Radar tile unavailable",
                    retryAfter = if (reason == MapResourceError.RateLimit) {
                        scheduler.rateLimitedUntil.value?.let(Instant::fromEpochMilliseconds)
                    } else null
                )
            }
            val live = registry.finishRequest(native, result is MapResourceLoad.Failed,
                cancelled = cancellation != null) { completed, failed ->
                synchronized(observationLock) {
                    observations.forEach { it.record(completed.source, completed.key, completed.sequence, failed) }
                }
            }
            finished = true
            cancellation?.let {
                currentCoroutineContext().ensureActive()
                if (live) throw it
            }
            if (!live) staleResource() else requireNotNull(result)
        } finally {
            if (!finished) registry.finishRequest(native, failed = true, cancelled = true)
        }
    }

    private fun staleResource() = MapResourceLoad.Failed(MapResourceError.Other, "Unknown or obsolete radar tile")
}

/** One native slot's observation; closing it is idempotent. */
class RadarNativeTileObservation internal constructor(
    private val source: RegisteredRadarSource,
    private val keys: Set<RadarTileKey>,
    private val release: (RadarNativeTileObservation) -> Unit
) : AutoCloseable {
    private val lock = Any()
    private val results = mutableMapOf<RadarTileKey, Pair<Long, Boolean>>()
    private var closed = false

    internal fun record(source: RegisteredRadarSource, key: RadarTileKey, sequence: Long, failed: Boolean) {
        synchronized(lock) {
            if (!closed && this.source.registration === source.registration && key in keys &&
                sequence > (results[key]?.first ?: 0)) {
                results[key] = sequence to failed
            }
        }
    }
    internal fun deactivate() { synchronized(lock) { closed = true } }
    val hasFailures: Boolean get() = synchronized(lock) { results.values.any { it.second } }
    override fun close() = release(this)
}

/** Options for the map owner in task 4.1; ordinary picker/route maps keep their default runtime. */
fun radarMapRuntimeOptions(cacheDirectory: File, provider: RadarMapResourceProvider): MapRuntimeOptions =
    MapRuntimeOptions(
        cacheFile = Path(File(cacheDirectory, RADAR_MAP_CACHE_FILENAME).absolutePath),
        maximumCacheSizeBytes = 32L * 1024 * 1024,
        resourceProvider = provider
    )

const val RADAR_MAP_CACHE_FILENAME = "radar-maplibre-volatile-v2.db"
