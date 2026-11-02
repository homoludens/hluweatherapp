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
import net.droopia.hluweather.data.model.RadarProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LibreWxrRadarApiTest {

    @Test
    fun metadata_fetches_documented_endpoint_and_parses_dynamic_hosts_paths_and_frame_times() = runTest {
        var capturedRequest: HttpRequestData? = null
        val client = mockClient { request ->
            capturedRequest = request
            jsonResponse(metadataJson)
        }

        val metadata = KtorLibreWxrRadarApi(client).metadata()

        val request = requireNotNull(capturedRequest)
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("api.librewxr.net", request.url.host)
        assertEquals("/public/weather-maps.json", request.url.encodedPath)
        assertEquals("https://tiles.example-cdn.test", metadata.host)
        assertEquals(RadarProvider.LIBREWXR, metadata.provider)
        assertTrue(metadata.frames.all { it.provider == RadarProvider.LIBREWXR })
        assertEquals(
            listOf(1_793_556_600L, 1_793_563_200L),
            metadata.observedFrames.map { it.timestampSeconds }
        )
        assertEquals(listOf("/v2/radar/1793556600", "/v2/radar/1793563200"),
            metadata.observedFrames.map { it.path })
        assertEquals(LibreWxrRadarFrameType.OBSERVED, metadata.frames[0].type)
        assertEquals(LibreWxrRadarFrameType.FORECAST, metadata.frames[2].type)
        assertEquals(1_793_566_800L, metadata.forecastFrames.single().timestampSeconds)
        assertEquals(LibreWxrRadarColorScheme(0, "Black and White"), metadata.colorSchemes[0])
        assertEquals(LibreWxrRadarColorScheme(2, "Universal Blue"), metadata.colorSchemes[2])
        assertEquals(LibreWxrRadarColorScheme(14, "Windy"), metadata.colorSchemes[14])

        client.close()
    }

    @Test
    fun metadata_accepts_empty_frame_and_color_scheme_arrays() {
        val metadata = parseLibreWxrRadarMetadata(
            """
                {
                  "host": "https://tiles.example.test/",
                  "radar": {"past": [], "nowcast": [], "colorSchemes": []}
                }
            """.trimIndent()
        )

        assertEquals(emptyList<LibreWxrRadarFrame>(), metadata.frames)
        assertEquals(emptyList<LibreWxrRadarColorScheme>(), metadata.colorSchemes)
    }

    @Test
    fun metadata_defaults_an_omitted_optional_color_scheme_list_to_empty() {
        val metadata = parseLibreWxrRadarMetadata(
            """
                {
                  "host": "https://tiles.example.test",
                  "radar": {"past": [], "nowcast": []}
                }
            """.trimIndent()
        )

        assertEquals(emptyList<LibreWxrRadarColorScheme>(), metadata.colorSchemes)
    }

    @Test
    fun metadata_rejects_malformed_json_and_missing_required_fields() {
        assertThrows(SerializationException::class.java) {
            parseLibreWxrRadarMetadata("{not-json")
        }
        assertThrows(SerializationException::class.java) {
            parseLibreWxrRadarMetadata("""{"host":"https://tiles.example.test","radar":{}}""")
        }
    }

    @Test
    fun metadata_rejects_invalid_host_and_frame_paths() {
        assertThrows(IllegalArgumentException::class.java) {
            parseLibreWxrRadarMetadata(metadataJson.replace("https://tiles.example-cdn.test", "http://tiles.example-cdn.test"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseLibreWxrRadarMetadata(metadataJson.replace("/v2/radar/1793556600", "//other.test/tile"))
        }
    }

    @Test
    fun tile_url_uses_dynamic_host_path_and_documented_options() {
        val url = LibreWxrRadarTileUrlBuilder.build(
            host = "https://tiles.example-cdn.test/",
            path = "/v2/radar/1793563200/",
            size = 256,
            zoom = 7,
            x = 71,
            y = 44,
            color = 2,
            smooth = true,
            snow = true,
            extension = "png"
        )

        assertEquals(
            "https://tiles.example-cdn.test/v2/radar/1793563200/256/7/71/44/2/1_1.png",
            url
        )
    }

    @Test
    fun tile_url_supports_documented_alternate_flags_format_and_max_color() {
        val url = LibreWxrRadarTileUrlBuilder.build(
            host = "https://tiles.example.test",
            path = "/radar/frame",
            size = 512,
            zoom = 0,
            x = 0,
            y = 0,
            color = 255,
            smooth = false,
            snow = false,
            extension = "webp"
        )

        assertEquals("https://tiles.example.test/radar/frame/512/0/0/0/255/0_0.webp", url)
    }

    @Test
    fun tile_template_preserves_xyz_placeholders_and_matches_concrete_tile_builder() {
        val template = LibreWxrRadarTileUrlBuilder.buildTemplate(
            host = "https://tiles.example.test/",
            path = "/radar/frame/",
            size = 256,
            color = 2,
            smooth = true,
            snow = true,
            extension = "png"
        )

        assertEquals("https://tiles.example.test/radar/frame/256/{z}/{x}/{y}/2/1_1.png", template)
        assertEquals(
            buildTile(),
            template.replace("{z}", "7").replace("{x}", "71").replace("{y}", "44")
        )
    }

    @Test
    fun tile_template_validates_the_same_provider_options_and_paths() {
        listOf(
            { LibreWxrRadarTileUrlBuilder.buildTemplate("http://tiles.test", "/frame", 256, 2, true, true, "png") },
            { LibreWxrRadarTileUrlBuilder.buildTemplate("https://tiles.test", "//other.test/frame", 256, 2, true, true, "png") },
            { LibreWxrRadarTileUrlBuilder.buildTemplate("https://tiles.test", "/frame", 128, 2, true, true, "png") },
            { LibreWxrRadarTileUrlBuilder.buildTemplate("https://tiles.test", "/frame", 256, 15, true, true, "png") },
            { LibreWxrRadarTileUrlBuilder.buildTemplate("https://tiles.test", "/frame", 256, 2, true, true, "jpg") }
        ).forEach { invalidTemplate ->
            assertThrows(IllegalArgumentException::class.java) { invalidTemplate() }
        }
    }

    @Test
    fun tile_url_rejects_malformed_host_path_and_options() {
        assertThrows(IllegalArgumentException::class.java) {
            buildTile(host = "http://tiles.example.test")
        }
        assertThrows(IllegalArgumentException::class.java) {
            buildTile(path = "https://other.test/frame")
        }
        assertThrows(IllegalArgumentException::class.java) {
            buildTile(x = 128)
        }
        assertThrows(IllegalArgumentException::class.java) {
            buildTile(extension = "jpg")
        }
    }

    private fun buildTile(
        host: String = "https://tiles.example.test",
        path: String = "/radar/frame",
        size: Int = 256,
        zoom: Int = 7,
        x: Int = 71,
        y: Int = 44,
        color: Int = 2,
        smooth: Boolean = true,
        snow: Boolean = true,
        extension: String = "png"
    ): String = LibreWxrRadarTileUrlBuilder.build(
        host, path, size, zoom, x, y, color, smooth, snow, extension
    )

    private fun mockClient(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
    ): HttpClient = HttpClient(MockEngine) {
        engine { addHandler(handler) }
    }

    private fun MockRequestHandleScope.jsonResponse(content: String) = respond(
        content = content,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    )

    private companion object {
        val metadataJson = """
            {
              "version": "2.0",
              "host": "https://tiles.example-cdn.test",
              "radar": {
                "past": [
                  {"time": 1793556600, "path": "/v2/radar/1793556600"},
                  {"time": 1793563200, "path": "/v2/radar/1793563200"}
                ],
                "nowcast": [
                  {"time": 1793566800, "path": "/v2/radar/1793566800"}
                ],
                "colorSchemes": [
                  {"id": 0, "name": "Black and White"},
                  {"id": 1, "name": "Rainviewer Original"},
                  {"id": 2, "name": "Universal Blue"},
                  {"id": 3, "name": "Titan"},
                  {"id": 4, "name": "The Weather Channel (TWC)"},
                  {"id": 5, "name": "Meteored"},
                  {"id": 6, "name": "NEXRAD Level III"},
                  {"id": 7, "name": "Rainbow @ Selex SI"},
                  {"id": 8, "name": "Dark Sky"},
                  {"id": 9, "name": "Datameteo Valerio"},
                  {"id": 10, "name": "Viper HD"},
                  {"id": 11, "name": "MRMS CREF"},
                  {"id": 12, "name": "33/40 Max Storm"},
                  {"id": 13, "name": "MetService NZ (Dark)"},
                  {"id": 14, "name": "Windy"}
                ]
              },
              "satellite": {"infrared": []}
            }
        """.trimIndent()
    }
}
