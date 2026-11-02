package net.droopia.hluweather.data.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import net.droopia.hluweather.data.model.RadarMetadata

interface RainViewerRadarApi : RadarMetadataApi

class RainViewerRadarApiException(
    message: String,
    statusCode: Int? = null,
    retryAfter: String? = null
) : RadarApiException(message, statusCode, retryAfter)

class KtorRainViewerRadarApi(
    private val client: HttpClient,
    private val metadataUrl: String = METADATA_URL
) : RainViewerRadarApi {
    override suspend fun metadata(): RadarMetadata {
        val response = client.get(metadataUrl)
        if (response.status.value !in 200..299) {
            throw RainViewerRadarApiException(
                "RainViewer radar service returned HTTP ${response.status.value}",
                statusCode = response.status.value,
                retryAfter = response.headers[HttpHeaders.RetryAfter]
            )
        }
        return parseRainViewerRadarMetadata(response.body())
    }

    private companion object {
        const val METADATA_URL = "https://api.rainviewer.com/public/weather-maps.json"
    }
}
