package net.droopia.hluweather.data.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.droopia.hluweather.data.model.RadarProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RadarNetworkSchedulerTest {
    @Test
    fun foregroundUsesRemainingSlotsAndPrecedesQueuedPrefetchWithinEightTotalSlots() = runTest {
        val scheduler = RadarNetworkScheduler(backgroundScope, { testScheduler.currentTime })
        val gates = mutableMapOf<String, CompletableDeferred<Unit>>()
        val starts = mutableListOf<String>()
        fun launchRequest(name: String, speculative: Boolean) =
            backgroundScope.launch {
                scheduler.execute(RadarProvider.LIBREWXR, RadarRequestPriority(speculative)) {
                    starts += name
                    gates.getValue(name).await()
                }
            }

        (1..6).forEach { index ->
            gates["spec$index"] = CompletableDeferred()
            launchRequest("spec$index", speculative = true)
        }
        runCurrent()
        assertEquals(listOf("spec1", "spec2", "spec3", "spec4"), starts)
        assertEquals(RadarNetworkScheduler.MAX_SPECULATIVE_CONCURRENT, starts.size)

        (1..5).forEach { index ->
            gates["demand$index"] = CompletableDeferred()
            launchRequest("demand$index", speculative = false)
        }
        runCurrent()
        assertEquals(
            listOf("spec1", "spec2", "spec3", "spec4", "demand1", "demand2", "demand3", "demand4"),
            starts
        )

        // A foreground request queued behind all eight held attempts outranks earlier speculation.
        gates.getValue("spec1").complete(Unit)
        runCurrent()
        assertEquals("demand5", starts[8])
        assertFalse("A fifth speculative request must still wait", "spec5" in starts)

        gates.getValue("demand5").complete(Unit)
        runCurrent()
        assertEquals("spec5", starts[9])
    }

    @Test
    fun pendingPrefetchHasFiniteQueue() = runTest {
        val scheduler = RadarNetworkScheduler(backgroundScope, { testScheduler.currentTime })
        val gate = CompletableDeferred<Unit>()
        repeat(RadarNetworkScheduler.MAX_SPECULATIVE_CONCURRENT) {
            backgroundScope.launch { scheduler.execute(RadarProvider.LIBREWXR, RadarRequestPriority(true)) { gate.await() } }
        }
        runCurrent()
        val queued = List(64) {
            backgroundScope.async { scheduler.execute(RadarProvider.LIBREWXR, RadarRequestPriority(true)) { gate.await() } }
        }
        runCurrent()
        val overflow = backgroundScope.async {
            runCatching { scheduler.execute(RadarProvider.LIBREWXR, RadarRequestPriority(true)) { gate.await() } }
        }
        runCurrent()
        assertTrue(overflow.await().exceptionOrNull() is RadarSpeculativeQueueFullException)
        assertTrue(queued.none { it.isCompleted })
        queued.first().cancel()
        runCurrent()
        val replacement = backgroundScope.async {
            scheduler.execute(RadarProvider.LIBREWXR, RadarRequestPriority(true)) { gate.await() }
        }
        runCurrent()
        assertFalse(replacement.isCompleted)
    }

    @Test
    fun synchronouslyInvalidatedSpeculationCannotStartWhileItsCoroutineCancellationIsPending() = runTest {
        val scheduler = RadarNetworkScheduler(backgroundScope, { testScheduler.currentTime })
        val gate = CompletableDeferred<Unit>()
        repeat(RadarNetworkScheduler.MAX_SPECULATIVE_CONCURRENT) {
            backgroundScope.launch { scheduler.execute(RadarProvider.LIBREWXR, RadarRequestPriority(true)) { gate.await() } }
        }
        runCurrent()
        var relevant = true
        var starts = 0
        val priority = RadarRequestPriority(true).also { it.mayRun = { relevant } }
        val obsolete = backgroundScope.async { scheduler.execute(RadarProvider.LIBREWXR, priority) { starts++ } }
        runCurrent()
        relevant = false // plan invalidation happens before asynchronous consumer cancellation
        gate.complete(Unit)
        runCurrent()
        assertEquals(0, starts)
        assertTrue(obsolete.isCancelled)
    }

    @Test
    fun rainViewerRollingBudgetReservesTwentyAttemptsForMetadataAndDemand() = runTest {
        val scheduler = RadarNetworkScheduler(backgroundScope, { testScheduler.currentTime })
        val speculative = RadarRequestPriority(true)
        var attempts = 0
        repeat(70) { scheduler.execute(RadarProvider.RAINVIEWER, speculative) { attempts++ } }
        val blockedSpec = backgroundScope.launch {
            scheduler.execute(RadarProvider.RAINVIEWER, speculative) { attempts++ }
        }
        runCurrent()
        assertEquals(70, attempts)
        repeat(20) { scheduler.execute(RadarProvider.RAINVIEWER) { attempts++ } }
        val blockedDemand = backgroundScope.launch {
            scheduler.execute(RadarProvider.RAINVIEWER) { attempts++ }
        }
        runCurrent()
        assertEquals(90, attempts)
        advanceTimeBy(59_999)
        runCurrent()
        assertFalse(blockedDemand.isCompleted)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(blockedDemand.isCompleted)
        assertTrue(blockedSpec.isCompleted)
        assertEquals(92, attempts)
    }

    @Test
    fun retryAfterBlocksMetadataAndTileAttemptsWithoutAffectingLibreWxr() = runTest {
        val epoch = 1_700_000_000_000L
        val scheduler = RadarNetworkScheduler(backgroundScope, { testScheduler.currentTime }, { epoch + testScheduler.currentTime })
        val failed = runCatching {
            scheduler.execute(RadarProvider.RAINVIEWER) {
                throw RadarApiException("rate limited", 429, "5")
            }
        }
        assertEquals(429, (failed.exceptionOrNull() as RadarApiException).statusCode)
        assertEquals(epoch + 5_000, scheduler.rateLimitedUntil.value)
        val rain = backgroundScope.async { scheduler.execute(RadarProvider.RAINVIEWER) { "metadata" } }
        val libre = backgroundScope.async { scheduler.execute(RadarProvider.LIBREWXR) { "tile" } }
        runCurrent()
        assertEquals("tile", libre.await())
        assertFalse(rain.isCompleted)
        advanceTimeBy(4_999)
        runCurrent()
        assertFalse(rain.isCompleted)
        advanceTimeBy(1)
        runCurrent()
        assertEquals("metadata", rain.await())
    }

    @Test
    fun negativeMonotonicClockOriginDoesNotCreateAnInitialCooldown() = runTest {
        val scheduler = RadarNetworkScheduler(backgroundScope, { -100_000L + testScheduler.currentTime })
        assertEquals("first", scheduler.execute(RadarProvider.RAINVIEWER) { "first" })
        runCatching { scheduler.execute(RadarProvider.RAINVIEWER) { throw RadarApiException("limited", 429, "1") } }
        val blocked = backgroundScope.async { scheduler.execute(RadarProvider.RAINVIEWER) { "after" } }
        runCurrent()
        assertFalse(blocked.isCompleted)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals("after", blocked.await())
    }

    @Test
    fun rateLimitStatusClearsAtDeadlineWithoutAnotherRequest() = runTest {
        val scheduler = RadarNetworkScheduler(backgroundScope, { testScheduler.currentTime }, { testScheduler.currentTime })
        runCatching { scheduler.execute(RadarProvider.RAINVIEWER) { throw RadarApiException("limited", 429, "3") } }
        runCurrent()
        assertEquals(3_000L, scheduler.rateLimitedUntil.value)
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(null, scheduler.rateLimitedUntil.value)
    }

    @Test
    fun retryAfterParsesHttpDatesAndRejectsInvalidValues() {
        assertEquals(10_000L, RadarNetworkScheduler.parseRetryAfterMillis("Wed, 21 Oct 2015 07:28:10 GMT", 1_445_412_480_000))
        assertEquals(null, RadarNetworkScheduler.parseRetryAfterMillis("-1", 0))
        assertEquals(null, RadarNetworkScheduler.parseRetryAfterMillis("garbage", 0))
    }

    @Test
    fun missingOrInvalidRetryAfterUsesExponentialFallbackCappedAtOneMinute() = runTest {
        val scheduler = RadarNetworkScheduler(backgroundScope, { testScheduler.currentTime }, { testScheduler.currentTime })
        val delays = listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 60_000L, 60_000L)
        for (wait in delays) {
            runCatching { scheduler.execute(RadarProvider.RAINVIEWER) { throw RadarApiException("limited", 429, "invalid") } }
            val retry = backgroundScope.async { scheduler.execute(RadarProvider.RAINVIEWER) { "ok" } }
            runCurrent()
            advanceTimeBy(wait - 1)
            runCurrent()
            assertFalse(retry.isCompleted)
            advanceTimeBy(1)
            runCurrent()
            assertEquals("ok", retry.await())
        }
    }
}
