package net.droopia.hluweather.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarTileCoverageTest {
    private val rain = RadarProviderDescriptors[RadarProvider.RAINVIEWER]
    private val libre = RadarProviderDescriptors[RadarProvider.LIBREWXR]

    @Test
    fun sourceTileSizeAndNativeZoomAreAppliedBeforeOverzoom() {
        val viewport = bounds(-0.1, -0.1, 0.1, 0.1, 20.0)
        assertEquals(setOf(7), RadarTileCoverage.tiles(viewport, rain).map { it.zoom }.toSet())
        assertEquals(setOf(12), RadarTileCoverage.tiles(viewport, libre).map { it.zoom }.toSet())
        assertEquals(setOf(4), RadarTileCoverage.tiles(viewport.copy(zoom = 3.3), rain).map { it.zoom }.toSet())
        assertEquals(setOf(5), RadarTileCoverage.tiles(viewport.copy(zoom = 3.6), rain).map { it.zoom }.toSet())
        assertEquals(setOf(4), RadarTileCoverage.tiles(viewport.copy(zoom = 3.49), rain).map { it.zoom }.toSet())
        assertEquals(setOf(5), RadarTileCoverage.tiles(viewport.copy(zoom = 3.5), rain).map { it.zoom }.toSet())
        val largerTiles = rain.copy(tileOptions = rain.tileOptions.copy(size = 512))
        assertEquals(setOf(4), RadarTileCoverage.tiles(viewport.copy(zoom = 3.6), largerTiles).map { it.zoom }.toSet())
        assertEquals(setOf(0), RadarTileCoverage.tiles(viewport.copy(zoom = -5.0), rain).map { it.zoom }.toSet())
    }

    @Test
    fun actualVisibleBoundsConservativelyCoverRotatedViewport() {
        // The map supplies the expanded north-aligned bounds of its rotated/tilted viewport.
        val tiles = RadarTileCoverage.tiles(bounds(-90.0, 0.0, 0.0, 60.0, 1.0), rain)
        assertEquals(listOf(RadarTileCoordinate(2, 1, 1)), tiles)
        val expanded = RadarTileCoverage.tiles(bounds(-100.0, -5.0, 5.0, 70.0, 1.0), rain)
        assertTrue(expanded.containsAll(tiles))
        assertTrue(expanded.size > tiles.size)
    }

    @Test
    fun antimeridianAndContinuousWorldCopiesUseTheSameCanonicalTiles() {
        val crossing = bounds(170.0, -1.0, -170.0, 1.0, 2.0, 180.0)
        val continuous = crossing.copy(east = 190.0)
        val wrapped = crossing.copy(west = 530.0, east = 550.0, centerLongitude = 540.0)
        val expected = setOf(
            RadarTileCoordinate(3, 7, 3), RadarTileCoordinate(3, 0, 3),
            RadarTileCoordinate(3, 7, 4), RadarTileCoordinate(3, 0, 4)
        )
        assertEquals(expected, RadarTileCoverage.tiles(crossing, rain).toSet())
        assertEquals(expected, RadarTileCoverage.tiles(continuous, rain).toSet())
        assertEquals(expected, RadarTileCoverage.tiles(wrapped, rain).toSet())
    }

    @Test
    fun multipleWorldsAreDeduplicatedAndLatitudesClamped() {
        val tiles = RadarTileCoverage.tiles(bounds(-600.0, -90.0, 600.0, 90.0, 0.0), rain)
        assertEquals(4, tiles.size)
        assertEquals(tiles.size, tiles.toSet().size)
        assertEquals((0..1).toSet(), tiles.map { it.x }.toSet())
        assertEquals((0..1).toSet(), tiles.map { it.y }.toSet())
    }

    @Test
    fun reversedWholeWorldBoundsDoNotCollapseToOneMeridian() {
        val reversed = bounds(180.0, -1.0, -180.0, 1.0, 2.0, 0.0)
        val continuous = reversed.copy(west = -180.0, east = 180.0)
        assertEquals(RadarTileCoverage.tiles(continuous, rain).toSet(), RadarTileCoverage.tiles(reversed, rain).toSet())
        assertEquals((0..7).toSet(), RadarTileCoverage.tiles(reversed, rain).map { it.x }.toSet())
    }

    @Test
    fun nearestTilesComeFirstRegardlessOfWrappedWorld() {
        val tiles = RadarTileCoverage.tiles(bounds(-170.0, -1.0, 170.0, 1.0, 2.0, 120.0), rain)
        assertEquals(6, tiles.first().x)
    }

    @Test(expected = IllegalArgumentException::class)
    fun impossibleWholeWorldHighZoomCannotAllocateUnboundedMemory() {
        RadarTileCoverage.tiles(bounds(-180.0, -85.0, 180.0, 85.0, 20.0), libre)
    }

    private fun bounds(
        west: Double, south: Double, east: Double, north: Double, zoom: Double,
        centerLongitude: Double = (west + east) / 2,
        centerLatitude: Double = (south + north) / 2
    ) = RadarViewportBounds(west, south, east, north, zoom, centerLongitude, centerLatitude)
}
