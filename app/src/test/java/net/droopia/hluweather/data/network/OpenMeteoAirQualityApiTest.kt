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
import io.ktor.serialization.JsonConvertException
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import net.droopia.hluweather.data.model.WeatherLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.function.ThrowingRunnable

class OpenMeteoAirQualityApiTest {

    @Test
    fun forecast_sends_current_and_hourly_air_quality_parameters() = runTest {
        var capturedRequest: HttpRequestData? = null
        val client = mockClient { request ->
            capturedRequest = request
            respond(
                content = validResponse,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }

        KtorOpenMeteoAirQualityApi(client, "https://air-quality-api.open-meteo.test")
            .forecast(WeatherLocation("svilajnac", "Svilajnac", 44.22, 21.20))

        val request = requireNotNull(capturedRequest)
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("air-quality-api.open-meteo.test", request.url.host)
        assertEquals("/v1/air-quality", request.url.encodedPath)
        assertEquals("44.22", request.url.parameters["latitude"])
        assertEquals("21.2", request.url.parameters["longitude"])
        assertEquals("auto", request.url.parameters["timezone"])
        assertEquals("7", request.url.parameters["forecast_days"])
        assertEquals("european_aqi,pm10,pm2_5", request.url.parameters["current"])
        assertEquals("european_aqi,pm10,pm2_5", request.url.parameters["hourly"])

        client.close()
    }

    @Test
    fun forecast_rejects_non_success_responses() = runTest {
        val client = mockClient {
            respond("service unavailable", HttpStatusCode.ServiceUnavailable)
        }

        val exception = assertThrows(OpenMeteoApiException::class.java, ThrowingRunnable {
            runBlocking {
                KtorOpenMeteoAirQualityApi(client, "https://air-quality-api.open-meteo.test")
                    .forecast(WeatherLocation("svilajnac", "Svilajnac", 44.22, 21.20))
            }
        })

        assertEquals("Weather service returned HTTP 503", exception.message)
        client.close()
    }

    @Test
    fun forecast_propagates_invalid_json_as_a_decoding_exception() = runTest {
        val client = mockClient {
            respond(
                content = "{not-valid-json",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }

        assertThrows(JsonConvertException::class.java, ThrowingRunnable {
            runBlocking {
                KtorOpenMeteoAirQualityApi(client, "https://air-quality-api.open-meteo.test")
                    .forecast(WeatherLocation("svilajnac", "Svilajnac", 44.22, 21.20))
            }
        })

        client.close()
    }

    @Test
    fun forecast_decodes_optional_blocks() = runTest {
        val client = mockClient {
            respond(
                content = """
                    {
                      "timezone": "Europe/Belgrade",
                      "current": {
                        "time": "2026-09-09T12:00",
                        "european_aqi": 42.0,
                        "pm10": 12.5,
                        "pm2_5": 8.2
                      },
                      "hourly": {
                        "time": ["2026-09-09T12:00"],
                        "european_aqi": [42.0, null],
                        "pm10": [12.5, null],
                        "pm2_5": [8.2, null]
                      }
                    }
                """.trimIndent(),
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }

        val response = KtorOpenMeteoAirQualityApi(client, "https://air-quality-api.open-meteo.test")
            .forecast(WeatherLocation("svilajnac", "Svilajnac", 44.22, 21.20))

        assertEquals("Europe/Belgrade", response.timezone)
        assertEquals(42.0, response.current?.europeanAqi)
        assertEquals(12.5, response.current?.pm10)
        assertEquals(8.2, response.current?.pm2_5)
        assertEquals(listOf(42.0, null), response.hourly?.europeanAqi)
        assertEquals(listOf(12.5, null), response.hourly?.pm10)
        assertEquals(listOf(8.2, null), response.hourly?.pm2_5)

        val optionalResponse = mockClient {
            respond(
                content = "{\"timezone\":\"Europe/Belgrade\"}",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }.use { optionalClient ->
            KtorOpenMeteoAirQualityApi(optionalClient, "https://air-quality-api.open-meteo.test")
                .forecast(WeatherLocation("svilajnac", "Svilajnac", 44.22, 21.20))
        }

        assertNull(optionalResponse.current)
        assertNull(optionalResponse.hourly)
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
              "timezone": "Europe/Belgrade",
              "current": {
                "time": "2026-09-09T12:00",
                "european_aqi": 42.0,
                "pm10": 12.5,
                "pm2_5": 8.2
              },
              "hourly": {
                "time": ["2026-09-09T12:00"],
                "european_aqi": [42.0],
                "pm10": [12.5],
                "pm2_5": [8.2]
              }
            }
        """.trimIndent()
    }
}
