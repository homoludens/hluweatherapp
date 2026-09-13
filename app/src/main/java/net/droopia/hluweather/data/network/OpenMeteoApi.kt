package net.droopia.hluweather.data.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import java.io.IOException
import net.droopia.hluweather.data.model.WeatherLocation

interface OpenMeteoApi {
    suspend fun forecast(location: WeatherLocation): OpenMeteoResponse
}

class OpenMeteoApiException(message: String) : IOException(message)

class KtorOpenMeteoApi(
    private val client: HttpClient,
    private val baseUrl: String = "https://api.open-meteo.com"
) : OpenMeteoApi {

    override suspend fun forecast(location: WeatherLocation): OpenMeteoResponse {
        val response = client.get("$baseUrl/v1/forecast") {
            parameter("latitude", location.latitude)
            parameter("longitude", location.longitude)
            parameter("timezone", "auto")
            parameter("forecast_days", 7)
            parameter("current", CURRENT_VARIABLES)
            parameter("hourly", HOURLY_VARIABLES)
            parameter("daily", DAILY_VARIABLES)
            parameter("temperature_unit", "celsius")
            parameter("wind_speed_unit", "kmh")
            parameter("precipitation_unit", "mm")
        }

        if (response.status.value !in 200..299) {
            throw OpenMeteoApiException("Weather service returned HTTP ${response.status.value}")
        }

        return response.body<OpenMeteoResponse>()
    }

    private companion object {
        const val CURRENT_VARIABLES =
            "temperature_2m,relative_humidity_2m,apparent_temperature,dew_point_2m,precipitation,weather_code,is_day"
        const val HOURLY_VARIABLES =
            "temperature_2m,relative_humidity_2m,dew_point_2m,apparent_temperature,precipitation,precipitation_probability," +
                "wind_speed_10m,wind_direction_10m,evapotranspiration,weather_code,is_day"
        const val DAILY_VARIABLES =
            "weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,sunrise,sunset,moon_phase"
    }
}
