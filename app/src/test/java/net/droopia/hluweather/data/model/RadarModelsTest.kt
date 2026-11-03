package net.droopia.hluweather.data.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.droopia.hluweather.data.network.RadarTileUrlBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RadarModelsTest {
    @Test
    fun normalized_metadata_orders_chronologically_and_deduplicates_frame_identities() {
        val observedLater = frame(200, "/radar/z", RadarFrameType.OBSERVED)
        val observedEarlierPath = frame(100, "/radar/a", RadarFrameType.OBSERVED)
        val observedLaterForecast = frame(200, "/radar/forecast", RadarFrameType.FORECAST)
        val metadata = RadarMetadata.normalized(
            provider = RadarProvider.LIBREWXR,
            host = host,
            frames = listOf(
                observedLaterForecast,
                observedLater,
                observedEarlierPath,
                observedLater,
                frame(100, "/radar/z", RadarFrameType.OBSERVED)
            )
        )

        assertEquals(
            listOf(
                observedEarlierPath,
                frame(100, "/radar/z", RadarFrameType.OBSERVED),
                observedLater,
                observedLaterForecast
            ),
            metadata.frames
        )
        assertEquals(3, metadata.observedFrames.size)
        assertEquals(listOf(observedLaterForecast), metadata.forecastFrames)
    }

    @Test
    fun frame_identity_distinguishes_provider_host_path_time_and_type() {
        val observed = frame(100, "/radar/100", RadarFrameType.OBSERVED)
        val identity = observed.identity(host)

        assertNotEquals(identity, observed.copy(provider = RadarProvider.RAINVIEWER).identity(host))
        assertNotEquals(identity, observed.identity("https://other-tiles.example.test"))
        assertNotEquals(identity, observed.copy(path = "/other/100").identity(host))
        assertNotEquals(identity, observed.copy(timestampSeconds = 101).identity(host))
        assertNotEquals(identity, observed.copy(type = RadarFrameType.FORECAST).identity(host))
    }

    @Test
    fun legacy_librewxr_metadata_decodes_as_observed_and_forecast_with_provider_identity() {
        val metadata = Json.decodeFromString<RadarMetadata>(
            """
                {
                  "host": "$host",
                  "frames": [
                    {"timestampSeconds": 100, "path": "/radar/100", "type": "OBSERVED"},
                    {"timestampSeconds": 200, "path": "/radar/200", "type": "FORECAST"}
                  ],
                  "colorSchemes": [{"id": 2, "name": "Universal Blue"}]
                }
            """.trimIndent()
        )

        assertEquals(RadarProvider.LIBREWXR, metadata.provider)
        assertEquals(RadarProvider.LIBREWXR, metadata.frames.first().provider)
        assertEquals(listOf(100L), metadata.observedFrames.map(RadarFrame::timestampSeconds))
        assertEquals(listOf(200L), metadata.forecastFrames.map(RadarFrame::timestampSeconds))
        assertEquals(listOf(RadarColorScheme(2, "Universal Blue")), metadata.colorSchemes)
    }

    @Test
    fun provider_descriptors_define_links_credits_tile_options_and_native_zoom_limits() {
        val libre = RadarProviderDescriptors[RadarProvider.LIBREWXR]
        val rainViewer = RadarProviderDescriptors[RadarProvider.RAINVIEWER]

        assertEquals("https://librewxr.net/", libre.sourceLink.url)
        assertEquals(3, libre.credits.size)
        assertEquals(12, libre.nativeMaxZoom)
        assertEquals("https://www.rainviewer.com/", rainViewer.sourceLink.url)
        assertEquals(7, rainViewer.nativeMaxZoom)
        assertEquals(256, rainViewer.tileOptions.size)
        assertEquals(2, rainViewer.tileOptions.color)
        assertEquals(
            "https://tiles.example.test/v2/radar/100/256/7/71/44/2/1_1.png",
            RadarTileUrlBuilder.build(rainViewer, host, "/v2/radar/100", 7, 71, 44)
        )
        assertThrows(IllegalArgumentException::class.java) {
            RadarTileUrlBuilder.build(rainViewer, host, "/v2/radar/100", 8, 71, 44)
        }
    }

    @Test
    fun provider_tile_urls_keep_supported_256_pixel_images_at_coarse_zoom_levels() {
        val libre = RadarProviderDescriptors[RadarProvider.LIBREWXR]
        val rainViewer = RadarProviderDescriptors[RadarProvider.RAINVIEWER]
        assertEquals(256, libre.tileOptions.size)
        assertEquals(256, rainViewer.tileOptions.size)
        assertEquals(
            "https://tiles.example.test/v2/radar/100/256/10/512/512/2/1_1.png",
            RadarTileUrlBuilder.build(libre, host, "/v2/radar/100", 10, 512, 512)
        )
        assertEquals(
            "https://tiles.example.test/v2/radar/100/256/5/16/16/2/1_1.png",
            RadarTileUrlBuilder.build(rainViewer, host, "/v2/radar/100", 5, 16, 16)
        )
    }

    @Test
    fun provider_url_builder_requires_https_hosts_and_safe_frame_paths() {
        val descriptor = RadarProviderDescriptors[RadarProvider.LIBREWXR]
        listOf(
            "http://tiles.example.test",
            "https://user:pass@tiles.example.test",
            "https://tiles.example.test/path",
            "https://tiles.example.test?query=1"
        ).forEach { invalidHost ->
            assertThrows(IllegalArgumentException::class.java) {
                RadarTileUrlBuilder.buildTemplate(descriptor, invalidHost, "/radar/100")
            }
        }
        listOf(
            "//other.example.test/frame",
            "https://other.example.test/frame",
            "/radar/../private",
            "/radar/%2e%2e/private",
            "/radar/frame?query=1",
            "/radar/frame#fragment"
        ).forEach { invalidPath ->
            assertThrows(IllegalArgumentException::class.java) {
                RadarTileUrlBuilder.buildTemplate(descriptor, host, invalidPath)
            }
        }
    }

    private fun frame(
        timestampSeconds: Long,
        path: String,
        type: RadarFrameType
    ) = RadarFrame(timestampSeconds, path, type, RadarProvider.LIBREWXR)

    private companion object {
        const val host = "https://tiles.example.test"
    }
}
