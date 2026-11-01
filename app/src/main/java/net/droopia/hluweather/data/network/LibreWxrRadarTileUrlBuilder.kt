package net.droopia.hluweather.data.network

import java.net.URI

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
    ): String {
        validateHost(host)
        validateFramePath(path)
        require(size == 256 || size == 512) { "Tile size must be 256 or 512" }
        require(zoom in MIN_ZOOM..MAX_ZOOM) { "Zoom must be between $MIN_ZOOM and $MAX_ZOOM" }
        val tileCount = 1 shl zoom
        require(x in 0 until tileCount && y in 0 until tileCount) {
            "Tile coordinates must be within the selected zoom level"
        }
        require(color in 0..14 || color == 255) { "Unsupported radar color scheme: $color" }
        require(extension == "png" || extension == "webp") {
            "Tile extension must be png or webp"
        }

        val normalizedHost = host.trimEnd('/')
        val normalizedPath = path.trimEnd('/')
        val smoothFlag = if (smooth) 1 else 0
        val snowFlag = if (snow) 1 else 0
        return URI(
            "$normalizedHost$normalizedPath/$size/$zoom/$x/$y/$color/" +
                "${smoothFlag}_${snowFlag}.$extension"
        ).toASCIIString()
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
