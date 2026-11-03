package net.droopia.hluweather.data.network

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
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

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RadarTileLoaderTest {
    private val registry = RadarTileRegistry()

    @Test
    fun completedPrefetchIsReusedForDisplayWithoutAnotherNetworkAttempt() = runTest {
        val scheduler = RadarNetworkScheduler(backgroundScope, { testScheduler.currentTime })
        var attempts = 0
        val loader = RadarTileLoader(backgroundScope, scheduler, RadarTileTransport { _, _ ->
            attempts++
            ok(1, 2, 3)
        }, { testScheduler.currentTime })
        val key = key(1)
        val prefetched = loader.load(key, speculative = true)
        val displayed = loader.load(key)
        assertArrayEquals(prefetched.bytes, displayed.bytes)
        assertEquals(1, attempts)
    }

    @Test
    fun cancellingOneSharedConsumerDoesNotCancelAnother() = runTest {
        val scheduler = RadarNetworkScheduler(backgroundScope, { testScheduler.currentTime })
        val gate = CompletableDeferred<Unit>()
        val attempts = AtomicInteger()
        var cancelled = false
        val loader = RadarTileLoader(backgroundScope, scheduler, RadarTileTransport { _, _ ->
            attempts.incrementAndGet()
            try { gate.await() } finally { cancelled = !gate.isCompleted }
            ok(7)
        }, { testScheduler.currentTime })
        val prefetch = backgroundScope.async { loader.load(key(1), speculative = true) }
        runCurrent()
        val demand = backgroundScope.async { loader.load(key(1)) }
        runCurrent()
        prefetch.cancel()
        runCurrent()
        assertFalse(cancelled)
        gate.complete(Unit)
        runCurrent()
        assertArrayEquals(byteArrayOf(7), demand.await().bytes)
        assertEquals(1, attempts.get())
        assertFalse(cancelled)
    }

    @Test
    fun invalidatingOneQueuedSpeculativeOwnerPreservesAnotherOwnerOfTheSameTile() = runTest {
        val scheduler = RadarNetworkScheduler(backgroundScope, { testScheduler.currentTime })
        val gate = CompletableDeferred<Unit>()
        repeat(RadarNetworkScheduler.MAX_SPECULATIVE_CONCURRENT) {
            backgroundScope.launch { scheduler.execute(RadarProvider.LIBREWXR, RadarRequestPriority(true)) { gate.await() } }
        }
        runCurrent()
        var attempts = 0
        val loader = RadarTileLoader(backgroundScope, scheduler, RadarTileTransport { _, _ -> attempts++; ok(7) },
            { testScheduler.currentTime })
        val firstOwner = RadarSpeculativeOwner()
        val secondOwner = RadarSpeculativeOwner()
        val first = backgroundScope.async { loader.load(key(1), speculative = true, speculativeOwner = firstOwner) }
        val second = backgroundScope.async { loader.load(key(1), speculative = true, speculativeOwner = secondOwner) }
        runCurrent()
        firstOwner.cancel()
        first.cancel()
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertArrayEquals(byteArrayOf(7), second.await().bytes)
        assertEquals(1, attempts)
    }

    @Test
    fun foregroundJoiningAnAlreadyInvalidatedQueuedPrefetchStartsAFreshRequest() = runTest {
        val scheduler = RadarNetworkScheduler(backgroundScope, { testScheduler.currentTime })
        val gate = CompletableDeferred<Unit>()
        repeat(RadarNetworkScheduler.MAX_SPECULATIVE_CONCURRENT) {
            backgroundScope.launch { scheduler.execute(RadarProvider.LIBREWXR, RadarRequestPriority(true)) { gate.await() } }
        }
        runCurrent()
        var attempts = 0
        val loader = RadarTileLoader(backgroundScope, scheduler, RadarTileTransport { _, _ -> attempts++; ok(9) },
            { testScheduler.currentTime })
        val owner = RadarSpeculativeOwner()
        val prefetch = backgroundScope.async { loader.load(key(1), speculative = true, speculativeOwner = owner) }
        runCurrent()
        owner.cancel()
        // Purge the scheduler queue synchronously, before the cancelled result resumes the loader.
        scheduler.priorityChanged(RadarRequestPriority(false))
        val demand = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) { loader.load(key(1)) }
        runCurrent()
        assertArrayEquals(byteArrayOf(9), demand.await().bytes)
        assertTrue(prefetch.isCancelled)
        assertEquals(1, attempts)
    }

    @Test
    fun joiningQueuedPrefetchAsDemandImmediatelyPromotesItAboveFourActiveSpeculations() = runTest {
        val scheduler = RadarNetworkScheduler(backgroundScope, { testScheduler.currentTime })
        val gate = CompletableDeferred<Unit>()
        val starts = mutableListOf<RadarTileKey>()
        val loader = RadarTileLoader(backgroundScope, scheduler, RadarTileTransport { key, _ ->
            starts += key
            gate.await()
            ok(1)
        }, { testScheduler.currentTime })
        val keys = (1..6).map(::key)
        keys.forEach { tile -> backgroundScope.launch { loader.load(tile, speculative = true) } }
        runCurrent()
        assertEquals(keys.take(RadarNetworkScheduler.MAX_SPECULATIVE_CONCURRENT), starts)
        val demand = backgroundScope.async { loader.load(keys[5]) }
        runCurrent()
        assertEquals(keys.take(RadarNetworkScheduler.MAX_SPECULATIVE_CONCURRENT) + keys[5], starts)
        gate.complete(Unit)
        runCurrent()
        assertArrayEquals(byteArrayOf(1), demand.await().bytes)
        assertEquals(1, starts.count { it == keys[5] })
    }

    @Test
    fun demotingRunningDemandNeverExceedsFourSpeculativeRequests() = runTest {
        val scheduler = RadarNetworkScheduler(backgroundScope, { testScheduler.currentTime })
        val gate = CompletableDeferred<Unit>()
        val starts = mutableListOf<RadarTileKey>()
        val cancellations = mutableListOf<RadarTileKey>()
        val loader = RadarTileLoader(backgroundScope, scheduler, RadarTileTransport { key, _ ->
            starts += key
            try { gate.await() } finally { if (!gate.isCompleted) cancellations += key }
            ok(1)
        }, { testScheduler.currentTime })
        val first = key(1)
        val prefetch = backgroundScope.async { loader.load(first, speculative = true) }
        runCurrent()
        val demand = backgroundScope.async { loader.load(first) }
        runCurrent()
        val others = (2..5).map { index -> backgroundScope.async { loader.load(key(index), speculative = true) } }
        runCurrent()
        assertEquals(listOf(first, key(2), key(3), key(4), key(5)), starts)
        demand.cancel()
        runCurrent()
        assertTrue("Demoted excess speculation must be cancelled", first in cancellations)
        assertTrue(prefetch.isCancelled)
        val queued = backgroundScope.async { loader.load(key(6), speculative = true) }
        runCurrent()
        assertEquals("Four surviving speculative slots remain busy", 5, starts.size)
        assertFalse(queued.isCompleted)
        gate.complete(Unit)
        runCurrent()
        others.forEach { it.await() }
        queued.await()
        assertEquals(6, starts.size)
    }

    @Test
    fun cancelledOldRequestCannotOverwriteNewConsumerOrItsCache() = runTest {
        val scheduler = RadarNetworkScheduler(backgroundScope, { testScheduler.currentTime })
        val oldGate = CompletableDeferred<Unit>()
        var attempts = 0
        val loader = RadarTileLoader(backgroundScope, scheduler, RadarTileTransport { _, _ ->
            attempts++
            if (attempts == 1) {
                withContext(NonCancellable) { oldGate.await() }
                ok(1)
            } else ok(2)
        }, { testScheduler.currentTime })
        val old = backgroundScope.async { loader.load(key(1)) }
        runCurrent()
        old.cancel()
        runCurrent()
        val fresh = backgroundScope.async { loader.load(key(1)) }
        runCurrent()
        assertArrayEquals(byteArrayOf(2), fresh.await().bytes)
        oldGate.complete(Unit)
        runCurrent()
        assertArrayEquals(byteArrayOf(2), loader.load(key(1)).bytes)
        assertEquals(2, attempts)
    }

    @Test
    fun lruEnforcesBytesAndEvictsLeastRecentlyUsedWithoutCachingFailures() = runTest {
        val scheduler = RadarNetworkScheduler(backgroundScope, { testScheduler.currentTime })
        val attempts = mutableMapOf<RadarTileKey, Int>()
        val loader = RadarTileLoader(backgroundScope, scheduler, RadarTileTransport { key, _ ->
            attempts[key] = attempts.getOrDefault(key, 0) + 1
            if (key == key(3)) ok(1, 2, 3, 4) else ok(1, 2)
        }, { testScheduler.currentTime }, maxCacheBytes = 4)
        val one = key(1)
        val two = key(2)
        val three = key(3)
        loader.load(one)
        loader.load(two)
        loader.load(one) // one is now most recently used
        loader.load(three) // consumes the entire cache
        assertEquals(4L, loader.cachedBytes)
        assertEquals(1, loader.cachedEntries)
        loader.load(two)
        assertEquals(2, attempts[two])
        assertTrue(loader.cachedBytes <= 4)

        val tooSmall = RadarTileLoader(backgroundScope, scheduler, RadarTileTransport { _, _ -> ok(1, 2) },
            { testScheduler.currentTime }, maxCacheBytes = 1)
        tooSmall.load(one)
        assertEquals(0L, tooSmall.cachedBytes)
    }

    @Test
    fun defaultSixteenMebibyteLimitEvictsAndBoundsSmallNoContentEntries() = runTest {
        val scheduler = RadarNetworkScheduler(backgroundScope, { testScheduler.currentTime })
        var attempts = 0
        val loader = RadarTileLoader(backgroundScope, scheduler, RadarTileTransport { _, _ ->
            attempts++
            RadarTileHttpResult(200, RadarTilePayload(ByteArray(KtorRadarTileTransport.MAX_TILE_BYTES), 100_000))
        }, { testScheduler.currentTime })
        repeat(4) { loader.load(key(it + 1)) }
        assertEquals(16L * 1024 * 1024, loader.cachedBytes)
        loader.load(key(5))
        assertEquals(16L * 1024 * 1024, loader.cachedBytes)
        assertEquals(4, loader.cachedEntries)
        loader.load(key(1))
        assertEquals(6, attempts)

        val empty = RadarTileLoader(backgroundScope, scheduler, RadarTileTransport { _, _ ->
            RadarTileHttpResult(204, RadarTilePayload(null, 100_000))
        }, { testScheduler.currentTime }, maxCacheEntries = 2)
        repeat(3) { empty.load(key(it + 1)) }
        assertEquals(2, empty.cachedEntries)
        assertEquals(2L, empty.cachedBytes)
    }

    @Test
    fun identicalRemoteUrlsFromDifferentProvidersNeverShareCacheOrInflight() = runTest {
        val scheduler = RadarNetworkScheduler(backgroundScope, { testScheduler.currentTime })
        val seen = mutableListOf<RadarProvider>()
        val loader = RadarTileLoader(backgroundScope, scheduler, RadarTileTransport { key, _ ->
            seen += key.provider
            ok(key.provider.ordinal)
        }, { testScheduler.currentTime })
        val libre = key(1, RadarProvider.LIBREWXR)
        val rain = key(1, RadarProvider.RAINVIEWER)
        assertEquals(libre.url, rain.url)
        assertFalse(libre == rain)
        assertArrayEquals(byteArrayOf(0), loader.load(libre).bytes)
        assertArrayEquals(byteArrayOf(1), loader.load(rain).bytes)
        assertArrayEquals(byteArrayOf(0), loader.load(libre).bytes)
        assertEquals(listOf(RadarProvider.LIBREWXR, RadarProvider.RAINVIEWER), seen)
    }

    @Test
    fun tile429RetryWaitsAlongsideMetadataAndEachRetryConsumesBudget() = runTest {
        val epoch = 1_700_000_000_000L
        val scheduler = RadarNetworkScheduler(backgroundScope, { testScheduler.currentTime }, { epoch + testScheduler.currentTime })
        var attempts = 0
        val loader = RadarTileLoader(backgroundScope, scheduler, RadarTileTransport { _, _ ->
            attempts++
            if (attempts == 1) RadarTileHttpResult(429, ok().payload, "5") else ok(9)
        }, { testScheduler.currentTime })
        val tile = backgroundScope.async { loader.load(key(1, RadarProvider.RAINVIEWER)) }
        runCurrent()
        assertEquals(1, attempts)
        val metadata = backgroundScope.async { scheduler.execute(RadarProvider.RAINVIEWER) { "metadata" } }
        advanceTimeBy(4_999)
        runCurrent()
        assertFalse(tile.isCompleted)
        assertFalse(metadata.isCompleted)
        assertEquals(1, attempts)
        advanceTimeBy(1)
        runCurrent()
        assertEquals("metadata", metadata.await())
        assertArrayEquals(byteArrayOf(9), tile.await().bytes)
        assertEquals(2, attempts)

        // The tile's failed and successful attempts and metadata used three of the 90 tokens.
        repeat(87) { scheduler.execute(RadarProvider.RAINVIEWER) { } }
        val blocked = backgroundScope.async { scheduler.execute(RadarProvider.RAINVIEWER) { } }
        runCurrent()
        assertFalse(blocked.isCompleted)
        advanceTimeBy(55_000)
        runCurrent()
        assertTrue(blocked.isCompleted)
    }

    @Test
    fun validNoContentIsCachedButErrorsAreNot() = runTest {
        val scheduler = RadarNetworkScheduler(backgroundScope, { testScheduler.currentTime })
        var attempts = 0
        val loader = RadarTileLoader(backgroundScope, scheduler, RadarTileTransport { _, _ ->
            attempts++
            if (attempts == 1) throw RadarApiException("not found", 404)
            RadarTileHttpResult(204, RadarTilePayload(null, 60_000))
        }, { testScheduler.currentTime })
        assertTrue(runCatching { loader.load(key(1)) }.isFailure)
        assertEquals(0, loader.cachedEntries)
        assertEquals(null, loader.load(key(1)).bytes)
        assertEquals(null, loader.load(key(1)).bytes)
        assertEquals(2, attempts)
        assertEquals(1, loader.cachedEntries)
    }

    @Test
    fun revalidationDoesNotMakeAnEarlierNoCacheResponseFresh() = runTest {
        val scheduler = RadarNetworkScheduler(backgroundScope, { testScheduler.currentTime })
        var attempts = 0
        val loader = RadarTileLoader(backgroundScope, scheduler, RadarTileTransport { _, prior ->
            attempts++
            if (attempts == 1) {
                RadarTileHttpResult(200, RadarTilePayload(byteArrayOf(1), 0, etag = "v1", mustRevalidate = true))
            } else {
                assertEquals("v1", prior?.etag)
                RadarTileHttpResult(304, RadarTilePayload(null, 100_000))
            }
        }, { testScheduler.currentTime })
        repeat(3) { assertArrayEquals(byteArrayOf(1), loader.load(key(1)).bytes) }
        assertEquals(3, attempts)
    }

    private fun key(index: Int, provider: RadarProvider = RadarProvider.LIBREWXR): RadarTileKey {
        val frame = RadarFrame(index.toLong(), "/v2/radar/$index", RadarFrameType.OBSERVED, provider)
        return registry.register("https://tiles.example.test", frame).tile(RadarTileCoordinate(1, 0, 0))
    }

    private fun ok(vararg values: Int): RadarTileHttpResult = RadarTileHttpResult(
        200, RadarTilePayload(values.map(Int::toByte).toByteArray(), 1_000_000)
    )
}
