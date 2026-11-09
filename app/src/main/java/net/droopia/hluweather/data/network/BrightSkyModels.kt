package net.droopia.hluweather.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class BrightSkyWeatherResponse(
    val weather: List<BrightSkyWeatherRecord> = emptyList(),
    val sources: List<BrightSkySource> = emptyList()
)

@Serializable
data class BrightSkyWeatherRecord(
    val timestamp: String? = null,
    @SerialName("source_id") val sourceId: Int? = null,
    @SerialName("cloud_cover") val cloudCover: Double? = null,
    val condition: String? = null,
    @SerialName("dew_point") val dewPoint: Double? = null,
    val icon: String? = null,
    @SerialName("pressure_msl") val pressureMsl: Double? = null,
    @SerialName("relative_humidity") val relativeHumidity: Int? = null,
    val temperature: Double? = null,
    val visibility: Int? = null,
    @SerialName("fallback_source_ids") val fallbackSourceIds: JsonObject = JsonObject(emptyMap()),
    val precipitation: Double? = null,
    val solar: Double? = null,
    val sunshine: Int? = null,
    @SerialName("wind_direction") val windDirection: Int? = null,
    @SerialName("wind_speed") val windSpeed: Double? = null,
    @SerialName("wind_gust_direction") val windGustDirection: Int? = null,
    @SerialName("wind_gust_speed") val windGustSpeed: Double? = null,
    @SerialName("precipitation_probability") val precipitationProbability: Int? = null,
    @SerialName("precipitation_probability_6h") val precipitationProbability6h: Int? = null
)

@Serializable
data class BrightSkyCurrentWeatherResponse(
    val weather: BrightSkyCurrentWeatherRecord? = null,
    val sources: List<BrightSkySource> = emptyList()
)

@Serializable
data class BrightSkyCurrentWeatherRecord(
    val timestamp: String? = null,
    @SerialName("source_id") val sourceId: Int? = null,
    @SerialName("cloud_cover") val cloudCover: Double? = null,
    val condition: String? = null,
    @SerialName("dew_point") val dewPoint: Double? = null,
    val icon: String? = null,
    @SerialName("pressure_msl") val pressureMsl: Double? = null,
    @SerialName("relative_humidity") val relativeHumidity: Int? = null,
    val temperature: Double? = null,
    val visibility: Int? = null,
    @SerialName("fallback_source_ids") val fallbackSourceIds: JsonObject = JsonObject(emptyMap()),
    @SerialName("precipitation_10") val precipitation10: Double? = null,
    @SerialName("precipitation_30") val precipitation30: Double? = null,
    @SerialName("precipitation_60") val precipitation60: Double? = null,
    @SerialName("solar_10") val solar10: Double? = null,
    @SerialName("solar_30") val solar30: Double? = null,
    @SerialName("solar_60") val solar60: Double? = null,
    @SerialName("sunshine_30") val sunshine30: Int? = null,
    @SerialName("sunshine_60") val sunshine60: Int? = null,
    @SerialName("wind_direction_10") val windDirection10: Int? = null,
    @SerialName("wind_direction_30") val windDirection30: Int? = null,
    @SerialName("wind_direction_60") val windDirection60: Int? = null,
    @SerialName("wind_speed_10") val windSpeed10: Double? = null,
    @SerialName("wind_speed_30") val windSpeed30: Double? = null,
    @SerialName("wind_speed_60") val windSpeed60: Double? = null,
    @SerialName("wind_gust_direction_10") val windGustDirection10: Int? = null,
    @SerialName("wind_gust_direction_30") val windGustDirection30: Int? = null,
    @SerialName("wind_gust_direction_60") val windGustDirection60: Int? = null,
    @SerialName("wind_gust_speed_10") val windGustSpeed10: Double? = null,
    @SerialName("wind_gust_speed_30") val windGustSpeed30: Double? = null,
    @SerialName("wind_gust_speed_60") val windGustSpeed60: Double? = null
)

@Serializable
data class BrightSkySource(
    val id: Int? = null,
    @SerialName("dwd_station_id") val dwdStationId: String? = null,
    @SerialName("wmo_station_id") val wmoStationId: String? = null,
    @SerialName("station_name") val stationName: String? = null,
    @SerialName("observation_type") val observationType: String? = null,
    @SerialName("first_record") val firstRecord: String? = null,
    @SerialName("last_record") val lastRecord: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val height: Double? = null,
    val distance: Int? = null
)
