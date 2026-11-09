package net.droopia.hluweather.data.repository

import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import net.droopia.hluweather.data.MoonPhaseCalculator
import net.droopia.hluweather.data.model.WeatherCondition
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherProvider
import net.droopia.hluweather.data.network.VisualCrossingApi
import net.droopia.hluweather.data.network.VisualCrossingApiException
import net.droopia.hluweather.data.network.VisualCrossingCurrent
import net.droopia.hluweather.data.network.VisualCrossingDay
import net.droopia.hluweather.data.network.VisualCrossingHour
import net.droopia.hluweather.data.network.VisualCrossingTimelineResponse
import net.droopia.hluweather.data.timezone.LocationTimeZone
import net.droopia.hluweather.data.timezone.TimeZoneResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.function.ThrowingRunnable

class VisualCrossingWeatherRepositoryTest {

    @Test
    fun getForecast_maps_core_current_hourly_daily_and_metric_fields() = runTest {
        val forecast = repository(readFixture()).getForecast(location)

        assertEquals(WeatherProvider.VISUAL_CROSSING, forecast.provider)
        assertEquals(location, forecast.location)
        assertEquals("Europe/Berlin", forecast.timezone)
        assertFalse(forecast.timezoneIsUtcFallback)

        assertEquals(8.4, forecast.current.temperature, 0.0)
        assertEquals(6.9, forecast.current.apparentTemperature!!, 0.0)
        assertEquals(82, forecast.current.humidity)
        assertEquals(5.4, forecast.current.dewPoint!!, 0.0)
        assertEquals(0.0, forecast.current.precipitation!!, 0.0)
        assertEquals(WeatherCondition.CLOUDY, forecast.current.condition)
        assertNull(forecast.current.isDay)

        assertEquals(2, forecast.hourly.size)
        val first = forecast.hourly[0]
        assertEquals(Instant.parse("2026-11-09T09:00:00Z"), first.time)
        assertEquals(8.4, first.temperature, 0.0)
        assertEquals(6.9, first.apparentTemperature!!, 0.0)
        assertEquals(82, first.humidity)
        assertEquals(5.4, first.dewPoint!!, 0.0)
        assertEquals(0.0, first.precipitation!!, 0.0)
        assertEquals(0, first.precipitationProbability)
        assertEquals(12.2, first.windSpeedKmh!!, 0.0)
        assertEquals(245.0, first.windDirectionDegrees!!, 0.0)
        assertEquals(WeatherCondition.CLOUDY, first.condition)
        assertEquals(35, forecast.hourly[1].precipitationProbability)
        assertEquals(WeatherCondition.RAIN, forecast.hourly[1].condition)

        val day = forecast.daily.single()
        assertEquals("2026-11-09", day.date.toString())
        assertEquals(WeatherCondition.RAIN, day.condition)
        assertEquals(5.0, day.temperatureMin, 0.0)
        assertEquals(9.1, day.temperatureMax, 0.0)
        assertEquals(0.2, day.precipitation!!, 0.0)
        assertEquals(Instant.parse("2026-11-09T06:12:00Z"), day.sunrise)
        assertEquals(Instant.parse("2026-11-09T15:18:00Z"), day.sunset)
        assertEquals(0.63, forecast.moonPhase, 0.0)
    }

    @Test
    fun getForecast_preserves_repeated_and_skipped_civil_hours_by_epoch() = runTest {
        val autumn = forecastFor(
            timezone = "Europe/Berlin",
            fetchedAt = "2026-10-24T22:30:00Z",
            day = VisualCrossingDay(
                datetime = "2026-10-25",
                tempmin = 1.0,
                tempmax = 4.0,
                hours = listOf(
                    hour("2026-10-25T00:00:00Z"),
                    hour("2026-10-25T01:00:00Z")
                )
            )
        )
        assertEquals(
            listOf("2026-10-25T00:00:00Z", "2026-10-25T01:00:00Z"),
            autumn.hourly.map { it.time.toString() }
        )
        assertEquals(
            listOf(2, 2),
            autumn.hourly.map { it.time.toLocalDateTime(TimeZone.of(autumn.timezone)).hour }
        )
        assertEquals(
            listOf("+02:00", "+01:00"),
            autumn.hourly.map { java.time.Instant.ofEpochMilli(it.time.toEpochMilliseconds()).atZone(java.time.ZoneId.of(autumn.timezone)).offset.toString() }
        )

        val spring = forecastFor(
            timezone = "Europe/Berlin",
            fetchedAt = "2026-03-28T23:30:00Z",
            day = VisualCrossingDay(
                datetime = "2026-03-29",
                tempmin = 1.0,
                tempmax = 4.0,
                hours = listOf(hour("2026-03-29T00:00:00Z"), hour("2026-03-29T01:00:00Z"))
            )
        )
        assertEquals(
            listOf(1, 3),
            spring.hourly.map { it.time.toLocalDateTime(TimeZone.of(spring.timezone)).hour }
        )
    }

    @Test
    fun missing_optional_fields_stay_unavailable_and_zero_stays_zero() = runTest {
        val response = VisualCrossingTimelineResponse(
            timezone = "Europe/Belgrade",
            currentConditions = VisualCrossingCurrent(temp = 18.0, precip = 0.0),
            days = listOf(
                VisualCrossingDay(
                    datetime = "2026-09-10",
                    tempmin = 10.0,
                    tempmax = 20.0,
                    hours = listOf(hour("2026-09-10T10:00:00Z", temp = 18.0, precip = 0.0, precipprob = 0.0))
                )
            )
        )

        val forecast = repository(
            response,
            clock = fixedClock(Instant.parse("2026-09-10T10:30:00Z"))
        ).getForecast(location)

        assertEquals(0.0, forecast.current.precipitation!!, 0.0)
        assertNull(forecast.current.apparentTemperature)
        assertNull(forecast.current.humidity)
        assertNull(forecast.current.dewPoint)
        assertNull(forecast.current.isDay)
        assertEquals(0.0, forecast.hourly.single().precipitation!!, 0.0)
        assertEquals(0, forecast.hourly.single().precipitationProbability)
        assertNull(forecast.hourly.single().windSpeedKmh)
        assertNull(forecast.hourly.single().windDirectionDegrees)
        assertNull(forecast.daily.single().precipitation)
        assertNull(forecast.daily.single().sunrise)
        assertNull(forecast.daily.single().sunset)
        assertEquals(MoonPhaseCalculator.phase(forecast.fetchedAt), forecast.moonPhase, 0.0)
    }

    @Test
    fun invalid_timezone_uses_the_pinned_resolver_and_keeps_utc_fallback_explicit() = runTest {
        var resolveCount = 0
        val fallbackResolver = TimeZoneResolver { _, _ ->
            resolveCount += 1
            LocationTimeZone("UTC", isUtcFallback = true)
        }
        val response = readFixture().copy(timezone = "Unknown/Zone")

        val forecast = repository(response, resolver = fallbackResolver).getForecast(location)

        assertEquals(1, resolveCount)
        assertEquals("UTC", forecast.timezone)
        assertTrue(forecast.timezoneIsUtcFallback)
        assertTrue(forecast.daily.isEmpty())
        assertEquals(MoonPhaseCalculator.phase(forecast.fetchedAt), forecast.moonPhase, 0.0)
    }

    @Test
    fun missing_required_data_and_api_failures_are_safe_and_cancellation_is_preserved() = runTest {
        val noTemperature = readFixture().copy(
            currentConditions = VisualCrossingCurrent(),
            timezone = "Europe/Berlin"
        )
        val error = assertThrows(WeatherRepositoryException::class.java, ThrowingRunnable {
            runBlocking { repository(noTemperature).getForecast(location) }
        })
        assertEquals("Missing current temperature", error.message)
        assertFalse(error.toString().contains("52.52"))
        assertFalse(error.toString().contains("13.419"))

        val apiFailure = VisualCrossingApiException("Weather service returned HTTP 401")
        val failingApi = object : VisualCrossingApi {
            override suspend fun forecast(location: WeatherLocation): VisualCrossingTimelineResponse = throw apiFailure
        }
        val wrapped = assertThrows(WeatherRepositoryException::class.java, ThrowingRunnable {
            runBlocking { repository(api = failingApi).getForecast(location) }
        })
        assertEquals("Weather service returned HTTP 401", wrapped.message)
        assertNull(wrapped.cause)

        val cancellation = CancellationException("cancelled")
        val cancellingApi = object : VisualCrossingApi {
            override suspend fun forecast(location: WeatherLocation): VisualCrossingTimelineResponse = throw cancellation
        }
        val thrown = assertThrows(CancellationException::class.java, ThrowingRunnable {
            runBlocking { repository(api = cancellingApi).getForecast(location) }
        })
        assertSame(cancellation, thrown)
    }

    private suspend fun forecastFor(
        timezone: String,
        fetchedAt: String,
        day: VisualCrossingDay
    ) = repository(
        response = VisualCrossingTimelineResponse(
            timezone = timezone,
            currentConditions = VisualCrossingCurrent(temp = 3.0),
            days = listOf(day)
        ),
        clock = fixedClock(Instant.parse(fetchedAt))
    ).getForecast(location)

    private fun repository(
        response: VisualCrossingTimelineResponse,
        clock: Clock = fixedClock(Instant.parse("2026-11-09T09:30:00Z")),
        resolver: TimeZoneResolver = TimeZoneResolver { _, _ -> error("Valid Visual Crossing zone should be used") }
    ) = VisualCrossingWeatherRepository(
        api = object : VisualCrossingApi {
            override suspend fun forecast(location: WeatherLocation): VisualCrossingTimelineResponse = response
        },
        clock = clock,
        timeZoneResolver = resolver
    )

    private fun repository(
        api: VisualCrossingApi
    ) = VisualCrossingWeatherRepository(api = api, clock = fixedClock(Instant.parse("2026-11-09T09:30:00Z")))

    private fun hour(
        time: String,
        temp: Double = 3.0,
        precip: Double? = null,
        precipprob: Double? = null
    ) = VisualCrossingHour(
        datetimeEpoch = Instant.parse(time).epochSeconds,
        temp = temp,
        precip = precip,
        precipprob = precipprob
    )

    private fun readFixture(): VisualCrossingTimelineResponse = Json { ignoreUnknownKeys = true }.decodeFromString(
        requireNotNull(javaClass.getResourceAsStream("/provider-fixtures/visual-crossing-timeline.json"))
            .bufferedReader().use { it.readText() }
    )

    private fun fixedClock(instant: Instant): Clock = object : Clock {
        override fun now(): Instant = instant
    }

    private companion object {
        val location = WeatherLocation("berlin", "Berlin", 52.52, 13.419)
    }
}
