package net.droopia.hluweather.data.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.droopia.hluweather.data.model.RadarColorScheme
import net.droopia.hluweather.data.model.RadarFrame
import net.droopia.hluweather.data.model.RadarFrameType
import net.droopia.hluweather.data.model.RadarMetadata
import net.droopia.hluweather.data.model.RadarProvider

typealias LibreWxrRadarFrameType = RadarFrameType
typealias LibreWxrRadarFrame = RadarFrame
typealias LibreWxrRadarColorScheme = RadarColorScheme
typealias LibreWxrRadarMetadata = RadarMetadata

private val radarMetadataJson = Json { ignoreUnknownKeys = true }

fun parseLibreWxrRadarMetadata(json: String): RadarMetadata {
    val response = radarMetadataJson.decodeFromString<LibreWxrWeatherMapsDto>(json)
    RadarTileUrlBuilder.validateHost(response.host)

    val observedFrames = response.radar.past.map { frame ->
        RadarTileUrlBuilder.validateFramePath(frame.path)
        RadarFrame(
            timestampSeconds = frame.time,
            path = frame.path,
            type = RadarFrameType.OBSERVED,
            provider = RadarProvider.LIBREWXR
        )
    }
    val forecastFrames = response.radar.nowcast.map { frame ->
        RadarTileUrlBuilder.validateFramePath(frame.path)
        RadarFrame(
            timestampSeconds = frame.time,
            path = frame.path,
            type = RadarFrameType.FORECAST,
            provider = RadarProvider.LIBREWXR
        )
    }

    return RadarMetadata.normalized(
        provider = RadarProvider.LIBREWXR,
        host = response.host,
        frames = observedFrames + forecastFrames,
        colorSchemes = response.radar.colorSchemes.orEmpty()
    )
}

@Serializable
private data class LibreWxrWeatherMapsDto(
    val host: String,
    val radar: LibreWxrRadarDto
)

@Serializable
private data class LibreWxrRadarDto(
    val past: List<LibreWxrRadarFrameDto>,
    val nowcast: List<LibreWxrRadarFrameDto>,
    val colorSchemes: List<RadarColorScheme>? = null
)

@Serializable
private data class LibreWxrRadarFrameDto(
    val time: Long,
    val path: String
)
