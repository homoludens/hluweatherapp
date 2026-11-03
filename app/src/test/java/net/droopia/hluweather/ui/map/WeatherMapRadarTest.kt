package net.droopia.hluweather.ui.map

import java.io.File
import net.droopia.hluweather.data.model.RadarFrame
import net.droopia.hluweather.data.model.RadarFrameType
import net.droopia.hluweather.data.model.RadarMetadata
import net.droopia.hluweather.data.model.RadarProvider
import net.droopia.hluweather.data.model.RadarTileCoordinate
import net.droopia.hluweather.data.model.RadarViewportBounds
import net.droopia.hluweather.data.network.RadarNextFrameCoverageOpportunity
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
        val mainMap = source("ui/weather/WeatherScreen.kt")
        assertTrue(mainMap.contains("radarResources = radarResources"))
        assertTrue(mainMap.contains("radarPlaybackPreparationEnabled = true"))
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

    @Test
    fun preparation_requires_current_playback_next_frame_plan_and_viewport() {
        val host = "https://tiles.example.test"
        val first = RadarFrame(100, "/v2/radar/100", RadarFrameType.OBSERVED)
        val next = RadarFrame(200, "/v2/radar/200", RadarFrameType.OBSERVED)
        val last = RadarFrame(300, "/v2/radar/300", RadarFrameType.FORECAST)
        val metadata = RadarMetadata.normalized(RadarProvider.LIBREWXR, host, listOf(first, next, last))
        val currentRequest = RadarRenderRequest(
            WeatherMapRadarConfig(host, first),
            listOf(RadarTileCoordinate(3, 1, 1)),
            styleEpoch = 4
        )
        val viewport = RadarViewportBounds(-1.0, -1.0, 1.0, 1.0, 5.0, 0.0, 0.0)
        val opportunity = RadarNextFrameCoverageOpportunity(
            generation = 7,
            requestedFrame = first.identity(host),
            nextFrame = next.identity(host),
            viewport = viewport
        )

        fun request(
            current: RadarRenderRequest = currentRequest,
            currentMetadata: RadarMetadata? = metadata,
            playing: Boolean = true,
            playbackNext: RadarFrame? = next,
            currentViewport: RadarViewportBounds? = viewport,
            coverage: RadarNextFrameCoverageOpportunity? = opportunity,
            generation: Long? = 7
        ) = nextRadarPreparationRequest(
            currentRequest = current,
            selectedProvider = RadarProvider.LIBREWXR,
            metadata = currentMetadata,
            lifecycleStarted = true,
            styleReady = true,
            playbackIsPlaying = playing,
            nextPlaybackFrame = playbackNext,
            viewport = currentViewport,
            opportunity = coverage,
            currentPlanGeneration = generation
        )

        val prepared = request()
        assertEquals(next, prepared?.config?.frame)
        assertEquals(currentRequest.coordinates, prepared?.coordinates)
        assertEquals(currentRequest.styleEpoch, prepared?.styleEpoch)
        assertEquals(null, request(playing = false))
        assertEquals(null, request(current = currentRequest.copy(config = currentRequest.config.copy(opacity = 0f))))
        assertEquals(null, request(playbackNext = last))
        assertEquals(null, request(generation = 8))
        assertEquals(null, request(coverage = opportunity.copy(requestedFrame = next.identity(host))))
        assertEquals(null, request(coverage = opportunity.copy(nextFrame = last.identity(host))))
        assertEquals(null, request(currentViewport = viewport.copy(zoom = viewport.zoom + 1)))
        assertEquals(null, request(currentMetadata = metadata.copy(frames = listOf(first, last))))

        val endRequest = currentRequest.copy(config = currentRequest.config.copy(frame = last))
        assertEquals(null, request(current = endRequest, playbackNext = first))
    }

    private fun config() = WeatherMapRadarConfig(
        "https://tiles.example.test", RadarFrame(100, "/v2/radar/100", RadarFrameType.OBSERVED)
    )

    private fun source(path: String): String =
        File("src/main/java/net/droopia/hluweather/$path").takeIf { it.isFile }
            ?.readText() ?: File("app/src/main/java/net/droopia/hluweather/$path").readText()
}
