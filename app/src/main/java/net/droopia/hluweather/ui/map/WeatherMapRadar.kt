package net.droopia.hluweather.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import java.net.URISyntaxException
import kotlin.time.Duration
import net.droopia.hluweather.data.model.RadarFrame
import net.droopia.hluweather.data.model.RadarProviderDescriptors
import net.droopia.hluweather.data.network.RadarTileUrlBuilder
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.Anchor
import org.maplibre.compose.layers.RasterLayer
import org.maplibre.compose.sources.RasterTileSource
import org.maplibre.compose.sources.TileCoordinateSystem
import org.maplibre.compose.sources.TileSetOptions
import org.maplibre.compose.util.MaplibreComposable

internal const val WEATHER_RADAR_SOURCE_ID = "weather_radar_source"
internal const val WEATHER_RADAR_LAYER_ID = "weather_radar_layer"

/**
 * Explicit opt-in for the main Map destination. Pass null when hidden or no frame is available.
 * The caller owns frame selection, visibility and metadata; picker maps never opt in by default.
 */
@Immutable
data class WeatherMapRadarConfig(
    val host: String,
    val frame: RadarFrame,
    val opacity: Float = 0.75f
)

internal fun weatherRadarTileTemplate(radar: WeatherMapRadarConfig?): String? {
    if (radar == null || !radar.opacity.isFinite()) return null
    return try {
        val descriptor = RadarProviderDescriptors[radar.frame.provider]
        RadarTileUrlBuilder.buildTemplate(
            descriptor = descriptor,
            host = radar.host,
            path = radar.frame.path
        )
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: URISyntaxException) {
        null
    }
}

// Below the first symbol, keeping labels readable; styles without symbols fall back to the top.
// OpenFreeMap interleaves some roads/buildings with symbols, so those features can remain above
// radar. Compose markers and MapOverlay controls are above all style layers.
private val radarAnchor = Anchor.Below { it.type == "symbol" }

@Composable
@MaplibreComposable
internal fun WeatherMapRadar(radar: WeatherMapRadarConfig? = null) {
    if (radar == null || !radar.opacity.isFinite()) return
    val descriptor = RadarProviderDescriptors[radar.frame.provider]
    val template = remember(radar.host, radar.frame.path, radar.frame.provider) {
        weatherRadarTileTemplate(radar)
    } ?: return

    // 0.16.0 has no raster setTiles API. Reconcile only this source and its dependent layer when
    // the template changes, retaining their IDs. Opacity is a paint-only update. Keeping these in
    // rememberMapState's style content lets MapLibre reattach once per loaded style generation and
    // reject outgoing-generation work; never key/recreate the map or run a separate reload job.
    val source = remember(template, descriptor.tileOptions.size, descriptor.nativeMaxZoom) {
        RasterTileSource(
            id = WEATHER_RADAR_SOURCE_ID,
            tiles = listOf(template),
            tileSize = descriptor.tileOptions.size,
            options = TileSetOptions(
                minZoom = descriptor.nativeMinZoom,
                maxZoom = descriptor.nativeMaxZoom,
                tileCoordinateSystem = TileCoordinateSystem.XYZ
            )
        )
    }
    Anchor.At(radarAnchor) {
        RasterLayer(
            id = WEATHER_RADAR_LAYER_ID,
            source = source,
            opacity = const(radar.opacity.coerceIn(0f, 1f)),
            fadeDuration = const(Duration.ZERO)
        )
    }
}
