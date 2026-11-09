package net.droopia.hluweather.data.timezone

import com.esri.core.geometry.OperatorIntersects
import com.esri.core.geometry.Point
import com.esri.core.geometry.Polygon
import com.esri.core.geometry.SpatialReference
import com.github.luben.zstd.ZstdInputStream
import java.io.BufferedInputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import net.iakovlev.timeshape.proto.Geojson
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream

/** A fallback is not a claim that the location itself is in UTC. */
data class LocationTimeZone(val id: String, val isUtcFallback: Boolean) {
    val zone: TimeZone get() = TimeZone.of(id)
}

fun interface TimeZoneResolver {
    suspend fun resolve(latitude: Double, longitude: Double): LocationTimeZone
}

/** All matching raw IDs, including IDs the current Android tzdb does not recognize. */
internal fun interface BoundaryLookup {
    fun find(latitude: Double, longitude: Double): Set<String>
}

class OfflineTimeZoneResolver internal constructor(
    private val lookup: BoundaryLookup,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : TimeZoneResolver {
    constructor() : this(TimeshapeBoundaryLookup())

    private val mutex = Mutex()
    private val cache = object : LinkedHashMap<Pair<Double, Double>, LocationTimeZone>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<Double, Double>, LocationTimeZone>?) = size > 128
    }

    override suspend fun resolve(latitude: Double, longitude: Double): LocationTimeZone {
        require(latitude.isFinite() && latitude in -90.0..90.0 && longitude.isFinite() && longitude in -180.0..180.0)
        return withContext(dispatcher) {
            mutex.withLock {
                val key = latitude to longitude
                cache[key] ?: lookup.find(latitude, longitude).toLocationTimeZone().also { cache[key] = it }
            }
        }
    }
}

internal fun Set<String>.toLocationTimeZone(): LocationTimeZone {
    val id = singleOrNull()
    return if (id != null && runCatching { TimeZone.of(id) }.isSuccess) {
        LocationTimeZone(id, false)
    } else {
        LocationTimeZone("UTC", true)
    }
}

/**
 * Reads the unmodified Timeshape 2026b.29 archive. Unlike TimeZoneEngine, no
 * zone ID is converted to ZoneId until *all* intersecting polygons are found.
 * Stream one feature at a time to avoid retaining the world geometry in RAM.
 */
internal class TimeshapeBoundaryLookup(
    private val openArchive: () -> TarArchiveInputStream = {
        val resource = requireNotNull(TimeshapeBoundaryLookup::class.java.getResourceAsStream("/data.tar.zstd")) {
            "Timezone boundary data is missing"
        }
        TarArchiveInputStream(BufferedInputStream(ZstdInputStream(resource)))
    }
) : BoundaryLookup {
    override fun find(latitude: Double, longitude: Double): Set<String> {
        val point = Point(longitude, latitude)
        val operator = OperatorIntersects.local()
        val spatialReference = SpatialReference.create(4326)
        val matches = mutableSetOf<String>()
        openArchive().use { archive ->
            while (archive.nextTarEntry != null) {
                val feature = Geojson.Feature.parseFrom(archive)
                require(feature.propertiesCount == 1 && feature.getProperties(0).valueString.isNotBlank()) {
                    "Invalid timezone boundary record"
                }
                val geometry = feature.geometry
                val polygons = when {
                    geometry.hasPolygon() -> listOf(geometry.polygon)
                    geometry.hasMultiPolygon() -> geometry.multiPolygon.coordinatesList
                    else -> error("Invalid timezone boundary geometry")
                }
                if (polygons.any { operator.execute(it.toPolygon(), point, spatialReference, null) }) {
                    matches.add(feature.getProperties(0).valueString)
                }
            }
        }
        return matches
    }
}

private fun Geojson.Polygon.toPolygon(): Polygon = Polygon().also { polygon ->
    coordinatesList.forEach { ring ->
        require(ring.coordinatesCount > 0) { "Empty timezone boundary ring" }
        polygon.startPath(ring.getCoordinates(0).lon.toDouble(), ring.getCoordinates(0).lat.toDouble())
        ring.coordinatesList.drop(1).forEach { point -> polygon.lineTo(point.lon.toDouble(), point.lat.toDouble()) }
    }
}
