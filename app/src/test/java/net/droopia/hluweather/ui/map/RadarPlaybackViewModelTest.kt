@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package net.droopia.hluweather.ui.map

import androidx.lifecycle.viewModelScope
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import net.droopia.hluweather.data.network.LibreWxrRadarFrame
import net.droopia.hluweather.data.network.LibreWxrRadarFrameType
import net.droopia.hluweather.data.network.LibreWxrRadarMetadata
import net.droopia.hluweather.data.repository.RadarMetadataState
import net.droopia.hluweather.data.repository.RadarRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarPlaybackViewModelTest {
    @Test
    fun selects_latest_observation_and_reconciles_selection_by_timestamp_on_refresh() = runTest {
        val repository = TestRadarRepository(backgroundScope)
        withViewModel(repository) { viewModel ->
            val observed1 = frame("2026-11-01T20:00:00Z", LibreWxrRadarFrameType.OBSERVED)
            val observed2 = frame("2026-11-01T20:10:00Z", LibreWxrRadarFrameType.OBSERVED)
            val forecast = frame("2026-11-01T20:20:00Z", LibreWxrRadarFrameType.FORECAST)

            viewModel.setVisible(true)
            repository.publish(metadata(observed1, observed2, forecast))
            runCurrent()

            assertEquals(observed2.timestampSeconds, viewModel.state.value.selectedFrame?.timestampSeconds)
            viewModel.selectFrame(forecast.timestampSeconds)
            assertEquals(forecast.timestampSeconds, viewModel.state.value.selectedFrame?.timestampSeconds)

            val laterObserved = frame("2026-11-01T20:30:00Z", LibreWxrRadarFrameType.OBSERVED)
            repository.publish(metadata(observed1, observed2, forecast, laterObserved))
            runCurrent()
            assertEquals(forecast.timestampSeconds, viewModel.state.value.selectedFrame?.timestampSeconds)

            repository.publish(metadata(observed1, observed2, laterObserved))
            runCurrent()
            assertEquals(laterObserved.timestampSeconds, viewModel.state.value.selectedFrame?.timestampSeconds)
        }
    }

    @Test
    fun labels_selected_frame_in_injected_local_zone_and_classifies_forecast_offset() = runTest {
        val repository = TestRadarRepository(backgroundScope)
        withViewModel(repository, timeZone = { ZoneId.of("Europe/Belgrade") }) { viewModel ->
            val observed = frame("2026-11-01T20:10:00Z", LibreWxrRadarFrameType.OBSERVED)
            val forecast = frame("2026-11-01T20:25:00Z", LibreWxrRadarFrameType.FORECAST)
            viewModel.setVisible(true)
            repository.publish(metadata(observed, forecast))
            runCurrent()

            assertEquals(LibreWxrRadarFrameType.OBSERVED, viewModel.state.value.selectedFrameType)
            assertEquals("Observed", viewModel.state.value.selectedFrameTypeLabel)
            assertEquals("Sun, Nov 1, 2026 • 21:10", viewModel.state.value.selectedFrameTimeLabel)
            assertNull(viewModel.state.value.forecastOffsetLabel)

            viewModel.selectFrame(forecast.timestampSeconds)
            assertEquals(LibreWxrRadarFrameType.FORECAST, viewModel.state.value.selectedFrameType)
            assertEquals("Forecast", viewModel.state.value.selectedFrameTypeLabel)
            assertEquals("Sun, Nov 1, 2026 • 21:25", viewModel.state.value.selectedFrameTimeLabel)
            assertEquals(900L, viewModel.state.value.forecastOffsetSeconds)
            assertEquals("+15 min", viewModel.state.value.forecastOffsetLabel)
        }
    }

    @Test
    fun observations_without_forecasts_and_empty_metadata_have_no_invented_forecast_labels() = runTest {
        val repository = TestRadarRepository(backgroundScope)
        withViewModel(repository) { viewModel ->
            val observed = frame("2026-11-01T20:00:00Z", LibreWxrRadarFrameType.OBSERVED)
            viewModel.setVisible(true)
            repository.publish(metadata(observed))
            runCurrent()

            assertEquals(listOf(observed), viewModel.state.value.observedFrames)
            assertTrue(viewModel.state.value.forecastFrames.isEmpty())
            assertFalse(viewModel.state.value.hasForecastFrames)
            assertEquals("Observed", viewModel.state.value.selectedFrameTypeLabel)
            assertNull(viewModel.state.value.forecastOffsetLabel)

            repository.publish(metadata())
            runCurrent()
            assertTrue(viewModel.state.value.frames.isEmpty())
            assertNull(viewModel.state.value.selectedFrame)
            assertNull(viewModel.state.value.selectedFrameTimeLabel)
            assertNull(viewModel.state.value.selectedFrameTypeLabel)
            assertNull(viewModel.state.value.forecastOffsetLabel)
            viewModel.play()
            assertFalse(viewModel.state.value.isPlaying)
        }
    }

    @Test
    fun playback_advances_every_600_milliseconds_and_loops_over_available_frames() = runTest {
        val repository = TestRadarRepository(backgroundScope)
        withViewModel(repository) { viewModel ->
            val observed1 = frame("2026-11-01T20:00:00Z", LibreWxrRadarFrameType.OBSERVED)
            val observed2 = frame("2026-11-01T20:10:00Z", LibreWxrRadarFrameType.OBSERVED)
            val forecast = frame("2026-11-01T20:20:00Z", LibreWxrRadarFrameType.FORECAST)
            viewModel.setVisible(true)
            repository.publish(metadata(observed1, observed2, forecast))
            runCurrent()
            viewModel.play()

            advanceTimeBy(RADAR_PLAYBACK_INTERVAL_MILLIS - 1)
            runCurrent()
            assertEquals(observed2.timestampSeconds, viewModel.state.value.selectedFrame?.timestampSeconds)

            advanceTimeBy(1)
            runCurrent()
            assertEquals(forecast.timestampSeconds, viewModel.state.value.selectedFrame?.timestampSeconds)

            advanceTimeBy(RADAR_PLAYBACK_INTERVAL_MILLIS)
            runCurrent()
            assertEquals(observed1.timestampSeconds, viewModel.state.value.selectedFrame?.timestampSeconds)

            viewModel.pause()
            advanceTimeBy(RADAR_PLAYBACK_INTERVAL_MILLIS * 2)
            runCurrent()
            assertEquals(observed1.timestampSeconds, viewModel.state.value.selectedFrame?.timestampSeconds)
            assertFalse(viewModel.state.value.isPlaying)
        }
    }

    @Test
    fun leaving_map_cancels_repository_collection_and_playback() = runTest {
        val repository = TestRadarRepository(backgroundScope)
        withViewModel(repository) { viewModel ->
            val observed1 = frame("2026-11-01T20:00:00Z", LibreWxrRadarFrameType.OBSERVED)
            val observed2 = frame("2026-11-01T20:10:00Z", LibreWxrRadarFrameType.OBSERVED)
            val forecast = frame("2026-11-01T20:20:00Z", LibreWxrRadarFrameType.FORECAST)
            repository.publish(metadata(observed1, observed2, forecast))

            viewModel.setVisible(true)
            runCurrent()
            assertEquals(1, repository.activeCollectors)
            viewModel.play()
            advanceTimeBy(RADAR_PLAYBACK_INTERVAL_MILLIS)
            runCurrent()
            val selectedBeforeLeaving = viewModel.state.value.selectedFrame?.timestampSeconds
            assertEquals(forecast.timestampSeconds, selectedBeforeLeaving)

            viewModel.setVisible(false)
            runCurrent()
            assertEquals(0, repository.activeCollectors)
            assertFalse(viewModel.state.value.isVisible)
            assertFalse(viewModel.state.value.isPlaying)

            advanceTimeBy(RADAR_PLAYBACK_INTERVAL_MILLIS * 2)
            runCurrent()
            assertEquals(selectedBeforeLeaving, viewModel.state.value.selectedFrame?.timestampSeconds)
            assertEquals(0, repository.activeCollectors)
        }
    }

    private suspend fun TestScope.withViewModel(
        repository: RadarRepository,
        timeZone: () -> ZoneId = { ZoneId.of("UTC") },
        test: suspend TestScope.(RadarPlaybackViewModel) -> Unit
    ) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = RadarPlaybackViewModel(repository, timeZone)
        try {
            test(viewModel)
        } finally {
            viewModel.viewModelScope.cancel()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    private fun frame(timestamp: String, type: LibreWxrRadarFrameType): LibreWxrRadarFrame {
        val seconds = java.time.Instant.parse(timestamp).epochSecond
        return LibreWxrRadarFrame(seconds, "/v2/radar/$seconds", type)
    }

    private fun metadata(vararg frames: LibreWxrRadarFrame) = LibreWxrRadarMetadata(
        host = "https://api.librewxr.net",
        frames = frames.toList()
    )

    private class TestRadarRepository(scope: CoroutineScope) : RadarRepository {
        private val updates = MutableStateFlow(RadarMetadataState())
        var activeCollectors = 0
            private set

        override val state: StateFlow<RadarMetadataState> = updates
            .onStart { activeCollectors++ }
            .onCompletion { activeCollectors-- }
            .stateIn(
                scope,
                SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0),
                RadarMetadataState()
            )

        fun publish(metadata: LibreWxrRadarMetadata) {
            updates.value = RadarMetadataState(metadata = metadata, isLoading = false)
        }
    }
}
