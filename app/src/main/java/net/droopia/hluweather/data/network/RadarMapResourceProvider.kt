package net.droopia.hluweather.data.network

import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant
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
    fun observeNativeTiles(keys: Collection<RadarTileKey>): RadarNativeTileObservation =
        RadarNativeTileObservation(keys.toSet()) { observation ->
            synchronized(observationLock) { observations.remove(observation) }
        }.also { synchronized(observationLock) { observations.add(it) } }

    override fun accepts(request: MapResourceRequest): Boolean =
        (request.kind == MapResourceKind.Tile || request.kind == MapResourceKind.Unknown) &&
            registry.isRadarUrl(request.url)

    override suspend fun load(request: MapResourceLoadRequest): MapResourceLoad {
        if (!accepts(MapResourceRequest(request.url, request.kind)) || request.range != null) {
            return MapResourceLoad.Failed(MapResourceError.Other, "Unsupported radar resource request")
        }
        // The native API cannot mark individual responses no-store. Never provide radar bytes to
        // a source that could write them to the native ambient cache, even on a cache hit.
        if (request.storagePolicy != MapResourceLoadRequest.StoragePolicy.Volatile) {
            return MapResourceLoad.Failed(MapResourceError.Other, "Radar source must be volatile")
        }
        val key = registry.resolve(request.url)
            ?: return MapResourceLoad.Failed(MapResourceError.Other, "Unknown or unsupported radar tile")
        // Native priorData cannot report whether the original response was no-store. Only the
        // app LRU retains cacheable bodies and their validators for conditional requests.
        val result = try {
            val payload = loader.load(
                key,
                // NetworkOnly excludes MapLibre's ambient cache; this provider's own fresh,
                // policy-aware LRU is the backing network resource and enables prefetch reuse.
                cacheOnly = request.loadingMethod == MapResourceLoadRequest.LoadingMethod.CacheOnly
            )
            val modified = payload.modifiedAtMillis?.let(Instant::fromEpochMilliseconds)
            // Volatility prevents native disk writes, but cannot itself tell the engine that an
            // individual response was no-store. Do not advertise such a response as fresh.
            val expires = Instant.fromEpochMilliseconds(if (payload.cacheable) payload.expiresAtMillis else 0)
            payload.bytes?.let {
                MapResourceLoad.Bytes(it, payload.etag, payload.mustRevalidate || !payload.cacheable, modified, expires)
            } ?: MapResourceLoad.NoContent(modified, expires)
        } catch (error: CancellationException) {
            throw error
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
        // Cache-only misses are a normal native fallback to network, not failed coverage.
        if (request.loadingMethod != MapResourceLoadRequest.LoadingMethod.CacheOnly) {
            synchronized(observationLock) {
                observations.forEach { it.record(key, result is MapResourceLoad.Failed) }
            }
        }
        return result
    }
}

/** One native slot's observation; closing it is idempotent. */
class RadarNativeTileObservation internal constructor(
    private val keys: Set<RadarTileKey>,
    private val release: (RadarNativeTileObservation) -> Unit
) : AutoCloseable {
    private val failures = ConcurrentHashMap.newKeySet<RadarTileKey>()
    internal fun record(key: RadarTileKey, failed: Boolean) {
        if (key in keys) {
            if (failed) failures.add(key) else failures.remove(key)
        }
    }
    val hasFailures: Boolean get() = failures.isNotEmpty()
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
