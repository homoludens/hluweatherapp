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

class PhotonApiTest {

    @Test
    fun search_sends_photon_parameters_and_decodes_features() = runTest {
        var capturedRequest: HttpRequestData? = null
        val client = mockClient { request ->
            capturedRequest = request
            respond(
                content = validResponse,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }

        val response = KtorPhotonApi(client, "https://photon.test")
            .search("trieste")

        val request = requireNotNull(capturedRequest)
        assertEquals("/api/", request.url.encodedPath)
        assertEquals("trieste", request.url.parameters["q"])
        assertEquals("8", request.url.parameters["limit"])
        assertEquals("en", request.url.parameters["lang"])
        assertEquals("HluWeather/3.2 route planner", request.headers[HttpHeaders.UserAgent])
        assertEquals(1, response.features.size)
        assertEquals(listOf(13.7768, 45.6495), response.features.single().geometry.coordinates)
        assertEquals("Trieste", response.features.single().properties.name)

        client.close()
    }

    @Test
    fun search_rejects_http_failures() = runTest {
        val client = mockClient {
            respond("service unavailable", HttpStatusCode.ServiceUnavailable)
        }

        val exception = assertThrows(PhotonApiException::class.java, ThrowingRunnable {
            runBlocking {
                KtorPhotonApi(client, "https://photon.test").search("trieste")
            }
        })

        assertEquals("Photon returned HTTP 503", exception.message)
        client.close()
    }

    @Test
    fun search_decodes_missing_or_empty_feature_arrays_as_empty() = runTest {
        val client = mockClient {
            respond(
                content = "{\"features\":[]}",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }

        assertEquals(emptyList<PhotonFeature>(), KtorPhotonApi(client, "https://photon.test")
            .search("trieste").features)

        client.close()

        val missingArrayClient = mockClient {
            respond(
                content = "{}",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }

        assertEquals(
            emptyList<PhotonFeature>(),
            KtorPhotonApi(missingArrayClient, "https://photon.test").search("trieste").features
        )

        missingArrayClient.close()
    }

    @Test
    fun search_decodes_nullable_coordinates_without_rejecting_the_response() = runTest {
        val client = mockClient {
            respond(
                content = """
                    {"features":[
                      {"geometry":{"coordinates":[null,45.0]},"properties":{"name":"Malformed"}},
                      {"geometry":{"coordinates":[13.7768,45.6495]},"properties":{"name":"Trieste"}}
                    ]}
                """.trimIndent(),
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }

        val features = KtorPhotonApi(client, "https://photon.test").search("trieste").features

        assertEquals(2, features.size)
        assertEquals(null, features.first().geometry.coordinates.first())
        assertEquals("Trieste", features.last().properties.name)
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
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "properties": {
                    "name": "Trieste",
                    "city": "Trieste",
                    "state": "Friuli Venezia Giulia",
                    "country": "Italy"
                  },
                  "geometry": {
                    "type": "Point",
                    "coordinates": [13.7768, 45.6495]
                  }
                }
              ]
            }
        """.trimIndent()
    }
}
