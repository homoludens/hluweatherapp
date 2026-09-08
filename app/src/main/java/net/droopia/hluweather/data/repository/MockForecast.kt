package net.droopia.hluweather.data.repository

import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import net.droopia.hluweather.data.MoonPhaseCalculator
import net.droopia.hluweather.data.model.CurrentWeather
import net.droopia.hluweather.data.model.DayForecast
import net.droopia.hluweather.data.model.HourForecast
import net.droopia.hluweather.data.model.WeatherCondition
import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherProvider

val Svilajnac = WeatherLocation(
    id = "svilajnac",
    name = "Svilajnac",
    latitude = 44.2380,
    longitude = 21.1970,
    altitude = 105
)

fun buildMockForecast(
    location: WeatherLocation,
    baseTime: Instant = Clock.System.now()
): WeatherForecast {
    val startDate =
        baseTime.toLocalDateTime(TimeZone.currentSystemDefault()).date

    val daily = (0 until 7).map { offset ->
        DayForecast(
            date = startDate.plus(offset, DateTimeUnit.DAY),
            condition = WeatherCondition.CLEAR,
            temperatureMin = 16.0,
            temperatureMax = 30.0,
            precipitation = 0.0,
            sunrise = null,
            sunset = null
        )
    }

    val hourly = (0 until 7 * 24).map { index ->
        val temperature = when (index % 24) {
            in 0..5 -> 17.0
            in 6..8 -> 20.0
            in 9..11 -> 25.0
            in 12..15 -> 30.0
            in 16..19 -> 27.0
            else -> 22.0
        }
        HourForecast(
            time = Instant.fromEpochSeconds(baseTime.epochSeconds + index * 3600L),
            temperature = temperature,
            apparentTemperature = temperature,
            humidity = 50,
            dewPoint = 10.0,
            precipitation = 0.0,
            precipitationProbability = null,
            condition = if (index % 24 == 14) {
                WeatherCondition.PARTLY_CLOUDY
            } else {
                WeatherCondition.CLEAR
            },
            isDay = (index % 24) in 6..19
        )
    }

    return WeatherForecast(
        location = location,
        provider = WeatherProvider.OPEN_METEO,
        fetchedAt = baseTime,
        current = CurrentWeather(
            temperature = 21.0,
            apparentTemperature = 21.0,
            humidity = 51,
            dewPoint = 10.0,
            precipitation = 0.0,
            condition = WeatherCondition.CLEAR,
            isDay = false
        ),
        hourly = hourly,
        daily = daily,
        moonPhase = MoonPhaseCalculator.phase(baseTime)
    )
}
