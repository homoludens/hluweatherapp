package net.droopia.hluweather.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.JsonPrimitive
import net.droopia.hluweather.data.model.WeatherLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.function.ThrowingRunnable

class BrightSkyApiTest {

    @Test
    fun weather_requests_coordinates_dwd_units_timezone_distance_and_seven_local_days() = runTest {
        var capturedRequest: HttpRequestData? = null
        val client = mockClient { request ->
            capturedRequest = request
            respond(fixture("bright-sky-weather.json"))
        }
        val fixedClock = fixedClock(Instant.parse("2026-03-29T00:30:00Z"))

        KtorBrightSkyApi(client, "https://brightsky.test", fixedClock)
            .weather(location, TimeZone.of("Europe/Berlin"))

        val request = requireNotNull(capturedRequest)
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("brightsky.test", request.url.host)
        assertEquals("/weather", request.url.encodedPath)
        assertEquals("52.52", request.url.parameters["lat"])
        assertEquals("13.419", request.url.parameters["lon"])
        assertEquals("50000", request.url.parameters["max_dist"])
        assertEquals("Europe/Berlin", request.url.parameters["tz"])
        assertEquals("dwd", request.url.parameters["units"])
        assertEquals("2026-03-28T23:00:00Z", request.url.parameters["date"])
        assertEquals("2026-04-04T21:59:59Z", request.url.parameters["last_date"])

        client.close()
    }

    @Test
    fun current_weather_requests_documented_coordinate_parameters_and_preserves_observation_source() = runTest {
        var capturedRequest: HttpRequestData? = null
        val client = mockClient { request ->
            capturedRequest = request
            respond(fixture("bright-sky-current-weather.json"))
        }

        val response = KtorBrightSkyApi(client, "https://brightsky.test")
            .currentWeather(location, TimeZone.of("Europe/Berlin"))

        val request = requireNotNull(capturedRequest)
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/current_weather", request.url.encodedPath)
        assertEquals("52.52", request.url.parameters["lat"])
        assertEquals("13.419", request.url.parameters["lon"])
        assertEquals("50000", request.url.parameters["max_dist"])
        assertEquals("Europe/Berlin", request.url.parameters["tz"])
        assertEquals("dwd", request.url.parameters["units"])
        assertNull(request.url.parameters["date"])

        val observation = requireNotNull(response.weather)
        assertEquals(6007, observation.sourceId)
        assertEquals("2026-11-09T09:30:00+01:00", observation.timestamp)
        assertEquals(8.4, observation.temperature!!, 0.0)
        val source = response.sources.single()
        assertEquals(6007, source.id)
        assertEquals("synop", source.observationType)
        assertEquals("01766", source.dwdStationId)
        assertEquals("10315", source.wmoStationId)
        assertEquals("Example SYNOP station", source.stationName)
        assertEquals(12_000, source.distance)
        assertEquals(52.13, source.lat!!, 0.0)
        assertEquals(7.70, source.lon!!, 0.0)

        client.close()
    }

    @Test
    fun weather_keeps_sparse_in_range_records_and_drops_records_from_distant_stations() = runTest {
        val sparseAndDistantResponse = fixture("bright-sky-weather.json")
            .replace("\"temperature\": 8.4", "\"temperature\": null")
            .replace(
                "\"source_id\": 6007,",
                "\"source_id\": 6007,\n      \"fallback_source_ids\": {\"temperature\": 6007},"
            )
            .replace("\"distance\": 0", "\"distance\": 50001")
        val client = mockClient { respond(sparseAndDistantResponse) }

        val response = KtorBrightSkyApi(client, "https://brightsky.test")
            .weather(location, TimeZone.of("Europe/Berlin"))

        assertEquals(1, response.weather.size)
        assertEquals(6007, response.weather.single().sourceId)
        assertNull(response.weather.single().temperature)
        assertEquals(JsonPrimitive(6007), response.weather.single().fallbackSourceIds["temperature"])
        assertEquals(setOf(6007, 7001), response.sources.mapNotNull { it.id }.toSet())
        assertEquals("synop", response.sources.single { it.id == 6007 }.observationType)
        assertEquals("forecast", response.sources.single { it.id == 7001 }.observationType)
        assertEquals(50001, response.sources.single { it.id == 7001 }.distance)

        client.close()

        val boundaryClient = mockClient {
            respond(fixture("bright-sky-weather.json").replace("\"distance\": 0", "\"distance\": 50000"))
        }
        val boundaryResponse = KtorBrightSkyApi(boundaryClient, "https://brightsky.test")
            .weather(location, TimeZone.of("Europe/Berlin"))
        assertEquals(2, boundaryResponse.weather.size)
        assertEquals(50_000, boundaryResponse.sources.single { it.id == 7001 }.distance)
        boundaryClient.close()
    }

    @Test
    fun current_weather_rejects_a_source_over_the_app_distance_ceiling_but_keeps_metadata() = runTest {
        val distantResponse = fixture("bright-sky-current-weather.json")
            .replace("\"distance\": 12000", "\"distance\": 50001")
        val client = mockClient { respond(distantResponse) }

        val response = KtorBrightSkyApi(client, "https://brightsky.test")
            .currentWeather(location, TimeZone.of("Europe/Berlin"))

        assertNull(response.weather)
        assertEquals(6007, response.sources.single().id)
        assertEquals(50001, response.sources.single().distance)

        client.close()
    }

    @Test
    fun http_errors_expose_only_the_status_not_response_or_request_details() = runTest {
        val client = mockClient {
            respond(
                "denied for 52.52,13.419 and station secret",
                HttpStatusCode.Forbidden
            )
        }

        val error = assertThrows(BrightSkyApiException::class.java, ThrowingRunnable {
            kotlinx.coroutines.runBlocking {
                KtorBrightSkyApi(client, "https://brightsky.test")
                    .currentWeather(location, TimeZone.of("Europe/Berlin"))
            }
        })

        assertEquals("Weather service returned HTTP 403", error.message)
        assertFalse(error.toString().contains("52.52"))
        assertFalse(error.toString().contains("station secret"))
        client.close()
    }

    @Test
    fun transport_and_decode_errors_are_sanitized() = runTest {
        val transportClient = mockClient {
            throw IllegalStateException("request https://brightsky.test/weather?lat=52.52")
        }
        val transportError = assertThrows(BrightSkyApiException::class.java, ThrowingRunnable {
            kotlinx.coroutines.runBlocking {
                KtorBrightSkyApi(transportClient, "https://brightsky.test")
                    .weather(location, TimeZone.of("Europe/Berlin"))
            }
        })
        assertEquals("Weather request failed", transportError.message)
        assertNull(transportError.cause)
        assertFalse(transportError.toString().contains("52.52"))
        transportClient.close()

        val decodeClient = mockClient { respond("not-json") }
        val decodeError = assertThrows(BrightSkyApiException::class.java, ThrowingRunnable {
            kotlinx.coroutines.runBlocking {
                KtorBrightSkyApi(decodeClient, "https://brightsky.test")
                    .weather(location, TimeZone.of("Europe/Berlin"))
            }
        })
        assertEquals("Unable to decode weather response", decodeError.message)
        assertFalse(decodeError.toString().contains("52.52"))
        decodeClient.close()
    }

    @Test
    fun cancellation_is_propagated_without_becoming_a_weather_error() = runTest {
        val cancellation = CancellationException("caller cancelled")
        val client = mockClient { throw cancellation }

        val thrown = assertThrows(CancellationException::class.java, ThrowingRunnable {
            kotlinx.coroutines.runBlocking {
                KtorBrightSkyApi(client, "https://brightsky.test")
                    .weather(location, TimeZone.of("Europe/Berlin"))
            }
        })

        assertEquals(cancellation.message, thrown.message)
        client.close()
    }

    @Test
    fun invalid_coordinates_are_rejected_before_network_access() = runTest {
        var requestCount = 0
        val client = mockClient {
            requestCount++
            respond(fixture("bright-sky-weather.json"))
        }

        val error = assertThrows(BrightSkyApiException::class.java, ThrowingRunnable {
            kotlinx.coroutines.runBlocking {
                KtorBrightSkyApi(client, "https://brightsky.test")
                    .weather(location.copy(latitude = Double.NaN), TimeZone.of("Europe/Berlin"))
            }
        })

        assertEquals("Invalid weather location", error.message)
        assertEquals(0, requestCount)
        client.close()
    }

    private fun mockClient(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
    ): HttpClient = HttpClient(MockEngine) {
        engine { addHandler(handler) }
    }

    private fun fixture(name: String): String = requireNotNull(
        javaClass.getResourceAsStream("/provider-fixtures/$name")
    ).bufferedReader().use { it.readText() }

    private fun fixedClock(instant: Instant): Clock = object : Clock {
        override fun now(): Instant = instant
    }

    private companion object {
        val location = WeatherLocation("berlin", "Berlin", 52.52, 13.419)
    }
}
