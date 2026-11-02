package net.droopia.hluweather.ui.map

import androidx.compose.runtime.Immutable
import java.net.URISyntaxException
import net.droopia.hluweather.data.model.RadarFrame
import net.droopia.hluweather.data.model.RadarProviderDescriptors
import net.droopia.hluweather.data.network.RadarTileUrlBuilder

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
