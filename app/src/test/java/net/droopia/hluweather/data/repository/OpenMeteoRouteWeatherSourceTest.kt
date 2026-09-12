package net.droopia.hluweather.data.repository

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.RouteWeatherSample
import net.droopia.hluweather.data.model.WeatherCondition
import net.droopia.hluweather.data.network.OpenMeteoRouteHourlyDto
import net.droopia.hluweather.data.network.OpenMeteoRouteResponse
import net.droopia.hluweather.data.network.OpenMeteoRouteWeatherApi
import net.droopia.hluweather.data.weatherroute.enrich
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.function.ThrowingRunnable

class OpenMeteoRouteWeatherSourceTest {

    @Test
    fun fetchSnapshot_preserves_the_exact_arrival_hour() = runTest {
        val samples = listOf(sampleAt(1_789_000_000L))
        val source = OpenMeteoRouteWeatherSource(FakeApi(listOf(responseWithHours())))

        val snapshot = source.fetchSnapshot(samples.map { it.point })
        val enriched = snapshot.enrich(samples)

        assertEquals(20.0, enriched.single().temperatureCelsius!!, 0.0)
        assertEquals(12.0, enriched.single().windSpeedKmh!!, 0.0)
        assertEquals(10, enriched.single().precipitationProbability)
        assertEquals(0.2, enriched.single().precipitationMm!!, 0.0)
        assertEquals(65, enriched.single().humidityPercent)
        assertEquals(true, enriched.single().isDay)
    }

    @Test
    fun fetchSnapshot_preserves_the_later_hour_for_a_halfway_arrival() = runTest {
        val samples = listOf(sampleAt(1_789_001_800L))
        val source = OpenMeteoRouteWeatherSource(FakeApi(listOf(responseWithHours())))

        val snapshot = source.fetchSnapshot(samples.map { it.point })
        val enriched = snapshot.enrich(samples)

        assertEquals(21.0, enriched.single().temperatureCelsius!!, 0.0)
        assertEquals(61, enriched.single().precipitationProbability)
    }

    @Test
    fun fetchSnapshot_maps_rain_and_thunderstorm_wmo_codes() = runTest {
        val samples = listOf(
            sampleAt(1_789_000_000L),
            sampleAt(1_789_003_600L, GeoPoint(45.0, 21.0))
        )
        val response = OpenMeteoRouteResponse(
            hourly = OpenMeteoRouteHourlyDto(
                time = listOf(1_789_000_000L, 1_789_003_600L),
                temperature = listOf(20.0, 18.0),
                weatherCode = listOf(82, 95),
                windSpeed = listOf(12.0, 30.0),
                precipitationProbability = listOf(80, 90),
                precipitation = listOf(1.0, 0.0),
                humidity = listOf(80, 60),
                isDay = listOf(1, 0)
            )
        )

        val source = OpenMeteoRouteWeatherSource(FakeApi(listOf(response, response)))
        val enriched = source.fetchSnapshot(samples.map { it.point }).enrich(samples)

        assertEquals(WeatherCondition.RAIN, enriched[0].condition)
        assertEquals(WeatherCondition.THUNDERSTORM, enriched[1].condition)
    }

    @Test
    fun fetchSnapshot_makes_only_an_invalid_location_unavailable() = runTest {
        val samples = listOf(
            sampleAt(1_789_000_000L),
            sampleAt(1_789_000_000L, GeoPoint(45.0, 21.0))
        )
        val response = listOf(
            OpenMeteoRouteResponse(hourly = null),
            responseWithHours()
        )

        val snapshot = OpenMeteoRouteWeatherSource(FakeApi(response))
            .fetchSnapshot(samples.map { it.point })

        assertEquals(0, snapshot.hourlyByPoint[0].size)
        assertEquals(2, snapshot.hourlyByPoint[1].size)
        assertEquals(20.0, snapshot.hourlyByPoint[1].first().temperatureCelsius!!, 0.0)
    }

    @Test
    fun fetchSnapshot_propagates_a_request_level_failure() = runTest {
        val source = OpenMeteoRouteWeatherSource(object : OpenMeteoRouteWeatherApi {
            override suspend fun forecast(points: List<GeoPoint>): List<OpenMeteoRouteResponse> {
                throw IllegalStateException("network failure")
            }
        })

        val exception = assertThrows(IllegalStateException::class.java, ThrowingRunnable {
            runBlocking { source.fetchSnapshot(listOf(GeoPoint(44.8, 20.4))) }
        })

        assertEquals("network failure", exception.message)
    }

    private fun sampleAt(epochSeconds: Long, point: GeoPoint = GeoPoint(44.8, 20.4)) = RouteWeatherSample(
        point = point,
        distanceMeters = 1000.0,
        arrivalTime = Instant.fromEpochSeconds(epochSeconds)
    )

    private fun responseWithHours() = OpenMeteoRouteResponse(
        hourly = OpenMeteoRouteHourlyDto(
            time = listOf(1_789_000_000L, 1_789_003_600L),
            temperature = listOf(20.0, 21.0),
            weatherCode = listOf(1, 61),
            windSpeed = listOf(12.0, 14.0),
            precipitationProbability = listOf(10, 61),
            precipitation = listOf(0.2, 0.4),
            humidity = listOf(65, 70),
            isDay = listOf(1, 1)
        )
    )

    private class FakeApi(
        private val response: List<OpenMeteoRouteResponse>
    ) : OpenMeteoRouteWeatherApi {
        override suspend fun forecast(points: List<GeoPoint>): List<OpenMeteoRouteResponse> = response
    }
}
