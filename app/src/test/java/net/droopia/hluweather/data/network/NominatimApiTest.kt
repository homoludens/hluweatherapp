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
import kotlinx.serialization.json.Json
import net.droopia.hluweather.data.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Test

class NominatimApiTest {

    @Test
    fun reverse_sends_nominatim_parameters_and_decodes_the_response() = runTest {
        var capturedRequest: HttpRequestData? = null
        val client = mockClient { request ->
            capturedRequest = request
            respond(
                content = validResponse,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }

        val response = KtorNominatimApi(client, "https://nominatim.test")
            .reverse(GeoPoint(44.8176, 20.4633))

        val request = requireNotNull(capturedRequest)
        assertEquals("/reverse", request.url.encodedPath)
        assertEquals("jsonv2", request.url.parameters["format"])
        assertEquals("44.8176", request.url.parameters["lat"])
        assertEquals("20.4633", request.url.parameters["lon"])
        assertEquals(
            "HluWeather/1.0 https://net.droopia.hluweather",
            request.headers["User-Agent"]
        )
        assertEquals("Stari Grad", response.address?.cityDistrict)
        assertNotNull(response.displayName)

        client.close()
    }

    @Test
    fun reverse_rejects_http_failures() = runTest {
        val client = mockClient {
            respond("service unavailable", HttpStatusCode.ServiceUnavailable)
        }

        try {
            KtorNominatimApi(client, "https://nominatim.test")
                .reverse(GeoPoint(44.8176, 20.4633))
            fail("Expected NominatimApiException")
        } catch (exception: NominatimApiException) {
            assertEquals("Nominatim returned HTTP 503", exception.message)
        }

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
              "place_id": 123,
              "display_name": "Stari Grad, Beograd, Serbia",
              "name": "Stari Grad",
              "address": {
                "city_district": "Stari Grad",
                "city": "Belgrade",
                "country": "Serbia"
              }
            }
        """.trimIndent()
    }
}
