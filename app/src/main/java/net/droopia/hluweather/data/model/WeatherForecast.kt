package net.droopia.hluweather.data.model

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable

@Serializable
enum class EuropeanAqiScale {
    NUMERIC_INDEX,
    CATEGORICAL_1_TO_6
}

@Serializable
enum class CurrentWeatherProvenance {
    UNSPECIFIED,
    OBSERVATION,
    FORECAST
}

data class CurrentWeather(
    val temperature: Double,
    val apparentTemperature: Double?,
    val humidity: Int?,
    val dewPoint: Double?,
    val precipitation: Double?,
    val condition: WeatherCondition,
    val isDay: Boolean?,
    val europeanAqi: Double? = null,
    val pm10: Double? = null,
    val pm2_5: Double? = null,
    val europeanAqiScale: EuropeanAqiScale = EuropeanAqiScale.NUMERIC_INDEX,
    val provenance: CurrentWeatherProvenance = CurrentWeatherProvenance.UNSPECIFIED,
    val validTime: Instant? = null
)

data class HourForecast(
    val time: Instant,
    val temperature: Double,
    val apparentTemperature: Double?,
    val humidity: Int?,
    val dewPoint: Double?,
    val precipitation: Double?,
    val precipitationProbability: Int?,
    val condition: WeatherCondition,
    val isDay: Boolean?,
    val windSpeedKmh: Double? = null,
    val windDirectionDegrees: Double? = null,
    val evapotranspiration: Double? = null,
    val europeanAqi: Double? = null,
    val pm10: Double? = null,
    val pm2_5: Double? = null,
    val europeanAqiScale: EuropeanAqiScale = EuropeanAqiScale.NUMERIC_INDEX
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
    val moonPhase: Double,
    val timezone: String = TimeZone.currentSystemDefault().id,
    val timezoneIsUtcFallback: Boolean = false
)
