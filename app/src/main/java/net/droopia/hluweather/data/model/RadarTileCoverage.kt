package net.droopia.hluweather.data.model

import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

data class RadarTileCoordinate(val zoom: Int, val x: Int, val y: Int)

/**
 * The map's actual north-aligned visible bounds, not an unrotated camera rectangle. Longitude may
 * be continuous outside ±180 degrees, as in MapLibre's VisibleBounds, or cross the antimeridian.
 */
data class RadarViewportBounds(
    val west: Double,
    val south: Double,
    val east: Double,
    val north: Double,
    val zoom: Double,
    val centerLongitude: Double,
    val centerLatitude: Double
)

/** Conservative Web Mercator coverage, in nearest-to-camera order with world copies deduplicated. */
object RadarTileCoverage {
    // Display supported 256-pixel provider images over a 1024-point map tile. Keeping this fixed
    // prevents native source selection from refining beyond the two-level-coarser coverage zoom.
    const val RENDER_TILE_SIZE = 1024
    private const val ZOOM_REDUCTION = 2

    fun renderMaxZoom(descriptor: RadarProviderDescriptor): Int =
        (descriptor.nativeMaxZoom - ZOOM_REDUCTION).coerceAtLeast(descriptor.nativeMinZoom)

    fun tiles(bounds: RadarViewportBounds, descriptor: RadarProviderDescriptor): List<RadarTileCoordinate> {
        require(listOf(
            bounds.west, bounds.south, bounds.east, bounds.north, bounds.zoom,
            bounds.centerLongitude, bounds.centerLatitude
        ).all(Double::isFinite)) { "Radar viewport must be finite" }
        require(bounds.south <= bounds.north) { "South must not exceed north" }

        // Native raster sources round the adjusted zoom; camera zoom uses 512-pixel world tiles.
        val sourceZoom = floor(bounds.zoom + ln(512.0 / RENDER_TILE_SIZE) / ln(2.0) + 0.5)
            .toInt().coerceIn(descriptor.nativeMinZoom, renderMaxZoom(descriptor))
        val count = 1 shl sourceZoom
        fun normalize(longitude: Double): Double =
            ((longitude % 360.0 + 540.0) % 360.0) - 180.0
        val west = normalize(bounds.west)
        val span = when {
            bounds.east >= bounds.west -> bounds.east - bounds.west
            // Opposite representations of the same meridian may span a whole world. Treat a
            // reversed span of at least one world conservatively rather than collapsing it to 0.
            bounds.west - bounds.east >= 360.0 -> 360.0
            else -> (normalize(bounds.east) - west + 360.0) % 360.0
        }

        fun worldX(longitude: Double) = (longitude + 180.0) / 360.0 * count
        fun worldY(latitude: Double): Double {
            val radians = latitude.coerceIn(-MAX_LATITUDE, MAX_LATITUDE) * PI / 180.0
            return ((1.0 - asinh(tan(radians)) / PI) / 2.0 * count).coerceIn(0.0, count.toDouble())
        }
        fun lastIndex(end: Double, start: Double): Long =
            floor(if (end > start) Math.nextDown(end) else end).toLong()

        val xStart = floor(worldX(west)).toLong()
        val xEnd = if (span >= 360.0) xStart + count - 1 else lastIndex(worldX(west + span), worldX(west))
        val northY = worldY(bounds.north)
        val southY = worldY(bounds.south)
        val yStart = floor(northY).toInt().coerceIn(0, count - 1)
        val yEnd = lastIndex(southY, northY).toInt().coerceIn(0, count - 1)
        val tileCount = (xEnd - xStart + 1) * (yEnd - yStart + 1).toLong()
        require(tileCount in 1..MAX_COVERAGE_TILES) { "Radar viewport coverage is too large" }

        val centerX = worldX(normalize(bounds.centerLongitude))
        val centerY = worldY(bounds.centerLatitude)
        return buildList(tileCount.toInt()) {
            for (x in xStart..xEnd) {
                val canonicalX = Math.floorMod(x, count.toLong()).toInt()
                for (y in yStart..yEnd) add(RadarTileCoordinate(sourceZoom, canonicalX, y))
            }
        }.distinct().sortedWith(compareBy<RadarTileCoordinate> {
            val canonicalCenterX = ((centerX % count) + count) % count
            val direct = kotlin.math.abs(it.x + 0.5 - canonicalCenterX)
            val dx = minOf(direct, count - direct)
            val dy = it.y + 0.5 - centerY
            dx * dx + dy * dy
        }.thenBy { it.y }.thenBy { it.x })
    }

    private const val MAX_LATITUDE = 85.0511287798066
    // Reject impossible camera snapshots rather than allocating a whole-world high-zoom list.
    private const val MAX_COVERAGE_TILES = 16_384L
}
