package net.droopia.hluweather.ui.map

import java.io.File
import net.droopia.hluweather.data.model.RadarFrame
import net.droopia.hluweather.data.model.RadarFrameType
import net.droopia.hluweather.data.model.RadarMetadata
import net.droopia.hluweather.data.model.RadarProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherMapRadarTest {
    @Test
    fun radarTemplatesRemainProviderValidatedAndUseSupportedOptions() {
        val radar = config()
        assertEquals("https://tiles.example.test/v2/radar/100/256/{z}/{x}/{y}/2/1_1.png", weatherRadarTileTemplate(radar))
        assertEquals(weatherRadarTileTemplate(radar), weatherRadarTileTemplate(radar.copy(
            frame = radar.frame.copy(provider = RadarProvider.RAINVIEWER)
        )))
        listOf(
            null,
            radar.copy(host = "http://tiles.example.test"),
            radar.copy(host = "not a URI"),
            radar.copy(frame = radar.frame.copy(path = "//other.test/frame")),
            radar.copy(opacity = Float.NaN)
        ).forEach { assertNull(weatherRadarTileTemplate(it)) }
    }

    @Test
    fun pickerMapsDoNotOptIntoRadarRuntime() {
        listOf("ui/locationpicker/LocationPickerScreen.kt", "ui/weatherroute/RouteEndpointPicker.kt", "ui/weatherroute/WeatherRouteMap.kt")
            .forEach { assertFalse(source(it).contains("radarResources =")) }
        assertTrue(source("ui/weather/WeatherScreen.kt").contains("radarResources = radarResources"))
    }

    @Test
    fun viewport_prefetch_requires_visible_matching_radar_and_metadata() {
        val radar = config()
        val metadata = RadarMetadata.normalized(
            radar.frame.provider,
            radar.host,
            listOf(radar.frame)
        )

        assertTrue(shouldPlanRadarPrefetch(
            radar, RadarProvider.LIBREWXR, metadata,
            lifecycleStarted = true, styleReady = true, hasViewport = true
        ))
        assertFalse(shouldPlanRadarPrefetch(
            null, RadarProvider.LIBREWXR, metadata,
            lifecycleStarted = true, styleReady = true, hasViewport = true
        ))
        assertFalse(shouldPlanRadarPrefetch(
            radar, RadarProvider.LIBREWXR, metadata,
            lifecycleStarted = false, styleReady = true, hasViewport = true
        ))
        assertFalse(shouldPlanRadarPrefetch(
            radar, RadarProvider.LIBREWXR, metadata,
            lifecycleStarted = true, styleReady = false, hasViewport = true
        ))
        assertFalse(shouldPlanRadarPrefetch(
            radar, RadarProvider.RAINVIEWER, metadata,
            lifecycleStarted = true, styleReady = true, hasViewport = true
        ))
        assertFalse(shouldPlanRadarPrefetch(
            radar, RadarProvider.LIBREWXR, null,
            lifecycleStarted = true, styleReady = true, hasViewport = true
        ))
        assertFalse(shouldPlanRadarPrefetch(
            radar, RadarProvider.LIBREWXR, metadata,
            lifecycleStarted = true, styleReady = true, hasViewport = false
        ))
    }

    private fun config() = WeatherMapRadarConfig(
        "https://tiles.example.test", RadarFrame(100, "/v2/radar/100", RadarFrameType.OBSERVED)
    )

    private fun source(path: String): String =
        File("src/main/java/net/droopia/hluweather/$path").takeIf { it.isFile }
            ?.readText() ?: File("app/src/main/java/net/droopia/hluweather/$path").readText()
}
