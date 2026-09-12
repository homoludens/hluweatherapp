package net.droopia.hluweather.data.repository

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.RouteWeatherSample
import net.droopia.hluweather.data.model.WeatherCondition
import net.droopia.hluweather.data.network.OpenMeteoRouteHourlyDto
import net.droopia.hluweather.data.network.OpenMeteoRouteResponse
import net.droopia.hluweather.data.network.OpenMeteoRouteWeatherApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.function.ThrowingRunnable

class OpenMeteoRouteWeatherSourceTest {

    @Test
    fun enrich_selects_the_exact_arrival_hour() = runTest {
        val samples = listOf(sampleAt(1_789_000_000L))
        val source = OpenMeteoRouteWeatherSource(FakeApi(listOf(responseWithHours())))

        val enriched = source.enrich(samples)

        assertEquals(20.0, enriched.single().temperatureCelsius!!, 0.0)
        assertEquals(12.0, enriched.single().windSpeedKmh!!, 0.0)
        assertEquals(10, enriched.single().precipitationProbability)
    }

    @Test
    fun enrich_selects_the_later_hour_when_arrival_is_halfway_between_hours() = runTest {
        val samples = listOf(sampleAt(1_789_001_800L))
        val source = OpenMeteoRouteWeatherSource(FakeApi(listOf(responseWithHours())))

        val enriched = source.enrich(samples)

        assertEquals(21.0, enriched.single().temperatureCelsius!!, 0.0)
        assertEquals(61, enriched.single().precipitationProbability)
    }

    @Test
    fun enrich_maps_rain_and_thunderstorm_wmo_codes() = runTest {
        val samples = listOf(sampleAt(1_789_000_000L), sampleAt(1_789_003_600L))
        val response = OpenMeteoRouteResponse(
            hourly = OpenMeteoRouteHourlyDto(
                time = listOf(1_789_000_000L, 1_789_003_600L),
                temperature = listOf(20.0, 18.0),
                weatherCode = listOf(82, 95),
                windSpeed = listOf(12.0, 30.0),
                precipitationProbability = listOf(80, 90)
            )
        )

        val enriched = OpenMeteoRouteWeatherSource(FakeApi(listOf(response, response))).enrich(samples)

        assertEquals(WeatherCondition.RAIN, enriched[0].condition)
        assertEquals(WeatherCondition.THUNDERSTORM, enriched[1].condition)
    }

    @Test
    fun enrich_makes_only_an_invalid_location_unavailable() = runTest {
        val samples = listOf(sampleAt(1_789_000_000L), sampleAt(1_789_000_000L))
        val response = listOf(
            OpenMeteoRouteResponse(hourly = null),
            responseWithHours()
        )

        val enriched = OpenMeteoRouteWeatherSource(FakeApi(response)).enrich(samples)

        assertNull(enriched[0].condition)
        assertNull(enriched[0].temperatureCelsius)
        assertEquals(20.0, enriched[1].temperatureCelsius!!, 0.0)
    }

    @Test
    fun enrich_propagates_a_request_level_failure() = runTest {
        val source = OpenMeteoRouteWeatherSource(object : OpenMeteoRouteWeatherApi {
            override suspend fun forecast(points: List<GeoPoint>): List<OpenMeteoRouteResponse> {
                throw IllegalStateException("network failure")
            }
        })

        val exception = assertThrows(IllegalStateException::class.java, ThrowingRunnable {
            runBlocking { source.enrich(listOf(sampleAt(1_789_000_000L))) }
        })

        assertEquals("network failure", exception.message)
    }

    private fun sampleAt(epochSeconds: Long) = RouteWeatherSample(
        point = GeoPoint(44.8, 20.4),
        distanceMeters = 1000.0,
        arrivalTime = Instant.fromEpochSeconds(epochSeconds)
    )

    private fun responseWithHours() = OpenMeteoRouteResponse(
        hourly = OpenMeteoRouteHourlyDto(
            time = listOf(1_789_000_000L, 1_789_003_600L),
            temperature = listOf(20.0, 21.0),
            weatherCode = listOf(1, 61),
            windSpeed = listOf(12.0, 14.0),
            precipitationProbability = listOf(10, 61)
        )
    )

    private class FakeApi(
        private val response: List<OpenMeteoRouteResponse>
    ) : OpenMeteoRouteWeatherApi {
        override suspend fun forecast(points: List<GeoPoint>): List<OpenMeteoRouteResponse> = response
    }
}
