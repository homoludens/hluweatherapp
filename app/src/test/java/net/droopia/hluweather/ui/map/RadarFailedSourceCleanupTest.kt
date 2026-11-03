package net.droopia.hluweather.ui.map

import net.droopia.hluweather.data.model.RadarFrame
import net.droopia.hluweather.data.model.RadarFrameType
import net.droopia.hluweather.data.model.RadarProvider
import net.droopia.hluweather.data.model.RadarTileCoordinate
import net.droopia.hluweather.data.network.RadarTileRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarFailedSourceCleanupTest {
    private val frame = RadarFrame(1, "/radar/frame", RadarFrameType.OBSERVED, RadarProvider.RAINVIEWER)
    private val coordinate = RadarTileCoordinate(2, 1, 1)

    @Test
    fun failedAndThrowingRemovalsRetainTheLeaseAndCapacityUntilARetrySucceeds() {
        val registry = RadarTileRegistry()
        val failed = registry.register("https://tiles.example.test", frame)
        val other = registry.register("https://tiles.example.test", frame)
        val cleanup = RadarFailedSourceCleanup()
        var attempts = 0
        var releases = 0
        cleanup.retain(2, remove = {
            when (++attempts) {
                1 -> false
                2 -> error("Native removal failed")
                else -> true
            }
        }, release = { releases++; registry.unregister(failed) })

        repeat(2) {
            cleanup.retry()
            assertEquals(1, cleanup.size)
            assertTrue(cleanup.contains(2))
            assertEquals(2, registry.registeredCount)
            assertEquals(failed.tile(coordinate), registry.resolve(url(failed.template)))
            assertEquals(other.tile(coordinate), registry.resolve(url(other.template)))
            assertEquals(0, releases)
        }
        cleanup.retry()
        assertEquals(0, cleanup.size)
        assertFalse(cleanup.contains(2))
        assertNull(registry.resolve(url(failed.template)))
        assertEquals(other.tile(coordinate), registry.resolve(url(other.template)))
        assertEquals(1, releases)
        cleanup.releaseAfterMap()
        assertEquals(1, releases)
        registry.unregister(other)
    }

    @Test
    fun unknownPartialAddIsRetainedUntilTheMapIsDestroyed() {
        val registry = RadarTileRegistry()
        val lease = registry.register("https://tiles.example.test", frame)
        val cleanup = RadarFailedSourceCleanup()
        var releases = 0
        cleanup.retain(1, remove = { false }, release = { releases++; registry.unregister(lease) })
        cleanup.retry()
        assertEquals(1, cleanup.size)
        assertEquals(1, registry.registeredCount)
        assertEquals(0, releases)
        cleanup.releaseAfterMap()
        assertEquals(0, cleanup.size)
        assertEquals(0, registry.registeredCount)
        assertEquals(1, releases)
        cleanup.releaseAfterMap()
        assertEquals(1, releases)
    }

    private fun url(template: String) = template.replace("{z}", "2").replace("{x}", "1").replace("{y}", "1")
}
