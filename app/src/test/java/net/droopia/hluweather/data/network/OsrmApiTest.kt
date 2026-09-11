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
import net.droopia.hluweather.data.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.function.ThrowingRunnable

class OsrmApiTest {

    @Test
    fun route_sends_driving_geojson_full_overview_request() = runTest {
        var capturedRequest: HttpRequestData? = null
        val client = mockClient { request ->
            capturedRequest = request
            respond(
                content = okRouteJson,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }

        val route = KtorOsrmApi(client, "https://osrm.test")
            .route(GeoPoint(44.8, 20.4), GeoPoint(45.6, 13.7))

        val request = requireNotNull(capturedRequest)
        assertEquals("/route/v1/driving/20.4,44.8;13.7,45.6", request.url.encodedPath)
        assertEquals("geojson", request.url.parameters["geometries"])
        assertEquals("full", request.url.parameters["overview"])
        assertEquals("false", request.url.parameters["steps"])
        assertEquals("Ok", route.code)

        client.close()
    }

    @Test
    fun route_rejects_a_non_success_response() = runTest {
        val client = mockClient {
            respond("service unavailable", HttpStatusCode.ServiceUnavailable)
        }

        val exception = assertThrows(OsrmApiException::class.java, ThrowingRunnable {
            runBlocking {
                KtorOsrmApi(client, "https://osrm.test")
                    .route(GeoPoint(44.8, 20.4), GeoPoint(45.6, 13.7))
            }
        })

        assertEquals("Routing service returned HTTP 503", exception.message)
        client.close()
    }

    @Test
    fun route_decodes_an_ok_response_with_no_routes() = runTest {
        val client = mockClient {
            respond(
                content = "{\"code\":\"Ok\",\"routes\":[]}",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }

        val response = KtorOsrmApi(client, "https://osrm.test")
            .route(GeoPoint(44.8, 20.4), GeoPoint(45.6, 13.7))

        assertEquals("Ok", response.code)
        assertEquals(emptyList<OsrmRoute>(), response.routes)
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
        val okRouteJson = """
            {
              "code": "Ok",
              "routes": [
                {
                  "distance": 12345.6,
                  "duration": 789.0,
                  "geometry": {
                    "coordinates": [[20.4, 44.8], [17.0, 45.2], [13.7, 45.6]]
                  }
                }
              ]
            }
        """.trimIndent()
    }
}
