package net.droopia.hluweather.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.function.ThrowingRunnable

class OpenMeteoGeocodingApiTest {

    @Test
    fun search_sends_geocoding_parameters_and_decodes_results() = runTest {
        var capturedRequest: HttpRequestData? = null
        val client = mockClient { request ->
            capturedRequest = request
            respond(
                content = validResponse,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }

        val response = KtorOpenMeteoGeocodingApi(client, "https://geocoding.test")
            .search("trieste")

        val request = requireNotNull(capturedRequest)
        assertEquals("/v1/search", request.url.encodedPath)
        assertEquals("trieste", request.url.parameters["name"])
        assertEquals("8", request.url.parameters["count"])
        assertEquals("en", request.url.parameters["language"])
        assertEquals("json", request.url.parameters["format"])
        assertEquals("HluWeather/3.2 route planner", request.headers[HttpHeaders.UserAgent])
        assertEquals(1, response.results.size)
        assertEquals("Trieste", response.results.single().name)
        assertEquals(45.6495, response.results.single().latitude)
        assertEquals(13.7768, response.results.single().longitude)

        client.close()
    }

    @Test
    fun search_rejects_http_failures() = runTest {
        val client = mockClient {
            respond("service unavailable", HttpStatusCode.ServiceUnavailable)
        }

        val exception = assertThrows(OpenMeteoGeocodingApiException::class.java, ThrowingRunnable {
            runBlocking {
                KtorOpenMeteoGeocodingApi(client, "https://geocoding.test").search("trieste")
            }
        })

        assertEquals("Open-Meteo geocoding returned HTTP 503", exception.message)
        client.close()
    }

    @Test
    fun search_decodes_missing_or_empty_result_arrays_as_empty() = runTest {
        val client = mockClient {
            respond(
                content = "{\"results\":[]}",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }

        assertEquals(emptyList<OpenMeteoGeocodingResult>(), KtorOpenMeteoGeocodingApi(
            client,
            "https://geocoding.test"
        ).search("trieste").results)

        client.close()

        val missingArrayClient = mockClient {
            respond(
                content = "{}",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }

        assertEquals(
            emptyList<OpenMeteoGeocodingResult>(),
            KtorOpenMeteoGeocodingApi(missingArrayClient, "https://geocoding.test")
                .search("trieste").results
        )

        missingArrayClient.close()
    }

    @Test
    fun search_decodes_nullable_coordinates_without_rejecting_the_response() = runTest {
        val client = mockClient {
            respond(
                content = """
                    {"results":[
                      {"name":"Malformed","latitude":null,"longitude":13.0},
                      {"name":"Trieste","latitude":45.6495,"longitude":13.7768}
                    ]}
                """.trimIndent(),
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }

        val results = KtorOpenMeteoGeocodingApi(client, "https://geocoding.test")
            .search("trieste").results

        assertEquals(2, results.size)
        assertEquals(null, results.first().latitude)
        assertEquals("Trieste", results.last().name)
        client.close()
    }

    private fun mockClient(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
    ): HttpClient = HttpClient(MockEngine) {
        engine {
            addHandler(handler)
        }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private companion object {
        val validResponse = """
            {
              "results": [
                {
                  "name": "Trieste",
                  "latitude": 45.6495,
                  "longitude": 13.7768,
                  "country": "Italy",
                  "admin1": "Friuli Venezia Giulia"
                }
              ]
            }
        """.trimIndent()
    }
}
