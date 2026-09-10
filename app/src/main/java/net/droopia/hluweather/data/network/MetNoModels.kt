package net.droopia.hluweather.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MetNoResponse(
    val properties: MetNoProperties = MetNoProperties()
)

@Serializable
data class MetNoProperties(
    val timeseries: List<MetNoTimeSeries> = emptyList()
)

@Serializable
data class MetNoTimeSeries(
    val time: String? = null,
    val data: MetNoTimeSeriesData? = null
)

@Serializable
data class MetNoTimeSeriesData(
    val instant: MetNoInstant? = null,
    @SerialName("next_1_hours") val next1Hours: MetNoData? = null,
    @SerialName("next_6_hours") val next6Hours: MetNoData? = null
)

@Serializable
data class MetNoInstant(
    val details: MetNoDetails? = null
)

@Serializable
data class MetNoDetails(
    @SerialName("air_temperature") val airTemperature: Double? = null,
    @SerialName("relative_humidity") val relativeHumidity: Double? = null,
    @SerialName("dew_point_temperature") val dewPoint: Double? = null
)

@Serializable
data class MetNoData(
    val summary: MetNoSummary? = null,
    val details: MetNoPrecipitationDetails? = null
) {
    constructor(summary: MetNoSummary?, precipitationAmount: Double?) : this(
        summary = summary,
        details = MetNoPrecipitationDetails(precipitationAmount)
    )
}

@Serializable
data class MetNoSummary(
    @SerialName("symbol_code") val symbolCode: String? = null
)

@Serializable
data class MetNoPrecipitationDetails(
    @SerialName("precipitation_amount") val precipitationAmount: Double? = null
)
