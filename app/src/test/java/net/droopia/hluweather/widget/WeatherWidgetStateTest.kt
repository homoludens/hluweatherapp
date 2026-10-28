package net.droopia.hluweather.widget

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

        val celsius = load.toWeatherWidgetState(
            settings = PersistedSettings(temperatureUnit = TemperatureUnit.CELSIUS)
        )
        val fahrenheit = load.toWeatherWidgetState(
            settings = PersistedSettings(temperatureUnit = TemperatureUnit.FAHRENHEIT)
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
            .toWeatherWidgetState(PersistedSettings())

        assertNull(state.apparentTemperatureText)
        assertNull(state.apparentTemperatureContentDescription)
    }

    @Test
    fun missing_and_zero_hourly_probability_have_distinct_display_and_spoken_values() {
        val missing = hour(probability = null)
        val zero = hour(probability = 0, time = Instant.parse("2026-10-28T13:00:00Z"))
        val state = ForecastLoad(forecast(hours = listOf(missing, zero)))
            .toWeatherWidgetState(PersistedSettings())

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
            locationName = "Current location"
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

    private fun forecast(
        fetchedAt: Instant = Instant.parse("2026-10-28T10:00:00Z"),
        apparentTemperature: Double? = 20.2,
        hours: List<HourForecast> = listOf(hour())
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
        timezone = "Europe/Belgrade"
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
}
