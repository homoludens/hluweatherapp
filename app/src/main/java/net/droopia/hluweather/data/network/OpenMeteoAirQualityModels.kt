package net.droopia.hluweather.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenMeteoAirQualityResponse(
    val timezone: String? = null,
    val current: AirQualityCurrentDto? = null,
    val hourly: AirQualityHourlyDto? = null
)

@Serializable
data class AirQualityCurrentDto(
    val time: String? = null,
    @SerialName("european_aqi") val europeanAqi: Double? = null,
    val pm10: Double? = null,
    @SerialName("pm2_5") val pm2_5: Double? = null
)

@Serializable
data class AirQualityHourlyDto(
    val time: List<String?>? = null,
    @SerialName("european_aqi") val europeanAqi: List<Double?>? = null,
    val pm10: List<Double?>? = null,
    @SerialName("pm2_5") val pm2_5: List<Double?>? = null
)
