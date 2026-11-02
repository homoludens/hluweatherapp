package net.droopia.hluweather.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import net.droopia.hluweather.data.model.RadarFrameType
import net.droopia.hluweather.data.model.RadarProvider
import net.droopia.hluweather.data.model.RadarProviderDescriptors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RainViewerRadarApiTest {
    @Test
    fun metadata_fetches_documented_endpoint_and_accepts_past_only_without_nowcast() = runTest {
        var capturedRequest: HttpRequestData? = null
        val client = mockClient { request ->
            capturedRequest = request
            jsonResponse(pastOnlyMetadataJson)
        }

        val metadata = KtorRainViewerRadarApi(client).metadata()

        val request = requireNotNull(capturedRequest)
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("api.rainviewer.com", request.url.host)
        assertEquals("/public/weather-maps.json", request.url.encodedPath)
        assertEquals(RadarProvider.RAINVIEWER, metadata.provider)
        assertEquals("https://tilecache.rainviewer.com", metadata.host)
        assertEquals(listOf(100L, 200L), metadata.frames.map { it.timestampSeconds })
        assertTrue(metadata.frames.all { it.provider == RadarProvider.RAINVIEWER })
        assertTrue(metadata.frames.all { it.type == RadarFrameType.OBSERVED })
        assertTrue(metadata.forecastFrames.isEmpty())

        client.close()
    }

    @Test
    fun metadata_accepts_empty_past_and_ignores_any_legacy_nowcast_frames() {
        val empty = parseRainViewerRadarMetadata(
            """{"host":"https://tiles.example.test","radar":{"past":[]}}"""
        )
        val withNowcast = parseRainViewerRadarMetadata(
            """
                {
                  "host": "https://tiles.example.test",
                  "radar": {
                    "past": [{"time": 100, "path": "/v2/radar/100"}],
                    "nowcast": [{"time": 200, "path": "/v2/radar/200"}]
                  }
                }
            """.trimIndent()
        )

        assertTrue(empty.frames.isEmpty())
        assertEquals(listOf(100L), withNowcast.observedFrames.map { it.timestampSeconds })
        assertTrue(withNowcast.forecastFrames.isEmpty())
    }

    @Test
    fun metadata_rejects_malformed_shapes_and_invalid_or_negative_timestamps() {
        assertThrows(SerializationException::class.java) {
            parseRainViewerRadarMetadata("{not-json")
        }
        assertThrows(SerializationException::class.java) {
            parseRainViewerRadarMetadata("""{"host":"https://tiles.example.test","radar":{}}""")
        }
        assertThrows(SerializationException::class.java) {
            parseRainViewerRadarMetadata(metadataWithFrame("\"soon\"", "\"/radar/1\""))
        }
        assertThrows(SerializationException::class.java) {
            parseRainViewerRadarMetadata(metadataWithFrame("1.5", "\"/radar/1\""))
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseRainViewerRadarMetadata(metadataWithFrame("-1", "\"/radar/-1\""))
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseRainViewerRadarMetadata(metadataWithFrame("0", "\"/radar/0\""))
        }
    }

    @Test
    fun metadata_rejects_invalid_hosts_and_frame_paths() {
        assertThrows(IllegalArgumentException::class.java) {
            parseRainViewerRadarMetadata(
                pastOnlyMetadataJson.replace("https://tilecache.rainviewer.com", "http://tilecache.rainviewer.com")
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseRainViewerRadarMetadata(
                pastOnlyMetadataJson.replace("/v2/radar/100", "//other.example.test/radar")
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseRainViewerRadarMetadata(
                pastOnlyMetadataJson.replace("/v2/radar/100", "/v2/radar/../private")
            )
        }
    }

    @Test
    fun metadata_reports_http_errors_with_status_code() = runTest {
        val client = mockClient {
            jsonResponse("unavailable", status = HttpStatusCode.ServiceUnavailable)
        }

        val failure = try {
            KtorRainViewerRadarApi(client).metadata()
            throw AssertionError("Expected a RainViewer HTTP error")
        } catch (error: RainViewerRadarApiException) {
            error
        }

        assertEquals(503, failure.statusCode)
        assertTrue(failure.message.orEmpty().contains("503"))
        client.close()
    }

    @Test
    fun tile_template_uses_metadata_host_path_and_only_supported_rainviewer_options() {
        val metadata = parseRainViewerRadarMetadata(pastOnlyMetadataJson)
        val descriptor = RadarProviderDescriptors[RadarProvider.RAINVIEWER]
        val template = RadarTileUrlBuilder.buildTemplate(
            descriptor = descriptor,
            host = metadata.host,
            path = metadata.frames.first().path
        )

        assertEquals(256, descriptor.tileOptions.size)
        assertEquals(2, descriptor.tileOptions.color)
        assertTrue(descriptor.tileOptions.smooth)
        assertTrue(descriptor.tileOptions.snow)
        assertEquals("png", descriptor.tileOptions.extension)
        assertEquals(7, descriptor.nativeMaxZoom)
        assertEquals(
            "https://tilecache.rainviewer.com/v2/radar/100/256/{z}/{x}/{y}/2/1_1.png",
            template
        )
        assertEquals(
            "https://tilecache.rainviewer.com/v2/radar/100/256/7/71/44/2/1_1.png",
            RadarTileUrlBuilder.build(descriptor, metadata.host, metadata.frames.first().path, 7, 71, 44)
        )
        assertThrows(IllegalArgumentException::class.java) {
            RadarTileUrlBuilder.build(descriptor, metadata.host, metadata.frames.first().path, 8, 71, 44)
        }
        assertFalse(template.contains("/512/"))
    }

    private fun metadataWithFrame(time: String, path: String) =
        """{"host":"https://tiles.example.test","radar":{"past":[{"time":$time,"path":$path}]}}"""

    private fun mockClient(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
    ): HttpClient = HttpClient(MockEngine) {
        engine { addHandler(handler) }
    }

    private fun MockRequestHandleScope.jsonResponse(
        content: String,
        status: HttpStatusCode = HttpStatusCode.OK
    ) = respond(
        content = content,
        status = status,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    )

    private companion object {
        val pastOnlyMetadataJson = """
            {
              "version": "2.0",
              "generated": 300,
              "host": "https://tilecache.rainviewer.com",
              "radar": {
                "past": [
                  {"time": 200, "path": "/v2/radar/200"},
                  {"time": 100, "path": "/v2/radar/100"}
                ]
              }
            }
        """.trimIndent()
    }
}
