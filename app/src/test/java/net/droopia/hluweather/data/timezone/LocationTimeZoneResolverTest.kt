package net.droopia.hluweather.data.timezone

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import net.iakovlev.timeshape.proto.Geojson
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationTimeZoneResolverTest {
    @Test
    fun bundledBoundariesResolveBerlin() {
        assertEquals(setOf("Europe/Berlin"), TimeshapeBoundaryLookup().find(52.52, 13.40))
    }

    @Test
    fun preservesUnknownRawIdsAndDetectsOverlappingPolygons() {
        val archive = archive(feature("Europe/Berlin"), feature("Unknown/Future"))
        val lookup = TimeshapeBoundaryLookup { TarArchiveInputStream(ByteArrayInputStream(archive)) }
        val matches = lookup.find(0.5, 0.5)
        assertEquals(setOf("Europe/Berlin", "Unknown/Future"), matches)
        assertEquals(LocationTimeZone("UTC", true), matches.toLocationTimeZone())
        assertEquals(LocationTimeZone("UTC", true), setOf("Unknown/Future").toLocationTimeZone())
        assertEquals(LocationTimeZone("UTC", true), emptySet<String>().toLocationTimeZone())
        assertEquals(LocationTimeZone("UTC", false), setOf("UTC").toLocationTimeZone())
        assertEquals(LocationTimeZone("Europe/Berlin", false), setOf("Europe/Berlin").toLocationTimeZone())
        assertTrue(lookup.find(10.0, 10.0).isEmpty())
    }

    @Test
    fun concurrentCallsAreCachedAndLookupRunsOffCallerThread() = runBlocking {
        val caller = Thread.currentThread().id
        var worker = caller
        var calls = 0
        val resolver = OfflineTimeZoneResolver(BoundaryLookup { _, _ ->
            worker = Thread.currentThread().id
            calls++
            setOf("Europe/Berlin")
        })
        val results = (1..8).map { async { resolver.resolve(52.52, 13.40) } }.awaitAll()
        assertEquals(1, calls)
        assertNotEquals(caller, worker)
        assertTrue(results.all { it == LocationTimeZone("Europe/Berlin", false) })
        resolver.resolve(52.53, 13.40)
        assertEquals(2, calls)
    }

    private fun feature(id: String): Geojson.Feature {
        val ring = Geojson.LineString.newBuilder().apply {
            listOf(0f to 0f, 0f to 1f, 1f to 1f, 1f to 0f, 0f to 0f).forEach { (lon, lat) ->
                addCoordinates(Geojson.Position.newBuilder().setLon(lon).setLat(lat))
            }
        }
        return Geojson.Feature.newBuilder()
            .setGeometry(Geojson.Geometry.newBuilder().setPolygon(Geojson.Polygon.newBuilder().addCoordinates(ring)))
            .addProperties(Geojson.Property.newBuilder().setKey("tzid").setValueString(id))
            .build()
    }

    private fun archive(vararg features: Geojson.Feature): ByteArray {
        val bytes = ByteArrayOutputStream()
        TarArchiveOutputStream(bytes).use { tar ->
            features.forEachIndexed { index, feature ->
                val data = feature.toByteArray()
                tar.putArchiveEntry(TarArchiveEntry("$index.pb").apply { size = data.size.toLong() })
                tar.write(data)
                tar.closeArchiveEntry()
            }
        }
        return bytes.toByteArray()
    }
}
