package net.droopia.hluweather.data.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import java.io.IOException
import kotlinx.serialization.Serializable

interface OpenMeteoGeocodingApi {
    suspend fun search(query: String): OpenMeteoGeocodingResponse
}

class OpenMeteoGeocodingApiException(message: String) : IOException(message)

@Serializable
data class OpenMeteoGeocodingResponse(
    val results: List<OpenMeteoGeocodingResult> = emptyList()
)

@Serializable
data class OpenMeteoGeocodingResult(
    val name: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val country: String? = null,
    val admin1: String? = null
)

class KtorOpenMeteoGeocodingApi(
    private val client: HttpClient,
    baseUrl: String = "https://geocoding-api.open-meteo.com",
    private val userAgent: String = USER_AGENT
) : OpenMeteoGeocodingApi {

    private val baseUrl = baseUrl.trimEnd('/')

    override suspend fun search(query: String): OpenMeteoGeocodingResponse {
        val response = client.get("$baseUrl/v1/search") {
            parameter("name", query)
            parameter("count", 8)
            parameter("language", "en")
            parameter("format", "json")
            header(HttpHeaders.UserAgent, userAgent)
        }

        if (response.status.value !in 200..299) {
            throw OpenMeteoGeocodingApiException(
                "Open-Meteo geocoding returned HTTP ${response.status.value}"
            )
        }

        return response.body()
    }

    private companion object {
        const val USER_AGENT = "HluWeather/3.2 route planner"
    }
}
