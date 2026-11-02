@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package net.droopia.hluweather.ui.map

import kotlin.time.Duration
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.maplibre.compose.map.MapEvent
import org.maplibre.compose.map.RenderStats

class RadarFullRenderEventsTest {
    @Test
    fun onlyLaterFullEventsAdvanceAcrossNativeFrameCounterReset() = runTest {
        val events = RadarFullRenderEvents()
        events.record(render(RenderStats.Mode.Full, 101))
        val barrier = events.ordinal.value
        val next = async { events.ordinal.first { it > barrier } }
        runCurrent()
        events.record(render(RenderStats.Mode.Partial, 1))
        events.record(MapEvent.Idle)
        events.record(MapEvent.FrameRendered(null))
        runCurrent()
        assertFalse(next.isCompleted)
        events.record(render(RenderStats.Mode.Full, 6))
        assertEquals(2L, next.await())
        val secondBarrier = events.ordinal.value
        val afterBarrier = async { events.ordinal.first { it > secondBarrier } }
        runCurrent()
        assertFalse(afterBarrier.isCompleted)
        events.record(render(RenderStats.Mode.Full, 6))
        assertEquals(3L, afterBarrier.await())
    }

    private fun render(mode: RenderStats.Mode, count: Long) = MapEvent.FrameRendered(
        RenderStats(mode, false, false, Duration.ZERO, Duration.ZERO, count, 0, 0)
    )
}
