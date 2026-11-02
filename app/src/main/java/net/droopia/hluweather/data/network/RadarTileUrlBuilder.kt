package net.droopia.hluweather.data.network

import java.net.URI
import net.droopia.hluweather.data.model.RadarProviderDescriptor
import net.droopia.hluweather.data.model.RadarTileOptions

// Builds the shared public tile URL shape used by both radar providers.
object RadarTileUrlBuilder {
    fun build(
        descriptor: RadarProviderDescriptor,
        host: String,
        path: String,
        zoom: Int,
        x: Int,
        y: Int
    ): String {
        require(zoom in descriptor.nativeMinZoom..descriptor.nativeMaxZoom) {
            "Zoom must be between ${descriptor.nativeMinZoom} and ${descriptor.nativeMaxZoom}"
        }
        val options = descriptor.tileOptions
        return build(
            host = host,
            path = path,
            size = options.size,
            zoom = zoom,
            x = x,
            y = y,
            color = options.color,
            smooth = options.smooth,
            snow = options.snow,
            extension = options.extension
        )
    }

    fun buildTemplate(
        descriptor: RadarProviderDescriptor,
        host: String,
        path: String
    ): String = buildTemplate(host, path, descriptor.tileOptions)

    fun buildTemplate(host: String, path: String, options: RadarTileOptions): String =
        buildTemplate(
            host = host,
            path = path,
            size = options.size,
            color = options.color,
            smooth = options.smooth,
            snow = options.snow,
            extension = options.extension
        )

    fun build(
        host: String,
        path: String,
        size: Int,
        zoom: Int,
        x: Int,
        y: Int,
        color: Int,
        smooth: Boolean,
        snow: Boolean,
        extension: String
    ): String {
        require(zoom in MIN_ZOOM..MAX_ZOOM) { "Zoom must be between $MIN_ZOOM and $MAX_ZOOM" }
        val tileCount = 1 shl zoom
        require(x in 0 until tileCount && y in 0 until tileCount) {
            "Tile coordinates must be within the selected zoom level"
        }
        return buildUrl(host, path, size, "$zoom/$x/$y", color, smooth, snow, extension)
    }

    /** XYZ template for MapLibre; keep placeholders unescaped for its tile loader. */
    fun buildTemplate(
        host: String,
        path: String,
        size: Int,
        color: Int,
        smooth: Boolean,
        snow: Boolean,
        extension: String
    ): String = buildUrl(host, path, size, "{z}/{x}/{y}", color, smooth, snow, extension)

    private fun buildUrl(
        host: String,
        path: String,
        size: Int,
        coordinates: String,
        color: Int,
        smooth: Boolean,
        snow: Boolean,
        extension: String
    ): String {
        validateHost(host)
        validateFramePath(path)
        require(size == 256 || size == 512) { "Tile size must be 256 or 512" }
        require(color in 0..14 || color == 255) { "Unsupported radar color scheme: $color" }
        require(extension == "png" || extension == "webp") {
            "Tile extension must be png or webp"
        }

        val normalizedHost = host.trimEnd('/')
        val normalizedPath = path.trimEnd('/')
        val smoothFlag = if (smooth) 1 else 0
        val snowFlag = if (snow) 1 else 0
        return URI("$normalizedHost$normalizedPath/$size/").toASCIIString() +
            "$coordinates/$color/${smoothFlag}_${snowFlag}.$extension"
    }

    internal fun validateHost(host: String) {
        val uri = URI(host)
        require(uri.isAbsolute && uri.scheme.equals("https", ignoreCase = true)) {
            "Radar tile host must be an absolute HTTPS URL"
        }
        require(!uri.host.isNullOrBlank() && uri.rawUserInfo == null) {
            "Radar tile host must contain a valid host and no credentials"
        }
        require(uri.rawQuery == null && uri.rawFragment == null) {
            "Radar tile host must not contain a query or fragment"
        }
        require(uri.rawPath.isNullOrEmpty() || uri.rawPath == "/") {
            "Radar tile host must not contain a path"
        }
    }

    internal fun validateFramePath(path: String) {
        val uri = URI(path)
        val decodedPath = uri.path.orEmpty()
        require(!uri.isAbsolute && uri.rawAuthority == null && uri.rawQuery == null && uri.rawFragment == null) {
            "Radar frame path must be a relative URL path"
        }
        require(uri.rawPath.startsWith('/') && decodedPath.length > 1) {
            "Radar frame path must be an absolute path"
        }
        require(!decodedPath.contains('\\') && decodedPath.split('/').none { it == "." || it == ".." }) {
            "Radar frame path must not contain traversal segments"
        }
    }

    private const val MIN_ZOOM = 0
    private const val MAX_ZOOM = 12
}

/** Source compatibility for code that used the original LibreWXR-named URL utility. */
object LibreWxrRadarTileUrlBuilder {
    fun build(
        host: String,
        path: String,
        size: Int,
        zoom: Int,
        x: Int,
        y: Int,
        color: Int,
        smooth: Boolean,
        snow: Boolean,
        extension: String
    ): String = RadarTileUrlBuilder.build(
        host, path, size, zoom, x, y, color, smooth, snow, extension
    )

    fun buildTemplate(
        host: String,
        path: String,
        size: Int,
        color: Int,
        smooth: Boolean,
        snow: Boolean,
        extension: String
    ): String = RadarTileUrlBuilder.buildTemplate(
        host, path, size, color, smooth, snow, extension
    )

    internal fun validateHost(host: String) = RadarTileUrlBuilder.validateHost(host)

    internal fun validateFramePath(path: String) = RadarTileUrlBuilder.validateFramePath(path)
}
