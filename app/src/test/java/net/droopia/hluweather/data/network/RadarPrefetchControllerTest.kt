package net.droopia.hluweather.data.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.droopia.hluweather.data.model.RadarFrame
import net.droopia.hluweather.data.model.RadarFrameType
import net.droopia.hluweather.data.model.RadarMetadata
import net.droopia.hluweather.data.model.RadarProvider
import net.droopia.hluweather.data.model.RadarProviderDescriptors
import net.droopia.hluweather.data.model.RadarTileCoverage
import net.droopia.hluweather.data.model.RadarViewportBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RadarPrefetchControllerTest {
    private val host = "https://radar.example.test"
    private val frames = (0..4).map {
        RadarFrame(1_700_000_000L + it * 631, "/radar/$it", RadarFrameType.OBSERVED, RadarProvider.RAINVIEWER)
    }
    private val metadata = RadarMetadata(host, frames, provider = RadarProvider.RAINVIEWER)
    private val viewport = RadarViewportBounds(10.0, 10.0, 20.0, 20.0, 1.0, 15.0, 15.0)

    @Test
    fun exactNeighborhoodUsesTimelineIndicesAndCenterNearestCoordinates() {
        val registry = RadarTileRegistry()
        val plan = planRadarTiles(registry, metadata, frames[2].identity(host), viewport)
        assertEquals(1, plan.demand.size)
        assertEquals(listOf("/radar/1/", "/radar/3/"),
            plan.speculative.map { key -> frames.first { key.url.contains(it.path + "/") }.path + "/" })
        assertTrue(plan.demand.all { it.coordinate.zoom == 0 })
        assertTrue(plan.speculative.none { it in plan.demand })
        assertEquals(plan.speculative.size, plan.speculative.toSet().size)
        assertEquals("Neighbors do not retain native source registrations", 1, registry.registeredCount)
        registry.unregister(plan.requestedSource)
        assertEquals(0, registry.registeredCount)
    }

    @Test
    fun largeViewportIsBoundedAndPrioritizesClosestFrameAndTiles() {
        val broad = RadarViewportBounds(-90.0, -66.0, 90.0, 66.0, 10.0, 0.0, 0.0)
        val plan = planRadarTiles(RadarTileRegistry(), metadata, frames[2].identity(host), broad)
        assertTrue(plan.demand.size > 16)
        assertEquals(64, plan.speculative.size)
        assertTrue(plan.speculative.first().url.contains("/radar/1/"))
        assertEquals(plan.demand.first().coordinate, plan.speculative.first().coordinate)
    }

    @Test
    fun replanAndStopCancelObsoletePrefetchButRetainSharedDemand() = runTest {
        val registry = RadarTileRegistry()
        val scheduler = RadarNetworkScheduler(backgroundScope, { testScheduler.currentTime })
        val started = mutableListOf<RadarTileKey>()
        val cancelled = mutableListOf<RadarTileKey>()
        val gates = mutableMapOf<RadarTileKey, CompletableDeferred<Unit>>()
        val loader = RadarTileLoader(backgroundScope, scheduler, RadarTileTransport { key, _ ->
            started += key
            val gate = gates.getOrPut(key) { CompletableDeferred() }
            try { gate.await() } finally { if (!gate.isCompleted) cancelled += key }
            RadarTileHttpResult(200, RadarTilePayload(byteArrayOf(1), 100_000))
        }, { testScheduler.currentTime })
        val controller = RadarPrefetchController(backgroundScope, loader)
        val initial = planRadarTiles(registry, metadata, frames[2].identity(host), viewport)
        controller.replan(initial)
        runCurrent()
        assertEquals(initial.speculative.take(RadarNetworkScheduler.MAX_SPECULATIVE_CONCURRENT), started)

        val selected = initial.speculative[1] // frame 3 was already being prefetched
        val display = backgroundScope.async { loader.load(selected) }
        runCurrent()
        val next = planRadarTiles(registry, metadata, frames[3].identity(host), viewport)
        controller.replan(next)
        runCurrent()
        assertEquals(1, started.count { it == selected })
        assertFalse(selected in cancelled)
        controller.stop()
        runCurrent()
        assertFalse(selected in cancelled)
        val countAtStop = started.size
        gates.getValue(selected).complete(Unit)
        runCurrent()
        assertEquals(1, display.await().bytes!!.size)
        assertEquals(countAtStop, started.size)
        assertTrue(cancelled.isNotEmpty())
    }

    @Test
    fun metadataRefreshAndEndpointProduceOnlyCurrentNeighbors() {
        val refreshed = metadata.copy(frames = frames.drop(2))
        val plan = planRadarTiles(RadarTileRegistry(), refreshed, frames[2].identity(host), viewport)
        assertEquals(1, plan.speculative.size)
        assertTrue(plan.speculative[0].url.contains("/radar/3/"))
    }

    @Test
    fun visibleAndSpeculativePlansShareCoarseCoordinatesAndPrefetchWarmsForegroundCache() = runTest {
        val registry = RadarTileRegistry()
        val broad = RadarViewportBounds(-90.0, -66.0, 90.0, 66.0, 4.0, 0.0, 0.0)
        val plan = planRadarTiles(registry, metadata, frames[2].identity(host), broad)
        val expectedCoordinates = RadarTileCoverage.tiles(
            broad,
            RadarProviderDescriptors[metadata.provider]
        ).toSet()
        assertTrue(expectedCoordinates.size > 1)
        assertEquals(expectedCoordinates, plan.demand.map { it.coordinate }.toSet())
        assertEquals(expectedCoordinates, plan.speculative.map { it.coordinate }.toSet())
        assertEquals(setOf(3), expectedCoordinates.map { it.zoom }.toSet())

        val scheduler = RadarNetworkScheduler(backgroundScope, { testScheduler.currentTime })
        val attempts = mutableListOf<RadarTileKey>()
        val loader = RadarTileLoader(backgroundScope, scheduler, RadarTileTransport { key, _ ->
            attempts += key
            RadarTileHttpResult(200, RadarTilePayload(byteArrayOf(key.coordinate.x.toByte()), 100_000))
        }, { testScheduler.currentTime })
        val controller = RadarPrefetchController(backgroundScope, loader)
        controller.replan(plan)
        runCurrent()
        assertEquals(plan.speculative, attempts)

        val previousFrame = plan.speculative.filter { it.url.contains(frames[1].path + "/") }
        assertEquals(expectedCoordinates, previousFrame.map { it.coordinate }.toSet())
        previousFrame.forEach { key -> loader.load(key) }
        assertEquals("Foreground demand must reuse completed coarse prefetch", plan.speculative, attempts)

        controller.stop()
        registry.unregister(plan.requestedSource)
    }

    @Test
    fun invalidNeighborDoesNotLeakARequestedSourceRegistration() {
        val registry = RadarTileRegistry()
        val invalid = metadata.copy(frames = listOf(frames[0], frames[1].copy(path = "/../invalid")))
        assertTrue(runCatching { planRadarTiles(registry, invalid, frames[0].identity(host), viewport) }.isFailure)
        assertEquals(0, registry.registeredCount)
    }

    @Test
    fun unchangedPlanDoesNotRepeatFailedPrefetchButAChangedPlanCanRetryIt() = runTest {
        val registry = RadarTileRegistry()
        val scheduler = RadarNetworkScheduler(backgroundScope, { testScheduler.currentTime })
        val attempts = mutableListOf<RadarTileKey>()
        val loader = RadarTileLoader(backgroundScope, scheduler, RadarTileTransport { key, _ ->
            attempts += key
            throw RadarApiException("missing", 404)
        }, { testScheduler.currentTime })
        val controller = RadarPrefetchController(backgroundScope, loader)
        val initial = planRadarTiles(registry, metadata, frames[2].identity(host), viewport)
        controller.replan(initial)
        runCurrent()
        assertEquals(initial.speculative.size, attempts.size)

        val same = planRadarTiles(registry, metadata, frames[2].identity(host), viewport)
        controller.replan(same)
        runCurrent()
        assertEquals("Equivalent viewport plans must not restart failed or no-store prefetch", initial.speculative.size, attempts.size)

        // Jump to the endpoint so the new one-neighbor plan still shares frame 3 with the old plan.
        val changed = planRadarTiles(registry, metadata, frames[4].identity(host), viewport)
        controller.replan(changed)
        runCurrent()
        val shared = initial.speculative.intersect(changed.speculative.toSet())
        assertTrue(shared.isNotEmpty())
        shared.forEach { assertEquals(2, attempts.count { attempt -> attempt == it }) }
        controller.stop()
        controller.replan(changed)
        runCurrent()
        shared.forEach { assertEquals(3, attempts.count { attempt -> attempt == it }) }
        controller.stop()
        listOf(initial, same, changed).forEach { registry.unregister(it.requestedSource) }
        assertEquals(0, registry.registeredCount)
    }

    @Test
    fun switchingProviderAndViewportInvalidatesQueuedOldWorkBeforeNewWorkStarts() = runTest {
        val registry = RadarTileRegistry()
        val scheduler = RadarNetworkScheduler(backgroundScope, { testScheduler.currentTime })
        val started = mutableListOf<RadarTileKey>()
        val loader = RadarTileLoader(backgroundScope, scheduler, RadarTileTransport { key, _ ->
            started += key
            CompletableDeferred<Unit>().await()
            error("unreachable")
        }, { testScheduler.currentTime })
        val controller = RadarPrefetchController(backgroundScope, loader)
        val old = planRadarTiles(registry, metadata, frames[2].identity(host), viewport)
        controller.replan(old)
        runCurrent()
        val oldStarted = old.speculative.take(RadarNetworkScheduler.MAX_SPECULATIVE_CONCURRENT)
        assertEquals(oldStarted.size, started.size)

        val newFrames = frames.map { it.copy(provider = RadarProvider.LIBREWXR) }
        val newer = RadarMetadata(host, newFrames, provider = RadarProvider.LIBREWXR)
        val moved = viewport.copy(west = -80.0, east = -70.0, centerLongitude = -75.0)
        val next = planRadarTiles(registry, newer, newFrames[2].identity(host), moved)
        controller.replan(next)
        runCurrent()
        val nextStarted = next.speculative.take(RadarNetworkScheduler.MAX_SPECULATIVE_CONCURRENT)
        assertEquals(oldStarted.size,
            started.count { it.provider == RadarProvider.RAINVIEWER })
        assertEquals(nextStarted.size,
            started.count { it.provider == RadarProvider.LIBREWXR })
        assertTrue(started.drop(oldStarted.size).all { it in next.speculative })
        controller.stop()
        runCurrent()
        val stoppedAt = started.size
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(stoppedAt, started.size)
    }
}
