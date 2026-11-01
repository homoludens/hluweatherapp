package net.droopia.hluweather.data.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
enum class LibreWxrRadarFrameType {
    OBSERVED,
    FORECAST
}

@Serializable
data class LibreWxrRadarFrame(
    val timestampSeconds: Long,
    val path: String,
    val type: LibreWxrRadarFrameType
)

@Serializable
data class LibreWxrRadarColorScheme(
    val id: Int,
    val name: String
)

@Serializable
data class LibreWxrRadarMetadata(
    val host: String,
    val frames: List<LibreWxrRadarFrame>,
    val colorSchemes: List<LibreWxrRadarColorScheme> = emptyList()
) {
    val observedFrames: List<LibreWxrRadarFrame>
        get() = frames.filter { it.type == LibreWxrRadarFrameType.OBSERVED }

    val forecastFrames: List<LibreWxrRadarFrame>
        get() = frames.filter { it.type == LibreWxrRadarFrameType.FORECAST }
}

private val radarMetadataJson = Json { ignoreUnknownKeys = true }

fun parseLibreWxrRadarMetadata(json: String): LibreWxrRadarMetadata {
    val response = radarMetadataJson.decodeFromString<LibreWxrWeatherMapsDto>(json)
    LibreWxrRadarTileUrlBuilder.validateHost(response.host)

    val observedFrames = response.radar.past.map { frame ->
        LibreWxrRadarTileUrlBuilder.validateFramePath(frame.path)
        LibreWxrRadarFrame(
            timestampSeconds = frame.time,
            path = frame.path,
            type = LibreWxrRadarFrameType.OBSERVED
        )
    }
    val forecastFrames = response.radar.nowcast.map { frame ->
        LibreWxrRadarTileUrlBuilder.validateFramePath(frame.path)
        LibreWxrRadarFrame(
            timestampSeconds = frame.time,
            path = frame.path,
            type = LibreWxrRadarFrameType.FORECAST
        )
    }

    return LibreWxrRadarMetadata(
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
    val colorSchemes: List<LibreWxrRadarColorScheme>? = null
)

@Serializable
private data class LibreWxrRadarFrameDto(
    val time: Long,
    val path: String
)
