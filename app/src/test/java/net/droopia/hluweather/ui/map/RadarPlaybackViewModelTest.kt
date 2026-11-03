@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package net.droopia.hluweather.ui.map

import androidx.lifecycle.viewModelScope
import java.time.ZoneId
import kotlin.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import net.droopia.hluweather.data.model.RadarFrame
import net.droopia.hluweather.data.model.RadarFrameIdentity
import net.droopia.hluweather.data.model.RadarFrameType
import net.droopia.hluweather.data.model.RadarMetadata
import net.droopia.hluweather.data.model.RadarProvider
import net.droopia.hluweather.data.model.RadarTileCoordinate
import net.droopia.hluweather.data.repository.RadarMetadataState
import net.droopia.hluweather.data.repository.RadarRepository
import org.maplibre.compose.map.MapEvent
import org.maplibre.compose.map.RenderStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarPlaybackViewModelTest {
    @Test
    fun selects_latest_observation_and_reconciles_selection_on_metadata_refresh() = runTest {
        val repository = TestRadarRepository(backgroundScope)
        withViewModel(repositories(repository)) { viewModel ->
            val observed1 = frame("2026-11-01T20:00:00Z")
            val observed2 = frame("2026-11-01T20:10:00Z")
            val forecast = frame("2026-11-01T20:20:00Z", RadarFrameType.FORECAST)

            viewModel.setVisible(true)
            repository.publish(metadata(observed1, observed2, forecast))
            runCurrent()

            assertEquals(observed2, viewModel.state.value.requestedFrame)
            assertTrue(viewModel.state.value.isPending)
            acknowledge(viewModel)
            viewModel.selectFrame(forecast.timestampSeconds)
            assertEquals(forecast, viewModel.state.value.requestedFrame)
            assertFalse(viewModel.state.value.isPlaying)

            val laterObserved = frame("2026-11-01T20:30:00Z")
            repository.publish(metadata(observed1, observed2, forecast, laterObserved))
            runCurrent()
            assertEquals(forecast, viewModel.state.value.requestedFrame)

            repository.publish(metadata(observed1, observed2, laterObserved))
            runCurrent()
            assertEquals(laterObserved, viewModel.state.value.requestedFrame)
            assertTrue(viewModel.state.value.isPending)
        }
    }

    @Test
    fun labels_requested_forecast_in_local_time_with_offset_from_latest_observation() = runTest {
        val repository = TestRadarRepository(backgroundScope)
        withViewModel(repositories(repository), timeZone = { ZoneId.of("Europe/Belgrade") }) { viewModel ->
            val observed = frame("2026-11-01T20:10:00Z")
            val forecast = frame("2026-11-01T20:25:00Z", RadarFrameType.FORECAST)
            viewModel.setVisible(true)
            repository.publish(metadata(observed, forecast))
            runCurrent()

            assertEquals(RadarFrameType.OBSERVED, viewModel.state.value.requestedFrameType)
            assertEquals("Observed", viewModel.state.value.requestedFrameTypeLabel)
            assertEquals("Sun, Nov 1, 2026 • 21:10", viewModel.state.value.requestedFrameTimeLabel)
            assertNull(viewModel.state.value.forecastOffsetLabel)

            viewModel.selectFrame(forecast.timestampSeconds)
            assertEquals(RadarFrameType.FORECAST, viewModel.state.value.requestedFrameType)
            assertEquals("Forecast", viewModel.state.value.requestedFrameTypeLabel)
            assertEquals("Sun, Nov 1, 2026 • 21:25", viewModel.state.value.requestedFrameTimeLabel)
            assertEquals(900L, viewModel.state.value.forecastOffsetSeconds)
            assertEquals("+15 min", viewModel.state.value.forecastOffsetLabel)
        }
    }

    @Test
    fun empty_and_past_only_metadata_do_not_invent_forecasts_or_observations() = runTest {
        val libre = TestRadarRepository(backgroundScope)
        val rainViewer = TestRadarRepository(backgroundScope)
        withViewModel(repositories(libre, rainViewer)) { viewModel ->
            viewModel.setVisible(true)
            val observation = frame("2026-11-01T20:00:00Z", provider = RadarProvider.LIBREWXR)
            libre.publish(metadata(observation))
            runCurrent()
            assertEquals(listOf(observation), viewModel.state.value.observedFrames)
            assertTrue(viewModel.state.value.forecastFrames.isEmpty())
            assertNull(viewModel.state.value.forecastOffsetLabel)

            viewModel.selectProvider(RadarProvider.RAINVIEWER)
            val past = frame("2026-11-01T20:05:00Z", provider = RadarProvider.RAINVIEWER)
            rainViewer.publish(metadata(RadarProvider.RAINVIEWER, past))
            runCurrent()
            assertEquals(past, viewModel.state.value.requestedFrame)
            assertTrue(viewModel.state.value.forecastFrames.isEmpty())
            assertFalse(viewModel.state.value.hasForecastFrames)

            rainViewer.publish(metadata(RadarProvider.RAINVIEWER))
            runCurrent()
            assertTrue(viewModel.state.value.frames.isEmpty())
            assertNull(viewModel.state.value.requestedFrame)
            assertNull(viewModel.state.value.requestedFrameTimeLabel)

            rainViewer.publish(metadata(
                RadarProvider.RAINVIEWER,
                frame("2026-11-01T20:10:00Z", RadarFrameType.FORECAST, RadarProvider.RAINVIEWER)
            ))
            runCurrent()
            assertNull(viewModel.state.value.latestObservedTimestampSeconds)
            assertNull(viewModel.state.value.requestedFrame)
            assertEquals(1, viewModel.state.value.forecastFrames.size)
        }
    }

    @Test
    fun playback_waits_for_each_display_ack_and_does_not_starve_a_slow_frame() = runTest {
        val repository = TestRadarRepository(backgroundScope)
        withViewModel(repositories(repository)) { viewModel ->
            val observed1 = frame("2026-11-01T20:00:00Z")
            val observed2 = frame("2026-11-01T20:10:00Z")
            val forecast = frame("2026-11-01T20:20:00Z", RadarFrameType.FORECAST)
            viewModel.setVisible(true)
            repository.publish(metadata(observed1, observed2, forecast))
            runCurrent()
            acknowledge(viewModel)

            viewModel.play()
            runCurrent()
            advanceTimeBy(RADAR_PLAYBACK_INTERVAL_MILLIS - 1)
            runCurrent()
            assertEquals(observed2, viewModel.state.value.requestedFrame)

            advanceTimeBy(1)
            runCurrent()
            assertEquals(forecast, viewModel.state.value.requestedFrame)
            assertTrue(viewModel.state.value.isPending)
            advanceTimeBy(RADAR_PLAYBACK_INTERVAL_MILLIS * 10)
            runCurrent()
            assertEquals(forecast, viewModel.state.value.requestedFrame)
            assertEquals(observed2, viewModel.state.value.displayedFrame)
            assertTrue(viewModel.state.value.isPlaying)

            acknowledge(viewModel)
            advanceTimeBy(RADAR_PLAYBACK_INTERVAL_MILLIS - 1)
            runCurrent()
            assertEquals(forecast, viewModel.state.value.requestedFrame)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(observed1, viewModel.state.value.requestedFrame)
            assertTrue(viewModel.state.value.isPending)
            viewModel.pause()
        }
    }

    @Test
    fun playback_waits_for_controller_coverage_and_viewport_acceptance_without_render_events() = runTest {
        val repository = TestRadarRepository(backgroundScope)
        withViewModel(repositories(repository)) { viewModel ->
            val observed = frame("2026-11-01T20:00:00Z")
            val forecast = frame("2026-11-01T20:10:00Z", RadarFrameType.FORECAST)
            val metadata = metadata(observed, forecast)
            val backend = PlaybackBackend()
            val controller = RadarRenderController(backgroundScope, backend)
            val initialCoordinates = listOf(RadarTileCoordinate(3, 1, 1))
            backgroundScope.launch { controller.state.collect(viewModel::onDisplayChanged) }
            backgroundScope.launch {
                viewModel.state.map { it.requestedFrameIdentity }.distinctUntilChanged().collect { identity ->
                    if (identity != null) {
                        val selected = metadata.frames.single { it.identity(metadata.host) == identity }
                        controller.update(
                            RadarRenderRequest(WeatherMapRadarConfig(metadata.host, selected), initialCoordinates, 1),
                            identity.provider
                        )
                    }
                }
            }
            viewModel.setVisible(true)
            repository.publish(metadata)
            runCurrent()
            viewModel.play(); runCurrent()
            advanceTimeBy(RADAR_PLAYBACK_INTERVAL_MILLIS * 3); runCurrent()
            assertEquals(observed, viewModel.state.value.requestedFrame)
            assertTrue(viewModel.state.value.isPending)

            backend.ready(1); runCurrent()
            assertEquals(observed, viewModel.state.value.displayedFrame)
            assertFalse(viewModel.state.value.isPending)
            // The identity is unchanged, but a new viewport still needs its own accepted coverage.
            controller.update(
                RadarRenderRequest(
                    WeatherMapRadarConfig(metadata.host, observed),
                    listOf(RadarTileCoordinate(3, 2, 2)), 1
                ), RadarProvider.LIBREWXR
            )
            runCurrent()
            assertEquals(observed, viewModel.state.value.displayedFrame)
            assertTrue(viewModel.state.value.isPending)
            advanceTimeBy(RADAR_PLAYBACK_INTERVAL_MILLIS * 3); runCurrent()
            assertEquals(observed, viewModel.state.value.requestedFrame)
            backend.ready(2); runCurrent()
            assertFalse(viewModel.state.value.isPending)
            advanceTimeBy(RADAR_PLAYBACK_INTERVAL_MILLIS); runCurrent()
            assertEquals(forecast, viewModel.state.value.requestedFrame)
            assertTrue(viewModel.state.value.isPending)
            backend.ready(3); runCurrent()
            assertEquals(forecast, viewModel.state.value.displayedFrame)
            assertFalse(viewModel.state.value.isPending)
            assertEquals(setOf(3L), backend.activeSlots)
            viewModel.pause()
            controller.close()
            assertTrue(backend.activeSlots.isEmpty())
        }
    }

    @Test
    fun prepared_frame_does_not_change_selection_and_uses_only_the_accepted_600ms_interval() = runTest {
        val repository = TestRadarRepository(backgroundScope)
        withViewModel(repositories(repository)) { viewModel ->
            val observed1 = frame("2026-11-01T20:00:00Z")
            val observed2 = frame("2026-11-01T20:10:00Z")
            val forecast = frame("2026-11-01T20:20:00Z", RadarFrameType.FORECAST)
            val timeline = metadata(observed1, observed2, forecast)
            val backend = PlaybackBackend(optionalSupport = true)
            val controller = RadarRenderController(
                backgroundScope,
                backend,
                timing = RadarHandoffTiming(nowMillis = { testScheduler.currentTime })
            )
            val coordinates = listOf(RadarTileCoordinate(3, 1, 1))
            backgroundScope.launch { controller.state.collect(viewModel::onDisplayChanged) }
            backgroundScope.launch {
                viewModel.state.map { it.requestedFrameIdentity }.distinctUntilChanged().collect { identity ->
                    if (identity != null) {
                        val selected = timeline.frames.single { it.identity(timeline.host) == identity }
                        controller.update(
                            RadarRenderRequest(
                                WeatherMapRadarConfig(timeline.host, selected), coordinates, styleEpoch = 1
                            ),
                            identity.provider
                        )
                    }
                }
            }

            viewModel.setVisible(true)
            repository.publish(timeline)
            runCurrent()
            assertEquals(observed2, viewModel.state.value.requestedFrame)
            backend.ready(1)
            runCurrent()
            assertEquals(observed2, viewModel.state.value.displayedFrame)

            viewModel.play()
            runCurrent()
            val playbackBeforePreparation = viewModel.state.value
            val displayBeforePreparation = controller.state.value
            controller.prepare(
                RadarRenderRequest(WeatherMapRadarConfig(timeline.host, forecast), coordinates, styleEpoch = 1),
                revision = 1
            )
            runCurrent()

            assertEquals(playbackBeforePreparation, viewModel.state.value)
            assertEquals(displayBeforePreparation, controller.state.value)
            assertEquals(setOf(1L, 2L), backend.activeSlots)
            assertEquals(1, backend.installCount[2L])

            val renderEvents = RadarFullRenderEvents()
            renderEvents.record(renderEvent(RenderStats.Mode.Partial, frameCount = 20))
            assertEquals(0L, renderEvents.ordinal.value)
            advanceTimeBy(RADAR_PLAYBACK_INTERVAL_MILLIS - 1)
            runCurrent()
            assertEquals(observed2, viewModel.state.value.requestedFrame)

            // This older, uncorrelated Full event is diagnostic only; it neither selects nor
            // acknowledges the prepared frame.
            renderEvents.record(renderEvent(RenderStats.Mode.Full, frameCount = 19))
            assertEquals(1L, renderEvents.ordinal.value)
            assertEquals(observed2, viewModel.state.value.requestedFrame)
            assertEquals(observed2, viewModel.state.value.displayedFrame)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(forecast, viewModel.state.value.requestedFrame)
            assertEquals(forecast, viewModel.state.value.displayedFrame)
            assertEquals(setOf(2L), backend.adoptedSlots)
            assertEquals(1, backend.installCount[2L])

            // Partial and stale Full diagnostics after acceptance do not restart the interval.
            renderEvents.record(renderEvent(RenderStats.Mode.Partial, frameCount = 21))
            renderEvents.record(renderEvent(RenderStats.Mode.Full, frameCount = 18))
            assertEquals(forecast, viewModel.state.value.requestedFrame)
            assertEquals(forecast, viewModel.state.value.displayedFrame)
            advanceTimeBy(RADAR_PLAYBACK_INTERVAL_MILLIS - 1)
            runCurrent()
            assertEquals(forecast, viewModel.state.value.requestedFrame)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(observed1, viewModel.state.value.requestedFrame)
            assertEquals(forecast, viewModel.state.value.displayedFrame)
            assertTrue(viewModel.state.value.isPending)
            controller.close()
        }
    }

    @Test
    fun manual_selection_pauses_playback_and_adopts_a_matching_prepared_frame() = runTest {
        val repository = TestRadarRepository(backgroundScope)
        withViewModel(repositories(repository)) { viewModel ->
            val observed = frame("2026-11-01T20:10:00Z")
            val forecast = frame("2026-11-01T20:20:00Z", RadarFrameType.FORECAST)
            val timeline = metadata(observed, forecast)
            val backend = PlaybackBackend(optionalSupport = true)
            val controller = RadarRenderController(
                backgroundScope,
                backend,
                timing = RadarHandoffTiming(nowMillis = { testScheduler.currentTime })
            )
            val coordinates = listOf(RadarTileCoordinate(3, 1, 1))
            backgroundScope.launch { controller.state.collect(viewModel::onDisplayChanged) }
            backgroundScope.launch {
                viewModel.state.map { it.requestedFrameIdentity }.distinctUntilChanged().collect { identity ->
                    if (identity != null) {
                        val selected = timeline.frames.single { it.identity(timeline.host) == identity }
                        controller.update(
                            RadarRenderRequest(
                                WeatherMapRadarConfig(timeline.host, selected), coordinates, styleEpoch = 1
                            ),
                            identity.provider
                        )
                    }
                }
            }

            viewModel.setVisible(true)
            repository.publish(timeline)
            runCurrent()
            backend.ready(1)
            runCurrent()
            viewModel.play()
            runCurrent()

            controller.prepare(
                RadarRenderRequest(WeatherMapRadarConfig(timeline.host, forecast), coordinates, styleEpoch = 1),
                revision = 1
            )
            runCurrent()
            advanceTimeBy(100)
            runCurrent()
            assertEquals(observed, viewModel.state.value.requestedFrame)
            assertEquals(observed, viewModel.state.value.displayedFrame)
            assertTrue(viewModel.state.value.isPlaying)

            viewModel.selectFrame(forecast.timestampSeconds)
            runCurrent()

            assertFalse(viewModel.state.value.isPlaying)
            assertEquals(forecast, viewModel.state.value.requestedFrame)
            assertEquals(forecast, viewModel.state.value.displayedFrame)
            assertEquals(setOf(2L), backend.adoptedSlots)
            assertEquals(1, backend.installCount[2L])
            controller.close()
        }
    }

    @Test
    fun failed_frame_pauses_and_play_retries_without_losing_the_displayed_frame() = runTest {
        val repository = TestRadarRepository(backgroundScope)
        withViewModel(repositories(repository)) { viewModel ->
            val observed = frame("2026-11-01T20:00:00Z")
            val forecast = frame("2026-11-01T20:10:00Z", RadarFrameType.FORECAST)
            viewModel.setVisible(true)
            repository.publish(metadata(observed, forecast))
            runCurrent()
            acknowledge(viewModel)

            viewModel.play()
            runCurrent()
            advanceTimeBy(RADAR_PLAYBACK_INTERVAL_MILLIS)
            runCurrent()
            val requested = requireNotNull(viewModel.state.value.requestedFrameIdentity)
            viewModel.onDisplayChanged(
                RadarDisplayState(
                    requested = requested,
                    displayed = viewModel.state.value.displayedFrameIdentity,
                    pending = false,
                    error = "tile request failed"
                )
            )

            assertFalse(viewModel.state.value.isPlaying)
            assertFalse(viewModel.state.value.isPending)
            assertEquals("tile request failed", viewModel.state.value.displayError)
            assertEquals(observed, viewModel.state.value.displayedFrame)
            val retryToken = viewModel.state.value.displayRetryToken

            viewModel.play()
            assertTrue(viewModel.state.value.isPlaying)
            assertTrue(viewModel.state.value.isPending)
            assertNull(viewModel.state.value.displayError)
            assertEquals(retryToken + 1, viewModel.state.value.displayRetryToken)
            acknowledge(viewModel)
            advanceTimeBy(RADAR_PLAYBACK_INTERVAL_MILLIS)
            runCurrent()
            assertEquals(observed, viewModel.state.value.requestedFrame)
        }
    }

    @Test
    fun provider_switch_clears_old_state_and_guards_coincident_timestamps_and_late_callbacks() = runTest {
        val libre = TestRadarRepository(backgroundScope)
        val rainViewer = TestRadarRepository(backgroundScope)
        withViewModel(repositories(libre, rainViewer)) { viewModel ->
            val libreFrame = frame("2026-11-01T20:00:00Z", provider = RadarProvider.LIBREWXR)
            val rainFrame = frame("2026-11-01T20:00:00Z", provider = RadarProvider.RAINVIEWER)
            viewModel.setVisible(true)
            libre.publish(metadata(libreFrame))
            runCurrent()
            acknowledge(viewModel)
            viewModel.play()

            viewModel.selectProvider(RadarProvider.RAINVIEWER)
            assertFalse(viewModel.state.value.isPlaying)
            assertNull(viewModel.state.value.metadata)
            assertNull(viewModel.state.value.requestedFrame)
            assertNull(viewModel.state.value.displayedFrame)
            runCurrent()
            assertEquals(0, libre.activeCollectors)
            assertEquals(1, rainViewer.activeCollectors)

            rainViewer.publish(metadata(RadarProvider.RAINVIEWER, rainFrame))
            runCurrent()
            val rainIdentity = requireNotNull(viewModel.state.value.requestedFrameIdentity)
            assertEquals(RadarProvider.RAINVIEWER, rainIdentity.provider)
            assertEquals(libreFrame.timestampSeconds, rainIdentity.timestampSeconds)
            assertTrue(viewModel.state.value.isPending)

            viewModel.onDisplayChanged(
                RadarDisplayState(
                    requested = libreFrame.identity(libre.currentMetadataHost),
                    displayed = libreFrame.identity(libre.currentMetadataHost),
                    pending = false
                )
            )
            libre.publish(metadata(frame("2026-11-01T20:30:00Z", provider = RadarProvider.LIBREWXR)))
            runCurrent()

            assertEquals(RadarProvider.RAINVIEWER, viewModel.state.value.selectedProvider)
            assertEquals(rainFrame, viewModel.state.value.requestedFrame)
            assertEquals(rainIdentity, viewModel.state.value.requestedFrameIdentity)
            assertNull(viewModel.state.value.displayedFrame)
        }
    }

    @Test
    fun late_same_provider_render_ack_is_ignored_after_metadata_changes_the_request() = runTest {
        val repository = TestRadarRepository(backgroundScope)
        withViewModel(repositories(repository)) { viewModel ->
            val first = frame("2026-11-01T20:00:00Z")
            val second = frame("2026-11-01T20:10:00Z")
            viewModel.setVisible(true)
            repository.publish(metadata(first))
            runCurrent()
            val oldIdentity = requireNotNull(viewModel.state.value.requestedFrameIdentity)

            repository.publish(metadata(second))
            runCurrent()
            val newIdentity = requireNotNull(viewModel.state.value.requestedFrameIdentity)
            assertNotEquals(oldIdentity, newIdentity)
            viewModel.onDisplayChanged(
                RadarDisplayState(oldIdentity, oldIdentity, pending = false)
            )

            assertEquals(newIdentity, viewModel.state.value.requestedFrameIdentity)
            assertNull(viewModel.state.value.displayedFrameIdentity)
            assertTrue(viewModel.state.value.isPending)
        }
    }

    @Test
    fun leaving_map_cancels_collection_and_playback_and_ignores_late_display_callbacks() = runTest {
        val repository = TestRadarRepository(backgroundScope)
        withViewModel(repositories(repository)) { viewModel ->
            val observed1 = frame("2026-11-01T20:00:00Z")
            val observed2 = frame("2026-11-01T20:10:00Z")
            val forecast = frame("2026-11-01T20:20:00Z", RadarFrameType.FORECAST)
            repository.publish(metadata(observed1, observed2, forecast))

            viewModel.setVisible(true)
            runCurrent()
            assertEquals(1, repository.activeCollectors)
            acknowledge(viewModel)
            viewModel.play()
            advanceTimeBy(RADAR_PLAYBACK_INTERVAL_MILLIS)
            runCurrent()
            val selectedBeforeLeaving = viewModel.state.value.requestedFrame
            val identityBeforeLeaving = viewModel.state.value.requestedFrameIdentity

            viewModel.setVisible(false)
            runCurrent()
            assertEquals(0, repository.activeCollectors)
            assertFalse(viewModel.state.value.isVisible)
            assertFalse(viewModel.state.value.isPlaying)
            assertNull(viewModel.state.value.displayedFrame)

            identityBeforeLeaving?.let { identity ->
                viewModel.onDisplayChanged(RadarDisplayState(identity, identity, pending = false))
            }
            advanceTimeBy(RADAR_PLAYBACK_INTERVAL_MILLIS * 2)
            runCurrent()
            assertEquals(selectedBeforeLeaving, viewModel.state.value.requestedFrame)
            assertEquals(0, repository.activeCollectors)
        }
    }

    @Test
    fun changing_provider_while_hidden_does_not_collect_until_map_becomes_visible() = runTest {
        val libre = TestRadarRepository(backgroundScope)
        val rainViewer = TestRadarRepository(backgroundScope)
        withViewModel(repositories(libre, rainViewer)) { viewModel ->
            val rainFrame = frame("2026-11-01T20:00:00Z", provider = RadarProvider.RAINVIEWER)

            viewModel.selectProvider(RadarProvider.RAINVIEWER)
            runCurrent()
            assertEquals(0, libre.activeCollectors)
            assertEquals(0, rainViewer.activeCollectors)
            rainViewer.publish(metadata(RadarProvider.RAINVIEWER, rainFrame))
            runCurrent()
            assertNull(viewModel.state.value.metadata)

            viewModel.setVisible(true)
            runCurrent()
            assertEquals(0, libre.activeCollectors)
            assertEquals(1, rainViewer.activeCollectors)
            assertEquals(rainFrame, viewModel.state.value.requestedFrame)
        }
    }

    private suspend fun TestScope.withViewModel(
        repositories: Map<RadarProvider, RadarRepository>,
        timeZone: () -> ZoneId = { ZoneId.of("UTC") },
        test: suspend TestScope.(RadarPlaybackViewModel) -> Unit
    ) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = RadarPlaybackViewModel(repositories, timeZone)
        try {
            test(viewModel)
        } finally {
            viewModel.viewModelScope.cancel()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    private fun TestScope.repositories(
        libre: TestRadarRepository,
        rainViewer: TestRadarRepository = TestRadarRepository(backgroundScope)
    ): Map<RadarProvider, RadarRepository> = mapOf(
        RadarProvider.LIBREWXR to libre,
        RadarProvider.RAINVIEWER to rainViewer
    )

    private fun frame(
        timestamp: String,
        type: RadarFrameType = RadarFrameType.OBSERVED,
        provider: RadarProvider = RadarProvider.LIBREWXR
    ): RadarFrame {
        val seconds = java.time.Instant.parse(timestamp).epochSecond
        return RadarFrame(
            timestampSeconds = seconds,
            path = "/v2/radar/$seconds",
            type = type,
            provider = provider
        )
    }

    private fun metadata(vararg frames: RadarFrame) = metadata(RadarProvider.LIBREWXR, *frames)

    private fun metadata(provider: RadarProvider, vararg frames: RadarFrame) = RadarMetadata.normalized(
        provider = provider,
        host = if (provider == RadarProvider.RAINVIEWER) {
            "https://tiles.rainviewer.example.test"
        } else {
            "https://tiles.librewxr.example.test"
        },
        frames = frames.toList()
    )

    private fun acknowledge(
        viewModel: RadarPlaybackViewModel,
        requested: RadarFrameIdentity? = viewModel.state.value.requestedFrameIdentity,
        displayed: RadarFrameIdentity? = requested
    ) {
        viewModel.onDisplayChanged(
            RadarDisplayState(requested = requested, displayed = displayed, pending = displayed != requested)
        )
    }

    private class PlaybackBackend(
        private val optionalSupport: Boolean = false
    ) : RadarRenderBackend {
        val activeSlots = mutableSetOf<Long>()
        val optionalSlots = mutableSetOf<Long>()
        val adoptedSlots = mutableSetOf<Long>()
        val installCount = mutableMapOf<Long, Int>()
        private val coverage = mutableMapOf<Long, CompletableDeferred<Unit>>()
        override fun create(slot: RadarRenderSlot) {
            activeSlots += slot.id
            coverage[slot.id] = CompletableDeferred()
        }
        override suspend fun install(slot: RadarRenderSlot, opacity: Float, guard: RadarMutationGuard) {
            check(guard {
                installCount[slot.id] = (installCount[slot.id] ?: 0) + 1
            })
        }
        override suspend fun paint(slot: RadarRenderSlot, opacity: Float, guard: RadarMutationGuard) {
            check(guard {})
        }
        override suspend fun promote(
            next: RadarRenderSlot, previous: RadarRenderSlot?, opacity: Float, guard: RadarMutationGuard
        ) {
            check(guard {})
        }
        override suspend fun rollback(
            failed: RadarRenderSlot, previous: RadarRenderSlot?, opacity: Float, guard: RadarMutationGuard
        ) = error("Default accepted promotions must not be rolled back")
        override suspend fun awaitCoverage(slot: RadarRenderSlot) = coverage.getValue(slot.id).await()
        override suspend fun awaitFullRender(
            slot: RadarRenderSlot, viewport: RadarRenderRequest, guard: RadarMutationGuard
        ) = error("Default playback must not wait for full-map render events")
        override fun createOptional(slot: RadarRenderSlot): Boolean {
            if (!optionalSupport) return false
            create(slot)
            optionalSlots += slot.id
            return true
        }
        override suspend fun hasCachedCoverage(slot: RadarRenderSlot): Boolean = slot.id in optionalSlots
        override suspend fun adoptOptional(slot: RadarRenderSlot, guard: RadarMutationGuard): Boolean = guard {
            check(slot.id in optionalSlots)
            adoptedSlots += slot.id
        }
        override suspend fun remove(slot: RadarRenderSlot) {
            activeSlots.remove(slot.id)
            optionalSlots.remove(slot.id)
        }
        fun ready(id: Long) { coverage.getValue(id).complete(Unit) }
    }

    private fun renderEvent(mode: RenderStats.Mode, frameCount: Long) = MapEvent.FrameRendered(
        RenderStats(mode, false, false, Duration.ZERO, Duration.ZERO, frameCount, 0, 0)
    )

    private class TestRadarRepository(scope: CoroutineScope) : RadarRepository {
        private val updates = MutableStateFlow(RadarMetadataState())
        var activeCollectors = 0
            private set
        var currentMetadataHost: String = "https://tiles.librewxr.example.test"
            private set

        override val state: StateFlow<RadarMetadataState> = updates
            .onStart { activeCollectors++ }
            .onCompletion { activeCollectors-- }
            .stateIn(
                scope,
                SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0),
                RadarMetadataState()
            )

        fun publish(metadata: RadarMetadata) {
            currentMetadataHost = metadata.host
            updates.value = RadarMetadataState(metadata = metadata, isLoading = false)
        }
    }
}
