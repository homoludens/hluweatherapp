package net.droopia.hluweather.data.repository

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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.network.KtorNominatimApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReverseGeocoderTest {

    @Test
    fun reverse_returns_the_most_useful_place_name() = runTest {
        val client = mockClient {
            respond(
                """
                    {"display_name":"Svilajnac, Serbia","address":{"town":"Svilajnac","country":"Serbia"}}
                """.trimIndent(),
                headers = jsonHeaders
            )
        }
        val geocoder = NominatimReverseGeocoder(
            KtorNominatimApi(client, "https://nominatim.test"),
            throttle = NominatimRateLimiter(nowMillis = { 0 }, delayMillis = {})
        )

        assertEquals("Svilajnac", geocoder.reverse(GeoPoint(44.23, 21.2)))

        client.close()
    }

    @Test
    fun reverse_prefers_a_neighborhood_over_a_city_or_municipality() = runTest {
        val client = mockClient {
            respond(
                """
                    {"address":{"city":"Belgrade","suburb":"Zvezdara","neighbourhood":"Lion","municipality":"Belgrade"}}
                """.trimIndent(),
                headers = jsonHeaders
            )
        }
        val geocoder = NominatimReverseGeocoder(
            KtorNominatimApi(client, "https://nominatim.test"),
            throttle = NominatimRateLimiter(nowMillis = { 0 }, delayMillis = {})
        )

        assertEquals("Lion, Belgrade", geocoder.reverse(GeoPoint(44.8, 20.5)))

        client.close()
    }

    @Test
    fun reverse_returns_null_for_http_and_json_failures() = runTest {
        var responseNumber = 0
        val client = mockClient {
            if (responseNumber++ == 0) {
                respond("unavailable", HttpStatusCode.ServiceUnavailable)
            } else {
                respond("{invalid", headers = jsonHeaders)
            }
        }
        val geocoder = NominatimReverseGeocoder(
            KtorNominatimApi(client, "https://nominatim.test"),
            throttle = NominatimRateLimiter(nowMillis = { 0 }, delayMillis = {})
        )

        assertNull(geocoder.reverse(GeoPoint(44.23, 21.2)))
        assertNull(geocoder.reverse(GeoPoint(44.24, 21.21)))

        client.close()
    }

    @Test
    fun reverse_waits_one_second_between_requests() = runTest {
        var now = 0L
        val waits = mutableListOf<Long>()
        val client = mockClient {
            respond("{\"name\":\"Belgrade\"}", headers = jsonHeaders)
        }
        val geocoder = NominatimReverseGeocoder(
            KtorNominatimApi(client, "https://nominatim.test"),
            throttle = NominatimRateLimiter(
                nowMillis = { now },
                delayMillis = { millis ->
                    waits += millis
                    now += millis
                }
            )
        )

        geocoder.reverse(GeoPoint(44.8, 20.4))
        now = 100L
        geocoder.reverse(GeoPoint(44.9, 20.5))

        assertEquals(listOf(900L), waits)
        client.close()
    }

    @Test
    fun cancellation_replaces_an_obsolete_request() = runTest {
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val requests = mutableListOf<String?>()
        val client = mockClient { request ->
            val latitude = request.url.parameters["lat"]
            requests += latitude
            if (latitude == "44.8") {
                firstStarted.complete(Unit)
                releaseFirst.await()
            }
            respond("{\"name\":\"Belgrade\"}", headers = jsonHeaders)
        }
        val geocoder = NominatimReverseGeocoder(
            KtorNominatimApi(client, "https://nominatim.test"),
            throttle = NominatimRateLimiter(nowMillis = { 0 }, delayMillis = { delay(it) })
        )

        val obsolete: Job = launch {
            geocoder.reverse(GeoPoint(44.8, 20.4))
        }
        firstStarted.await()
        obsolete.cancel()
        obsolete.join()

        val replacement = launch {
            geocoder.reverse(GeoPoint(44.9, 20.5))
        }
        testScheduler.advanceUntilIdle()
        replacement.join()

        assertEquals(listOf("44.8", "44.9"), requests)
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
        val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    }
}
