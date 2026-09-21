package net.droopia.hluweather.data.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import net.droopia.hluweather.data.model.WeatherLocation

interface OpenMeteoAirQualityApi {
    suspend fun forecast(location: WeatherLocation): OpenMeteoAirQualityResponse
}

class KtorOpenMeteoAirQualityApi(
    private val client: HttpClient,
    private val baseUrl: String = "https://air-quality-api.open-meteo.com"
) : OpenMeteoAirQualityApi {

    override suspend fun forecast(location: WeatherLocation): OpenMeteoAirQualityResponse {
        val response = client.get("$baseUrl/v1/air-quality") {
            parameter("latitude", location.latitude)
            parameter("longitude", location.longitude)
            parameter("timezone", "auto")
            parameter("forecast_days", 7)
            parameter("current", AIR_QUALITY_VARIABLES)
            parameter("hourly", AIR_QUALITY_VARIABLES)
        }

        if (response.status.value !in 200..299) {
            throw OpenMeteoApiException("Weather service returned HTTP ${response.status.value}")
        }

        return response.body<OpenMeteoAirQualityResponse>()
    }

    private companion object {
        const val AIR_QUALITY_VARIABLES = "european_aqi,pm10,pm2_5"
    }
}
