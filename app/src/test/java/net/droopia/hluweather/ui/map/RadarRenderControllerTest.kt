@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package net.droopia.hluweather.ui.map

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import java.util.Base64
import net.droopia.hluweather.data.model.RadarFrame
import net.droopia.hluweather.data.model.RadarFrameType
import net.droopia.hluweather.data.model.RadarProvider
import net.droopia.hluweather.data.model.RadarTileCoordinate
import net.droopia.hluweather.data.network.RadarNetworkScheduler
import net.droopia.hluweather.data.network.RadarTileHttpResult
import net.droopia.hluweather.data.network.RadarTileLoader
import net.droopia.hluweather.data.network.RadarTilePayload
import net.droopia.hluweather.data.network.RadarTileRegistry
import net.droopia.hluweather.data.network.RadarTileTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarRenderControllerTest {
    @Test
    fun frontIsRetainedUntilCoverageInstallAndBothRenderBarriers() = runTest {
        val backend = RecordingBackend()
        val controller = RadarRenderController(backgroundScope, backend, waitForFullRender = true)
        val first = request(1)
        controller.update(first, RadarProvider.LIBREWXR)
        runCurrent()
        backend.ready(1)
        runCurrent()
        backend.render(1)
        runCurrent()
        assertNull(controller.state.value.displayed)
        backend.render(1)
        runCurrent()
        assertEquals(first.identity, controller.state.value.displayed)

        val second = request(2)
        controller.update(second, RadarProvider.LIBREWXR)
        runCurrent()
        assertTrue(backend.operations.contains("install:2:0.001"))
        backend.render(2) // An early render cannot replace coverage.
        runCurrent()
        assertFalse(backend.operations.contains("promote:2:1"))
        backend.ready(2)
        runCurrent()
        // The queued fake event is from before coverage; real backend starts its barrier only now.
        backend.render(2)
        runCurrent()
        assertTrue(backend.operations.contains("promote:2:1"))
        assertFalse(backend.operations.contains("remove:1"))
        assertEquals(first.identity, controller.state.value.displayed)
        backend.render(2)
        runCurrent()
        assertEquals(second.identity, controller.state.value.displayed)
        assertTrue(backend.operations.contains("remove:1"))
    }

    @Test
    fun supersededCallbacksCannotPromoteAndFailureKeepsFront() = runTest {
        val backend = RecordingBackend()
        val controller = RadarRenderController(backgroundScope, backend, waitForFullRender = true)
        val first = request(1)
        controller.update(first, RadarProvider.LIBREWXR)
        runCurrent()
        backend.ready(1); runCurrent(); backend.render(1); runCurrent(); backend.render(1); runCurrent()
        controller.update(request(2), RadarProvider.LIBREWXR)
        runCurrent()
        controller.update(request(3), RadarProvider.LIBREWXR)
        runCurrent()
        backend.ready(2); backend.render(2)
        backend.fail(3)
        runCurrent()
        assertFalse(backend.operations.contains("promote:2:1"))
        assertFalse(backend.operations.contains("remove:1"))
        assertTrue(backend.operations.contains("remove:3"))
        assertEquals(first.identity, controller.state.value.displayed)
        assertNotNull(controller.state.value.error)
        controller.retry()
        runCurrent()
        backend.ready(4); runCurrent(); backend.render(4); runCurrent(); backend.render(4); runCurrent()
        assertEquals(request(3).identity, controller.state.value.displayed)
    }

    @Test
    fun timeoutIsFailureNeverReadiness() = runTest {
        val backend = RecordingBackend()
        val controller = RadarRenderController(backgroundScope, backend, timeoutMillis = 100)
        controller.update(request(1), RadarProvider.LIBREWXR)
        runCurrent()
        advanceTimeBy(101)
        runCurrent()
        assertNotNull(controller.state.value.error)
        assertFalse(backend.operations.any { it.startsWith("promote:") })
    }

    @Test
    fun replacementTimeoutRetainsTheDisplayedFront() = runTest {
        val backend = RecordingBackend()
        val controller = RadarRenderController(backgroundScope, backend, timeoutMillis = 100, waitForFullRender = true)
        controller.update(request(1), RadarProvider.LIBREWXR)
        runCurrent(); backend.ready(1); runCurrent(); backend.render(1); runCurrent(); backend.render(1); runCurrent()
        controller.update(request(2), RadarProvider.LIBREWXR)
        runCurrent()
        advanceTimeBy(101)
        runCurrent()
        assertEquals(request(1).identity, controller.state.value.displayed)
        assertNotNull(controller.state.value.error)
        assertFalse(backend.operations.contains("remove:1"))
        assertFalse(backend.operations.contains("promote:2:1"))
        assertTrue(backend.operations.contains("remove:2"))
    }

    @Test
    fun timeoutAfterSwapRestoresTheDisplayedFront() = runTest {
        val backend = RecordingBackend(immediateCoverage = true)
        val controller = RadarRenderController(backgroundScope, backend, timeoutMillis = 100, waitForFullRender = true)
        controller.update(request(1), RadarProvider.LIBREWXR)
        runCurrent(); backend.render(1); runCurrent(); backend.render(1); runCurrent()

        controller.update(request(2), RadarProvider.LIBREWXR)
        runCurrent(); backend.render(2); runCurrent()
        assertTrue(backend.operations.contains("promote:2:1"))
        advanceTimeBy(101)
        runCurrent()

        assertEquals(request(1).identity, controller.state.value.displayed)
        assertEquals(request(2).identity, controller.state.value.requested)
        assertFalse(controller.state.value.pending)
        assertNotNull(controller.state.value.error)
        assertEquals(0.75f, backend.opacities[1])
        assertEquals(0f, backend.opacities[2])
        assertTrue(backend.operations.contains("rollback:2:1:0.75"))
        assertFalse(backend.operations.contains("remove:1"))
        assertFalse(backend.operations.contains("remove:2"))

        // A late event from the failed slot cannot acknowledge it or release either lease.
        backend.render(2); runCurrent()
        assertEquals(request(1).identity, controller.state.value.displayed)
        assertFalse(backend.operations.contains("remove:2"))

        controller.retry()
        runCurrent()
        assertTrue(controller.state.value.pending)
        assertNull(controller.state.value.error)
        assertFalse(backend.operations.contains("create:3"))
        backend.render(2); runCurrent()
        assertFalse(backend.operations.contains("create:3"))
        backend.render(1); runCurrent()
        assertTrue(backend.operations.contains("remove:2"))
        assertTrue(backend.operations.contains("create:3"))
        assertFalse(backend.operations.contains("remove:1"))
        backend.render(3); runCurrent(); backend.render(3); runCurrent()
        assertTrue(backend.operations.contains("promote:3:1"))
        assertEquals(request(2).identity, controller.state.value.displayed)
        assertFalse(controller.state.value.pending)
        assertNull(controller.state.value.error)
        assertTrue(backend.operations.contains("remove:1"))
    }

    @Test
    fun renderErrorAfterSwapRestoresFrontAndWaitsForItsRenderBeforeCleanup() = runTest {
        val backend = RecordingBackend(immediateCoverage = true)
        val controller = RadarRenderController(backgroundScope, backend, waitForFullRender = true)
        controller.update(request(1), RadarProvider.LIBREWXR)
        runCurrent(); backend.render(1); runCurrent(); backend.render(1); runCurrent()
        controller.update(request(2, opacity = 0.4f), RadarProvider.LIBREWXR)
        runCurrent(); backend.render(2); runCurrent()
        backend.failRender(2); runCurrent()

        assertEquals(request(1).identity, controller.state.value.displayed)
        assertEquals(request(2).identity, controller.state.value.requested)
        assertEquals("render failed", controller.state.value.error)
        assertFalse(controller.state.value.pending)
        assertEquals(0.4f, backend.opacities[1])
        assertEquals(0f, backend.opacities[2])
        assertFalse(backend.operations.contains("remove:1"))
        assertFalse(backend.operations.contains("remove:2"))
        backend.render(1); runCurrent()
        assertTrue(backend.operations.contains("remove:2"))
        assertFalse(backend.operations.contains("remove:1"))
        assertEquals(request(1).identity, controller.state.value.displayed)
        assertEquals("render failed", controller.state.value.error)
    }

    @Test
    fun firstFrameFailureHidesAndReleasesUnacknowledgedSlot() = runTest {
        val backend = RecordingBackend(immediateCoverage = true)
        val controller = RadarRenderController(backgroundScope, backend, timeoutMillis = 100, waitForFullRender = true)
        controller.update(request(1), RadarProvider.LIBREWXR)
        runCurrent(); backend.render(1); runCurrent()
        advanceTimeBy(101); runCurrent()
        assertTrue(backend.operations.contains("rollback:1:null:0.75"))
        assertTrue(backend.operations.contains("remove:1"))
        assertNull(controller.state.value.displayed)
        assertNotNull(controller.state.value.error)
        controller.retry(); runCurrent()
        assertTrue(backend.operations.contains("create:2"))
        backend.render(1); runCurrent()
        assertNull(controller.state.value.displayed)
        backend.render(2); runCurrent(); backend.render(2); runCurrent()
        assertEquals(request(1).identity, controller.state.value.displayed)
    }

    @Test
    fun failedRollbackGuardCannotRestoreImageryAfterHide() = runTest {
        val gate = CompletableDeferred<Unit>()
        val backend = RecordingBackend(immediateCoverage = true, rollbackGate = gate)
        val controller = RadarRenderController(backgroundScope, backend, timeoutMillis = 100, waitForFullRender = true)
        controller.update(request(1), RadarProvider.LIBREWXR)
        runCurrent(); backend.render(1); runCurrent(); backend.render(1); runCurrent()
        controller.update(request(2), RadarProvider.LIBREWXR)
        runCurrent(); backend.render(2); runCurrent()
        advanceTimeBy(101); runCurrent()
        assertTrue(backend.rollbackStarted.isCompleted)
        assertTrue(controller.state.value.pending)
        assertNull(controller.state.value.error)
        controller.update(null, RadarProvider.LIBREWXR)
        gate.complete(Unit); runCurrent()
        assertFalse(backend.operations.any { it.startsWith("rollback:") })
        assertTrue(backend.operations.contains("remove:1"))
        assertTrue(backend.operations.contains("remove:2"))
        assertNull(controller.state.value.displayed)
        assertNull(controller.state.value.requested)
        assertTrue(backend.opacities.isEmpty())
    }

    @Test
    fun failedRollbackGuardCannotRestoreOldStyleAfterReload() = runTest {
        val gate = CompletableDeferred<Unit>()
        val backend = RecordingBackend(immediateCoverage = true, rollbackGate = gate)
        val controller = RadarRenderController(backgroundScope, backend, timeoutMillis = 100, waitForFullRender = true)
        controller.update(request(1), RadarProvider.LIBREWXR)
        runCurrent(); backend.render(1); runCurrent(); backend.render(1); runCurrent()
        controller.update(request(2), RadarProvider.LIBREWXR)
        runCurrent(); backend.render(2); runCurrent()
        advanceTimeBy(101); runCurrent()
        assertTrue(backend.rollbackStarted.isCompleted)
        controller.update(request(3, style = 2), RadarProvider.LIBREWXR)
        gate.complete(Unit); runCurrent()
        assertFalse(backend.operations.any { it.startsWith("rollback:") })
        assertTrue(backend.operations.contains("remove:1"))
        assertTrue(backend.operations.contains("remove:2"))
        assertTrue(backend.operations.contains("create:3"))
        assertNull(controller.state.value.displayed)
        backend.render(3); runCurrent(); backend.render(3); runCurrent()
        assertEquals(request(3).identity, controller.state.value.displayed)
    }

    @Test
    fun newerRequestFinishesInterruptedRollbackBeforeUsingEitherSlot() = runTest {
        val gate = CompletableDeferred<Unit>()
        val backend = RecordingBackend(immediateCoverage = true, rollbackGate = gate)
        val controller = RadarRenderController(backgroundScope, backend, timeoutMillis = 100, waitForFullRender = true)
        controller.update(request(1), RadarProvider.LIBREWXR)
        runCurrent(); backend.render(1); runCurrent(); backend.render(1); runCurrent()
        controller.update(request(2), RadarProvider.LIBREWXR)
        runCurrent(); backend.render(2); runCurrent()
        advanceTimeBy(101); runCurrent()
        assertTrue(backend.rollbackStarted.isCompleted)
        controller.update(request(3, opacity = 0.4f), RadarProvider.LIBREWXR)
        gate.complete(Unit); runCurrent()
        assertEquals(listOf("rollback:2:1:0.4"), backend.operations.filter { it.startsWith("rollback:") })
        assertEquals(0.4f, backend.opacities[1])
        assertEquals(0f, backend.opacities[2])
        assertEquals(request(1).identity, controller.state.value.displayed)
        assertFalse(backend.operations.contains("create:3"))
        backend.render(2); runCurrent()
        assertFalse(backend.operations.contains("create:3"))
        backend.render(1); runCurrent()
        assertTrue(backend.operations.contains("remove:2"))
        assertTrue(backend.operations.contains("create:3"))
        backend.render(3); runCurrent(); backend.render(3); runCurrent()
        assertEquals(request(3).identity, controller.state.value.displayed)
    }

    @Test
    fun supersededFirstPromotionStillNeedsItsRenderBeforeAcknowledgment() = runTest {
        val backend = RecordingBackend(immediateCoverage = true)
        val controller = RadarRenderController(backgroundScope, backend, waitForFullRender = true)
        controller.update(request(1), RadarProvider.LIBREWXR)
        runCurrent(); backend.render(1); runCurrent()
        controller.update(request(2), RadarProvider.LIBREWXR)
        runCurrent()
        assertNull(controller.state.value.displayed)
        assertFalse(backend.operations.contains("create:2"))
        backend.render(1); runCurrent()
        assertEquals(request(1).identity, controller.state.value.displayed)
        assertTrue(backend.operations.contains("create:2"))
    }

    @Test
    fun invalidViewportRejectRestoresUnacknowledgedPromotion() = runTest {
        val backend = RecordingBackend(immediateCoverage = true)
        val controller = RadarRenderController(backgroundScope, backend, waitForFullRender = true)
        controller.update(request(1), RadarProvider.LIBREWXR)
        runCurrent(); backend.render(1); runCurrent(); backend.render(1); runCurrent()
        controller.update(request(2), RadarProvider.LIBREWXR)
        runCurrent(); backend.render(2); runCurrent()
        controller.reject(request(3).identity, RadarProvider.LIBREWXR, 1, "invalid viewport")
        runCurrent()
        assertEquals(request(1).identity, controller.state.value.displayed)
        assertEquals("invalid viewport", controller.state.value.error)
        assertEquals(0.75f, backend.opacities[1])
        assertEquals(0f, backend.opacities[2])
        assertFalse(backend.operations.contains("remove:1"))
    }

    @Test
    fun newerRequestCompletesRollbackInterruptedByInvalidViewportRejection() = runTest {
        val gate = CompletableDeferred<Unit>()
        val backend = RecordingBackend(immediateCoverage = true, rollbackGate = gate)
        val controller = RadarRenderController(backgroundScope, backend, waitForFullRender = true)
        controller.update(request(1), RadarProvider.LIBREWXR)
        runCurrent(); backend.render(1); runCurrent(); backend.render(1); runCurrent()
        controller.update(request(2), RadarProvider.LIBREWXR)
        runCurrent(); backend.render(2); runCurrent()
        controller.reject(request(3).identity, RadarProvider.LIBREWXR, 1, "invalid viewport")
        runCurrent()
        assertTrue(backend.rollbackStarted.isCompleted)
        controller.update(request(3), RadarProvider.LIBREWXR)
        gate.complete(Unit); runCurrent()
        assertEquals(listOf("rollback:2:1:0.75"), backend.operations.filter { it.startsWith("rollback:") })
        assertEquals(request(1).identity, controller.state.value.displayed)
        assertFalse(backend.operations.contains("create:3"))
        backend.render(2); runCurrent()
        assertFalse(backend.operations.contains("create:3"))
        backend.render(1); runCurrent()
        assertTrue(backend.operations.contains("create:3"))
        backend.render(3); runCurrent(); backend.render(3); runCurrent()
        assertEquals(request(3).identity, controller.state.value.displayed)
    }

    @Test
    fun invalidViewportRollbackFailureIsReportedAndCanBeRetried() = runTest {
        val backend = RecordingBackend(immediateCoverage = true, rollbackFailures = 1)
        val controller = RadarRenderController(backgroundScope, backend, waitForFullRender = true)
        controller.update(request(1), RadarProvider.LIBREWXR)
        runCurrent(); backend.render(1); runCurrent(); backend.render(1); runCurrent()
        controller.update(request(2), RadarProvider.LIBREWXR)
        runCurrent(); backend.render(2); runCurrent()
        controller.reject(request(3).identity, RadarProvider.LIBREWXR, 1, "invalid viewport")
        runCurrent()
        assertTrue(requireNotNull(controller.state.value.error).contains("restore failed"))
        assertFalse(backend.operations.contains("remove:1"))
        assertFalse(backend.operations.contains("remove:2"))
        controller.update(request(3), RadarProvider.LIBREWXR)
        runCurrent()
        assertTrue(backend.operations.contains("rollback:2:1:0.75"))
        assertEquals(request(1).identity, controller.state.value.displayed)
        backend.render(1); runCurrent()
        assertTrue(backend.operations.contains("create:3"))
    }

    @Test
    fun failedNativeRollbackRetainsBothSlotsUntilRetryCanRestoreAndRender() = runTest {
        val backend = RecordingBackend(immediateCoverage = true, rollbackFailures = 1)
        val controller = RadarRenderController(backgroundScope, backend, timeoutMillis = 100, waitForFullRender = true)
        controller.update(request(1), RadarProvider.LIBREWXR)
        runCurrent(); backend.render(1); runCurrent(); backend.render(1); runCurrent()
        controller.update(request(2), RadarProvider.LIBREWXR)
        runCurrent(); backend.render(2); runCurrent()
        advanceTimeBy(101); runCurrent()
        assertTrue(requireNotNull(controller.state.value.error).contains("restore failed"))
        assertFalse(backend.operations.contains("remove:1"))
        assertFalse(backend.operations.contains("remove:2"))
        controller.retry(); runCurrent()
        assertTrue(backend.operations.contains("rollback:2:1:0.75"))
        assertEquals(0.75f, backend.opacities[1])
        assertEquals(0f, backend.opacities[2])
        assertFalse(backend.operations.contains("create:3"))
        backend.render(1); runCurrent()
        assertTrue(backend.operations.contains("remove:2"))
        assertTrue(backend.operations.contains("create:3"))
        assertFalse(backend.operations.contains("remove:1"))
    }

    @Test
    fun rollbackCleanupTimeoutKeepsBothSlotsUntilRestoredRender() = runTest {
        val backend = RecordingBackend(immediateCoverage = true)
        val controller = RadarRenderController(backgroundScope, backend, timeoutMillis = 100, waitForFullRender = true)
        controller.update(request(1), RadarProvider.LIBREWXR)
        runCurrent(); backend.render(1); runCurrent(); backend.render(1); runCurrent()
        controller.update(request(2), RadarProvider.LIBREWXR)
        runCurrent(); backend.render(2); runCurrent()
        advanceTimeBy(201); runCurrent()
        assertEquals(0.75f, backend.opacities[1])
        assertEquals(0f, backend.opacities[2])
        assertFalse(backend.operations.contains("remove:1"))
        assertFalse(backend.operations.contains("remove:2"))
        assertFalse(controller.state.value.pending)
        assertNotNull(controller.state.value.error)
        controller.retry(); runCurrent()
        backend.render(1); runCurrent()
        assertTrue(backend.operations.contains("remove:2"))
        assertFalse(backend.operations.contains("remove:1"))
    }

    @Test
    fun nativeMemoryHitWithoutResourceCallbacksStillRequiresLaterFullRender() = runTest {
        val backend = RecordingBackend(immediateCoverage = true)
        val controller = RadarRenderController(backgroundScope, backend, waitForFullRender = true)
        controller.update(request(1), RadarProvider.LIBREWXR)
        runCurrent()
        // This backend emits no resource callback. Immediate coverage alone cannot promote it.
        assertFalse(backend.operations.any { it.startsWith("promote:") })
        backend.render(1); runCurrent()
        assertTrue(backend.operations.contains("promote:1:null"))
        assertNull(controller.state.value.displayed)
        backend.render(1); runCurrent()
        assertEquals(request(1).identity, controller.state.value.displayed)
    }

    @Test
    fun cachedOrTransparentCoverageStillRequiresLaterNativeRenders() = runTest {
        val backend = RecordingBackend(immediateCoverage = true)
        val controller = RadarRenderController(backgroundScope, backend, waitForFullRender = true)
        controller.update(request(1), RadarProvider.LIBREWXR)
        runCurrent()
        assertFalse(backend.operations.any { it.startsWith("promote:") })
        backend.render(1); runCurrent()
        assertTrue(backend.operations.contains("promote:1:null"))
        assertNull(controller.state.value.displayed)
        backend.render(1); runCurrent()
        assertEquals(request(1).identity, controller.state.value.displayed)
    }

    @Test
    fun defaultAcceptsCachedTransparentAndNoContentCoverageWithoutRenderEvents() = runTest {
        // A genuine transparent PNG and an HTTP 204 are successful tiles, not failed/blank markers.
        val transparent = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR4nGNgAAIAAAUAAXpeqz8AAAAASUVORK5CYII="
        )
        val registry = RadarTileRegistry()
        val source = registry.register("https://tiles.example.test", request(1).config.frame)
        val scheduler = RadarNetworkScheduler(backgroundScope, { testScheduler.currentTime })
        var calls = 0
        val loader = RadarTileLoader(backgroundScope, scheduler, RadarTileTransport { key, _ ->
            calls++
            if (key.coordinate.x == 1) RadarTileHttpResult(200, RadarTilePayload(transparent, 100_000))
            else RadarTileHttpResult(204, RadarTilePayload(null, 100_000))
        }, { testScheduler.currentTime })
        val keys = listOf(source.tile(RadarTileCoordinate(3, 1, 1)), source.tile(RadarTileCoordinate(3, 2, 1)))
        val payloads = loader.awaitCoverage(keys)
        assertTrue(payloads[0].bytes!!.contentEquals(transparent))
        assertNull(payloads[1].bytes)
        val backend = RecordingBackend(coverageAction = { loader.awaitCoverage(keys) }, failOnRenderBarrier = true)
        val controller = RadarRenderController(backgroundScope, backend)
        controller.update(request(1), RadarProvider.LIBREWXR)
        runCurrent()
        assertEquals("App-cache coverage must not repeat the transport", 2, calls)
        assertEquals(request(1).identity, controller.state.value.displayed)
        assertFalse(controller.state.value.pending)
        assertTrue(backend.operations.contains("promote:1:null"))
        registry.unregister(source)
    }

    @Test
    fun zeroOpacityHidesImmediatelyAndPositiveRestorationStagesAgain() = runTest {
        val backend = RecordingBackend(immediateCoverage = true)
        val controller = RadarRenderController(backgroundScope, backend, waitForFullRender = true)
        controller.update(request(1), RadarProvider.LIBREWXR)
        runCurrent(); backend.render(1); runCurrent(); backend.render(1); runCurrent()
        controller.update(request(1, opacity = 0f), RadarProvider.LIBREWXR)
        runCurrent()
        assertTrue(backend.operations.contains("paint:1:0.0"))
        controller.update(request(1, opacity = 0.4f), RadarProvider.LIBREWXR)
        runCurrent()
        assertTrue(backend.operations.contains("install:2:0.001"))
        assertFalse(backend.operations.contains("paint:1:0.4"))
        backend.render(2); runCurrent(); backend.render(2); runCurrent()
        assertTrue(backend.operations.contains("promote:2:1"))
    }

    @Test
    fun hidingAnUnacknowledgedSwapRestoresOldFrontBeforeShowingAgain() = runTest {
        val backend = RecordingBackend(immediateCoverage = true)
        val controller = RadarRenderController(backgroundScope, backend, waitForFullRender = true)
        controller.update(request(1), RadarProvider.LIBREWXR)
        runCurrent(); backend.render(1); runCurrent(); backend.render(1); runCurrent()
        controller.update(request(2), RadarProvider.LIBREWXR)
        runCurrent(); backend.render(2); runCurrent()
        controller.update(request(2, opacity = 0f), RadarProvider.LIBREWXR)
        runCurrent()
        assertEquals(0f, backend.opacities[1])
        assertEquals(0f, backend.opacities[2])
        controller.update(request(2), RadarProvider.LIBREWXR)
        runCurrent()
        assertTrue(backend.operations.contains("rollback:2:1:0.75"))
        assertEquals(request(1).identity, controller.state.value.displayed)
        assertFalse(backend.operations.contains("create:3"))
        backend.render(1); runCurrent()
        assertTrue(backend.operations.contains("create:3"))
        backend.render(3); runCurrent(); backend.render(3); runCurrent()
        assertEquals(request(2).identity, controller.state.value.displayed)
    }

    @Test
    fun hidingAnUnacknowledgedFirstFrameCannotAcknowledgeAnInvisibleLayer() = runTest {
        val backend = RecordingBackend(immediateCoverage = true)
        val controller = RadarRenderController(backgroundScope, backend, waitForFullRender = true)
        controller.update(request(1), RadarProvider.LIBREWXR)
        runCurrent(); backend.render(1); runCurrent()
        controller.update(request(1, opacity = 0f), RadarProvider.LIBREWXR)
        runCurrent()
        controller.update(request(1), RadarProvider.LIBREWXR)
        runCurrent()
        assertTrue(backend.operations.contains("rollback:1:null:0.75"))
        assertTrue(backend.operations.contains("remove:1"))
        assertTrue(backend.operations.contains("create:2"))
        assertNull(controller.state.value.displayed)
        backend.render(1); runCurrent()
        assertNull(controller.state.value.displayed)
        backend.render(2); runCurrent(); backend.render(2); runCurrent()
        assertEquals(request(1).identity, controller.state.value.displayed)
    }

    @Test
    fun providerStyleAndVisibilityChangesInvalidateSlotsAndStaleWork() = runTest {
        val backend = RecordingBackend(immediateCoverage = true)
        val controller = RadarRenderController(backgroundScope, backend, waitForFullRender = true)
        controller.update(request(1), RadarProvider.LIBREWXR)
        runCurrent(); backend.render(1); runCurrent(); backend.render(1); runCurrent()
        controller.update(request(2), RadarProvider.LIBREWXR)
        runCurrent()
        controller.update(null, RadarProvider.RAINVIEWER)
        runCurrent()
        assertTrue(backend.operations.contains("remove:1"))
        assertTrue(backend.operations.contains("remove:2"))
        assertNull(controller.state.value.displayed)
        backend.render(2); runCurrent()
        assertFalse(backend.operations.contains("promote:2:1"))
        controller.update(request(3, style = 2), RadarProvider.LIBREWXR)
        runCurrent()
        assertTrue(backend.operations.contains("install:3:0.001"))
        controller.update(null, RadarProvider.LIBREWXR)
        runCurrent()
        assertTrue(backend.operations.contains("remove:3"))
    }

    @Test
    fun viewportChangeRestagesWithoutRemovingFront() = runTest {
        val backend = RecordingBackend(immediateCoverage = true)
        val controller = RadarRenderController(backgroundScope, backend, waitForFullRender = true)
        controller.update(request(1), RadarProvider.LIBREWXR)
        runCurrent(); backend.render(1); runCurrent(); backend.render(1); runCurrent()
        val moved = request(1).copy(coordinates = listOf(RadarTileCoordinate(3, 2, 2)))
        controller.update(moved, RadarProvider.LIBREWXR)
        runCurrent()
        assertTrue(backend.operations.contains("install:2:0.001"))
        assertFalse(backend.operations.contains("remove:1"))
    }

    @Test
    fun supersedingAfterSwapWaitsForPromotedRenderBeforeRecycling() = runTest {
        val backend = RecordingBackend(immediateCoverage = true)
        val controller = RadarRenderController(backgroundScope, backend, waitForFullRender = true)
        controller.update(request(1), RadarProvider.LIBREWXR)
        runCurrent(); backend.render(1); runCurrent(); backend.render(1); runCurrent()
        controller.update(request(2), RadarProvider.LIBREWXR)
        runCurrent(); backend.render(2); runCurrent()
        assertTrue(backend.operations.contains("promote:2:1"))
        controller.update(request(3), RadarProvider.LIBREWXR)
        runCurrent()
        assertFalse(backend.operations.contains("remove:1"))
        assertFalse(backend.operations.contains("create:3"))
        backend.render(2); runCurrent()
        assertTrue(backend.operations.contains("remove:1"))
        assertTrue(backend.operations.contains("create:3"))
        assertEquals(request(2).identity, controller.state.value.displayed)
        backend.render(3); runCurrent(); backend.render(3); runCurrent()
        assertEquals(request(3).identity, controller.state.value.displayed)
    }

    @Test
    fun nativeMutationGuardRejectsOldCommandsAfterHideOrStyleReload() = runTest {
        val backend = RecordingBackend(immediateCoverage = true)
        val controller = RadarRenderController(backgroundScope, backend)
        controller.update(request(1), RadarProvider.LIBREWXR)
        runCurrent()
        val oldGuard = requireNotNull(backend.lastInstallGuard)
        controller.update(null, RadarProvider.LIBREWXR)
        runCurrent()
        var applied = false
        assertFalse(oldGuard { applied = true })
        assertFalse(applied)
        controller.update(request(2, style = 2), RadarProvider.LIBREWXR)
        runCurrent()
        assertFalse(oldGuard { applied = true })
        assertFalse(applied)
    }

    @Test
    fun defaultRequiresBothInstallationAndCoverageAndRecyclesWithoutRenderEvents() = runTest {
        val installGate = CompletableDeferred<Unit>()
        val backend = RecordingBackend(installGate = 2L to installGate, failOnRenderBarrier = true)
        val controller = RadarRenderController(backgroundScope, backend)
        controller.update(request(1), RadarProvider.LIBREWXR)
        runCurrent()
        backend.ready(1); runCurrent()
        assertEquals(request(1).identity, controller.state.value.displayed)

        controller.update(request(2), RadarProvider.LIBREWXR)
        runCurrent()
        backend.ready(2); runCurrent()
        assertEquals(request(1).identity, controller.state.value.displayed)
        assertFalse(backend.operations.contains("promote:2:1"))
        assertEquals(setOf(1L, 2L), backend.activeSlots)
        installGate.complete(Unit); runCurrent()
        assertEquals(request(2).identity, controller.state.value.displayed)
        assertFalse(controller.state.value.pending)
        assertEquals(setOf(2L), backend.activeSlots)

        controller.update(request(3), RadarProvider.LIBREWXR)
        runCurrent()
        assertTrue(backend.operations.contains("install:3:0.001"))
        assertFalse(backend.operations.contains("promote:3:2"))
        assertEquals(request(2).identity, controller.state.value.displayed)
        backend.ready(3); runCurrent()
        assertEquals(request(3).identity, controller.state.value.displayed)
        assertEquals(setOf(3L), backend.activeSlots)
        controller.close()
        assertTrue(backend.activeSlots.isEmpty())
    }

    @Test
    fun defaultCoverageFailureAndTimeoutKeepFrontAndRetryWithoutRenderEvents() = runTest {
        val backend = RecordingBackend(failOnRenderBarrier = true)
        val controller = RadarRenderController(backgroundScope, backend, timeoutMillis = 100)
        controller.update(request(1), RadarProvider.LIBREWXR)
        runCurrent(); backend.ready(1); runCurrent()
        controller.update(request(2), RadarProvider.LIBREWXR)
        runCurrent(); backend.fail(2); runCurrent()
        assertEquals(request(1).identity, controller.state.value.displayed)
        assertNotNull(controller.state.value.error)
        assertEquals(setOf(1L), backend.activeSlots)

        controller.retry(); runCurrent()
        advanceTimeBy(101); runCurrent()
        assertEquals(request(1).identity, controller.state.value.displayed)
        assertNotNull(controller.state.value.error)
        assertEquals(setOf(1L), backend.activeSlots)

        controller.retry(); runCurrent(); backend.ready(4); runCurrent()
        assertEquals(request(2).identity, controller.state.value.displayed)
        assertNull(controller.state.value.error)
        assertEquals(setOf(4L), backend.activeSlots)
    }

    @Test
    fun defaultInstallationFailureKeepsFrontAndRetryReleasesFailedLease() = runTest {
        val backend = RecordingBackend(immediateCoverage = true, installFailures = setOf(2L), failOnRenderBarrier = true)
        val controller = RadarRenderController(backgroundScope, backend)
        controller.update(request(1), RadarProvider.LIBREWXR); runCurrent()
        controller.update(request(2), RadarProvider.LIBREWXR); runCurrent()
        assertEquals(request(1).identity, controller.state.value.displayed)
        assertEquals("installation failed", controller.state.value.error)
        assertEquals(setOf(1L), backend.activeSlots)
        controller.retry(); runCurrent()
        assertEquals(request(2).identity, controller.state.value.displayed)
        assertNull(controller.state.value.error)
        assertEquals(setOf(3L), backend.activeSlots)
    }

    @Test
    fun partialNativePromotionFailureRestoresFrontBeforeReleasingStaging() = runTest {
        val backend = RecordingBackend(
            immediateCoverage = true, partialPromotionFailures = setOf(2L), failOnRenderBarrier = true
        )
        val controller = RadarRenderController(backgroundScope, backend)
        controller.update(request(1), RadarProvider.LIBREWXR); runCurrent()
        controller.update(request(2), RadarProvider.LIBREWXR); runCurrent()
        assertEquals(request(1).identity, controller.state.value.displayed)
        assertEquals("partial promotion failed", controller.state.value.error)
        assertTrue(backend.operations.contains("rollback:2:1:0.75"))
        assertEquals(0.75f, backend.opacities[1])
        assertEquals(setOf(1L), backend.activeSlots)
        controller.retry(); runCurrent()
        assertEquals(request(2).identity, controller.state.value.displayed)
        assertEquals(setOf(3L), backend.activeSlots)
    }

    @Test
    fun reentrantInvalidationDuringNativeMutationCannotAcknowledgeObsoleteSwap() = runTest {
        for (hide in listOf(false, true)) {
            lateinit var controller: RadarRenderController
            val backend = RecordingBackend(
                immediateCoverage = true,
                failOnRenderBarrier = true,
                duringPromotion = { slot ->
                    if (slot.id == 2L) controller.update(if (hide) null else request(3), RadarProvider.LIBREWXR)
                }
            )
            controller = RadarRenderController(backgroundScope, backend)
            controller.update(request(1), RadarProvider.LIBREWXR); runCurrent()
            controller.update(request(2), RadarProvider.LIBREWXR); runCurrent()
            assertEquals(if (hide) null else request(3).identity, controller.state.value.displayed)
            assertEquals(if (hide) emptySet<Long>() else setOf(3L), backend.activeSlots)
            assertFalse(controller.state.value.displayed == request(2).identity)
            if (!hide) assertTrue(backend.operations.contains("rollback:2:1:0.75"))
            controller.close()
        }
    }

    @Test
    fun cancellationImmediatelyAfterAcceptedSwapKeepsAcknowledgmentAndReleasesRetiredSlots() = runTest {
        lateinit var controller: RadarRenderController
        var stateAtSwap: RadarDisplayState? = null
        val backend = RecordingBackend(failOnRenderBarrier = true, afterPromotion = { slot ->
            if (slot.id == 2L) {
                stateAtSwap = controller.state.value
                controller.update(request(3), RadarProvider.LIBREWXR)
            }
        })
        controller = RadarRenderController(backgroundScope, backend)
        controller.update(request(1), RadarProvider.LIBREWXR)
        runCurrent(); backend.ready(1); runCurrent()
        controller.update(request(2), RadarProvider.LIBREWXR)
        runCurrent(); backend.ready(2); runCurrent()

        assertEquals(request(2).identity, stateAtSwap?.displayed)
        assertFalse(requireNotNull(stateAtSwap).pending)
        assertEquals(request(3).identity, controller.state.value.requested)
        assertEquals(request(2).identity, controller.state.value.displayed)
        assertTrue(controller.state.value.pending)
        assertEquals(setOf(2L, 3L), backend.activeSlots)
        assertTrue(backend.operations.indexOf("remove:1") < backend.operations.indexOf("create:3"))
        backend.ready(3); runCurrent()
        assertEquals(request(3).identity, controller.state.value.displayed)
        assertEquals(setOf(3L), backend.activeSlots)
        controller.update(null, RadarProvider.LIBREWXR); runCurrent()
        assertTrue(backend.activeSlots.isEmpty())
    }

    @Test
    fun timeoutAfterAcceptedSwapDoesNotTurnAcceptanceIntoFailureOrLeakPredecessor() = runTest {
        val resume = CompletableDeferred<Unit>()
        val backend = RecordingBackend(immediateCoverage = true, afterPromotionGate = 2L to resume, failOnRenderBarrier = true)
        val controller = RadarRenderController(backgroundScope, backend, timeoutMillis = 100)
        controller.update(request(1), RadarProvider.LIBREWXR); runCurrent()
        controller.update(request(2), RadarProvider.LIBREWXR); runCurrent()
        assertEquals(request(2).identity, controller.state.value.displayed)
        assertEquals(setOf(1L, 2L), backend.activeSlots)
        advanceTimeBy(101); runCurrent()
        assertEquals(request(2).identity, controller.state.value.displayed)
        assertFalse(controller.state.value.pending)
        assertNull(controller.state.value.error)
        assertEquals(setOf(2L), backend.activeSlots)
        resume.complete(Unit); runCurrent()
        assertEquals(setOf(2L), backend.activeSlots)
    }

    @Test
    fun cancellingOnlyThePromotionCoroutineStillCleansUpAnAcceptedPredecessor() = runTest {
        val backend = RecordingBackend(
            immediateCoverage = true, cancelAfterPromotion = setOf(2L), failOnRenderBarrier = true
        )
        val controller = RadarRenderController(backgroundScope, backend)
        controller.update(request(1), RadarProvider.LIBREWXR); runCurrent()
        controller.update(request(2), RadarProvider.LIBREWXR); runCurrent()
        assertEquals(request(2).identity, controller.state.value.displayed)
        assertFalse(controller.state.value.pending)
        assertNull(controller.state.value.error)
        assertEquals(setOf(2L), backend.activeSlots)
        controller.close()
        assertTrue(backend.activeSlots.isEmpty())
    }

    @Test
    fun invalidViewportOrZeroOpacityAfterAcceptedSwapReleasesRetiredLease() = runTest {
        for (zeroOpacity in listOf(false, true)) {
            lateinit var controller: RadarRenderController
            val backend = RecordingBackend(immediateCoverage = true, failOnRenderBarrier = true, afterPromotion = { slot ->
                if (slot.id == 2L) {
                    if (zeroOpacity) controller.update(request(2, opacity = 0f), RadarProvider.LIBREWXR)
                    else controller.reject(request(3).identity, RadarProvider.LIBREWXR, 1, "invalid viewport")
                }
            })
            controller = RadarRenderController(backgroundScope, backend)
            controller.update(request(1), RadarProvider.LIBREWXR); runCurrent()
            controller.update(request(2), RadarProvider.LIBREWXR); runCurrent()
            assertEquals(request(2).identity, controller.state.value.displayed)
            assertEquals(setOf(2L), backend.activeSlots)
            if (zeroOpacity) assertEquals(0f, backend.opacities[2])
            else assertEquals("invalid viewport", controller.state.value.error)
            controller.close()
            assertTrue(backend.activeSlots.isEmpty())
            assertNull(controller.state.value.displayed)
        }
    }

    @Test
    fun defaultLatePromotionCannotChangeNewSelectionViewportStyleProviderOrHiddenMap() = runTest {
        val moved = request(3).copy(coordinates = listOf(RadarTileCoordinate(3, 2, 2)))
        val targets = listOf(
            request(3) to RadarProvider.LIBREWXR,
            moved to RadarProvider.LIBREWXR,
            request(3, style = 2) to RadarProvider.LIBREWXR,
            request(3, provider = RadarProvider.RAINVIEWER) to RadarProvider.RAINVIEWER,
            null to RadarProvider.LIBREWXR
        )
        for ((target, provider) in targets) {
            val gate = CompletableDeferred<Unit>()
            val backend = RecordingBackend(immediateCoverage = true, promotionGate = 2L to gate, failOnRenderBarrier = true)
            val controller = RadarRenderController(backgroundScope, backend)
            controller.update(request(1), RadarProvider.LIBREWXR); runCurrent()
            controller.update(request(2), RadarProvider.LIBREWXR); runCurrent()
            val oldGuard = requireNotNull(backend.lastPromotionGuard)
            assertEquals(request(1).identity, controller.state.value.displayed)
            controller.update(target, provider); runCurrent()
            val state = controller.state.value
            val opacities = backend.opacities.toMap()
            val active = backend.activeSlots.toSet()
            var mutated = false
            assertFalse(oldGuard { mutated = true })
            gate.complete(Unit); runCurrent()
            assertFalse(mutated)
            assertEquals(state, controller.state.value)
            assertEquals(opacities, backend.opacities)
            assertEquals(active, backend.activeSlots)
            assertFalse(backend.operations.any { it.startsWith("promote:2:") })
            assertEquals(target?.identity, controller.state.value.displayed)
            assertEquals(if (target == null) 0 else 1, backend.activeSlots.size)
            controller.update(null, provider); runCurrent()
            assertTrue(backend.activeSlots.isEmpty())
        }
    }

    @Test
    fun timeoutInvalidatesQueuedPromotionEvenBeforeAnotherRequest() = runTest {
        val gate = CompletableDeferred<Unit>()
        val backend = RecordingBackend(immediateCoverage = true, promotionGate = 2L to gate, failOnRenderBarrier = true)
        val controller = RadarRenderController(backgroundScope, backend, timeoutMillis = 100)
        controller.update(request(1), RadarProvider.LIBREWXR); runCurrent()
        controller.update(request(2), RadarProvider.LIBREWXR); runCurrent()
        val oldGuard = requireNotNull(backend.lastPromotionGuard)
        advanceTimeBy(101); runCurrent()
        assertEquals(request(1).identity, controller.state.value.displayed)
        assertNotNull(controller.state.value.error)
        assertEquals(setOf(1L), backend.activeSlots)
        var mutated = false
        assertFalse(oldGuard { mutated = true })
        gate.complete(Unit); runCurrent()
        assertFalse(mutated)
        assertEquals(request(1).identity, controller.state.value.displayed)
    }

    @Test
    fun timeoutInvalidatesQueuedInstallationEvenBeforeAnotherRequest() = runTest {
        val gate = CompletableDeferred<Unit>()
        val backend = RecordingBackend(immediateCoverage = true, installGate = 2L to gate, failOnRenderBarrier = true)
        val controller = RadarRenderController(backgroundScope, backend, timeoutMillis = 100)
        controller.update(request(1), RadarProvider.LIBREWXR); runCurrent()
        controller.update(request(2), RadarProvider.LIBREWXR); runCurrent()
        val oldGuard = requireNotNull(backend.lastInstallGuard)
        advanceTimeBy(101); runCurrent()
        assertEquals(request(1).identity, controller.state.value.displayed)
        assertNotNull(controller.state.value.error)
        assertEquals(setOf(1L), backend.activeSlots)
        var mutated = false
        assertFalse(oldGuard { mutated = true })
        gate.complete(Unit); runCurrent()
        assertFalse(mutated)
        assertEquals(setOf(1L), backend.activeSlots)
    }

    @Test
    fun closeInvalidatesQueuedPromotionAndReleasesEverySlot() = runTest {
        val gate = CompletableDeferred<Unit>()
        val backend = RecordingBackend(immediateCoverage = true, promotionGate = 2L to gate, failOnRenderBarrier = true)
        val controller = RadarRenderController(backgroundScope, backend)
        controller.update(request(1), RadarProvider.LIBREWXR); runCurrent()
        controller.update(request(2), RadarProvider.LIBREWXR); runCurrent()
        val oldGuard = requireNotNull(backend.lastPromotionGuard)
        controller.close()
        assertNull(controller.state.value.displayed)
        assertNull(controller.state.value.requested)
        assertTrue(backend.activeSlots.isEmpty())
        var mutated = false
        assertFalse(oldGuard { mutated = true })
        gate.complete(Unit); runCurrent()
        assertFalse(mutated)
        assertTrue(backend.activeSlots.isEmpty())
    }

    @Test
    fun defaultZeroOpacityAndInvalidViewportNeverRestoreObsoleteImagery() = runTest {
        val gate = CompletableDeferred<Unit>()
        val backend = RecordingBackend(immediateCoverage = true, promotionGate = 2L to gate, failOnRenderBarrier = true)
        val controller = RadarRenderController(backgroundScope, backend)
        controller.update(request(1), RadarProvider.LIBREWXR); runCurrent()
        controller.update(request(2), RadarProvider.LIBREWXR); runCurrent()
        val oldGuard = requireNotNull(backend.lastPromotionGuard)
        controller.update(request(2, opacity = 0f), RadarProvider.LIBREWXR); runCurrent()
        assertEquals(0f, backend.opacities[1])
        assertFalse(oldGuard { backend.opacities[1] = 0.75f })
        gate.complete(Unit); runCurrent()
        assertEquals(0f, backend.opacities[1])
        assertEquals(setOf(1L), backend.activeSlots)
        controller.update(request(2), RadarProvider.LIBREWXR); runCurrent()
        assertEquals(request(2).identity, controller.state.value.displayed)
        assertEquals(setOf(3L), backend.activeSlots)
        controller.reject(request(3).identity, RadarProvider.LIBREWXR, 1, "invalid viewport"); runCurrent()
        assertEquals(request(2).identity, controller.state.value.displayed)
        assertEquals("invalid viewport", controller.state.value.error)
        assertEquals(setOf(3L), backend.activeSlots)
        controller.close()
        assertTrue(backend.activeSlots.isEmpty())
    }

    @Test
    fun timedHandoffStartsAfterBothCoverageAndInstallationAndRetiresWithoutEvents() = runTest {
        val installGate = CompletableDeferred<Unit>()
        val backend = RecordingBackend(installGate = 2L to installGate, failOnRenderBarrier = true)
        val controller = timedController(backend)
        controller.update(request(1), RadarProvider.LIBREWXR)
        runCurrent(); backend.ready(1); runCurrent()
        assertEquals(request(1).identity, controller.state.value.displayed) // No initial delay.

        controller.update(request(2), RadarProvider.LIBREWXR)
        runCurrent(); backend.ready(2); runCurrent()
        advanceTimeBy(100); runCurrent()
        assertFalse(backend.operations.contains("promote:2:1"))
        installGate.complete(Unit); runCurrent()
        advanceTimeBy(49); runCurrent()
        assertEquals(request(1).identity, controller.state.value.displayed)
        advanceTimeBy(1); runCurrent()
        assertEquals(request(2).identity, controller.state.value.displayed)
        assertFalse(controller.state.value.pending)
        assertEquals(setOf(1L, 2L), backend.activeSlots)
        assertEquals(0f, backend.opacities[1])
        advanceTimeBy(49); runCurrent()
        assertEquals(setOf(1L, 2L), backend.activeSlots)
        advanceTimeBy(1); runCurrent()
        assertEquals(setOf(2L), backend.activeSlots)
    }

    @Test
    fun timedWarmupStartsAtLaterCoverageAndUsesAcceptedInstallTimeNotContinuationTime() = runTest {
        val afterInstall = CompletableDeferred<Unit>()
        val backend = RecordingBackend(afterInstallGate = 2L to afterInstall, failOnRenderBarrier = true)
        val controller = timedController(backend)
        controller.update(request(1), RadarProvider.LIBREWXR); runCurrent(); backend.ready(1); runCurrent()
        controller.update(request(2), RadarProvider.LIBREWXR); runCurrent()
        advanceTimeBy(100); runCurrent() // Installation alone is not coverage.
        backend.ready(2); runCurrent()
        advanceTimeBy(49); runCurrent()
        assertEquals(request(1).identity, controller.state.value.displayed)
        advanceTimeBy(1); runCurrent()
        afterInstall.complete(Unit); runCurrent()
        assertEquals(request(2).identity, controller.state.value.displayed)
        assertEquals(setOf(1L, 2L), backend.activeSlots)
    }

    @Test
    fun agedOptionalSlotIsAdoptedAcrossPauseAndManualSelectionWithoutAnotherWarmup() = runTest {
        val backend = RecordingBackend(immediateCoverage = true, optionalSupport = true, failOnRenderBarrier = true)
        val controller = timedController(backend)
        val second = request(2)
        controller.update(request(1), RadarProvider.LIBREWXR); runCurrent()
        val before = controller.state.value
        controller.prepare(second, revision = 1); runCurrent()
        assertEquals(before, controller.state.value)
        assertEquals(setOf(1L, 2L), backend.activeSlots)
        assertTrue(backend.operations.contains("install:2:0.001"))
        advanceTimeBy(100); runCurrent()

        // Both calls may arrive in one UI turn when the slider pauses playback.
        controller.prepare(null)
        controller.update(second, RadarProvider.LIBREWXR)
        runCurrent()
        assertEquals(second.identity, controller.state.value.displayed)
        assertEquals(setOf(2L), backend.adoptedSlots)
        assertFalse(backend.operations.contains("create:3"))
        assertEquals(1, backend.operations.count { it == "install:2:0.001" })
        assertEquals(setOf(1L, 2L), backend.activeSlots)
        advanceTimeBy(50); runCurrent()
        assertEquals(setOf(2L), backend.activeSlots)
    }

    @Test
    fun partialWarmupAndOpacityChangeReuseAgeAndLatestOpacity() = runTest {
        val backend = RecordingBackend(immediateCoverage = true, optionalSupport = true, failOnRenderBarrier = true)
        val controller = timedController(backend)
        controller.update(request(1), RadarProvider.LIBREWXR); runCurrent()
        controller.prepare(request(2), revision = 1); runCurrent()
        advanceTimeBy(20); runCurrent()
        controller.update(request(2), RadarProvider.LIBREWXR); runCurrent()
        advanceTimeBy(10); runCurrent()
        controller.update(request(2, opacity = 0.4f), RadarProvider.LIBREWXR); runCurrent()
        advanceTimeBy(19); runCurrent()
        assertEquals(request(1).identity, controller.state.value.displayed)
        advanceTimeBy(1); runCurrent()
        assertEquals(request(2).identity, controller.state.value.displayed)
        assertEquals(0.4f, backend.opacities[2])
        assertFalse(backend.operations.contains("create:3"))
        assertEquals(1, backend.operations.count { it == "install:2:0.001" })
    }

    @Test
    fun expiredOptionalCoverageIsDiscardedAndForegroundReloadsNormally() = runTest {
        var fresh = true
        val backend = RecordingBackend(immediateCoverage = true, optionalSupport = true,
            optionalCoverage = { fresh }, failOnRenderBarrier = true)
        val controller = timedController(backend)
        controller.update(request(1), RadarProvider.LIBREWXR); runCurrent()
        controller.prepare(request(2), revision = 1); runCurrent()
        advanceTimeBy(20); runCurrent()
        controller.update(request(2), RadarProvider.LIBREWXR); runCurrent()
        fresh = false // Expiry after adoption but before promotion must also be checked.
        advanceTimeBy(30); runCurrent()
        assertTrue(backend.operations.contains("remove:2"))
        assertTrue(backend.operations.contains("install:3:0.001"))
        assertEquals(request(1).identity, controller.state.value.displayed)
        advanceTimeBy(50); runCurrent()
        assertEquals(request(2).identity, controller.state.value.displayed)
        assertFalse(backend.operations.contains("promote:2:1"))
        assertEquals(setOf(1L, 3L), backend.activeSlots)
    }

    @Test
    fun missingOptionalCoverageIsSilentAndRetriesOnlyForNewOpportunity() = runTest {
        var fresh = false
        val backend = RecordingBackend(immediateCoverage = true, optionalSupport = true,
            optionalCoverage = { fresh }, failOnRenderBarrier = true)
        val controller = timedController(backend)
        controller.update(request(1), RadarProvider.LIBREWXR); runCurrent()
        val state = controller.state.value
        controller.prepare(request(2), revision = 1); runCurrent()
        assertEquals(state, controller.state.value)
        assertEquals(setOf(1L), backend.activeSlots)
        controller.prepare(request(2), revision = 1); runCurrent()
        assertFalse(backend.operations.contains("create:3"))
        fresh = true
        controller.prepare(request(2), revision = 2); runCurrent()
        assertTrue(backend.operations.contains("install:3:0.001"))
        assertEquals(state, controller.state.value)
    }

    @Test
    fun demandPreemptsUninterruptibleOptionalCoverageAndFencesItsLateCompletion() = runTest {
        val gate = CompletableDeferred<Unit>()
        val backend = RecordingBackend(immediateCoverage = true, optionalSupport = true,
            optionalCoverage = { withContext(NonCancellable) { gate.await() }; true }, failOnRenderBarrier = true)
        val controller = timedController(backend)
        controller.update(request(1), RadarProvider.LIBREWXR); runCurrent()
        controller.prepare(request(2), revision = 1); runCurrent()
        assertEquals(setOf(1L, 2L), backend.activeSlots)
        controller.update(request(3), RadarProvider.LIBREWXR); runCurrent()
        assertTrue(backend.operations.contains("remove:2"))
        assertTrue(backend.operations.contains("install:3:0.001"))
        assertEquals(setOf(1L, 3L), backend.activeSlots)
        gate.complete(Unit); runCurrent()
        assertFalse(backend.operations.contains("install:2:0.001"))
        advanceTimeBy(50); runCurrent()
        assertEquals(request(3).identity, controller.state.value.displayed)
    }

    @Test
    fun pauseDiscardsUnrequestedSlotAndCannotChangeDisplayedState() = runTest {
        val backend = RecordingBackend(immediateCoverage = true, optionalSupport = true, failOnRenderBarrier = true)
        val controller = timedController(backend)
        controller.update(request(1), RadarProvider.LIBREWXR); runCurrent()
        val state = controller.state.value
        controller.prepare(request(2)); runCurrent()
        controller.prepare(null); runCurrent()
        assertEquals(state, controller.state.value)
        assertEquals(setOf(1L), backend.activeSlots)
        controller.update(request(2), RadarProvider.LIBREWXR); runCurrent()
        assertTrue(backend.operations.contains("create:3"))
        assertTrue(backend.adoptedSlots.isEmpty())
    }

    @Test
    fun newDemandPreemptsRetirementAndOldTimerCannotRemoveNewRetiredSlot() = runTest {
        val backend = RecordingBackend(immediateCoverage = true, failOnRenderBarrier = true)
        val controller = timedController(backend, warmupMillis = 0)
        controller.update(request(1), RadarProvider.LIBREWXR); runCurrent()
        controller.update(request(2), RadarProvider.LIBREWXR); runCurrent()
        assertEquals(setOf(1L, 2L), backend.activeSlots)
        advanceTimeBy(20); runCurrent()
        controller.update(request(3), RadarProvider.LIBREWXR); runCurrent()
        assertTrue(backend.operations.indexOf("remove:1") < backend.operations.indexOf("create:3"))
        assertEquals(setOf(2L, 3L), backend.activeSlots)
        advanceTimeBy(30); runCurrent() // The old timer would have fired now.
        assertEquals(setOf(2L, 3L), backend.activeSlots)
        advanceTimeBy(20); runCurrent()
        assertEquals(setOf(3L), backend.activeSlots)
    }

    @Test
    fun acceptedPromotionStaysAcknowledgedEvenWhenItsCoroutineIsSuperseded() = runTest {
        lateinit var controller: RadarRenderController
        var atSwap: RadarDisplayState? = null
        val backend = RecordingBackend(immediateCoverage = true, failOnRenderBarrier = true, afterPromotion = {
            if (it.id == 2L) {
                atSwap = controller.state.value
                controller.update(request(3), RadarProvider.LIBREWXR)
            }
        })
        controller = timedController(backend)
        controller.update(request(1), RadarProvider.LIBREWXR); runCurrent()
        controller.update(request(2), RadarProvider.LIBREWXR); runCurrent()
        advanceTimeBy(50); runCurrent()
        assertEquals(request(2).identity, atSwap?.displayed)
        assertEquals(request(2).identity, controller.state.value.displayed)
        assertEquals(request(3).identity, controller.state.value.requested)
        assertEquals(setOf(2L, 3L), backend.activeSlots)
        advanceTimeBy(50); runCurrent()
        assertEquals(request(3).identity, controller.state.value.displayed)
    }

    @Test
    fun partialPromotionAndFailedRollbackKeepBothLeasesUntilRetryRestoresFront() = runTest {
        val backend = RecordingBackend(immediateCoverage = true, partialPromotionFailures = setOf(2L),
            rollbackFailures = 1, failOnRenderBarrier = true)
        val controller = timedController(backend)
        controller.update(request(1), RadarProvider.LIBREWXR); runCurrent()
        controller.update(request(2), RadarProvider.LIBREWXR); runCurrent()
        advanceTimeBy(50); runCurrent()
        assertEquals(request(1).identity, controller.state.value.displayed)
        assertTrue(requireNotNull(controller.state.value.error).contains("restore failed"))
        assertEquals(setOf(1L, 2L), backend.activeSlots)
        controller.retry(); runCurrent()
        assertTrue(backend.operations.contains("rollback:2:1:0.75"))
        assertTrue(backend.operations.indexOf("remove:2") < backend.operations.indexOf("create:3"))
        assertEquals(0.75f, backend.opacities[1])
        advanceTimeBy(50); runCurrent()
        assertEquals(request(2).identity, controller.state.value.displayed)
    }

    @Test
    fun timedCoverageTimeoutAndRetryKeepTheFront() = runTest {
        val backend = RecordingBackend(failOnRenderBarrier = true)
        val controller = timedController(backend, timeoutMillis = 100)
        controller.update(request(1), RadarProvider.LIBREWXR); runCurrent(); backend.ready(1); runCurrent()
        controller.update(request(2), RadarProvider.LIBREWXR); runCurrent()
        advanceTimeBy(101); runCurrent()
        assertEquals(request(1).identity, controller.state.value.displayed)
        assertNotNull(controller.state.value.error)
        assertEquals(setOf(1L), backend.activeSlots)
        controller.retry(); runCurrent(); backend.ready(3); runCurrent()
        advanceTimeBy(50); runCurrent()
        assertEquals(request(2).identity, controller.state.value.displayed)
        assertNull(controller.state.value.error)
    }

    @Test
    fun failedRetirementNeverAllocatesThirdSlotOrClearsTheFront() = runTest {
        val backend = RecordingBackend(immediateCoverage = true, failOnRenderBarrier = true,
            removeFailures = mutableMapOf(1L to 2))
        val controller = timedController(backend, warmupMillis = 0)
        controller.update(request(1), RadarProvider.LIBREWXR); runCurrent()
        controller.update(request(2), RadarProvider.LIBREWXR); runCurrent()
        advanceTimeBy(50); runCurrent() // First removal fails in the retirement job.
        controller.update(request(3), RadarProvider.LIBREWXR); runCurrent() // Second removal fails.
        assertEquals(request(2).identity, controller.state.value.displayed)
        assertNotNull(controller.state.value.error)
        assertFalse(backend.operations.contains("create:3"))
        assertEquals(setOf(1L, 2L), backend.activeSlots)
        controller.retry(); runCurrent()
        assertEquals(request(3).identity, controller.state.value.displayed)
        assertTrue(backend.operations.indexOf("remove:1") < backend.operations.indexOf("create:3"))
    }

    @Test
    fun timedTransparentAndNoContentCoveragePromotesNormally() = runTest {
        val transparent = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR4nGNgAAIAAAUAAXpeqz8AAAAASUVORK5CYII="
        )
        val registry = RadarTileRegistry()
        val source = registry.register("https://tiles.example.test", request(2).config.frame)
        val scheduler = RadarNetworkScheduler(backgroundScope, { testScheduler.currentTime })
        val loader = RadarTileLoader(backgroundScope, scheduler, RadarTileTransport { key, _ ->
            if (key.coordinate.x == 1) RadarTileHttpResult(200, RadarTilePayload(transparent, 100_000))
            else RadarTileHttpResult(204, RadarTilePayload(null, 100_000))
        }, { testScheduler.currentTime })
        val keys = listOf(source.tile(RadarTileCoordinate(3, 1, 1)), source.tile(RadarTileCoordinate(3, 2, 1)))
        val backend = RecordingBackend(coverageAction = { loader.awaitCoverage(keys) }, failOnRenderBarrier = true)
        val controller = timedController(backend)
        controller.update(request(1), RadarProvider.LIBREWXR); runCurrent()
        controller.update(request(2), RadarProvider.LIBREWXR); runCurrent()
        advanceTimeBy(50); runCurrent()
        assertEquals(request(2).identity, controller.state.value.displayed)
        assertEquals(0f, backend.opacities[1])
        advanceTimeBy(50); runCurrent()
        assertEquals(setOf(2L), backend.activeSlots)
        registry.unregister(source)
    }

    @Test
    fun opacityZeroCancelsPreparationAndRestoreUsesCompatibleFrontWithoutDelay() = runTest {
        val backend = RecordingBackend(immediateCoverage = true, optionalSupport = true, failOnRenderBarrier = true)
        val controller = timedController(backend)
        controller.update(request(1), RadarProvider.LIBREWXR); runCurrent()
        controller.prepare(request(2)); runCurrent()
        controller.update(request(1, opacity = 0f), RadarProvider.LIBREWXR); runCurrent()
        assertEquals(setOf(1L), backend.activeSlots)
        assertEquals(0f, backend.opacities[1])
        assertFalse(controller.state.value.pending)
        controller.prepare(request(2), revision = 1); runCurrent()
        assertFalse(backend.operations.contains("create:3"))
        controller.update(request(1, opacity = 0.4f), RadarProvider.LIBREWXR); runCurrent()
        assertEquals(request(1).identity, controller.state.value.displayed)
        assertEquals(0.4f, backend.opacities[1])
    }

    @Test
    fun incompatibleViewportStyleProviderHideAndCloseFenceOptionalWork() = runTest {
        val moved = request(1).copy(coordinates = listOf(RadarTileCoordinate(3, 2, 2)))
        val targets = listOf(
            moved to RadarProvider.LIBREWXR,
            request(3, style = 2) to RadarProvider.LIBREWXR,
            request(3, provider = RadarProvider.RAINVIEWER) to RadarProvider.RAINVIEWER,
            null to RadarProvider.LIBREWXR
        )
        for ((target, selected) in targets) {
            val gate = CompletableDeferred<Unit>()
            val backend = RecordingBackend(immediateCoverage = true, optionalSupport = true,
                optionalCoverage = { withContext(NonCancellable) { gate.await() }; true }, failOnRenderBarrier = true)
            val controller = timedController(backend, warmupMillis = 0)
            controller.update(request(1), RadarProvider.LIBREWXR); runCurrent()
            controller.prepare(request(2)); runCurrent()
            controller.update(target, selected); runCurrent()
            gate.complete(Unit); runCurrent()
            assertFalse(backend.operations.contains("install:2:0.001"))
            assertEquals(target?.identity, controller.state.value.displayed)
            controller.close()
            assertTrue(backend.activeSlots.isEmpty())
            assertNull(controller.state.value.displayed)
        }
    }

    @Test
    fun manualSelectionCanAdoptAcceptedOptionalInstallBeforeItsContinuationResumes() = runTest {
        val gate = CompletableDeferred<Unit>()
        val backend = RecordingBackend(immediateCoverage = true, optionalSupport = true,
            afterInstallGate = 2L to gate, failOnRenderBarrier = true)
        val controller = timedController(backend)
        controller.update(request(1), RadarProvider.LIBREWXR); runCurrent()
        controller.prepare(request(2)); runCurrent()
        assertTrue(backend.operations.contains("install:2:0.001"))
        advanceTimeBy(50); runCurrent()
        controller.prepare(null)
        controller.update(request(2), RadarProvider.LIBREWXR); runCurrent()
        assertEquals(request(2).identity, controller.state.value.displayed)
        assertEquals(setOf(2L), backend.adoptedSlots)
        assertFalse(backend.operations.contains("remove:2"))
        gate.complete(Unit); runCurrent()
        assertFalse(backend.operations.contains("create:3"))
    }

    @Test
    fun alreadyRemovingOptionalSlotCannotBeClaimedByDemand() = runTest {
        val gate = CompletableDeferred<Unit>()
        val backend = RecordingBackend(immediateCoverage = true, optionalSupport = true,
            removeGate = 2L to gate, failOnRenderBarrier = true)
        val controller = timedController(backend)
        controller.update(request(1), RadarProvider.LIBREWXR); runCurrent()
        controller.prepare(request(2)); runCurrent()
        controller.prepare(null); runCurrent()
        assertTrue(backend.removeStarted.isCompleted)
        controller.update(request(2), RadarProvider.LIBREWXR); runCurrent()
        assertTrue(backend.adoptedSlots.isEmpty())
        assertFalse(backend.operations.contains("create:3"))
        gate.complete(Unit); runCurrent()
        assertTrue(backend.operations.indexOf("remove:2") < backend.operations.indexOf("create:3"))
        assertTrue(backend.adoptedSlots.isEmpty())
        advanceTimeBy(50); runCurrent()
        assertEquals(request(2).identity, controller.state.value.displayed)
    }

    @Test
    fun cancelledAcceptedPromotionStillRetiresWithoutAnotherRequest() = runTest {
        val backend = RecordingBackend(immediateCoverage = true, cancelAfterPromotion = setOf(2L),
            failOnRenderBarrier = true)
        val controller = timedController(backend)
        controller.update(request(1), RadarProvider.LIBREWXR); runCurrent()
        controller.update(request(2), RadarProvider.LIBREWXR); runCurrent()
        advanceTimeBy(50); runCurrent()
        assertEquals(request(2).identity, controller.state.value.displayed)
        assertNull(controller.state.value.error)
        assertEquals(setOf(1L, 2L), backend.activeSlots)
        advanceTimeBy(50); runCurrent()
        assertEquals(setOf(2L), backend.activeSlots)
    }

    @Test
    fun canonicalTileSetCanAdoptButChangedTileSetCannot() = runTest {
        val tiles = listOf(RadarTileCoordinate(3, 1, 1), RadarTileCoordinate(3, 2, 1))
        val backend = RecordingBackend(immediateCoverage = true, optionalSupport = true, failOnRenderBarrier = true)
        val controller = timedController(backend)
        val first = request(1).copy(coordinates = tiles)
        val second = request(2).copy(coordinates = tiles.reversed())
        controller.update(first, RadarProvider.LIBREWXR); runCurrent()
        controller.prepare(second); runCurrent()
        advanceTimeBy(50); runCurrent()
        controller.update(second.copy(coordinates = tiles), RadarProvider.LIBREWXR); runCurrent()
        assertEquals(setOf(2L), backend.adoptedSlots)
        assertEquals(second.identity, controller.state.value.displayed)
        advanceTimeBy(50); runCurrent()
        controller.prepare(request(3).copy(coordinates = tiles)); runCurrent()
        controller.update(request(3).copy(coordinates = listOf(tiles.first())), RadarProvider.LIBREWXR); runCurrent()
        assertTrue(backend.operations.contains("remove:3"))
        assertTrue(backend.operations.contains("create:4"))
        assertFalse(4L in backend.adoptedSlots)
        advanceTimeBy(50); runCurrent()
        assertEquals(request(3).identity, controller.state.value.displayed)
    }

    @Test
    fun timedCloseAndTimeoutInvalidateQueuedNativePromotion() = runTest {
        for (close in listOf(false, true)) {
            val gate = CompletableDeferred<Unit>()
            val backend = RecordingBackend(immediateCoverage = true, promotionGate = 2L to gate,
                failOnRenderBarrier = true)
            val controller = timedController(backend, timeoutMillis = 100)
            controller.update(request(1), RadarProvider.LIBREWXR); runCurrent()
            controller.update(request(2), RadarProvider.LIBREWXR); runCurrent()
            advanceTimeBy(50); runCurrent()
            val guard = requireNotNull(backend.lastPromotionGuard)
            if (close) controller.close() else { advanceTimeBy(51); runCurrent() }
            var applied = false
            assertFalse(guard { applied = true })
            gate.complete(Unit); runCurrent()
            assertFalse(applied)
            assertEquals(if (close) null else request(1).identity, controller.state.value.displayed)
            assertEquals(if (close) emptySet<Long>() else setOf(1L), backend.activeSlots)
            if (!close) assertNotNull(controller.state.value.error)
        }
    }

    @Test
    fun demandPreemptsUninterruptibleOptionalInstallAndItsOldGuardIsFenced() = runTest {
        val gate = CompletableDeferred<Unit>()
        val backend = RecordingBackend(immediateCoverage = true, optionalSupport = true,
            nonCancellableInstallGate = 2L to gate, failOnRenderBarrier = true)
        val controller = timedController(backend)
        controller.update(request(1), RadarProvider.LIBREWXR); runCurrent()
        controller.prepare(request(2)); runCurrent()
        val oldGuard = requireNotNull(backend.lastInstallGuard)
        controller.update(request(3), RadarProvider.LIBREWXR); runCurrent()
        assertEquals(setOf(1L, 3L), backend.activeSlots)
        var applied = false
        assertFalse(oldGuard { applied = true })
        gate.complete(Unit); runCurrent()
        assertFalse(applied)
        assertFalse(backend.operations.contains("install:2:0.001"))
        advanceTimeBy(50); runCurrent()
        assertEquals(request(3).identity, controller.state.value.displayed)
    }

    @Test
    fun hideAfterPartialPromotionNeverRestoresOldImageryWhenRemovalFails() = runTest {
        lateinit var controller: RadarRenderController
        val backend = RecordingBackend(immediateCoverage = true, failOnRenderBarrier = true,
            removeFailures = mutableMapOf(2L to 2), duringPromotion = {
                if (it.id == 2L) controller.update(null, RadarProvider.LIBREWXR)
            })
        controller = timedController(backend)
        controller.update(request(1), RadarProvider.LIBREWXR); runCurrent()
        controller.update(request(2), RadarProvider.LIBREWXR); runCurrent()
        advanceTimeBy(50); runCurrent()
        assertNull(controller.state.value.displayed)
        assertNull(controller.state.value.requested)
        assertFalse(backend.operations.any { it.startsWith("rollback:") })
        assertEquals(setOf(2L), backend.activeSlots)
        assertEquals(0f, backend.opacities[2])
        controller.update(request(3), RadarProvider.LIBREWXR); runCurrent()
        assertTrue(backend.operations.indexOf("remove:2") < backend.operations.indexOf("create:3"))
        assertEquals(request(3).identity, controller.state.value.displayed)
    }

    @Test
    fun failedOptionalRemovalBlocksAnotherAllocationUntilRetry() = runTest {
        var fresh = true
        val backend = RecordingBackend(immediateCoverage = true, optionalSupport = true,
            optionalCoverage = { fresh }, removeFailures = mutableMapOf(2L to 2), failOnRenderBarrier = true)
        val controller = timedController(backend)
        controller.update(request(1), RadarProvider.LIBREWXR); runCurrent()
        controller.prepare(request(2)); runCurrent()
        fresh = false
        controller.update(request(2), RadarProvider.LIBREWXR); runCurrent()
        assertEquals(request(1).identity, controller.state.value.displayed)
        assertNotNull(controller.state.value.error)
        assertEquals(setOf(1L, 2L), backend.activeSlots)
        assertFalse(backend.operations.contains("create:3"))
        controller.retry(); runCurrent()
        assertTrue(backend.operations.indexOf("remove:2") < backend.operations.indexOf("create:3"))
        advanceTimeBy(50); runCurrent()
        assertEquals(request(2).identity, controller.state.value.displayed)
    }

    @Test
    fun newerMatchingOpportunityDoesNotDiscardAnAlreadyWarmingSlot() = runTest {
        val backend = RecordingBackend(immediateCoverage = true, optionalSupport = true, failOnRenderBarrier = true)
        val controller = timedController(backend)
        controller.update(request(1), RadarProvider.LIBREWXR); runCurrent()
        controller.prepare(request(2), revision = 1); runCurrent()
        advanceTimeBy(20); runCurrent()
        controller.prepare(request(2), revision = 2); runCurrent()
        assertFalse(backend.operations.contains("remove:2"))
        advanceTimeBy(30); runCurrent()
        controller.update(request(2), RadarProvider.LIBREWXR); runCurrent()
        assertEquals(request(2).identity, controller.state.value.displayed)
        assertEquals(setOf(2L), backend.adoptedSlots)
        assertFalse(backend.operations.contains("create:3"))
    }

    @Test
    fun obsoleteOptionalCleanupCannotRemoveTheNextPreparedSlot() = runTest {
        val backend = RecordingBackend(immediateCoverage = true, optionalSupport = true, failOnRenderBarrier = true)
        val controller = timedController(backend)
        controller.update(request(1), RadarProvider.LIBREWXR); runCurrent()
        controller.prepare(request(2)); runCurrent()
        controller.prepare(null)
        controller.prepare(request(3), revision = 1)
        runCurrent()
        assertEquals(setOf(1L, 3L), backend.activeSlots)
        assertTrue(backend.operations.contains("install:3:0.001"))
        advanceTimeBy(50); runCurrent()
        controller.update(request(3), RadarProvider.LIBREWXR); runCurrent()
        assertEquals(request(3).identity, controller.state.value.displayed)
        assertEquals(setOf(3L), backend.adoptedSlots)
    }

    @Test
    fun nextPreparationStartsAfterRetirementWithoutChangingSelection() = runTest {
        val backend = RecordingBackend(immediateCoverage = true, optionalSupport = true, failOnRenderBarrier = true)
        val controller = timedController(backend, warmupMillis = 0)
        controller.update(request(1), RadarProvider.LIBREWXR); runCurrent()
        controller.update(request(2), RadarProvider.LIBREWXR); runCurrent()
        controller.prepare(request(3)); runCurrent()
        assertFalse(backend.operations.contains("create:3"))
        advanceTimeBy(50); runCurrent()
        assertTrue(backend.operations.contains("install:3:0.001"))
        assertEquals(request(2).identity, controller.state.value.displayed)
        assertEquals(request(2).identity, controller.state.value.requested)
        assertEquals(setOf(2L, 3L), backend.activeSlots)
    }

    @Test
    fun optionalRevalidationOrAdoptionErrorsFallBackToForegroundWithoutAVisibleError() = runTest {
        for (failAdoption in listOf(false, true)) {
            var checks = 0
            val backend = RecordingBackend(immediateCoverage = true, optionalSupport = true,
                optionalCoverage = {
                    checks++
                    if (!failAdoption && checks > 1) error("cache unavailable")
                    true
                }, adoptionFailure = failAdoption, failOnRenderBarrier = true)
            val controller = timedController(backend)
            controller.update(request(1), RadarProvider.LIBREWXR); runCurrent()
            controller.prepare(request(2)); runCurrent()
            advanceTimeBy(50); runCurrent()
            controller.update(request(2), RadarProvider.LIBREWXR); runCurrent()
            assertTrue(backend.operations.contains("remove:2"))
            assertTrue(backend.operations.contains("create:3"))
            assertNull(controller.state.value.error)
            assertEquals(request(1).identity, controller.state.value.displayed)
            advanceTimeBy(50); runCurrent()
            assertEquals(request(2).identity, controller.state.value.displayed)
            assertNull(controller.state.value.error)
        }
    }

    @Test
    fun unsupportedOptionalBackendFailsClosedAndDemandStillWorks() = runTest {
        val backend = RecordingBackend(immediateCoverage = true, failOnRenderBarrier = true)
        val controller = timedController(backend)
        controller.update(request(1), RadarProvider.LIBREWXR); runCurrent()
        controller.prepare(request(2)); runCurrent()
        assertEquals(setOf(1L), backend.activeSlots)
        assertFalse(backend.operations.contains("create:2"))
        controller.update(request(2), RadarProvider.LIBREWXR); runCurrent()
        advanceTimeBy(50); runCurrent()
        assertEquals(request(2).identity, controller.state.value.displayed)
    }

    @Test
    fun retirementDeadlineIsMeasuredFromGuardAcceptanceNotDelayedJobStart() = runTest {
        var clock = 0L
        val backend = RecordingBackend(immediateCoverage = true, failOnRenderBarrier = true,
            afterPromotion = { if (it.id == 2L) clock = 100L })
        val controller = RadarRenderController(backgroundScope, backend,
            timing = RadarHandoffTiming(warmupMillis = 0, nowMillis = { clock }))
        controller.update(request(1), RadarProvider.LIBREWXR); runCurrent()
        controller.update(request(2), RadarProvider.LIBREWXR); runCurrent()
        assertEquals(request(2).identity, controller.state.value.displayed)
        assertEquals(setOf(2L), backend.activeSlots)
    }

    @Test
    fun zeroOpacityAlsoHidesStagingWhenItsRemovalFails() = runTest {
        val backend = RecordingBackend(immediateCoverage = true, optionalSupport = true,
            removeFailures = mutableMapOf(2L to 2), failOnRenderBarrier = true)
        val controller = timedController(backend)
        controller.update(request(1), RadarProvider.LIBREWXR); runCurrent()
        controller.prepare(request(2)); runCurrent()
        controller.update(request(1, opacity = 0f), RadarProvider.LIBREWXR); runCurrent()
        assertEquals(setOf(1L, 2L), backend.activeSlots)
        assertEquals(0f, backend.opacities[1])
        assertEquals(0f, backend.opacities[2])
        assertFalse(controller.state.value.pending)
        controller.update(request(1, opacity = 0.4f), RadarProvider.LIBREWXR); runCurrent()
        assertEquals(setOf(1L), backend.activeSlots)
        assertEquals(0.4f, backend.opacities[1])
    }

    @Test
    fun reentrantNewSelectionRepairsPartiallyAppliedSwapBeforeAllocating() = runTest {
        lateinit var controller: RadarRenderController
        val backend = RecordingBackend(immediateCoverage = true, failOnRenderBarrier = true,
            duringPromotion = { if (it.id == 2L) controller.update(request(3), RadarProvider.LIBREWXR) })
        controller = timedController(backend)
        controller.update(request(1), RadarProvider.LIBREWXR); runCurrent()
        controller.update(request(2), RadarProvider.LIBREWXR); runCurrent()
        advanceTimeBy(50); runCurrent()
        assertEquals(request(1).identity, controller.state.value.displayed)
        assertEquals(request(3).identity, controller.state.value.requested)
        assertTrue(backend.operations.contains("rollback:2:1:0.75"))
        assertTrue(backend.operations.indexOf("remove:2") < backend.operations.indexOf("create:3"))
        advanceTimeBy(50); runCurrent()
        assertEquals(request(3).identity, controller.state.value.displayed)
        assertFalse(controller.state.value.pending)
    }

    @Test
    fun timedInvalidViewportRejectFencesQueuedSwapAndRetainsCompatibleFront() = runTest {
        val gate = CompletableDeferred<Unit>()
        val backend = RecordingBackend(immediateCoverage = true, promotionGate = 2L to gate,
            failOnRenderBarrier = true)
        val controller = timedController(backend)
        controller.update(request(1), RadarProvider.LIBREWXR); runCurrent()
        controller.update(request(2), RadarProvider.LIBREWXR); runCurrent()
        advanceTimeBy(50); runCurrent()
        val oldGuard = requireNotNull(backend.lastPromotionGuard)
        controller.reject(request(3).identity, RadarProvider.LIBREWXR, 1, "invalid viewport"); runCurrent()
        assertEquals(request(1).identity, controller.state.value.displayed)
        assertEquals("invalid viewport", controller.state.value.error)
        assertEquals(setOf(1L), backend.activeSlots)
        var applied = false
        assertFalse(oldGuard { applied = true })
        gate.complete(Unit); runCurrent()
        assertFalse(applied)
        assertEquals(request(1).identity, controller.state.value.displayed)
    }

    @Test
    fun zeroOpacityHidesBothLayersEvenIfPartialSwapRollbackStillFails() = runTest {
        val backend = RecordingBackend(immediateCoverage = true, partialPromotionFailures = setOf(2L),
            rollbackFailures = 3, failOnRenderBarrier = true)
        val controller = timedController(backend)
        controller.update(request(1), RadarProvider.LIBREWXR); runCurrent()
        controller.update(request(2), RadarProvider.LIBREWXR); runCurrent()
        advanceTimeBy(50); runCurrent()
        assertEquals(0.75f, backend.opacities[2])
        controller.update(request(2, opacity = 0f), RadarProvider.LIBREWXR); runCurrent()
        assertEquals(0f, backend.opacities[1])
        assertEquals(0f, backend.opacities[2])
        assertNotNull(controller.state.value.error)
        controller.update(request(2, opacity = 0.4f), RadarProvider.LIBREWXR); runCurrent()
        assertEquals(0.4f, backend.opacities[1])
        assertEquals(request(1).identity, controller.state.value.displayed)
        advanceTimeBy(49); runCurrent()
        assertEquals(request(1).identity, controller.state.value.displayed)
        advanceTimeBy(1); runCurrent()
        assertEquals(request(2).identity, controller.state.value.displayed)
    }

    @Test
    fun replacedSurfaceCannotBeChangedByOldSurfacePreparation() = runTest {
        val gate = CompletableDeferred<Unit>()
        val oldBackend = RecordingBackend(immediateCoverage = true, optionalSupport = true,
            optionalCoverage = { withContext(NonCancellable) { gate.await() }; true }, failOnRenderBarrier = true)
        val old = timedController(oldBackend)
        old.update(request(1), RadarProvider.LIBREWXR); runCurrent()
        old.prepare(request(2)); runCurrent()
        old.close()

        val newBackend = RecordingBackend(immediateCoverage = true, failOnRenderBarrier = true)
        val replacement = timedController(newBackend)
        replacement.update(request(3), RadarProvider.LIBREWXR); runCurrent()
        gate.complete(Unit); runCurrent()
        assertTrue(oldBackend.activeSlots.isEmpty())
        assertFalse(oldBackend.operations.contains("install:2:0.001"))
        assertEquals(request(3).identity, replacement.state.value.displayed)
        assertEquals(setOf(1L), newBackend.activeSlots)
    }

    @Test
    fun failedOptionalCleanupCannotInstallAfterItsLeaseWasMarkedUnusable() = runTest {
        val gate = CompletableDeferred<Unit>()
        val backend = RecordingBackend(immediateCoverage = true, optionalSupport = true,
            optionalCoverage = { withContext(NonCancellable) { gate.await() }; true },
            paintFailures = setOf(1L to 0.4f), removeFailures = mutableMapOf(2L to 1), failOnRenderBarrier = true)
        val controller = timedController(backend)
        controller.update(request(1), RadarProvider.LIBREWXR); runCurrent()
        controller.prepare(request(2)); runCurrent()
        controller.update(request(1, opacity = 0.4f), RadarProvider.LIBREWXR); runCurrent()
        assertNotNull(controller.state.value.error)
        assertEquals(setOf(1L, 2L), backend.activeSlots)
        gate.complete(Unit); runCurrent()
        assertFalse(backend.operations.contains("install:2:0.001"))
        assertEquals(setOf(1L), backend.activeSlots)
        assertEquals(request(1).identity, controller.state.value.displayed)
    }

    private fun TestScope.timedController(
        backend: RecordingBackend,
        timeoutMillis: Long = 45_000,
        warmupMillis: Long = 50
    ) = RadarRenderController(backgroundScope, backend, timeoutMillis = timeoutMillis,
        timing = RadarHandoffTiming(warmupMillis = warmupMillis, nowMillis = { testScheduler.currentTime }))

    private fun request(
        time: Long,
        opacity: Float = 0.75f,
        style: Long = 1,
        provider: RadarProvider = RadarProvider.LIBREWXR
    ) = RadarRenderRequest(
        WeatherMapRadarConfig("https://tiles.example.test", RadarFrame(time, "/v2/radar/$time", RadarFrameType.OBSERVED, provider), opacity),
        listOf(RadarTileCoordinate(3, 1, 1)), style
    )

    private class RecordingBackend(
        private val immediateCoverage: Boolean = false,
        private val coverageAction: (suspend () -> Unit)? = null,
        private val rollbackGate: CompletableDeferred<Unit>? = null,
        private var rollbackFailures: Int = 0,
        private val installFailures: Set<Long> = emptySet(),
        private val partialPromotionFailures: Set<Long> = emptySet(),
        private val cancelAfterPromotion: Set<Long> = emptySet(),
        private val installGate: Pair<Long, CompletableDeferred<Unit>>? = null,
        private val promotionGate: Pair<Long, CompletableDeferred<Unit>>? = null,
        private val afterPromotionGate: Pair<Long, CompletableDeferred<Unit>>? = null,
        private val duringPromotion: ((RadarRenderSlot) -> Unit)? = null,
        private val afterPromotion: ((RadarRenderSlot) -> Unit)? = null,
        private val failOnRenderBarrier: Boolean = false,
        private val optionalSupport: Boolean = false,
        private val optionalCoverage: (suspend (RadarRenderSlot) -> Boolean)? = null,
        private val adoptionFailure: Boolean = false,
        private val afterInstallGate: Pair<Long, CompletableDeferred<Unit>>? = null,
        private val nonCancellableInstallGate: Pair<Long, CompletableDeferred<Unit>>? = null,
        private val removeGate: Pair<Long, CompletableDeferred<Unit>>? = null,
        private val paintFailures: Set<Pair<Long, Float>> = emptySet(),
        val removeFailures: MutableMap<Long, Int> = mutableMapOf()
    ) : RadarRenderBackend {
        val operations = mutableListOf<String>()
        val opacities = mutableMapOf<Long, Float>()
        val activeSlots = mutableSetOf<Long>()
        val optionalSlots = mutableSetOf<Long>()
        val adoptedSlots = mutableSetOf<Long>()
        val cacheChecks = mutableListOf<Long>()
        val rollbackStarted = CompletableDeferred<Unit>()
        val removeStarted = CompletableDeferred<Unit>()
        var lastInstallGuard: RadarMutationGuard? = null
        var lastPromotionGuard: RadarMutationGuard? = null
        private val coverage = mutableMapOf<Long, CompletableDeferred<Unit>>()
        private val renders = mutableMapOf<Long, Channel<Result<Unit>>>()
        override fun create(slot: RadarRenderSlot) {
            check(activeSlots.size < 2) { "Allocated a third radar slot" }
            operations += "create:${slot.id}"
            activeSlots += slot.id
            coverage[slot.id] = CompletableDeferred<Unit>().also { if (immediateCoverage) it.complete(Unit) }
            renders[slot.id] = Channel(Channel.RENDEZVOUS)
        }
        override fun createOptional(slot: RadarRenderSlot): Boolean {
            if (!optionalSupport) return false
            create(slot)
            optionalSlots += slot.id
            return true
        }
        override suspend fun hasCachedCoverage(slot: RadarRenderSlot): Boolean {
            cacheChecks += slot.id
            return optionalCoverage?.invoke(slot) ?: true
        }
        override suspend fun adoptOptional(slot: RadarRenderSlot, guard: RadarMutationGuard): Boolean = guard {
            check(!adoptionFailure) { "optional adoption failed" }
            check(slot.id in optionalSlots)
            operations += "adopt:${slot.id}"
            adoptedSlots += slot.id
        }
        override suspend fun install(slot: RadarRenderSlot, opacity: Float, guard: RadarMutationGuard) {
            lastInstallGuard = guard
            installGate?.takeIf { it.first == slot.id }?.second?.await()
            nonCancellableInstallGate?.takeIf { it.first == slot.id }?.second?.let {
                withContext(NonCancellable) { it.await() }
            }
            if (!guard {
                    check(slot.id !in installFailures) { "installation failed" }
                    operations += "install:${slot.id}:$opacity"
                    opacities[slot.id] = opacity
                }) {
                throw CancellationException("Obsolete radar installation")
            }
            afterInstallGate?.takeIf { it.first == slot.id }?.second?.await()
        }
        override suspend fun paint(slot: RadarRenderSlot, opacity: Float, guard: RadarMutationGuard) {
            guard {
                check(slot.id to opacity !in paintFailures) { "paint failed" }
                operations += "paint:${slot.id}:$opacity"; opacities[slot.id] = opacity
            }
        }
        override suspend fun promote(next: RadarRenderSlot, previous: RadarRenderSlot?, opacity: Float, guard: RadarMutationGuard) {
            lastPromotionGuard = guard
            promotionGate?.takeIf { it.first == next.id }?.second?.await()
            val applied = guard {
                operations += "promote:${next.id}:${previous?.id}"
                opacities[next.id] = opacity
                previous?.let { opacities[it.id] = 0f }
                duringPromotion?.invoke(next)
                check(next.id !in partialPromotionFailures) { "partial promotion failed" }
            }
            if (!applied) throw CancellationException("Obsolete radar promotion")
            afterPromotion?.invoke(next)
            if (next.id in cancelAfterPromotion) {
                currentCoroutineContext().cancel()
                currentCoroutineContext().ensureActive()
            }
            afterPromotionGate?.takeIf { it.first == next.id }?.second?.await()
        }
        override suspend fun rollback(failed: RadarRenderSlot, previous: RadarRenderSlot?, opacity: Float, guard: RadarMutationGuard) {
            val accepted = withContext(NonCancellable) {
                rollbackStarted.complete(Unit)
                rollbackGate?.await()
                guard {
                    if (rollbackFailures > 0) {
                        rollbackFailures--
                        throw IllegalStateException("native rollback unavailable")
                    }
                    operations += "rollback:${failed.id}:${previous?.id}:$opacity"
                    previous?.let { opacities[it.id] = opacity }
                    opacities[failed.id] = 0f
                }
            }
            if (!accepted) throw CancellationException("Obsolete radar rollback")
        }
        override suspend fun awaitCoverage(slot: RadarRenderSlot) {
            if (coverageAction != null) coverageAction.invoke() else coverage.getValue(slot.id).await()
        }
        override suspend fun awaitFullRender(slot: RadarRenderSlot, viewport: RadarRenderRequest, guard: RadarMutationGuard) {
            check(!failOnRenderBarrier) { "Default path must not wait for a full-map render" }
            renders.getValue(slot.id).receive().getOrThrow()
            guard { operations += "full:${slot.id}" }
        }
        override suspend fun remove(slot: RadarRenderSlot) {
            removeGate?.takeIf { it.first == slot.id }?.second?.let {
                removeStarted.complete(Unit)
                withContext(NonCancellable) { it.await() }
            }
            val remaining = removeFailures[slot.id] ?: 0
            if (remaining > 0) {
                removeFailures[slot.id] = remaining - 1
                throw IllegalStateException("removal failed")
            }
            operations += "remove:${slot.id}"
            opacities.remove(slot.id)
            activeSlots.remove(slot.id)
            optionalSlots.remove(slot.id)
        }
        fun ready(id: Long) { coverage.getValue(id).complete(Unit) }
        fun fail(id: Long) { coverage.getValue(id).completeExceptionally(IllegalStateException("tile failed")) }
        fun render(id: Long) { renders.getValue(id).trySend(Result.success(Unit)) }
        fun failRender(id: Long) { renders.getValue(id).trySend(Result.failure(IllegalStateException("render failed"))) }
    }
}
