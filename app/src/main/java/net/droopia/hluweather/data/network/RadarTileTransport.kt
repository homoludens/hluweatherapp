package net.droopia.hluweather.data.network

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.readRemaining
import java.io.IOException
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.io.readByteArray

data class RadarTilePayload(
    /** Null is a successful HTTP 204, not a failed or missing tile. */
    val bytes: ByteArray?,
    val expiresAtMillis: Long,
    val etag: String? = null,
    val modifiedAtMillis: Long? = null,
    val mustRevalidate: Boolean = false,
    val cacheable: Boolean = true
)

data class RadarTileHttpResult(
    val status: Int,
    val payload: RadarTilePayload,
    val retryAfter: String? = null
)

fun interface RadarTileTransport {
    /** One HTTP attempt; retry and shared scheduling are the caller's responsibility. */
    suspend fun fetch(key: RadarTileKey, prior: RadarTilePayload?): RadarTileHttpResult
}

class RadarTileTooLargeException : IOException("Radar tile exceeds the response size limit")

/** Uses a redirect/retry-disabled radar client so each HTTP attempt passes through the scheduler. */
class KtorRadarTileTransport(
    private val client: HttpClient,
    private val wallMillis: () -> Long = System::currentTimeMillis
) : RadarTileTransport {
    override suspend fun fetch(key: RadarTileKey, prior: RadarTilePayload?): RadarTileHttpResult =
        client.prepareGet(key.url) {
            prior?.etag?.let { header(HttpHeaders.IfNoneMatch, it) }
            prior?.modifiedAtMillis?.let {
                header(HttpHeaders.IfModifiedSince, DateTimeFormatter.RFC_1123_DATE_TIME.format(
                    Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC)
                ))
            }
        }.execute { response ->
            val status = response.status.value
            if (status != 200 && status != 204 && status != 304) {
                throw RadarApiException(
                    "Radar tile service returned HTTP $status", status,
                    response.headers[HttpHeaders.RetryAfter]
                )
            }
            val length = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            if (length != null && length > MAX_TILE_BYTES) throw RadarTileTooLargeException()
            val bytes = if (status == 200) {
                response.bodyAsChannel().readRemaining(MAX_TILE_BYTES.toLong() + 1).readByteArray().also {
                    if (it.size > MAX_TILE_BYTES) throw RadarTileTooLargeException()
                }
            } else null
            RadarTileHttpResult(status, cachePolicy(response.headers, wallMillis(), bytes))
        }

    private fun cachePolicy(headers: Headers, now: Long, bytes: ByteArray?): RadarTilePayload {
        val directives = headers.getAll(HttpHeaders.CacheControl).orEmpty()
            .flatMap { it.split(',') }.map { it.trim().lowercase() }
        val noStore = directives.any { it == "no-store" }
        val noCache = directives.any { it == "no-cache" || it.startsWith("no-cache=") }
        val maxAge = directives.firstNotNullOfOrNull {
            if (it.startsWith("max-age=")) it.substringAfter('=').trim('"').toLongOrNull()?.takeIf { n -> n >= 0 }
            else null
        }
        val age = headers[HttpHeaders.Age]?.toLongOrNull()?.coerceAtLeast(0) ?: 0
        val expires = when {
            noCache || noStore -> now
            maxAge != null -> saturatingAdd(now, ((maxAge - age).coerceAtLeast(0)).coerceAtMost(Long.MAX_VALUE / 1_000) * 1_000)
            else -> parseHttpDate(headers[HttpHeaders.Expires]) ?: saturatingAdd(now, DEFAULT_FRESH_MILLIS)
        }
        return RadarTilePayload(
            bytes = bytes,
            expiresAtMillis = expires,
            etag = headers[HttpHeaders.ETag],
            modifiedAtMillis = parseHttpDate(headers[HttpHeaders.LastModified]),
            mustRevalidate = noCache || directives.any { it == "must-revalidate" },
            cacheable = !noStore
        )
    }

    private fun parseHttpDate(value: String?): Long? = try {
        value?.let { ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
    } catch (_: Exception) {
        null
    }

    private fun saturatingAdd(a: Long, b: Long) =
        if (b > 0 && a > Long.MAX_VALUE - b) Long.MAX_VALUE else a + b

    companion object {
        const val MAX_TILE_BYTES = 4 * 1024 * 1024
        private const val DEFAULT_FRESH_MILLIS = 5 * 60_000L
    }
}
