@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package net.droopia.hluweather.ui.map

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.unit.dp
import java.io.IOException
import java.util.TimeZone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.droopia.hluweather.ComposeTestActivity
import net.droopia.hluweather.data.model.ActiveLocation
import net.droopia.hluweather.data.model.ForecastMode
import net.droopia.hluweather.data.model.RadarFrame
import net.droopia.hluweather.data.model.RadarFrameType
import net.droopia.hluweather.data.model.RadarProvider
import net.droopia.hluweather.data.model.WeatherProvider
import net.droopia.hluweather.data.network.LibreWxrRadarFrame
import net.droopia.hluweather.data.network.LibreWxrRadarFrameType
import net.droopia.hluweather.data.network.LibreWxrRadarMetadata
import net.droopia.hluweather.data.network.RadarApiException
import net.droopia.hluweather.data.repository.ForecastLoad
import net.droopia.hluweather.data.repository.MockWeatherRepository
import net.droopia.hluweather.data.repository.RadarMetadataState
import net.droopia.hluweather.data.repository.RadarRepository
import net.droopia.hluweather.data.repository.Svilajnac
import net.droopia.hluweather.data.repository.WeatherRepository
import net.droopia.hluweather.ui.theme.HluWeatherTheme
import net.droopia.hluweather.ui.weather.WeatherScreen
import net.droopia.hluweather.ui.weather.WeatherViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RadarMapOverlayTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeTestActivity>()
    private val radarViewModels = mutableListOf<RadarPlaybackViewModel>()
    private val trackingRadarRepositories = mutableListOf<TrackingRadarRepository>()

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun teardown() {
        radarViewModels.forEach { it.setVisible(false) }
        trackingRadarRepositories.forEach(TrackingRadarRepository::close)
        Dispatchers.resetMain()
    }

    @Test
    fun layer_menu_exposes_visibility_and_seekable_zero_to_one_hundred_opacity() {
        val playback = playbackViewModel()
        playback.setVisible(true)
        val enabled = mutableStateOf(true)
        val opacity = mutableFloatStateOf(0.75f)
        setOverlayContent(playback, enabled, opacity, darkTheme = false)

        composeRule.onNodeWithContentDescription("Radar layer options")
            .assertContentDescriptionEquals("Radar layer options")
            .assertHasClickAction()
            .performClick()
        composeRule.onNodeWithText("Radar layers").assertIsDisplayed()
        composeRule.onNodeWithTag("radar_layer_visibility").assertIsDisplayed()
        composeRule.onNodeWithText("75%").assertIsDisplayed()

        composeRule.onNodeWithTag("radar_opacity_slider")
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                assertTrue(setProgress(0.42f))
            }

        assertEquals(0.42f, opacity.floatValue, 0.011f)
        composeRule.onNodeWithTag("radar_opacity_value").assertIsDisplayed()
        composeRule.onNodeWithText("42%").assertIsDisplayed()

        composeRule.onNodeWithTag("radar_layer_visibility").performClick()
        assertFalse(enabled.value)
    }

    @Test
    fun layer_provider_choice_works_when_radar_is_disabled_without_changing_other_settings() {
        val playback = playbackViewModel()
        val enabled = mutableStateOf(false)
        val opacity = mutableFloatStateOf(0.42f)
        val selectedProvider = mutableStateOf(RadarProvider.LIBREWXR)
        var providerChanges = 0
        setOverlayContent(
            playback,
            enabled,
            opacity,
            darkTheme = false,
            selectedProvider = selectedProvider,
            onProviderChange = { providerChanges++ }
        )

        assertTagAbsent("radar_timeline")
        composeRule.onNodeWithTag("radar_layer_options").performClick()
        composeRule.onNodeWithText("Radar provider").assertIsDisplayed()
        composeRule.onNodeWithTag("radar_layer_provider_librewxr")
            .assertIsDisplayed()
            .assertIsSelected()
        composeRule.onNodeWithTag("radar_layer_provider_rainviewer")
            .assertIsDisplayed()
            .assertIsNotSelected()

        composeRule.onNodeWithTag("radar_layer_provider_rainviewer").performClick()

        assertEquals(1, providerChanges)
        assertEquals(RadarProvider.RAINVIEWER, selectedProvider.value)
        assertFalse(enabled.value)
        assertEquals(0.42f, opacity.floatValue, 0f)
        composeRule.onNodeWithTag("radar_layer_provider_rainviewer").assertIsSelected()
        composeRule.onNodeWithText("42%").assertIsDisplayed()
        assertTagAbsent("radar_timeline")
    }

    @Test
    fun timeline_seeks_frames_classifies_forecasts_and_controls_playback() {
        val playback = playbackViewModel()
        playback.setVisible(true)
        val enabled = mutableStateOf(true)
        val opacity = mutableFloatStateOf(0.75f)
        setOverlayContent(playback, enabled, opacity, darkTheme = false)

        composeRule.onNodeWithText("Observed").assertIsDisplayed()
        composeRule.onNodeWithText("Sun, Nov 1, 2026 • 20:10").assertIsDisplayed()
        composeRule.onNodeWithTag("radar_latest_observed_marker").assertIsDisplayed()
        composeRule.onNodeWithTag("radar_timeline_slider")
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                assertTrue(setProgress(2f))
            }

        composeRule.onNodeWithText("Forecast +10 min").assertIsDisplayed()
        composeRule.onNodeWithText("Sun, Nov 1, 2026 • 20:20").assertIsDisplayed()
        composeRule.onNodeWithTag("radar_timeline_slider").run {
            val range = fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
            assertEquals(0f..2f, range.range)
            assertEquals(2f, range.current, 0f)
        }

        composeRule.onNodeWithContentDescription("Play radar playback").performClick()
        assertTrue(playback.state.value.isPlaying)
        composeRule.onNodeWithContentDescription("Pause radar playback").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Pause radar playback").performClick()
        assertFalse(playback.state.value.isPlaying)
    }

    @Test
    fun pending_frame_keeps_timeline_text_stable_and_failures_still_show() {
        val playback = playbackViewModel()
        playback.setVisible(true)
        val state = playback.state.value
        val requested = requireNotNull(state.requestedFrameIdentity)
        val shown = requireNotNull(state.metadata).frames.first().identity(state.metadata.host)
        playback.onDisplayChanged(RadarDisplayState(requested, shown, pending = true))
        val enabled = mutableStateOf(true)
        val opacity = mutableFloatStateOf(0.75f)
        setOverlayContent(playback, enabled, opacity, darkTheme = false)

        composeRule.onNodeWithText("Loading radar frame…").assertDoesNotExist()
        composeRule.onNodeWithText("Observed").assertIsDisplayed()
        composeRule.onNodeWithText("Sun, Nov 1, 2026 • 20:10").assertIsDisplayed()
        composeRule.onNodeWithTag("radar_requested_time").assertIsDisplayed()
        composeRule.onNodeWithTag("radar_displayed_time").assertDoesNotExist()

        playback.selectFrame(playback.state.value.frames.last().timestampSeconds)
        val forecastIdentity = requireNotNull(playback.state.value.requestedFrameIdentity)
        playback.onDisplayChanged(
            RadarDisplayState(
                requested = forecastIdentity,
                displayed = shown,
                pending = false,
                error = "HTTP 429: rate limit reached"
            )
        )
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Radar rate limit reached").assertIsDisplayed()
        composeRule.onNodeWithText("Forecast +10 min").assertIsDisplayed()
        composeRule.onNodeWithText("Sun, Nov 1, 2026 • 20:20").assertIsDisplayed()
        playback.onDisplayChanged(
            RadarDisplayState(
                requested = forecastIdentity,
                displayed = shown,
                pending = false,
                error = "Connection reset"
            )
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Radar network unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("Sun, Nov 1, 2026 • 20:20").assertIsDisplayed()
        val retryToken = playback.state.value.displayRetryToken
        composeRule.onNodeWithContentDescription("Play radar playback").performClick()
        assertTrue(playback.state.value.displayRetryToken > retryToken)
        assertTrue(playback.state.value.isPending)
    }

    @Test
    fun disabling_radar_hides_timeline_but_keeps_accessible_map_toggle() {
        val playback = playbackViewModel()
        playback.setVisible(true)
        val enabled = mutableStateOf(true)
        val opacity = mutableFloatStateOf(0.75f)
        setOverlayContent(playback, enabled, opacity, darkTheme = false)

        composeRule.onNodeWithTag("radar_timeline").assertIsDisplayed()
        composeRule.onNodeWithTag("radar_visibility_toggle")
            .assertHeightIsAtLeast(48.dp)
            .assertHasClickAction()
        composeRule.onNodeWithContentDescription("Radar visibility")
            .assertContentDescriptionEquals("Radar visibility")
            .performClick()

        assertFalse(enabled.value)
        assertTagAbsent("radar_timeline")
        composeRule.onNodeWithTag("radar_layer_options").assertIsDisplayed()
    }

    @Test
    fun status_and_attribution_are_nonblocking_and_required_credits_are_visible() {
        val emptyRepository = TestRadarRepository(
            RadarMetadataState(isLoading = true)
        )
        val playback = own(RadarPlaybackViewModel(emptyRepository))
        playback.setVisible(true)
        val enabled = mutableStateOf(true)
        val opacity = mutableFloatStateOf(0.75f)
        setOverlayContent(playback, enabled, opacity, darkTheme = false)

        composeRule.onNodeWithText("Loading radar…").assertIsDisplayed()
        composeRule.onNodeWithTag("radar_attribution").assertIsDisplayed()
        composeRule.onNodeWithText("Weather radar via LibreWXR (librewxr.net)", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("Upstream may include", substring = true)
            .assertDoesNotExist()
        composeRule.onNodeWithText("EUMETNET OPERA", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(
            "Precipitation data from NOAA Enterprise Rain Rate (RRQPE)",
            substring = true
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Radar-DPC (Italy; CC BY-SA 4.0 where included)", substring = true)
            .assertIsDisplayed()
        assertTagAbsent("radar_timeline_slider")

        emptyRepository.updates.value = RadarMetadataState(isLoading = false)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("No radar frames").assertIsDisplayed()

        emptyRepository.updates.value = RadarMetadataState(error = IllegalStateException("offline"))
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Radar unavailable").assertIsDisplayed()
        composeRule.onNodeWithTag("radar_attribution").assertIsDisplayed()
        composeRule.onNodeWithText("Radar-DPC (Italy; CC BY-SA 4.0 where included)", substring = true)
            .assertIsDisplayed()

        emptyRepository.updates.value = RadarMetadataState(error = IOException("offline"))
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Radar network unavailable").assertIsDisplayed()

        emptyRepository.updates.value = RadarMetadataState(
            error = RadarApiException("Too many requests", statusCode = 429)
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Radar rate limit reached").assertIsDisplayed()
    }

    @Test
    fun provider_source_links_are_accessible_and_open_their_descriptor_urls() {
        val playback = playbackViewModel()
        val enabled = mutableStateOf(true)
        val opacity = mutableFloatStateOf(0.75f)
        val selectedProvider = mutableStateOf(RadarProvider.LIBREWXR)
        val uriHandler = RecordingUriHandler()
        setOverlayContent(
            playback,
            enabled,
            opacity,
            darkTheme = false,
            selectedProvider = selectedProvider,
            uriHandler = uriHandler
        )

        composeRule.onNodeWithText("Weather radar via LibreWXR (librewxr.net)").assertIsDisplayed()
        composeRule.onNodeWithText("EUMETNET OPERA", substring = true).assertIsDisplayed()
        assertSourceLink(
            label = "LibreWXR (librewxr.net)",
            url = "https://librewxr.net/",
            uriHandler = uriHandler
        )

        selectedProvider.value = RadarProvider.RAINVIEWER
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Weather radar via RainViewer").assertIsDisplayed()
        composeRule.onNodeWithText("EUMETNET OPERA", substring = true).assertDoesNotExist()
        assertSourceLink(
            label = "RainViewer",
            url = "https://www.rainviewer.com/",
            uriHandler = uriHandler
        )
    }

    @Test
    fun attribution_tracks_retained_displayed_provider_then_falls_back_when_no_frame_is_shown() {
        val playback = playbackViewModel()
        val enabled = mutableStateOf(true)
        val opacity = mutableFloatStateOf(0.75f)
        val selectedProvider = mutableStateOf(RadarProvider.RAINVIEWER)
        val attributionState = mutableStateOf(
            attributionPlaybackState(
                selectedProvider = RadarProvider.RAINVIEWER,
                displayedProvider = RadarProvider.LIBREWXR,
                pending = true
            )
        )
        val uriHandler = RecordingUriHandler()
        setOverlayContent(
            playback,
            enabled,
            opacity,
            darkTheme = false,
            selectedProvider = selectedProvider,
            uriHandler = uriHandler,
            playbackStateOverride = attributionState
        )

        composeRule.onNodeWithText("Loading radar frame…").assertDoesNotExist()
        composeRule.onNodeWithText("Weather radar via LibreWXR (librewxr.net)").assertIsDisplayed()
        composeRule.onNodeWithText("Radar-DPC (Italy; CC BY-SA 4.0 where included)", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("Weather radar via RainViewer").assertDoesNotExist()

        attributionState.value = attributionPlaybackState(
            selectedProvider = RadarProvider.RAINVIEWER,
            displayedProvider = RadarProvider.RAINVIEWER,
            pending = true
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Weather radar via RainViewer").assertIsDisplayed()
        composeRule.onNodeWithText("EUMETNET OPERA", substring = true).assertDoesNotExist()

        attributionState.value = attributionPlaybackState(
            selectedProvider = RadarProvider.RAINVIEWER,
            displayedProvider = RadarProvider.RAINVIEWER,
            pending = false,
            displayError = "HTTP 500"
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Radar network unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("Weather radar via RainViewer").assertIsDisplayed()

        attributionState.value = attributionPlaybackState(
            selectedProvider = RadarProvider.RAINVIEWER,
            displayedProvider = null,
            pending = true
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Weather radar via RainViewer").assertIsDisplayed()
        composeRule.onNodeWithText("Weather radar via LibreWXR (librewxr.net)").assertDoesNotExist()
    }

    @Test
    fun controls_remain_legible_and_compact_in_light_and_dark_themes() {
        val playback = playbackViewModel()
        playback.setVisible(true)
        val enabled = mutableStateOf(true)
        val opacity = mutableFloatStateOf(0.75f)
        val darkTheme = mutableStateOf(false)

        composeRule.setContent {
            val playbackState by playback.state.collectAsState()
            HluWeatherTheme(darkTheme = darkTheme.value) {
                Box(Modifier.size(360.dp, 620.dp).testTag("radar_overlay_position_container")) {
                    RadarMapOverlay(
                        enabled = enabled.value,
                        onEnabledChange = { enabled.value = it },
                        selectedProvider = RadarProvider.LIBREWXR,
                        onProviderChange = {},
                        playbackState = playbackState,
                        metadataState = playbackState.metadataState,
                        opacity = opacity.floatValue,
                        onOpacityChange = { opacity.floatValue = it },
                        onFrameSelected = playback::selectFrame,
                        onPlayPause = { playback.pause() }
                    )
                }
            }
        }

        listOf(false, true).forEach { useDarkTheme ->
            darkTheme.value = useDarkTheme
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription("Radar visibility").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Radar layer options").assertIsDisplayed()
            composeRule.onNodeWithText("Observed").assertIsDisplayed()
            composeRule.onNodeWithTag("radar_timeline").assertIsDisplayed()
            composeRule.onNodeWithTag("radar_attribution").assertIsDisplayed()

            val containerBounds = composeRule.onNodeWithTag("radar_overlay_position_container")
                .fetchSemanticsNode().boundsInRoot
            val timeline = composeRule.onNodeWithTag("radar_timeline")
                .fetchSemanticsNode().boundsInRoot
            assertEquals(
                with(composeRule.density) { 56.dp.toPx() },
                containerBounds.bottom - timeline.bottom,
                1f
            )
            assertTrue("Radar timeline should stay compact", timeline.height <= with(composeRule.density) { 190.dp.toPx() })
            assertReadableContrast(composeRule.onNodeWithTag("radar_timeline").captureToImage())
        }
    }

    @Test
    fun weather_screen_stops_radar_collection_when_disabled_or_leaving_map() {
        val repository = TestRadarRepository(RadarMetadataState(metadata = metadata(), isLoading = false))
        val radarViewModel = own(RadarPlaybackViewModel(repository))
        val weatherViewModel = WeatherViewModel(MockWeatherRepository(), Svilajnac)
        weatherViewModel.onForecastModeSelected(ForecastMode.MAP)
        val enabled = mutableStateOf(true)

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                WeatherScreen(
                    viewModel = weatherViewModel,
                    radarPlaybackViewModel = radarViewModel,
                    radarEnabled = enabled.value,
                    onRadarEnabledChange = { enabled.value = it },
                    mapContent = { _, _, _, _, _ ->
                        androidx.compose.material3.Text("Map", Modifier.testTag("test_map_content"))
                    }
                )
            }
        }
        composeRule.waitForIdle()
        assertTrue(radarViewModel.state.value.isVisible)

        composeRule.onNodeWithTag("radar_visibility_toggle").performClick()
        composeRule.waitForIdle()
        assertFalse(enabled.value)
        assertFalse(radarViewModel.state.value.isVisible)
        assertTagAbsent("radar_timeline")

        enabled.value = true
        composeRule.waitForIdle()
        assertTrue(radarViewModel.state.value.isVisible)
        weatherViewModel.onForecastModeSelected(ForecastMode.HOURLY)
        composeRule.waitForIdle()
        assertFalse(radarViewModel.state.value.isVisible)
    }

    @Test
    fun missing_forecast_stops_radar_collection_and_playback_even_in_map_mode() {
        val repository = TrackingRadarRepository(
            RadarMetadataState(metadata = metadata(), isLoading = false)
        )
        trackingRadarRepositories += repository
        val radarViewModel = own(RadarPlaybackViewModel(repository))
        radarViewModel.setVisible(true)
        radarViewModel.play()
        assertTrue(radarViewModel.state.value.isPlaying)

        val weatherViewModel = WeatherViewModel(NoForecastWeatherRepository(), Svilajnac)
        weatherViewModel.onForecastModeSelected(ForecastMode.MAP)
        var mapRendered = false
        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                WeatherScreen(
                    viewModel = weatherViewModel,
                    radarPlaybackViewModel = radarViewModel,
                    mapContent = { _, _, _, _, _ -> mapRendered = true }
                )
            }
        }
        composeRule.waitForIdle()

        assertEquals(ForecastMode.MAP, weatherViewModel.state.value.forecastMode)
        assertTrue(weatherViewModel.state.value.activeLocation != null)
        assertNull(weatherViewModel.state.value.forecast)
        assertFalse(mapRendered)
        assertEquals(0, repository.activeCollectors)
        assertFalse(radarViewModel.state.value.isVisible)
        assertFalse(radarViewModel.state.value.isPlaying)
    }

    private fun setOverlayContent(
        playback: RadarPlaybackViewModel,
        enabled: MutableState<Boolean>,
        opacity: androidx.compose.runtime.MutableFloatState,
        darkTheme: Boolean,
        selectedProvider: MutableState<RadarProvider> = mutableStateOf(RadarProvider.LIBREWXR),
        onProviderChange: (RadarProvider) -> Unit = {},
        uriHandler: UriHandler = NoopUriHandler,
        playbackStateOverride: MutableState<RadarPlaybackState>? = null
    ) {
        composeRule.setContent {
            val collectedPlaybackState by playback.state.collectAsState()
            val playbackState = playbackStateOverride?.value ?: collectedPlaybackState
            CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                HluWeatherTheme(darkTheme = darkTheme) {
                    Box(Modifier.size(360.dp, 620.dp)) {
                        RadarMapOverlay(
                            enabled = enabled.value,
                            onEnabledChange = { enabled.value = it },
                            selectedProvider = selectedProvider.value,
                            onProviderChange = { provider ->
                                selectedProvider.value = provider
                                onProviderChange(provider)
                            },
                            playbackState = playbackState,
                            metadataState = playbackState.metadataState,
                            opacity = opacity.floatValue,
                            onOpacityChange = { opacity.floatValue = it },
                            onFrameSelected = playback::selectFrame,
                            onPlayPause = {
                                if (playback.state.value.isPlaying) playback.pause() else playback.play()
                            }
                        )
                    }
                }
            }
        }
    }

    private fun attributionPlaybackState(
        selectedProvider: RadarProvider,
        displayedProvider: RadarProvider?,
        pending: Boolean,
        displayError: String? = null
    ): RadarPlaybackState {
        val requested = RadarFrame(
            timestampSeconds = 1_793_563_200L,
            path = "/requested",
            type = RadarFrameType.OBSERVED,
            provider = selectedProvider
        )
        val displayed = displayedProvider?.let { provider ->
            RadarFrame(
                timestampSeconds = 1_793_562_600L,
                path = "/displayed",
                type = RadarFrameType.OBSERVED,
                provider = provider
            )
        }
        val host = providerHost(selectedProvider)
        return RadarPlaybackState(
            selectedProvider = selectedProvider,
            frames = listOf(requested),
            requestedFrame = requested,
            requestedFrameIdentity = requested.identity(host),
            displayedFrame = displayed,
            displayedFrameIdentity = displayed?.identity(providerHost(displayedProvider!!)),
            requestedFrameTimeLabel = "Requested frame",
            requestedFrameType = RadarFrameType.OBSERVED,
            requestedFrameTypeLabel = "Observed",
            displayedFrameTimeLabel = displayed?.let { "Displayed frame" },
            isPending = pending,
            displayError = displayError
        )
    }

    private fun providerHost(provider: RadarProvider): String = when (provider) {
        RadarProvider.LIBREWXR -> "https://api.librewxr.net"
        RadarProvider.RAINVIEWER -> "https://tilecache.rainviewer.com"
    }

    private fun assertSourceLink(label: String, url: String, uriHandler: RecordingUriHandler) {
        val node = composeRule.onNodeWithTag("radar_attribution_source")
            .assertIsDisplayed()
        val semantics = node.fetchSemanticsNode().config
        val annotatedText = semantics[SemanticsProperties.Text].single()
        val link = annotatedText.getLinkAnnotations(0, annotatedText.length).single().item
        assertTrue("Source text should carry a URL link annotation", link is LinkAnnotation.Url)
        assertEquals(url, (link as LinkAnnotation.Url).url)
        node.assertContentDescriptionEquals("Open $label source link")
        composeRule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.LinkTestMarker, Unit),
            useUnmergedTree = true
        ).assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        assertEquals(url, uriHandler.openedUris.last())
    }

    private fun assertTagAbsent(tag: String) {
        assertTrue(composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty())
    }

    private fun assertReadableContrast(image: ImageBitmap) {
        val pixels = IntArray(image.width * image.height)
        image.readPixels(pixels)
        val luminances = pixels.asSequence()
            .map { Color(it) }
            .filter { it.alpha >= 0.9f }
            .map { color ->
                fun linear(channel: Float): Double = if (channel <= 0.04045f) {
                    (channel / 12.92f).toDouble()
                } else {
                    Math.pow(((channel + 0.055f) / 1.055f).toDouble(), 2.4)
                }
                0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)
            }
            .toList()
        val contrast = (luminances.maxOrNull()!! + 0.05) / (luminances.minOrNull()!! + 0.05)
        assertTrue("Timeline text and controls need strong theme contrast", contrast >= 4.5)
    }

    private fun playbackViewModel(): RadarPlaybackViewModel = own(
        RadarPlaybackViewModel(
            TestRadarRepository(
                RadarMetadataState(metadata = metadata(), isLoading = false)
            )
        )
    )

    private fun own(viewModel: RadarPlaybackViewModel): RadarPlaybackViewModel =
        viewModel.also(radarViewModels::add)

    private class RecordingUriHandler : UriHandler {
        val openedUris = mutableListOf<String>()

        override fun openUri(uri: String) {
            openedUris += uri
        }
    }

    private object NoopUriHandler : UriHandler {
        override fun openUri(uri: String) = Unit
    }

    private fun metadata(): LibreWxrRadarMetadata = LibreWxrRadarMetadata(
        host = "https://api.librewxr.net",
        frames = listOf(
            frame("2026-11-01T20:00:00Z", LibreWxrRadarFrameType.OBSERVED),
            frame("2026-11-01T20:10:00Z", LibreWxrRadarFrameType.OBSERVED),
            frame("2026-11-01T20:20:00Z", LibreWxrRadarFrameType.FORECAST)
        )
    )

    private fun frame(timestamp: String, type: LibreWxrRadarFrameType): LibreWxrRadarFrame {
        val seconds = java.time.Instant.parse(timestamp).epochSecond
        return LibreWxrRadarFrame(seconds, "/v2/radar/$seconds", type)
    }

    private class TestRadarRepository(initial: RadarMetadataState) : RadarRepository {
        val updates = MutableStateFlow(initial)
        override val state: StateFlow<RadarMetadataState> = updates
    }

    private class TrackingRadarRepository(initial: RadarMetadataState) : RadarRepository {
        private val updates = MutableStateFlow(initial)
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var activeCollectors = 0
            private set

        override val state: StateFlow<RadarMetadataState> = updates
            .onStart { activeCollectors++ }
            .onCompletion { activeCollectors-- }
            .stateIn(
                scope,
                SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0),
                initial
            )

        fun close() = scope.cancel()
    }

    private class NoForecastWeatherRepository : WeatherRepository {
        override suspend fun getForecast(
            provider: WeatherProvider,
            location: ActiveLocation
        ): ForecastLoad = throw IllegalStateException("No forecast in test")

        override suspend fun clearCache() = Unit
    }
}
