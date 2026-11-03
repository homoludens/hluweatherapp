package net.droopia.hluweather.ui.map

import net.droopia.hluweather.data.model.RadarFrame
import net.droopia.hluweather.data.model.RadarFrameType
import net.droopia.hluweather.data.model.RadarProvider
import net.droopia.hluweather.data.model.RadarTileCoverage
import net.droopia.hluweather.data.network.RadarTileRegistry
import org.maplibre.compose.sources.TileCoordinateSystem
import org.junit.Assert.assertEquals
import org.junit.Test

class RadarRasterTileSourceTest {
    @Test
    fun sourceUses1024PointTilesAndProviderSpecificCoarseMaximumZoom() {
        val registry = RadarTileRegistry()
        listOf(
            RadarProvider.LIBREWXR to 10,
            RadarProvider.RAINVIEWER to 5
        ).forEachIndexed { index, (provider, maxZoom) ->
            val frame = RadarFrame(100, "/radar/100", RadarFrameType.OBSERVED, provider)
            val lease = registry.register("https://tiles.example.test", frame)
            try {
                val config = radarRasterTileSourceConfig(lease)
                assertEquals(lease.template, config.template)
                assertEquals(RadarTileCoverage.RENDER_TILE_SIZE, config.tileSize)
                assertEquals(1024, config.tileSize)
                assertEquals(0, config.options.minZoom)
                assertEquals(maxZoom, config.options.maxZoom)
                assertEquals(TileCoordinateSystem.XYZ, config.options.tileCoordinateSystem)
                radarRasterTileSource("radar-$index", lease)
            } finally {
                registry.unregister(lease)
            }
        }
    }
}
