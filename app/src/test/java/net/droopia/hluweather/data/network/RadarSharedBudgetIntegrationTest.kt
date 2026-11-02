package net.droopia.hluweather.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.droopia.hluweather.data.cache.CachedRadarMetadata
import net.droopia.hluweather.data.cache.RadarMetadataCacheStore
import net.droopia.hluweather.data.model.RadarFrame
import net.droopia.hluweather.data.model.RadarFrameType
import net.droopia.hluweather.data.model.RadarProvider
import net.droopia.hluweather.data.model.RadarTileCoordinate
import net.droopia.hluweather.data.repository.RadarMetadataRepository
import net.droopia.hluweather.data.repository.RadarRefreshPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RadarSharedBudgetIntegrationTest {
    @Test
    fun metadataHttp429DelaysTileAndRepositoryRetryAndEveryAttemptUsesTheSameWindow() = runTest {
        val epoch = 1_700_000_000_000L
        val scheduler = RadarNetworkScheduler(backgroundScope, { testScheduler.currentTime }, { epoch + testScheduler.currentTime })
        val metadataTimes = mutableListOf<Long>()
        val client = HttpClient(MockEngine) {
            engine {
                dispatcher = StandardTestDispatcher(testScheduler)
                addHandler { request ->
                    assertEquals("api.rainviewer.com", request.url.host)
                    metadataTimes += testScheduler.currentTime
                    if (metadataTimes.size == 1) respond("limited", HttpStatusCode.TooManyRequests,
                        headersOf(HttpHeaders.RetryAfter, "5"))
                    else respond(METADATA, HttpStatusCode.OK)
                }
            }
        }
        try {
            val repository = repository(scheduler, KtorRainViewerRadarApi(client))
            val observer = backgroundScope.launch { repository.state.collect() }
            runCurrent()
            assertEquals(listOf(0L), metadataTimes)
            assertEquals(epoch + 5_000, scheduler.rateLimitedUntil.value)

            val tileTimes = mutableListOf<Long>()
            val loader = RadarTileLoader(backgroundScope, scheduler, RadarTileTransport { _, _ ->
                tileTimes += testScheduler.currentTime
                RadarTileHttpResult(200, RadarTilePayload(byteArrayOf(1), epoch + 60_000))
            }, { epoch + testScheduler.currentTime })
            val tile = backgroundScope.async { loader.load(tile(RadarProvider.RAINVIEWER)) }
            val libre = backgroundScope.async { scheduler.execute(RadarProvider.LIBREWXR) { "unblocked" } }
            runCurrent()
            assertEquals("unblocked", libre.await())
            advanceTimeBy(4_999)
            runCurrent()
            assertTrue(tileTimes.isEmpty())
            assertEquals(listOf(0L), metadataTimes)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(listOf(0L, 5_000L), metadataTimes)
            assertEquals(listOf(5_000L), tileTimes)
            assertEquals(1, tile.await().bytes!!.size)
            assertEquals(RadarProvider.RAINVIEWER, repository.state.value.metadata?.provider)
            observer.cancel()
            runCurrent()

            // Two metadata attempts and one tile attempt consumed three of the 90 tokens.
            repeat(87) { scheduler.execute(RadarProvider.RAINVIEWER) { } }
            val blocked = backgroundScope.async { scheduler.execute(RadarProvider.RAINVIEWER) { "after-window" } }
            runCurrent()
            assertFalse(blocked.isCompleted)
            advanceTimeBy(54_999)
            runCurrent()
            assertFalse(blocked.isCompleted)
            advanceTimeBy(1)
            runCurrent()
            assertEquals("after-window", blocked.await())
        } finally {
            client.close()
        }
    }

    @Test
    fun lastObserverCancelsQueuedAndInFlightMetadataAndAReobserverCanRestart() = runTest {
        val scheduler = RadarNetworkScheduler(backgroundScope, { testScheduler.currentTime })
        val gates = List(4) { CompletableDeferred<Unit>() }
        gates.forEach { gate -> backgroundScope.launch { scheduler.execute(RadarProvider.LIBREWXR) { gate.await() } } }
        runCurrent()
        var attempts = 0
        var cancelled = false
        val client = HttpClient(MockEngine) {
            engine {
                dispatcher = StandardTestDispatcher(testScheduler)
                addHandler {
                    attempts++
                    if (attempts == 1) {
                        try { awaitCancellation() } finally { cancelled = true }
                    }
                    respond(METADATA, HttpStatusCode.OK)
                }
            }
        }
        try {
            val repository = repository(scheduler, KtorRainViewerRadarApi(client))
            val queuedObserver = backgroundScope.launch { repository.state.collect() }
            runCurrent()
            assertEquals(0, attempts)
            queuedObserver.cancel()
            runCurrent()
            gates.forEach { it.complete(Unit) }
            runCurrent()
            assertEquals("Cancelled queued work must not start", 0, attempts)

            val first = backgroundScope.launch { repository.state.collect() }
            val second = backgroundScope.launch { repository.state.collect() }
            runCurrent()
            assertEquals(1, attempts)
            first.cancel()
            runCurrent()
            assertFalse(cancelled)
            second.cancel()
            runCurrent()
            assertTrue(cancelled)

            val restarted = backgroundScope.launch { repository.state.collect() }
            runCurrent()
            assertEquals(2, attempts)
            assertEquals(RadarProvider.RAINVIEWER, repository.state.value.metadata?.provider)
            restarted.cancel()
        } finally {
            client.close()
        }
    }

    private fun TestScope.repository(scheduler: RadarNetworkScheduler, api: RadarMetadataApi) = RadarMetadataRepository(
        provider = RadarProvider.RAINVIEWER,
        api = api,
        cache = object : RadarMetadataCacheStore {
            override suspend fun get(provider: RadarProvider): CachedRadarMetadata? = null
            override suspend fun put(entry: CachedRadarMetadata) = Unit
        },
        scope = backgroundScope,
        policy = RadarRefreshPolicy(refreshInterval = 10.minutes, requestTimeout = 5.seconds,
            maxRetries = 1, initialRetryDelay = 100.milliseconds, maxRetryDelay = 1.seconds),
        now = { Instant.fromEpochMilliseconds(1_700_000_000_000L + testScheduler.currentTime) },
        dispatcher = StandardTestDispatcher(testScheduler),
        requestBudget = scheduler
    )

    private fun tile(provider: RadarProvider) = RadarTileRegistry().register(
        "https://tilecache.rainviewer.com", RadarFrame(100, "/v2/radar/100", RadarFrameType.OBSERVED, provider)
    ).tile(RadarTileCoordinate(1, 0, 0))

    private companion object {
        const val METADATA = """{"host":"https://tilecache.rainviewer.com","radar":{"past":[{"time":100,"path":"/v2/radar/100"}]}}"""
    }
}
