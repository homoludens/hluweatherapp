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
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import kotlin.time.Instant
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.repository.VisualCrossingWeatherRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.function.ThrowingRunnable

class VisualCrossingApiTest {

    @Test
    fun forecast_requests_a_bounded_keyless_metric_timeline_and_decodes_gzip() = runTest {
        var capturedRequest: HttpRequestData? = null
        val client = mockClient { request ->
            capturedRequest = request
            respond(
                content = gzip(fixture()),
                headers = headersOf(
                    HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()),
                    HttpHeaders.ContentEncoding to listOf("gzip")
                )
            )
        }

        val response = KtorVisualCrossingApi(
            client = client,
            baseUrl = "https://weather.visualcrossing.test/VisualCrossingWebServices/rest/services",
            clock = fixedClock(Instant.parse("2026-11-09T12:00:00Z"))
        ).forecast(location)

        val request = requireNotNull(capturedRequest)
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("weather.visualcrossing.test", request.url.host)
        assertEquals(
            "/VisualCrossingWebServices/rest/services/timeline/52.52,13.419/2026-11-08/2026-11-16",
            request.url.encodedPath
        )
        assertEquals("gzip", request.headers[HttpHeaders.AcceptEncoding])
        assertEquals("metric", request.url.parameters["unitGroup"])
        assertEquals("days,hours,current", request.url.parameters["include"])
        assertEquals("json", request.url.parameters["contentType"])
        val elements = requireNotNull(request.url.parameters["elements"]).split(',').toSet()
        assertTrue("datetime" in elements)
        assertTrue("datetimeEpoch" in elements)
        assertTrue("precipprob" in elements)
        assertTrue("windspeed" in elements)
        assertTrue("moonphase" in elements)
        assertFalse("et0" in elements)
        assertFalse("aqieur" in elements)
        assertFalse("pm2p5" in elements)
        assertFalse("pm10" in elements)
        assertFalse("key" in request.url.parameters.names())
        assertEquals("Europe/Berlin", response.timezone)
        assertEquals(2, response.days?.single()?.hours?.size)

        client.close()
    }

    @Test
    fun air_quality_requests_only_optional_hourly_products_separately() = runTest {
        var capturedRequest: HttpRequestData? = null
        val client = mockClient { request ->
            capturedRequest = request
            respond(fixture("visual-crossing-air-quality.json"))
        }

        val response = KtorVisualCrossingApi(
            client = client,
            baseUrl = "https://weather.visualcrossing.test/VisualCrossingWebServices/rest/services",
            clock = fixedClock(Instant.parse("2026-11-09T12:00:00Z"))
        ).airQuality(location)

        val request = requireNotNull(capturedRequest)
        assertEquals(
            "/VisualCrossingWebServices/rest/services/timeline/52.52,13.419/2026-11-08/2026-11-16",
            request.url.encodedPath
        )
        assertEquals("gzip", request.headers[HttpHeaders.AcceptEncoding])
        assertEquals("metric", request.url.parameters["unitGroup"])
        assertEquals("days,hours", request.url.parameters["include"])
        assertEquals("json", request.url.parameters["contentType"])
        assertEquals(
            setOf("datetime", "datetimeEpoch", "aqieur", "pm2p5", "pm10"),
            requireNotNull(request.url.parameters["elements"]).split(',').toSet()
        )
        assertFalse("et0" in requireNotNull(request.url.parameters["elements"]).split(','))
        assertFalse("key" in request.url.parameters.names())
        assertNotNull(response?.days?.single()?.hours?.first()?.aqieur)

        client.close()
    }

    @Test
    fun denied_optional_air_quality_statuses_are_unavailable_not_errors() = runTest {
        listOf(
            HttpStatusCode.Unauthorized,
            HttpStatusCode.Forbidden,
            HttpStatusCode.TooManyRequests,
            HttpStatusCode.InternalServerError
        )
            .forEach { status ->
                val client = mockClient { respond("optional product denied", status) }
                val result = KtorVisualCrossingApi(
                    client = client,
                    clock = fixedClock(Instant.parse("2026-11-09T12:00:00Z"))
                ).airQuality(location)

                assertNull("HTTP ${status.value}", result)
                client.close()
            }
    }

    @Test
    fun denied_optional_products_leave_the_core_forecast_usable() = runTest {
        listOf(
            HttpStatusCode.Unauthorized,
            HttpStatusCode.Forbidden,
            HttpStatusCode.TooManyRequests,
            HttpStatusCode.InternalServerError
        ).forEach { status ->
            var requestCount = 0
            val client = mockClient {
                if (requestCount++ == 0) {
                    respond(fixture())
                } else {
                    respond("optional product denied", status)
                }
            }
            val forecast = VisualCrossingWeatherRepository(
                api = KtorVisualCrossingApi(
                    client = client,
                    baseUrl = "https://weather.visualcrossing.test/VisualCrossingWebServices/rest/services",
                    clock = fixedClock(Instant.parse("2026-11-09T12:00:00Z"))
                ),
                clock = fixedClock(Instant.parse("2026-11-09T12:00:00Z"))
            ).getForecast(location)

            assertEquals("HTTP ${status.value}", 2, requestCount)
            assertEquals("HTTP ${status.value}", 8.4, forecast.current.temperature, 0.0)
            assertEquals("HTTP ${status.value}", 2, forecast.hourly.size)
            assertNull("HTTP ${status.value}", forecast.hourly.first().europeanAqi)
            assertNull("HTTP ${status.value}", forecast.hourly.first().pm2_5)
            assertNull("HTTP ${status.value}", forecast.hourly.first().pm10)
            client.close()
        }
    }

    @Test
    fun forecast_reports_only_status_for_http_errors() = runTest {
        val sensitiveResponse = "denied for 52.52,13.419; key=secret-token"
        val client = mockClient {
            respond(sensitiveResponse, HttpStatusCode.Forbidden)
        }

        val exception = assertThrows(VisualCrossingApiException::class.java, ThrowingRunnable {
            kotlinx.coroutines.runBlocking {
                KtorVisualCrossingApi(client, clock = fixedClock(Instant.parse("2026-11-09T12:00:00Z")))
                    .forecast(location)
            }
        })

        assertEquals("Weather service returned HTTP 403", exception.message)
        assertFalse(exception.toString().contains("52.52"))
        assertFalse(exception.toString().contains("secret-token"))
        client.close()
    }

    @Test
    fun forecast_hides_request_details_when_response_decoding_fails() = runTest {
        val client = mockClient {
            respond(
                content = "secret-token 52.52,13.419",
                headers = headersOf(HttpHeaders.ContentEncoding, "gzip")
            )
        }

        val exception = assertThrows(VisualCrossingApiException::class.java, ThrowingRunnable {
            kotlinx.coroutines.runBlocking {
                KtorVisualCrossingApi(client, clock = fixedClock(Instant.parse("2026-11-09T12:00:00Z")))
                    .forecast(location)
            }
        })

        assertEquals("Unable to decode weather response", exception.message)
        assertFalse(exception.toString().contains("52.52"))
        assertFalse(exception.toString().contains("secret-token"))
        client.close()
    }

    @Test
    fun forecast_sanitizes_transport_errors_without_preserving_the_cause() = runTest {
        val client = mockClient {
            throw IllegalStateException(
                "https://weather.visualcrossing.test/timeline/52.52,13.419?key=secret-token"
            )
        }

        val exception = assertThrows(VisualCrossingApiException::class.java, ThrowingRunnable {
            kotlinx.coroutines.runBlocking {
                KtorVisualCrossingApi(client, clock = fixedClock(Instant.parse("2026-11-09T12:00:00Z")))
                    .forecast(location)
            }
        })

        assertEquals("Weather request failed", exception.message)
        assertNull(exception.cause)
        assertFalse(exception.toString().contains("52.52"))
        assertFalse(exception.toString().contains("secret-token"))
        assertFalse(exception.toString().contains("weather.visualcrossing.test"))
        client.close()
    }

    private fun mockClient(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
    ): HttpClient = HttpClient(MockEngine) {
        engine { addHandler(handler) }
    }

    private fun gzip(value: String): ByteArray = ByteArrayOutputStream().use { output ->
        GZIPOutputStream(output).use { it.write(value.toByteArray(Charsets.UTF_8)) }
        output.toByteArray()
    }

    private fun fixedClock(instant: Instant): Clock = object : Clock {
        override fun now(): Instant = instant
    }

    private fun fixture(name: String = "visual-crossing-timeline.json"): String = requireNotNull(
        javaClass.getResourceAsStream("/provider-fixtures/$name")
    ).bufferedReader().use { it.readText() }

    private companion object {
        val location = WeatherLocation("berlin", "Berlin", 52.52, 13.419)
    }
}
