package net.droopia.hluweather.data.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

data class CurrentWeather(
    val temperature: Double,
    val apparentTemperature: Double?,
    val humidity: Int?,
    val dewPoint: Double?,
    val precipitation: Double?,
    val condition: WeatherCondition,
    val isDay: Boolean?
)

data class HourForecast(
    val time: Instant,
    val temperature: Double,
    val apparentTemperature: Double?,
    val humidity: Int?,
    val dewPoint: Double?,
    val precipitation: Double,
    val precipitationProbability: Int?,
    val condition: WeatherCondition,
    val isDay: Boolean?
)

data class DayForecast(
    val date: LocalDate,
    val condition: WeatherCondition,
    val temperatureMin: Double,
    val temperatureMax: Double,
    val precipitation: Double?,
    val sunrise: Instant?,
    val sunset: Instant?
)

data class WeatherForecast(
    val location: WeatherLocation,
    val provider: WeatherProvider,
    val fetchedAt: Instant,
    val current: CurrentWeather,
    val hourly: List<HourForecast>,
    val daily: List<DayForecast>,
    val moonPhase: Double
)
