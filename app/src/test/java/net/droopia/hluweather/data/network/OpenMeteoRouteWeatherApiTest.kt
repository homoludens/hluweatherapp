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
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.function.ThrowingRunnable

class OpenMeteoRouteWeatherApiTest {

    @Test
    fun forecast_batches_coordinates_and_requests_driving_fields() = runTest {
        var captured: HttpRequestData? = null
        val client = mockClient { request ->
            captured = request
            respondJson(multiLocationResponse)
        }

        val points = listOf(GeoPoint(44.8, 20.4), GeoPoint(45.6, 13.7))
        val response = KtorOpenMeteoRouteWeatherApi(client, "https://open-meteo.test")
            .forecast(points)

        val request = requireNotNull(captured)
        assertEquals("44.8,45.6", request.url.parameters["latitude"])
        assertEquals("20.4,13.7", request.url.parameters["longitude"])
        assertEquals(
            "temperature_2m,weather_code,wind_speed_10m,precipitation_probability",
            request.url.parameters["hourly"]
        )
        assertEquals("unixtime", request.url.parameters["timeformat"])
        assertEquals("GMT", request.url.parameters["timezone"])
        assertEquals("16", request.url.parameters["forecast_days"])
        assertEquals("celsius", request.url.parameters["temperature_unit"])
        assertEquals("kmh", request.url.parameters["wind_speed_unit"])
        assertEquals("mm", request.url.parameters["precipitation_unit"])
        assertNull(request.url.parameters["current"])
        assertNull(request.url.parameters["daily"])
        assertEquals(2, response.size)

        client.close()
    }

    @Test
    fun forecast_decodes_a_single_location_object() = runTest {
        val client = mockClient { respondJson(singleLocationResponse) }

        val response = KtorOpenMeteoRouteWeatherApi(client, "https://open-meteo.test")
            .forecast(listOf(GeoPoint(44.8, 20.4)))

        assertEquals(1, response.size)
        assertEquals(listOf(1_789_000_000L), response.single().hourly!!.time)

        client.close()
    }

    @Test
    fun forecast_makes_only_a_malformed_array_location_unavailable() = runTest {
        val client = mockClient { respondJson(malformedLocationResponse) }

        val response = KtorOpenMeteoRouteWeatherApi(client, "https://open-meteo.test")
            .forecast(
                listOf(
                    GeoPoint(44.8, 20.4),
                    GeoPoint(45.2, 17.0),
                    GeoPoint(45.6, 13.7)
                )
            )

        assertEquals(3, response.size)
        assertNotNull(response[0].hourly)
        assertNull(response[1].hourly)
        assertEquals(listOf(1789007200L), response[2].hourly!!.time)

        client.close()
    }

    @Test
    fun forecast_rejects_a_non_success_response() = runTest {
        val client = mockClient {
            respond(content = "service unavailable", status = HttpStatusCode.ServiceUnavailable)
        }

        assertThrows(OpenMeteoApiException::class.java, ThrowingRunnable {
            kotlinx.coroutines.runBlocking {
                KtorOpenMeteoRouteWeatherApi(client, "https://open-meteo.test")
                    .forecast(listOf(GeoPoint(44.8, 20.4)))
            }
        })

        client.close()
    }

    private fun MockRequestHandleScope.respondJson(content: String): HttpResponseData = respond(
        content = content,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    )

    private fun mockClient(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
    ): HttpClient = HttpClient(MockEngine) {
        engine { addHandler(handler) }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    private companion object {
        val singleLocationResponse = """
            {
              "hourly": {
                "time": [1789000000],
                "temperature_2m": [20.0],
                "weather_code": [1],
                "wind_speed_10m": [12.0],
                "precipitation_probability": [10]
              }
            }
        """.trimIndent()

        val multiLocationResponse = """
            [
              {
                "hourly": {
                  "time": [1789000000],
                  "temperature_2m": [20.0],
                  "weather_code": [1],
                  "wind_speed_10m": [12.0],
                  "precipitation_probability": [10]
                }
              },
              {
                "hourly": {
                  "time": [1789003600],
                  "temperature_2m": [21.0],
                  "weather_code": [61],
                  "wind_speed_10m": [14.0],
                  "precipitation_probability": [20]
                }
              }
            ]
        """.trimIndent()

        val malformedLocationResponse = """
            [
              {
                "hourly": {
                  "time": [1789000000],
                  "temperature_2m": [20.0],
                  "weather_code": [1],
                  "wind_speed_10m": [12.0],
                  "precipitation_probability": [10]
                }
              },
              {
                "hourly": {
                  "time": [1789003600],
                  "temperature_2m": ["not-a-number"],
                  "weather_code": [61],
                  "wind_speed_10m": [14.0],
                  "precipitation_probability": [20]
                }
              },
              {
                "hourly": {
                  "time": [1789007200],
                  "temperature_2m": [22.0],
                  "weather_code": [2],
                  "wind_speed_10m": [10.0],
                  "precipitation_probability": [0]
                }
              }
            ]
        """.trimIndent()
    }
}
