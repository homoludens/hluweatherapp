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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.coroutines.runBlocking
import net.droopia.hluweather.data.model.WeatherLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.function.ThrowingRunnable

class OpenMeteoApiTest {

    @Test
    fun forecast_sends_the_open_meteo_request_and_decodes_the_response() = runTest {
        var capturedRequest: HttpRequestData? = null
        val client = mockClient { request ->
            capturedRequest = request
            respond(
                content = validResponse,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }

        val response = KtorOpenMeteoApi(client, "https://api.open-meteo.test")
            .forecast(WeatherLocation("svilajnac", "Svilajnac", 44.22, 21.20))

        val request = requireNotNull(capturedRequest)
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("api.open-meteo.test", request.url.host)
        assertEquals("/v1/forecast", request.url.encodedPath)
        assertEquals("44.22", request.url.parameters["latitude"])
        assertEquals("21.2", request.url.parameters["longitude"])
        assertEquals("auto", request.url.parameters["timezone"])
        assertEquals("7", request.url.parameters["forecast_days"])
        assertEquals(
            "temperature_2m,relative_humidity_2m,apparent_temperature,dew_point_2m,precipitation,weather_code,is_day",
            request.url.parameters["current"]
        )
        assertEquals(
            "temperature_2m,relative_humidity_2m,dew_point_2m,apparent_temperature,precipitation,precipitation_probability,weather_code,is_day",
            request.url.parameters["hourly"]
        )
        assertEquals(
            "weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,sunrise,sunset,moon_phase",
            request.url.parameters["daily"]
        )
        assertEquals("celsius", request.url.parameters["temperature_unit"])
        assertEquals("kmh", request.url.parameters["wind_speed_unit"])
        assertEquals("mm", request.url.parameters["precipitation_unit"])
        assertEquals("Europe/Belgrade", response.timezone)
        assertNotNull(response.current)
        assertNotNull(response.hourly)
        assertNotNull(response.daily)

        client.close()
    }

    @Test
    fun forecast_rejects_a_non_success_response() = runTest {
        val client = mockClient {
            respond("service unavailable", HttpStatusCode.ServiceUnavailable)
        }

        val exception = assertThrows(OpenMeteoApiException::class.java, ThrowingRunnable {
            runBlocking {
                KtorOpenMeteoApi(client, "https://api.open-meteo.test")
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
                KtorOpenMeteoApi(client, "https://api.open-meteo.test")
                    .forecast(WeatherLocation("svilajnac", "Svilajnac", 44.22, 21.20))
            }
        })

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
                "temperature_2m": 21.0,
                "relative_humidity_2m": 51,
                "apparent_temperature": 21.0,
                "dew_point_2m": 10.0,
                "precipitation": 0.0,
                "weather_code": 0,
                "is_day": 1
              },
              "hourly": {
                "time": ["2026-09-09T12:00"],
                "temperature_2m": [21.0],
                "relative_humidity_2m": [51],
                "dew_point_2m": [10.0],
                "apparent_temperature": [21.0],
                "precipitation": [0.0],
                "precipitation_probability": [0],
                "weather_code": [0],
                "is_day": [1]
              },
              "daily": {
                "time": ["2026-09-09"],
                "weather_code": [0],
                "temperature_2m_max": [30.0],
                "temperature_2m_min": [16.0],
                "precipitation_sum": [0.0],
                "sunrise": ["2026-09-09T06:10"],
                "sunset": ["2026-09-09T19:00"],
                "moon_phase": [0.5]
              }
            }
        """.trimIndent()
    }
}
