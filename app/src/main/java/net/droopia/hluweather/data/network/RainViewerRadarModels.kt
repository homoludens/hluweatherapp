package net.droopia.hluweather.data.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.droopia.hluweather.data.model.RadarFrame
import net.droopia.hluweather.data.model.RadarFrameType
import net.droopia.hluweather.data.model.RadarMetadata
import net.droopia.hluweather.data.model.RadarProvider

private val rainViewerMetadataJson = Json { ignoreUnknownKeys = true }

fun parseRainViewerRadarMetadata(json: String): RadarMetadata {
    val response = rainViewerMetadataJson.decodeFromString<RainViewerWeatherMapsDto>(json)
    RadarTileUrlBuilder.validateHost(response.host)

    val frames = response.radar.past.map { frame ->
        require(frame.time > 0) { "RainViewer frame timestamp must be positive" }
        RadarTileUrlBuilder.validateFramePath(frame.path)
        RadarFrame(
            timestampSeconds = frame.time,
            path = frame.path,
            type = RadarFrameType.OBSERVED,
            provider = RadarProvider.RAINVIEWER
        )
    }

    return RadarMetadata.normalized(
        provider = RadarProvider.RAINVIEWER,
        host = response.host,
        frames = frames
    )
}

@Serializable
private data class RainViewerWeatherMapsDto(
    val host: String,
    val radar: RainViewerRadarDto
)

@Serializable
private data class RainViewerRadarDto(
    val past: List<RainViewerRadarFrameDto>
)

@Serializable
private data class RainViewerRadarFrameDto(
    val time: Long,
    val path: String
)
