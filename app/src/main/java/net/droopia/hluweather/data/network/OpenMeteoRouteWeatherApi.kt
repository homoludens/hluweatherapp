package net.droopia.hluweather.data.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import net.droopia.hluweather.data.model.GeoPoint

interface OpenMeteoRouteWeatherApi {
    suspend fun forecast(points: List<GeoPoint>): List<OpenMeteoRouteResponse>
}

@Serializable
data class OpenMeteoRouteResponse(
    val hourly: OpenMeteoRouteHourlyDto? = null
)

@Serializable
data class OpenMeteoRouteHourlyDto(
    val time: List<Long?> = emptyList(),
    @SerialName("temperature_2m") val temperature: List<Double?> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
    @SerialName("wind_speed_10m") val windSpeed: List<Double?> = emptyList(),
    @SerialName("precipitation_probability") val precipitationProbability: List<Int?> = emptyList()
)

class KtorOpenMeteoRouteWeatherApi(
    private val client: HttpClient,
    baseUrl: String = "https://api.open-meteo.com"
) : OpenMeteoRouteWeatherApi {
    private val baseUrl = baseUrl.trimEnd('/')
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun forecast(points: List<GeoPoint>): List<OpenMeteoRouteResponse> {
        if (points.isEmpty()) return emptyList()

        val response = client.get("$baseUrl/v1/forecast") {
            parameter("latitude", points.joinToString(",") { it.latitude.toString() })
            parameter("longitude", points.joinToString(",") { it.longitude.toString() })
            parameter(
                "hourly",
                "temperature_2m,weather_code,wind_speed_10m,precipitation_probability"
            )
            parameter("timeformat", "unixtime")
            parameter("timezone", "GMT")
            parameter("forecast_days", 16)
            parameter("temperature_unit", "celsius")
            parameter("wind_speed_unit", "kmh")
            parameter("precipitation_unit", "mm")
        }

        if (response.status.value !in 200..299) {
            throw OpenMeteoApiException("Weather service returned HTTP ${response.status.value}")
        }

        return response.body<String>().decodeResponses()
    }

    private fun String.decodeResponses(): List<OpenMeteoRouteResponse> {
        val element = json.parseToJsonElement(this)
        return when (element) {
            is JsonArray -> element.map {
                json.decodeFromJsonElement<OpenMeteoRouteResponse>(it)
            }
            else -> listOf(json.decodeFromJsonElement<OpenMeteoRouteResponse>(element))
        }
    }
}
