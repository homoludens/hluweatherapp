package net.droopia.hluweather.widget

import java.util.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant
import net.droopia.hluweather.data.model.CurrentWeather
import net.droopia.hluweather.data.model.DayForecast
import net.droopia.hluweather.data.model.HourForecast
import net.droopia.hluweather.data.model.WeatherCondition
import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherProvider
import net.droopia.hluweather.data.repository.ForecastLoad
import net.droopia.hluweather.ui.settings.PersistedSettings
import net.droopia.hluweather.ui.settings.TemperatureUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherWidgetStateTest {

    @Test
    fun maps_celsius_and_fahrenheit_using_shared_rounding_and_spoken_units() {
        val load = ForecastLoad(forecast())
        val clock = fixedClock(Instant.parse("2026-10-28T09:00:00Z"))

        val celsius = load.toWeatherWidgetState(
            settings = PersistedSettings(temperatureUnit = TemperatureUnit.CELSIUS),
            clock = clock
        )
        val fahrenheit = load.toWeatherWidgetState(
            settings = PersistedSettings(temperatureUnit = TemperatureUnit.FAHRENHEIT),
            clock = clock
        )

        assertEquals("Zaječar", celsius.locationName)
        assertEquals("22°C", celsius.temperatureText)
        assertEquals("22 degrees C", celsius.temperatureContentDescription)
        assertEquals("20°C", celsius.apparentTemperatureText)
        assertEquals("20 degrees C", celsius.apparentTemperatureContentDescription)
        assertEquals("71°F", fahrenheit.temperatureText)
        assertEquals("71 degrees F", fahrenheit.temperatureContentDescription)
        assertEquals("68°F", fahrenheit.apparentTemperatureText)
        assertEquals("68 degrees F", fahrenheit.apparentTemperatureContentDescription)

        val hour = fahrenheit.hours.single()
        assertEquals("32°F", hour.temperatureText)
        assertEquals("32 degrees F", hour.temperatureContentDescription)
    }

    @Test
    fun null_apparent_temperature_is_omitted() {
        val state = ForecastLoad(forecast(apparentTemperature = null))
            .toWeatherWidgetState(
                settings = PersistedSettings(),
                clock = fixedClock(Instant.parse("2026-10-28T09:00:00Z"))
            )

        assertNull(state.apparentTemperatureText)
        assertNull(state.apparentTemperatureContentDescription)
    }

    @Test
    fun missing_and_zero_hourly_probability_have_distinct_display_and_spoken_values() {
        val missing = hour(probability = null)
        val zero = hour(probability = 0, time = Instant.parse("2026-10-28T13:00:00Z"))
        val state = ForecastLoad(forecast(hours = listOf(missing, zero)))
            .toWeatherWidgetState(
                settings = PersistedSettings(),
                clock = fixedClock(Instant.parse("2026-10-28T11:00:00Z"))
            )

        assertEquals("—", state.hours[0].precipitationProbabilityText)
        assertEquals("Rain probability unavailable", state.hours[0].precipitationProbabilityContentDescription)
        assertEquals(0, state.hours[1].precipitationProbability)
        assertEquals("0%", state.hours[1].precipitationProbabilityText)
        assertEquals("Rain probability 0 percent", state.hours[1].precipitationProbabilityContentDescription)
    }

    @Test
    fun mapping_preserves_shared_condition_day_flag_and_weather_timestamp_for_stale_data() {
        val fetchedAt = Instant.parse("2026-10-28T09:17:00Z")
        val load = ForecastLoad(
            forecast(fetchedAt = fetchedAt),
            isStale = true
        )

        val state = load.toWeatherWidgetState(
            settings = PersistedSettings(),
            locationName = "Current location",
            clock = fixedClock(Instant.parse("2026-10-28T09:00:00Z"))
        )

        assertEquals("Current location", state.locationName)
        assertSame(WeatherCondition.CLEAR, state.condition)
        assertEquals("Clear sky", state.conditionLabel)
        assertEquals(true, state.isDay)
        assertEquals(fetchedAt, state.lastWeatherUpdate)
        assertTrue(state.isStale)
        assertSame(WeatherCondition.PARTLY_CLOUDY, state.hours.single().condition)
        assertEquals("Partly cloudy", state.hours.single().conditionLabel)
        assertFalse(state.hours.single().isDay ?: true)
    }

    @Test
    fun selects_future_hours_by_instant_sorts_them_and_caps_at_five_across_midnight() {
        val forecast = forecast(
            fetchedAt = Instant.parse("2026-10-28T20:00:00Z"),
            hours = listOf(
                hour(time = Instant.parse("2026-10-29T02:00:00Z")),
                hour(time = Instant.parse("2026-10-28T20:00:00Z")),
                hour(time = Instant.parse("2026-10-29T01:00:00Z")),
                hour(time = Instant.parse("2026-10-28T21:00:00Z")),
                hour(time = Instant.parse("2026-10-28T20:30:00Z")),
                hour(time = Instant.parse("2026-10-28T23:00:00Z")),
                hour(time = Instant.parse("2026-10-28T22:00:00Z")),
                hour(time = Instant.parse("2026-10-29T00:00:00Z"))
            )
        )

        val state = ForecastLoad(forecast).toWeatherWidgetState(
            settings = PersistedSettings(),
            clock = fixedClock(Instant.parse("2026-10-28T20:30:00Z"))
        )

        assertEquals(
            listOf(
                Instant.parse("2026-10-28T21:00:00Z"),
                Instant.parse("2026-10-28T22:00:00Z"),
                Instant.parse("2026-10-28T23:00:00Z"),
                Instant.parse("2026-10-29T00:00:00Z"),
                Instant.parse("2026-10-29T01:00:00Z")
            ),
            state.hours.map { it.time }
        )
        assertEquals(listOf("22:00", "23:00", "00:00", "01:00", "02:00"), state.hours.map { it.timeText })
    }

    @Test
    fun hourly_and_update_labels_use_forecast_zone_instead_of_device_zone() = withDeviceTimeZone("America/Los_Angeles") {
        val fetchedAt = Instant.parse("2026-10-28T21:17:00Z")
        val state = ForecastLoad(
            forecast(
                fetchedAt = fetchedAt,
                hours = listOf(hour(time = Instant.parse("2026-10-28T22:00:00Z")))
            )
        ).toWeatherWidgetState(
            settings = PersistedSettings(),
            clock = fixedClock(Instant.parse("2026-10-28T20:30:00Z"))
        )

        assertEquals("23:00", state.hours.single().timeText)
        assertEquals("Wed, Oct 28, 2026 • 22:17", state.lastWeatherUpdateText)
        assertEquals(fetchedAt, state.lastWeatherUpdate)
    }

    @Test
    fun daylight_saving_repeated_and_skipped_hours_follow_sorted_instants() {
        val repeated = ForecastLoad(
            forecast(
                timezone = "Europe/Belgrade",
                hours = listOf(
                    hour(time = Instant.parse("2026-10-25T01:30:00Z")),
                    hour(time = Instant.parse("2026-10-25T00:30:00Z")),
                    hour(time = Instant.parse("2026-10-25T01:00:00Z"))
                )
            )
        ).toWeatherWidgetState(
            settings = PersistedSettings(),
            clock = fixedClock(Instant.parse("2026-10-25T00:00:00Z"))
        )
        val skipped = ForecastLoad(
            forecast(
                timezone = "Europe/Belgrade",
                hours = listOf(
                    hour(time = Instant.parse("2026-03-29T01:30:00Z")),
                    hour(time = Instant.parse("2026-03-29T00:30:00Z")),
                    hour(time = Instant.parse("2026-03-29T01:00:00Z"))
                )
            )
        ).toWeatherWidgetState(
            settings = PersistedSettings(),
            clock = fixedClock(Instant.parse("2026-03-29T00:00:00Z"))
        )

        assertEquals(
            listOf(Instant.parse("2026-10-25T00:30:00Z"), Instant.parse("2026-10-25T01:00:00Z"), Instant.parse("2026-10-25T01:30:00Z")),
            repeated.hours.map { it.time }
        )
        assertEquals(listOf("02:30", "02:00", "02:30"), repeated.hours.map { it.timeText })
        assertEquals(
            listOf(Instant.parse("2026-03-29T00:30:00Z"), Instant.parse("2026-03-29T01:00:00Z"), Instant.parse("2026-03-29T01:30:00Z")),
            skipped.hours.map { it.time }
        )
        assertEquals(listOf("01:30", "03:00", "03:30"), skipped.hours.map { it.timeText })
    }

    @Test
    fun invalid_forecast_zone_falls_back_to_system_zone_for_both_timestamps() = withDeviceTimeZone("Asia/Tokyo") {
        val state = ForecastLoad(
            forecast(
                fetchedAt = Instant.parse("2026-10-28T20:15:00Z"),
                timezone = "Invalid/Forecast-Zone",
                hours = listOf(hour(time = Instant.parse("2026-10-28T21:00:00Z")))
            )
        ).toWeatherWidgetState(
            settings = PersistedSettings(),
            clock = fixedClock(Instant.parse("2026-10-28T20:00:00Z"))
        )

        assertEquals("06:00", state.hours.single().timeText)
        assertEquals("Thu, Oct 29, 2026 • 05:15", state.lastWeatherUpdateText)
    }

    @Test
    fun fewer_future_hours_are_not_repeated_and_expired_stale_cache_keeps_original_timestamp() {
        val fetchedAt = Instant.parse("2026-10-28T10:17:00Z")
        val hours = listOf(
            hour(time = Instant.parse("2026-10-28T21:00:00Z")),
            hour(time = Instant.parse("2026-10-28T23:00:00Z"))
        )
        val clock = fixedClock(Instant.parse("2026-10-28T20:00:00Z"))
        val partial = ForecastLoad(forecast(fetchedAt = fetchedAt, hours = hours), isStale = true)
            .toWeatherWidgetState(settings = PersistedSettings(), clock = clock)
        val expired = ForecastLoad(
            forecast(
                fetchedAt = fetchedAt,
                hours = hours.map { it.copy(time = Instant.parse("2026-10-28T19:00:00Z")) }
            ),
            isStale = true
        ).toWeatherWidgetState(settings = PersistedSettings(), clock = clock)

        assertEquals(2, partial.hours.size)
        assertEquals(listOf("22:00", "00:00"), partial.hours.map { it.timeText })
        assertEquals(fetchedAt, partial.lastWeatherUpdate)
        assertEquals("Wed, Oct 28, 2026 • 11:17", partial.lastWeatherUpdateText)
        assertTrue(partial.isStale)
        assertTrue(expired.hours.isEmpty())
        assertEquals("22°C", expired.temperatureText)
        assertEquals(fetchedAt, expired.lastWeatherUpdate)
        assertTrue(expired.isStale)
    }

    private fun forecast(
        fetchedAt: Instant = Instant.parse("2026-10-28T10:00:00Z"),
        apparentTemperature: Double? = 20.2,
        hours: List<HourForecast> = listOf(hour()),
        timezone: String = "Europe/Belgrade"
    ) = WeatherForecast(
        location = WeatherLocation("zajecar", "Zaječar", 43.9, 20.3),
        provider = WeatherProvider.OPEN_METEO,
        fetchedAt = fetchedAt,
        current = CurrentWeather(
            temperature = 21.6,
            apparentTemperature = apparentTemperature,
            humidity = 50,
            dewPoint = 10.0,
            precipitation = 0.0,
            condition = WeatherCondition.CLEAR,
            isDay = true
        ),
        hourly = hours,
        daily = listOf(
            DayForecast(
                date = kotlinx.datetime.LocalDate(2026, 10, 28),
                condition = WeatherCondition.CLEAR,
                temperatureMin = 10.0,
                temperatureMax = 22.0,
                precipitation = null,
                sunrise = null,
                sunset = null
            )
        ),
        moonPhase = 0.5,
        timezone = timezone
    )

    private fun hour(
        probability: Int? = 0,
        time: Instant = Instant.parse("2026-10-28T12:00:00Z")
    ) = HourForecast(
        time = time,
        temperature = 0.2,
        apparentTemperature = null,
        humidity = null,
        dewPoint = null,
        precipitation = null,
        precipitationProbability = probability,
        condition = WeatherCondition.PARTLY_CLOUDY,
        isDay = false
    )

    private fun fixedClock(instant: Instant): Clock = object : Clock {
        override fun now(): Instant = instant
    }

    private fun withDeviceTimeZone(zoneId: String, block: () -> Unit) {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(zoneId))
            block()
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
