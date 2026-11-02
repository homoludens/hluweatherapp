package net.droopia.hluweather.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class RadarProvider {
    LIBREWXR,
    RAINVIEWER
}

@Serializable
enum class RadarFrameType {
    OBSERVED,
    FORECAST
}

@Serializable
data class RadarFrame(
    val timestampSeconds: Long,
    val path: String,
    val type: RadarFrameType,
    val provider: RadarProvider = RadarProvider.LIBREWXR
) {
    fun identity(host: String) = RadarFrameIdentity(
        provider = provider,
        host = host,
        path = path,
        timestampSeconds = timestampSeconds,
        type = type
    )
}

data class RadarFrameIdentity(
    val provider: RadarProvider,
    val host: String,
    val path: String,
    val timestampSeconds: Long,
    val type: RadarFrameType
)

@Serializable
data class RadarColorScheme(
    val id: Int,
    val name: String
)

@Serializable
data class RadarMetadata(
    val host: String,
    val frames: List<RadarFrame>,
    val colorSchemes: List<RadarColorScheme> = emptyList(),
    val provider: RadarProvider = RadarProvider.LIBREWXR
) {
    val observedFrames: List<RadarFrame>
        get() = frames.filter { it.type == RadarFrameType.OBSERVED }

    val forecastFrames: List<RadarFrame>
        get() = frames.filter { it.type == RadarFrameType.FORECAST }

    companion object {
        fun normalized(
            provider: RadarProvider,
            host: String,
            frames: List<RadarFrame>,
            colorSchemes: List<RadarColorScheme> = emptyList()
        ): RadarMetadata = RadarMetadata(
            host = host,
            frames = normalizeRadarFrames(provider, host, frames),
            colorSchemes = colorSchemes,
            provider = provider
        )
    }
}

fun normalizeRadarFrames(
    provider: RadarProvider,
    host: String,
    frames: List<RadarFrame>
): List<RadarFrame> {
    val seen = HashSet<RadarFrameIdentity>()
    return frames.asSequence()
        .onEach { require(it.provider == provider) { "Radar frame provider does not match metadata" } }
        .filter { seen.add(it.identity(host)) }
        .sortedWith(
            compareBy<RadarFrame> { it.timestampSeconds }
                .thenBy { it.type.ordinal }
                .thenBy { it.path }
        )
        .toList()
}

data class RadarSourceLink(
    val label: String,
    val url: String
)

data class RadarTileOptions(
    val size: Int,
    val color: Int,
    val smooth: Boolean,
    val snow: Boolean,
    val extension: String
)

data class RadarProviderDescriptor(
    val provider: RadarProvider,
    val displayName: String,
    val sourceLink: RadarSourceLink,
    val credits: List<String>,
    val tileOptions: RadarTileOptions,
    val nativeMinZoom: Int,
    val nativeMaxZoom: Int
)

object RadarProviderDescriptors {
    private val descriptors = listOf(
        RadarProviderDescriptor(
            provider = RadarProvider.LIBREWXR,
            displayName = "LibreWXR",
            sourceLink = RadarSourceLink("LibreWXR (librewxr.net)", "https://librewxr.net/"),
            credits = listOf(
                "EUMETNET OPERA",
                "Precipitation data from NOAA Enterprise Rain Rate (RRQPE)",
                "Radar-DPC (Italy; CC BY-SA 4.0 where included)."
            ),
            tileOptions = RadarTileOptions(
                size = 256,
                color = 2,
                smooth = true,
                snow = true,
                extension = "png"
            ),
            nativeMinZoom = 0,
            nativeMaxZoom = 12
        ),
        RadarProviderDescriptor(
            provider = RadarProvider.RAINVIEWER,
            displayName = "RainViewer",
            sourceLink = RadarSourceLink("RainViewer", "https://www.rainviewer.com/"),
            credits = emptyList(),
            tileOptions = RadarTileOptions(
                size = 256,
                color = 2,
                smooth = true,
                snow = true,
                extension = "png"
            ),
            nativeMinZoom = 0,
            nativeMaxZoom = 7
        )
    ).associateBy(RadarProviderDescriptor::provider)

    operator fun get(provider: RadarProvider): RadarProviderDescriptor =
        requireNotNull(descriptors[provider]) { "No radar descriptor for $provider" }
}
