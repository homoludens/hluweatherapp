package net.droopia.hluweather.data.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import java.io.IOException
import net.droopia.hluweather.data.model.WeatherLocation

interface MetNoApi {
    suspend fun forecast(location: WeatherLocation): MetNoResponse
}

class MetNoApiException(message: String) : IOException(message)

class KtorMetNoApi(
    private val client: HttpClient,
    private val baseUrl: String = "https://api.met.no"
) : MetNoApi {

    override suspend fun forecast(location: WeatherLocation): MetNoResponse {
        val response = client.get("$baseUrl/weatherapi/locationforecast/2.0/compact") {
            parameter("lat", location.latitude)
            parameter("lon", location.longitude)
            location.altitude?.let { parameter("altitude", it) }
            header(HttpHeaders.UserAgent, USER_AGENT)
        }

        if (response.status.value !in 200..299) {
            throw MetNoApiException("Weather service returned HTTP ${response.status.value}")
        }

        return response.body<MetNoResponse>()
    }

    private companion object {
        const val USER_AGENT = "HluWeather/1.0 https://net.droopia.hluweather"
    }
}
