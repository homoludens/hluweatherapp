package net.droopia.hluweather.data.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import java.io.IOException
import net.droopia.hluweather.data.model.RadarMetadata

interface LibreWxrRadarApi {
    suspend fun metadata(): RadarMetadata
}

class LibreWxrRadarApiException(message: String, val statusCode: Int? = null) : IOException(message)

class KtorLibreWxrRadarApi(
    private val client: HttpClient,
    private val metadataUrl: String = METADATA_URL
) : LibreWxrRadarApi {
    override suspend fun metadata(): RadarMetadata {
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
