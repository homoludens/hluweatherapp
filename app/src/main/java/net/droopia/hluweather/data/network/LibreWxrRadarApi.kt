package net.droopia.hluweather.data.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import java.io.IOException

interface LibreWxrRadarApi {
    suspend fun metadata(): LibreWxrRadarMetadata
}

class LibreWxrRadarApiException(message: String, val statusCode: Int? = null) : IOException(message)

class KtorLibreWxrRadarApi(
    private val client: HttpClient,
    private val metadataUrl: String = METADATA_URL
) : LibreWxrRadarApi {
    override suspend fun metadata(): LibreWxrRadarMetadata {
        val response = client.get(metadataUrl)
        if (response.status.value !in 200..299) {
            throw LibreWxrRadarApiException(
                "LibreWXR radar service returned HTTP ${response.status.value}",
                statusCode = response.status.value
            )
        }
        return parseLibreWxrRadarMetadata(response.body())
    }

    private companion object {
        const val METADATA_URL = "https://api.librewxr.net/public/weather-maps.json"
    }
}
