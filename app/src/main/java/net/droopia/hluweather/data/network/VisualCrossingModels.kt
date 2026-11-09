package net.droopia.hluweather.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VisualCrossingTimelineResponse(
    val timezone: String? = null,
    @SerialName("currentConditions") val currentConditions: VisualCrossingCurrent? = null,
    val days: List<VisualCrossingDay>? = null
)

@Serializable
data class VisualCrossingCurrent(
    @SerialName("datetimeEpoch") val datetimeEpoch: Long? = null,
    val temp: Double? = null,
    val feelslike: Double? = null,
    val humidity: Double? = null,
    val dew: Double? = null,
    val precip: Double? = null,
    val conditions: String? = null,
    val icon: String? = null
)

@Serializable
data class VisualCrossingDay(
    val datetime: String? = null,
    @SerialName("datetimeEpoch") val datetimeEpoch: Long? = null,
    val tempmin: Double? = null,
    val tempmax: Double? = null,
    val precip: Double? = null,
    val conditions: String? = null,
    val icon: String? = null,
    val sunrise: String? = null,
    @SerialName("sunriseEpoch") val sunriseEpoch: Long? = null,
    val sunset: String? = null,
    @SerialName("sunsetEpoch") val sunsetEpoch: Long? = null,
    val moonphase: Double? = null,
    val hours: List<VisualCrossingHour>? = null
)

@Serializable
data class VisualCrossingHour(
    val datetime: String? = null,
    @SerialName("datetimeEpoch") val datetimeEpoch: Long? = null,
    val temp: Double? = null,
    val feelslike: Double? = null,
    val humidity: Double? = null,
    val dew: Double? = null,
    val precip: Double? = null,
    val precipprob: Double? = null,
    val windspeed: Double? = null,
    val winddir: Double? = null,
    val conditions: String? = null,
    val icon: String? = null,
    val aqieur: Double? = null,
    val pm2p5: Double? = null,
    val pm10: Double? = null
)
