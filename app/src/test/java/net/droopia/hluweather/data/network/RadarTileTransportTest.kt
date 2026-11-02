package net.droopia.hluweather.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import net.droopia.hluweather.data.model.RadarFrame
import net.droopia.hluweather.data.model.RadarFrameType
import net.droopia.hluweather.data.model.RadarProvider
import net.droopia.hluweather.data.model.RadarTileCoordinate
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarTileTransportTest {
    @Test
    fun honorsCacheAgeEtagAndLastModifiedOnConditionalRequests() = runTest {
        var calls = 0
        var conditional: HttpRequestData? = null
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    calls++
                    if (calls == 1) respond(
                        byteArrayOf(1, 2, 3), HttpStatusCode.OK,
                        headersOf(
                            HttpHeaders.CacheControl to listOf("public, max-age=60"),
                            HttpHeaders.Age to listOf("10"),
                            HttpHeaders.ETag to listOf("\"tile-v1\""),
                            HttpHeaders.LastModified to listOf("Wed, 21 Oct 2015 07:28:00 GMT")
                        )
                    ) else {
                        conditional = request
                        respond(byteArrayOf(), HttpStatusCode.NotModified,
                            headersOf(HttpHeaders.CacheControl, "max-age=120"))
                    }
                }
            }
        }
        val transport = KtorRadarTileTransport(client) { 1_445_412_480_000 }
        val first = transport.fetch(key(), null)
        assertArrayEquals(byteArrayOf(1, 2, 3), first.payload.bytes)
        assertEquals(1_445_412_530_000, first.payload.expiresAtMillis)
        assertEquals("\"tile-v1\"", first.payload.etag)
        assertEquals(1_445_412_480_000, first.payload.modifiedAtMillis)
        val second = transport.fetch(key(), first.payload)
        assertEquals(304, second.status)
        assertEquals("\"tile-v1\"", conditional?.headers?.get(HttpHeaders.IfNoneMatch))
        assertEquals("Wed, 21 Oct 2015 07:28:00 GMT", conditional?.headers?.get(HttpHeaders.IfModifiedSince))
        assertEquals(1_445_412_600_000, second.payload.expiresAtMillis)
        client.close()
    }

    @Test
    fun noStoreNoCacheAndNoContentAreNotMistakenForFreshImages() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    calls++
                    when (calls) {
                        1 -> respond(byteArrayOf(1), HttpStatusCode.OK,
                            headersOf(HttpHeaders.CacheControl, "no-store"))
                        2 -> respond(byteArrayOf(2), HttpStatusCode.OK,
                            headersOf(HttpHeaders.CacheControl, "no-cache"))
                        else -> respond(byteArrayOf(), HttpStatusCode.NoContent)
                    }
                }
            }
        }
        val transport = KtorRadarTileTransport(client) { 12_000 }
        val noStore = transport.fetch(key(), null).payload
        assertFalse(noStore.cacheable)
        assertEquals(12_000, noStore.expiresAtMillis)
        val noCache = transport.fetch(key(), null).payload
        assertTrue(noCache.mustRevalidate)
        assertEquals(12_000, noCache.expiresAtMillis)
        val empty = transport.fetch(key(), null)
        assertEquals(204, empty.status)
        assertEquals(null, empty.payload.bytes)
        client.close()
    }

    @Test
    fun loaderDiscardsPriorBodyOnNoStoreRevalidationAndNeverRetainsNoStoreResponses() = runTest {
        var calls = 0
        val validators = mutableListOf<String?>()
        val client = HttpClient(MockEngine) {
            engine {
                dispatcher = StandardTestDispatcher(testScheduler)
                addHandler { request ->
                    calls++
                    validators += request.headers[HttpHeaders.IfNoneMatch]
                    when (calls) {
                        1 -> respond(byteArrayOf(1), HttpStatusCode.OK, headersOf(
                            HttpHeaders.CacheControl to listOf("max-age=0"),
                            HttpHeaders.ETag to listOf("\"v1\"")
                        ))
                        2 -> respond(byteArrayOf(), HttpStatusCode.NotModified,
                            headersOf(HttpHeaders.CacheControl, "no-store"))
                        3 -> respond(byteArrayOf(2), HttpStatusCode.OK,
                            headersOf(HttpHeaders.CacheControl, "no-store"))
                        else -> respond(byteArrayOf(3), HttpStatusCode.OK,
                            headersOf(HttpHeaders.CacheControl, "max-age=60"))
                    }
                }
            }
        }
        try {
            val scheduler = RadarNetworkScheduler(backgroundScope, { testScheduler.currentTime })
            val loader = RadarTileLoader(backgroundScope, scheduler,
                KtorRadarTileTransport(client) { testScheduler.currentTime }, { testScheduler.currentTime })
            val tile = key()
            assertArrayEquals(byteArrayOf(1), loader.load(tile).bytes)
            assertEquals(1, loader.cachedEntries)
            assertArrayEquals(byteArrayOf(1), loader.load(tile).bytes)
            assertEquals(0, loader.cachedEntries)
            assertArrayEquals(byteArrayOf(2), loader.load(tile).bytes)
            assertEquals(0, loader.cachedEntries)
            assertArrayEquals(byteArrayOf(3), loader.load(tile).bytes)
            assertArrayEquals(byteArrayOf(3), loader.load(tile).bytes)
            assertEquals(4, calls)
            assertEquals(listOf(null, "\"v1\"", null, null), validators)
        } finally {
            client.close()
        }
    }

    @Test
    fun oversizedContentIsRejectedWhetherOrNotLengthIsDeclared() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    if (request.headers["X-Unused"] == "never") error("unexpected")
                    respond(ByteArray(KtorRadarTileTransport.MAX_TILE_BYTES + 1), HttpStatusCode.OK)
                }
            }
        }
        val failure = runCatching { KtorRadarTileTransport(client).fetch(key(), null) }.exceptionOrNull()
        assertTrue(failure is RadarTileTooLargeException)
        client.close()

        val declared = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(byteArrayOf(1), HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentLength, (KtorRadarTileTransport.MAX_TILE_BYTES + 1).toString()))
                }
            }
        }
        assertTrue(runCatching { KtorRadarTileTransport(declared).fetch(key(), null) }.exceptionOrNull() is RadarTileTooLargeException)
        declared.close()
    }

    @Test
    fun metadataAndTileAdaptersPreserveRetryAfterForTheSharedScheduler() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond("rate limited", HttpStatusCode.TooManyRequests,
                        headersOf(HttpHeaders.RetryAfter, "17"))
                }
            }
        }
        val tile = runCatching { KtorRadarTileTransport(client).fetch(key(), null) }.exceptionOrNull() as RadarApiException
        val metadata = runCatching { KtorRainViewerRadarApi(client).metadata() }.exceptionOrNull() as RadarApiException
        assertEquals(429, tile.statusCode)
        assertEquals("17", tile.retryAfter)
        assertEquals(429, metadata.statusCode)
        assertEquals("17", metadata.retryAfter)
        client.close()
    }

    private fun key(): RadarTileKey = RadarTileRegistry().register(
        "https://tiles.example.test",
        RadarFrame(1, "/radar/1", RadarFrameType.OBSERVED, RadarProvider.RAINVIEWER)
    ).tile(RadarTileCoordinate(1, 0, 0))
}
