@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package net.droopia.hluweather.ui.map

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.advanceTimeBy
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
        val controller = RadarRenderController(backgroundScope, backend)
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
        val controller = RadarRenderController(backgroundScope, backend)
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
        val controller = RadarRenderController(backgroundScope, backend, timeoutMillis = 100)
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
        val controller = RadarRenderController(backgroundScope, backend, timeoutMillis = 100)
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
        val controller = RadarRenderController(backgroundScope, backend)
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
        val controller = RadarRenderController(backgroundScope, backend, timeoutMillis = 100)
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
        val controller = RadarRenderController(backgroundScope, backend, timeoutMillis = 100)
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
        val controller = RadarRenderController(backgroundScope, backend, timeoutMillis = 100)
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
        val controller = RadarRenderController(backgroundScope, backend, timeoutMillis = 100)
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
        val controller = RadarRenderController(backgroundScope, backend)
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
        val controller = RadarRenderController(backgroundScope, backend)
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
        val controller = RadarRenderController(backgroundScope, backend)
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
        val controller = RadarRenderController(backgroundScope, backend)
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
        val controller = RadarRenderController(backgroundScope, backend, timeoutMillis = 100)
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
        val controller = RadarRenderController(backgroundScope, backend, timeoutMillis = 100)
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
        val controller = RadarRenderController(backgroundScope, backend)
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
        val controller = RadarRenderController(backgroundScope, backend)
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
    fun realTransparentAndNoContentCoverageIsValidButDoesNotBypassRenderBarriers() = runTest {
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
        val backend = RecordingBackend(coverageAction = { loader.awaitCoverage(keys) })
        val controller = RadarRenderController(backgroundScope, backend)
        controller.update(request(1), RadarProvider.LIBREWXR)
        runCurrent()
        assertEquals("App-cache coverage must not repeat the transport", 2, calls)
        assertNull(controller.state.value.displayed)
        backend.render(1); runCurrent()
        assertNull(controller.state.value.displayed)
        backend.render(1); runCurrent()
        assertEquals(request(1).identity, controller.state.value.displayed)
        registry.unregister(source)
    }

    @Test
    fun zeroOpacityHidesImmediatelyAndPositiveRestorationStagesAgain() = runTest {
        val backend = RecordingBackend(immediateCoverage = true)
        val controller = RadarRenderController(backgroundScope, backend)
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
        val controller = RadarRenderController(backgroundScope, backend)
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
        val controller = RadarRenderController(backgroundScope, backend)
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
        val controller = RadarRenderController(backgroundScope, backend)
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
        val controller = RadarRenderController(backgroundScope, backend)
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
        val controller = RadarRenderController(backgroundScope, backend)
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

    private fun request(time: Long, opacity: Float = 0.75f, style: Long = 1) = RadarRenderRequest(
        WeatherMapRadarConfig("https://tiles.example.test", RadarFrame(time, "/v2/radar/$time", RadarFrameType.OBSERVED), opacity),
        listOf(RadarTileCoordinate(3, 1, 1)), style
    )

    private class RecordingBackend(
        private val immediateCoverage: Boolean = false,
        private val coverageAction: (suspend () -> Unit)? = null,
        private val rollbackGate: CompletableDeferred<Unit>? = null,
        private var rollbackFailures: Int = 0
    ) : RadarRenderBackend {
        val operations = mutableListOf<String>()
        val opacities = mutableMapOf<Long, Float>()
        val rollbackStarted = CompletableDeferred<Unit>()
        var lastInstallGuard: RadarMutationGuard? = null
        private val coverage = mutableMapOf<Long, CompletableDeferred<Unit>>()
        private val renders = mutableMapOf<Long, Channel<Result<Unit>>>()
        override fun create(slot: RadarRenderSlot) {
            operations += "create:${slot.id}"
            coverage[slot.id] = CompletableDeferred<Unit>().also { if (immediateCoverage) it.complete(Unit) }
            renders[slot.id] = Channel(Channel.RENDEZVOUS)
        }
        override suspend fun install(slot: RadarRenderSlot, opacity: Float, guard: RadarMutationGuard) {
            lastInstallGuard = guard
            guard { operations += "install:${slot.id}:$opacity"; opacities[slot.id] = opacity }
        }
        override suspend fun paint(slot: RadarRenderSlot, opacity: Float, guard: RadarMutationGuard) {
            guard { operations += "paint:${slot.id}:$opacity"; opacities[slot.id] = opacity }
        }
        override suspend fun promote(next: RadarRenderSlot, previous: RadarRenderSlot?, opacity: Float, guard: RadarMutationGuard) {
            guard {
                operations += "promote:${next.id}:${previous?.id}"
                opacities[next.id] = opacity
                previous?.let { opacities[it.id] = 0f }
            }
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
            renders.getValue(slot.id).receive().getOrThrow()
            guard { operations += "full:${slot.id}" }
        }
        override suspend fun remove(slot: RadarRenderSlot) { operations += "remove:${slot.id}"; opacities.remove(slot.id) }
        fun ready(id: Long) { coverage.getValue(id).complete(Unit) }
        fun fail(id: Long) { coverage.getValue(id).completeExceptionally(IllegalStateException("tile failed")) }
        fun render(id: Long) { renders.getValue(id).trySend(Result.success(Unit)) }
        fun failRender(id: Long) { renders.getValue(id).trySend(Result.failure(IllegalStateException("render failed"))) }
    }
}
