package net.droopia.hluweather.data.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import java.io.IOException
import net.droopia.hluweather.data.model.GeoPoint

interface NominatimApi {
    suspend fun reverse(point: GeoPoint): NominatimResponse
}

class NominatimApiException(message: String) : IOException(message)

class KtorNominatimApi(
    private val client: HttpClient,
    baseUrl: String = "https://nominatim.openstreetmap.org",
    private val userAgent: String = USER_AGENT
) : NominatimApi {

    private val baseUrl = baseUrl.trimEnd('/')

    override suspend fun reverse(point: GeoPoint): NominatimResponse {
        val response = client.get("$baseUrl/reverse") {
            parameter("format", "jsonv2")
            parameter("lat", point.latitude)
            parameter("lon", point.longitude)
            header(HttpHeaders.UserAgent, userAgent)
        }

        if (response.status.value !in 200..299) {
            throw NominatimApiException("Nominatim returned HTTP ${response.status.value}")
        }

        return response.body()
    }

    private companion object {
        const val USER_AGENT = "HluWeather/1.0 https://net.droopia.hluweather"
    }
}
