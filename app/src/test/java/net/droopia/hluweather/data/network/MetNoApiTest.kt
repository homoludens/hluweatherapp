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
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import net.droopia.hluweather.data.model.WeatherLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.function.ThrowingRunnable

class MetNoApiTest {

    @Test
    fun forecast_sends_compact_request_with_identifying_user_agent_and_altitude() = runTest {
        var capturedRequest: HttpRequestData? = null
        val client = mockClient { request ->
            capturedRequest = request
            respond(
                content = validResponse,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }

        val response = KtorMetNoApi(client, "https://api.met.no")
            .forecast(WeatherLocation("nis", "Nis", 44.8176, 20.4633, altitude = 198))

        val request = requireNotNull(capturedRequest)
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("api.met.no", request.url.host)
        assertEquals("/weatherapi/locationforecast/2.0/compact", request.url.encodedPath)
        assertEquals("44.8176", request.url.parameters["lat"])
        assertEquals("20.4633", request.url.parameters["lon"])
        assertEquals("198", request.url.parameters["altitude"])
        assertEquals(
            "HluWeather/1.0 https://net.droopia.hluweather",
            request.headers[HttpHeaders.UserAgent]
        )
        assertEquals(1, response.properties.timeseries.size)

        client.close()
    }

    @Test
    fun forecast_omits_optional_altitude() = runTest {
        var capturedRequest: HttpRequestData? = null
        val client = mockClient { request ->
            capturedRequest = request
            respond(
                content = validResponse,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }

        KtorMetNoApi(client, "https://api.met.no")
            .forecast(WeatherLocation("nis", "Nis", 44.8176, 20.4633))

        assertNull(requireNotNull(capturedRequest).url.parameters["altitude"])
        client.close()
    }

    @Test
    fun forecast_rejects_a_non_success_response() = runTest {
        val client = mockClient {
            respond("service unavailable", HttpStatusCode.ServiceUnavailable)
        }

        val exception = assertThrows(MetNoApiException::class.java, ThrowingRunnable {
            kotlinx.coroutines.runBlocking {
                KtorMetNoApi(client, "https://api.met.no").forecast(location)
            }
        })

        assertEquals("Weather service returned HTTP 503", exception.message)
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
        val location = WeatherLocation("nis", "Nis", 44.8176, 20.4633)

        val validResponse = """
            {
              "type": "Feature",
              "geometry": { "type": "Point", "coordinates": [20.4633, 44.8176, 198] },
              "properties": {
                "meta": { "updated_at": "2026-09-10T00:00:00Z", "units": {} },
                "timeseries": [
                  {
                    "time": "2026-09-10T00:00:00Z",
                    "data": {
                      "instant": { "details": { "air_temperature": 20.0 } },
                      "next_1_hours": {
                        "summary": { "symbol_code": "clearsky_day" },
                        "details": { "precipitation_amount": 0.0 }
                      }
                    }
                  }
                ]
              }
            }
        """.trimIndent()
    }
}
