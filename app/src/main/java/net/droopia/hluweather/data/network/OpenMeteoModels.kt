package net.droopia.hluweather.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenMeteoResponse(
    val timezone: String? = null,
    val current: CurrentDto? = null,
    val hourly: HourlyDto? = null,
    val daily: DailyDto? = null
)

@Serializable
data class CurrentDto(
    val time: String? = null,
    @SerialName("temperature_2m") val temperature: Double? = null,
    @SerialName("relative_humidity_2m") val humidity: Int? = null,
    @SerialName("apparent_temperature") val apparentTemperature: Double? = null,
    @SerialName("dew_point_2m") val dewPoint: Double? = null,
    val precipitation: Double? = null,
    @SerialName("weather_code") val weatherCode: Int? = null,
    @SerialName("is_day") val isDay: Int? = null
)

@Serializable
data class HourlyDto(
    val time: List<String?> = emptyList(),
    @SerialName("temperature_2m") val temperature: List<Double?> = emptyList(),
    @SerialName("relative_humidity_2m") val humidity: List<Int?>? = null,
    @SerialName("dew_point_2m") val dewPoint: List<Double?>? = null,
    @SerialName("apparent_temperature") val apparentTemperature: List<Double?>? = null,
    val precipitation: List<Double?> = emptyList(),
    @SerialName("precipitation_probability") val precipitationProbability: List<Int?>? = null,
    @SerialName("wind_speed_10m") val windSpeed: List<Double?>? = null,
    @SerialName("wind_direction_10m") val windDirection: List<Double?>? = null,
    val evapotranspiration: List<Double?>? = null,
    @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
    @SerialName("is_day") val isDay: List<Int?>? = null
)

@Serializable
data class DailyDto(
    val time: List<String?> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
    @SerialName("temperature_2m_max") val temperatureMax: List<Double?> = emptyList(),
    @SerialName("temperature_2m_min") val temperatureMin: List<Double?> = emptyList(),
    @SerialName("precipitation_sum") val precipitation: List<Double?>? = null,
    val sunrise: List<String?>? = null,
    val sunset: List<String?>? = null,
    @SerialName("moon_phase") val moonPhase: List<Double?> = emptyList()
)
