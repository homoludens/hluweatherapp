package net.droopia.hluweather.data.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import java.io.IOException
import kotlinx.serialization.Serializable

interface PhotonApi {
    suspend fun search(query: String): PhotonResponse
}

class PhotonApiException(message: String) : IOException(message)

@Serializable
data class PhotonResponse(
    val features: List<PhotonFeature> = emptyList()
)

@Serializable
data class PhotonFeature(
    val geometry: PhotonGeometry = PhotonGeometry(),
    val properties: PhotonProperties = PhotonProperties()
)

@Serializable
data class PhotonGeometry(
    val coordinates: List<Double?> = emptyList()
)

@Serializable
data class PhotonProperties(
    val name: String? = null,
    val city: String? = null,
    val state: String? = null,
    val country: String? = null
)

class KtorPhotonApi(
    private val client: HttpClient,
    baseUrl: String = "https://photon.komoot.io",
    private val userAgent: String = USER_AGENT
) : PhotonApi {

    private val baseUrl = baseUrl.trimEnd('/')

    override suspend fun search(query: String): PhotonResponse {
        val response = client.get("$baseUrl/api/") {
            parameter("q", query)
            parameter("limit", 8)
            parameter("lang", "en")
            header(HttpHeaders.UserAgent, userAgent)
        }

        if (response.status.value !in 200..299) {
            throw PhotonApiException("Photon returned HTTP ${response.status.value}")
        }

        return response.body()
    }

    private companion object {
        const val USER_AGENT = "HluWeather/3.2 route planner"
    }
}
