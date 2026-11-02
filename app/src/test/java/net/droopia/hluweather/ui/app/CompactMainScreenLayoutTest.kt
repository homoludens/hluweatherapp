package net.droopia.hluweather.ui.app

import android.graphics.Insets
import android.view.WindowInsets
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets as ComposeWindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import java.util.TimeZone
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.droopia.hluweather.ComposeTestActivity
import net.droopia.hluweather.data.dayText
import net.droopia.hluweather.data.device.DeviceLocationSource
import net.droopia.hluweather.data.model.ActiveLocation
import net.droopia.hluweather.data.model.DrivingRoute
import net.droopia.hluweather.data.model.ForecastMode
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.GpsResult
import net.droopia.hluweather.data.model.LocationMode
import net.droopia.hluweather.data.model.WeatherCondition
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.network.LibreWxrRadarFrame
import net.droopia.hluweather.data.network.LibreWxrRadarFrameType
import net.droopia.hluweather.data.network.LibreWxrRadarMetadata
import net.droopia.hluweather.data.repository.LocationRepository
import net.droopia.hluweather.data.repository.MockWeatherRepository
import net.droopia.hluweather.data.repository.PlaceSearchProvider
import net.droopia.hluweather.data.repository.PlaceSearchSource
import net.droopia.hluweather.data.repository.RadarMetadataState
import net.droopia.hluweather.data.repository.RadarRepository
import net.droopia.hluweather.data.repository.RouteWeatherSource
import net.droopia.hluweather.data.repository.RoutingSource
import net.droopia.hluweather.data.repository.Svilajnac
import net.droopia.hluweather.data.weatherroute.RouteWeatherForecastHour
import net.droopia.hluweather.data.weatherroute.RouteWeatherSnapshot
import net.droopia.hluweather.navigation.MainDestination
import net.droopia.hluweather.ui.map.RadarPlaybackViewModel
import net.droopia.hluweather.ui.settings.DistanceUnit
import net.droopia.hluweather.ui.settings.PrecipitationUnit
import net.droopia.hluweather.ui.settings.TemperatureUnit
import net.droopia.hluweather.ui.settings.WindUnit
import net.droopia.hluweather.ui.theme.HluWeatherTheme
import net.droopia.hluweather.ui.weather.WeatherScreen
import net.droopia.hluweather.ui.weather.WeatherViewModel
import net.droopia.hluweather.ui.weatherroute.RouteEndpointSlot
import net.droopia.hluweather.ui.weatherroute.WeatherRouteScreen
import net.droopia.hluweather.ui.weatherroute.WeatherRouteViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h568dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompactMainScreenLayoutTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeTestActivity>()

    private val now = Instant.parse("2026-11-01T20:00:00Z")
    private val departure = WeatherLocation("departure", "Start", 45.6495, 13.7768)
    private val destination = WeatherLocation("destination", "Destination", 44.8176, 20.4633)
    private val weatherRadarViewModels = mutableListOf<RadarPlaybackViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        composeRule.activity.enableEdgeToEdge()
    }

    @After
    fun tearDown() {
        weatherRadarViewModels.forEach { it.setVisible(false) }
        Dispatchers.resetMain()
    }

    @Test
    fun hourly_and_daily_forecasts_scroll_between_shared_chrome_in_both_themes() {
        val weatherViewModel = WeatherViewModel(MockWeatherRepository(baseTime = now), Svilajnac)
        val darkTheme = mutableStateOf(false)
        val radarPlayback = radarViewModel()
        composeRule.setContent { renderWeatherShell(weatherViewModel, radarPlayback, darkTheme) }
        dispatchCompactInsets()

        assertCompactShellBounds()
        val contentBounds = composeRule.onNodeWithTag("compact_shared_content")
            .fetchSemanticsNode().boundsInRoot
        val pageSpacing = with(composeRule.density) { 16.dp.toPx() }
        val currentCard = composeRule.onNodeWithTag("current_weather_card")
            .fetchSemanticsNode().boundsInRoot
        assertEquals(
            "Hourly page spacing starts within the inset-adjusted content, not below the status bar twice",
            contentBounds.top + pageSpacing,
            currentCard.top,
            1f
        )
        assertEquals(contentBounds.left + pageSpacing, currentCard.left, 1f)
        composeRule.onNodeWithTag("main_destination_hourly").assertIsSelected()
        composeRule.onNodeWithTag("current_temperature").assertIsDisplayed()

        listOf(false, true).forEach { dark ->
            composeRule.runOnIdle { darkTheme.value = dark }
            composeRule.waitForIdle()

            val contentBounds = composeRule.onNodeWithTag("compact_shared_content")
                .fetchSemanticsNode().boundsInRoot
            composeRule.onNodeWithTag("weather_scroll").performScrollToIndex(28)
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("hourly_day_strip").assertIsDisplayed()
            composeRule.onNodeWithTag("hourly_column_header").assertIsDisplayed()
            assertInside(
                composeRule.onNodeWithTag("hourly_day_strip").fetchSemanticsNode().boundsInRoot,
                contentBounds,
                "Hourly day selector in ${themeName(dark)} theme"
            )
            assertInside(
                composeRule.onNodeWithTag("hourly_column_header").fetchSemanticsNode().boundsInRoot,
                contentBounds,
                "Hourly column header in ${themeName(dark)} theme"
            )
            assertEquals(
                contentBounds,
                composeRule.onNodeWithTag("weather_scroll").fetchSemanticsNode().boundsInRoot
            )

            composeRule.onNodeWithTag("main_destination_daily").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("main_destination_daily").assertIsSelected()
            val forecast = weatherViewModel.state.value.forecast!!
            composeRule.onNodeWithTag("daily_list").performScrollToIndex(0)
            composeRule.waitForIdle()
            val dailyList = composeRule.onNodeWithTag("daily_list")
                .fetchSemanticsNode().boundsInRoot
            val firstDailyCard = composeRule.onNodeWithTag("daily_card_0")
                .fetchSemanticsNode().boundsInRoot
            assertEquals(
                "Daily page top gap remains 16dp on a compact screen",
                pageSpacing,
                firstDailyCard.top - dailyList.top,
                1f
            )
            assertEquals(
                "Daily page leading gutter remains 16dp",
                pageSpacing,
                firstDailyCard.left - dailyList.left,
                1f
            )
            val lastDay = forecast.daily.last()
            composeRule.onNodeWithTag("daily_list")
                .performScrollToIndex(forecast.daily.lastIndex)
            composeRule.waitForIdle()
            val lastDailyCard = composeRule.onNodeWithTag("daily_card_${forecast.daily.lastIndex}")
                .fetchSemanticsNode().boundsInRoot
            assertEquals(
                "Daily bottom margin remains reachable above the navigation bar",
                pageSpacing,
                dailyList.bottom - lastDailyCard.bottom,
                1f
            )
            composeRule.onNodeWithText(lastDay.date.dayText(ZoneId.of(forecast.timezone)))
                .assertIsDisplayed()
            assertEquals(
                contentBounds,
                composeRule.onNodeWithTag("daily_list").fetchSemanticsNode().boundsInRoot
            )

            composeRule.onNodeWithTag("main_destination_hourly").performClick()
            composeRule.waitForIdle()
        }
    }

    @Test
    fun radar_toggle_layer_menu_timeline_and_playback_fit_compact_map_in_both_themes() {
        val weatherViewModel = WeatherViewModel(MockWeatherRepository(baseTime = now), Svilajnac)
        weatherViewModel.onForecastModeSelected(ForecastMode.MAP)
        val playback = radarViewModel()
        val darkTheme = mutableStateOf(false)
        composeRule.setContent { renderWeatherShell(weatherViewModel, playback, darkTheme) }
        dispatchCompactInsets()

        assertCompactShellBounds()
        composeRule.onNodeWithTag("main_destination_map").assertIsSelected()
        composeRule.onNodeWithTag("map_placeholder").assertIsDisplayed()

        listOf(false, true).forEach { dark ->
            composeRule.runOnIdle { darkTheme.value = dark }
            composeRule.waitForIdle()
            val contentBounds = composeRule.onNodeWithTag("compact_shared_content")
                .fetchSemanticsNode().boundsInRoot

            composeRule.onNodeWithTag("radar_visibility_toggle").assertIsDisplayed()
            composeRule.onNodeWithTag("radar_layer_options").assertIsDisplayed()
            composeRule.onNodeWithTag("radar_timeline").assertIsDisplayed()
            composeRule.onNodeWithTag("radar_timeline_slider").assertIsDisplayed()
            assertInside(
                composeRule.onNodeWithTag("radar_timeline").fetchSemanticsNode().boundsInRoot,
                contentBounds,
                "Radar timeline in ${themeName(dark)} theme"
            )

            composeRule.onNodeWithTag("radar_visibility_toggle").performClick()
            composeRule.onNodeWithTag("radar_timeline").assertDoesNotExist()
            composeRule.onNodeWithTag("radar_visibility_toggle").performClick()
            composeRule.onNodeWithTag("radar_timeline").assertIsDisplayed()

            composeRule.onNodeWithTag("radar_layer_options").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Radar layers").assertIsDisplayed()
            val opacityNode = composeRule.onNodeWithTag("radar_opacity_value").fetchSemanticsNode()
            val opacityText = opacityNode.config[SemanticsProperties.Text].map { it.text }
            assertTrue(
                "Opacity value should remain visible; rendered=$opacityText",
                opacityText.contains("75%")
            )
            assertInside(opacityNode.boundsInRoot, contentBounds, "Radar opacity value")
            val opacitySlider = composeRule.onNodeWithTag("radar_opacity_slider").fetchSemanticsNode()
            assertInside(opacitySlider.boundsInRoot, contentBounds, "Radar opacity slider")
            composeRule.onNodeWithTag("radar_opacity_slider").assertIsDisplayed()
            composeRule.onNodeWithTag("radar_layer_visibility").performClick()
            composeRule.onNodeWithTag("radar_timeline").assertDoesNotExist()
            composeRule.onNodeWithTag("radar_visibility_toggle").performClick()

            val targetFrame = if (dark) 0f else 2f
            composeRule.onNodeWithTag("radar_timeline_slider")
                .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                    assertTrue(setProgress(targetFrame))
                }
            composeRule.onNodeWithText(if (dark) "Observed" else "Forecast +10 min").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Play radar playback").performClick()
            composeRule.onNodeWithContentDescription("Pause radar playback").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Pause radar playback").performClick()
        }
    }

    @Test
    fun route_planner_actions_and_scrolling_stay_above_shared_bar_in_both_themes() {
        val routeViewModel = routeViewModel().also {
            it.selectSavedLocation(RouteEndpointSlot.START, departure)
            it.selectSavedLocation(RouteEndpointSlot.END, destination)
        }
        val darkTheme = mutableStateOf(false)
        composeRule.setContent { renderRouteShell(routeViewModel, darkTheme) }
        dispatchCompactInsets()

        assertCompactShellBounds()
        var contentBounds = composeRule.onNodeWithTag("compact_shared_content")
            .fetchSemanticsNode().boundsInRoot
        assertEquals(
            contentBounds,
            composeRule.onNodeWithTag("weather_route_screen").fetchSemanticsNode().boundsInRoot
        )

        listOf(false, true).forEach { dark ->
            composeRule.runOnIdle { darkTheme.value = dark }
            composeRule.waitForIdle()
            contentBounds = composeRule.onNodeWithTag("compact_shared_content")
                .fetchSemanticsNode().boundsInRoot
            assertEquals(
                contentBounds,
                composeRule.onNodeWithTag("weather_route_screen").fetchSemanticsNode().boundsInRoot
            )
            composeRule.onNodeWithTag("weather_route_content").assertIsDisplayed()
            composeRule.onNodeWithTag("trip_show_weather").assertIsDisplayed()
            assertInside(
                composeRule.onNodeWithTag("trip_show_weather").fetchSemanticsNode().boundsInRoot,
                contentBounds,
                "Route action in ${themeName(dark)} theme"
            )

            composeRule.onNodeWithTag("weather_route_content").performScrollToIndex(0)
            scrollRouteUntilVisible("route_start_search")
            scrollRouteUntilVisible("route_end_search")

            composeRule.onNodeWithTag("weather_route_content").performScrollToIndex(1)
            composeRule.onNodeWithTag("trip_speed_slider").assertIsDisplayed()
            composeRule.onNodeWithTag("weather_route_content").performScrollToIndex(2)
            composeRule.onNodeWithTag("trip_start_time_slider").assertIsDisplayed()
            composeRule.onNodeWithTag("trip_show_weather").assertIsDisplayed()
        }

        composeRule.onNodeWithTag("trip_show_weather").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("weather_route_content").performScrollToIndex(3)
        composeRule.onNodeWithTag("weather_route_map").assertIsDisplayed()
        composeRule.onNodeWithTag("trip_show_weather").assertIsDisplayed()
        composeRule.onNodeWithTag("weather_route_content").performScrollToIndex(4)
        composeRule.onNodeWithTag("weather_route_table").assertIsDisplayed()
        composeRule.onNodeWithTag("weather_route_content").performScrollToIndex(5)
        composeRule.onNodeWithTag("route_summary").assertIsDisplayed()
        assertInside(
            composeRule.onNodeWithTag("trip_show_weather").fetchSemanticsNode().boundsInRoot,
            contentBounds,
            "Pinned route action after result scrolling"
        )
    }

    @Composable
    private fun renderWeatherShell(
        viewModel: WeatherViewModel,
        radarPlaybackViewModel: RadarPlaybackViewModel,
        darkTheme: MutableState<Boolean>
    ) {
        HluWeatherTheme(darkTheme = darkTheme.value) {
            var selected by remember(viewModel) {
                mutableStateOf(
                    when (viewModel.state.value.forecastMode) {
                        ForecastMode.HOURLY -> MainDestination.HOURLY
                        ForecastMode.DAILY -> MainDestination.DAILY
                        ForecastMode.MAP -> MainDestination.MAP
                    }
                )
            }
            var radarEnabled by remember { mutableStateOf(true) }
            Column(Modifier.fillMaxSize()) {
                MainScreenHeader(onSettingsClick = {})
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .consumeWindowInsets(ComposeWindowInsets.safeDrawing)
                        .testTag("compact_shared_content")
                ) {
                    WeatherScreen(
                        viewModel = viewModel,
                        darkTheme = darkTheme.value,
                        radarEnabled = radarEnabled,
                        onRadarEnabledChange = { radarEnabled = it },
                        radarPlaybackViewModel = radarPlaybackViewModel,
                        now = now,
                        mapContent = { _, _, _, _, _ ->
                            Box(Modifier.fillMaxSize().testTag("map_placeholder"))
                        }
                    )
                }
                MainBottomNavigationBar(
                    selected = selected,
                    onDestinationSelected = { destination ->
                        selected = destination
                        when (destination) {
                            MainDestination.HOURLY -> viewModel.onForecastModeSelected(ForecastMode.HOURLY)
                            MainDestination.DAILY -> viewModel.onForecastModeSelected(ForecastMode.DAILY)
                            MainDestination.MAP -> viewModel.onForecastModeSelected(ForecastMode.MAP)
                            MainDestination.ROUTE -> Unit
                        }
                    }
                )
            }
        }
    }

    @Composable
    private fun renderRouteShell(
        viewModel: WeatherRouteViewModel,
        darkTheme: MutableState<Boolean>
    ) {
        HluWeatherTheme(darkTheme = darkTheme.value) {
            Column(Modifier.fillMaxSize()) {
                MainScreenHeader(onSettingsClick = {})
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .consumeWindowInsets(ComposeWindowInsets.safeDrawing)
                        .testTag("compact_shared_content")
                ) {
                    WeatherRouteScreen(
                        viewModel = viewModel,
                        savedLocations = listOf(departure, destination),
                        darkTheme = darkTheme.value,
                        temperatureUnit = TemperatureUnit.CELSIUS,
                        windUnit = WindUnit.KMH,
                        distanceUnit = DistanceUnit.KM,
                        precipitationUnit = PrecipitationUnit.MM,
                        onBackClick = {},
                        modifier = Modifier.fillMaxSize(),
                        mapContent = { _, _, _, _ ->
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(300.dp)
                                    .testTag("weather_route_map")
                            )
                        }
                    )
                }
                MainBottomNavigationBar(
                    selected = MainDestination.ROUTE,
                    onDestinationSelected = {}
                )
            }
        }
    }

    private fun radarViewModel(): RadarPlaybackViewModel = RadarPlaybackViewModel(
        repository = TestRadarRepository(
            RadarMetadataState(metadata = radarMetadata(), isLoading = false)
        )
    ).also(weatherRadarViewModels::add)

    private fun routeViewModel() = WeatherRouteViewModel(
        routingSource = object : RoutingSource {
            override val providerName = "Test route"
            override suspend fun route(start: GeoPoint, end: GeoPoint) = DrivingRoute(
                providerName = providerName,
                polyline = listOf(start, end),
                distanceMeters = 80_000.0,
                providerDurationSeconds = 3_600.0
            )
        },
        placeSearchSources = listOf(object : PlaceSearchSource {
            override val provider = PlaceSearchProvider.PHOTON
            override suspend fun search(query: String) = emptyList<net.droopia.hluweather.data.repository.PlaceSearchResult>()
        }),
        placeSearchProvider = PlaceSearchProvider.PHOTON,
        routeWeatherSource = object : RouteWeatherSource {
            override suspend fun fetchSnapshot(points: List<GeoPoint>): RouteWeatherSnapshot =
                RouteWeatherSnapshot(
                    points = points,
                    hourlyByPoint = points.map {
                        listOf(
                            RouteWeatherForecastHour(
                                time = now + 1.hours,
                                condition = WeatherCondition.CLEAR,
                                temperatureCelsius = 19.0,
                                windSpeedKmh = 10.0,
                                precipitationProbability = 0,
                                precipitationMm = 0.0,
                                humidityPercent = 50,
                                isDay = true
                            )
                        )
                    },
                    fetchedAt = now
                )
        },
        locationRepository = TestLocationRepository(),
        deviceLocationSource = object : DeviceLocationSource {
            override suspend fun currentLocation() = GpsResult.Unavailable
        },
        now = { now }
    )

    private fun radarMetadata(): LibreWxrRadarMetadata {
        val observed = radarFrame("2026-11-01T20:00:00Z", LibreWxrRadarFrameType.OBSERVED)
        val latestObserved = radarFrame("2026-11-01T20:10:00Z", LibreWxrRadarFrameType.OBSERVED)
        val forecast = radarFrame("2026-11-01T20:20:00Z", LibreWxrRadarFrameType.FORECAST)
        return LibreWxrRadarMetadata(
            host = "https://api.librewxr.net",
            frames = listOf(observed, latestObserved, forecast)
        )
    }

    private fun radarFrame(timestamp: String, type: LibreWxrRadarFrameType): LibreWxrRadarFrame {
        val seconds = java.time.Instant.parse(timestamp).epochSecond
        return LibreWxrRadarFrame(seconds, "/v2/radar/$seconds", type)
    }

    private fun dispatchCompactInsets() {
        val density = composeRule.activity.resources.displayMetrics.density
        fun px(dp: Int) = (dp * density).roundToInt()
        val insets = WindowInsets.Builder()
            .setInsets(WindowInsets.Type.statusBars(), Insets.of(0, px(24), 0, 0))
            .setInsets(WindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, px(24)))
            .setInsets(WindowInsets.Type.displayCutout(), Insets.of(px(12), 0, px(12), 0))
            .build()
        composeRule.activity.window.decorView.dispatchApplyWindowInsets(insets)
        composeRule.waitForIdle()
    }

    private fun assertCompactShellBounds() {
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val density = composeRule.density
        val statusBar = with(density) { 24.dp.toPx() }
        val cutout = with(density) { 12.dp.toPx() }
        val header = composeRule.onNodeWithTag("main_header_content")
            .fetchSemanticsNode().boundsInRoot
        val content = composeRule.onNodeWithTag("compact_shared_content")
            .fetchSemanticsNode().boundsInRoot
        val bottomBar = composeRule.onNodeWithTag("main_bottom_navigation")
            .fetchSemanticsNode().boundsInRoot
        val firstTab = composeRule.onNodeWithTag("main_destination_hourly")
            .fetchSemanticsNode().boundsInRoot
        val lastTab = composeRule.onNodeWithTag("main_destination_route")
            .fetchSemanticsNode().boundsInRoot

        assertTrue("Header should be below the status bar", header.top >= statusBar)
        assertTrue("Header title should clear the left cutout", header.left >= cutout)
        assertTrue("Body should start below the compact header", content.top >= header.bottom)
        assertEquals("Content should end at the shared bottom bar", bottomBar.top, content.bottom, 1f)
        assertTrue("Bottom tabs should clear the left cutout", firstTab.left >= cutout)
        assertTrue("Bottom tabs should clear the right cutout", lastTab.right <= root.right - cutout)
        assertTrue("Bottom bar should remain within the app window", bottomBar.bottom <= root.bottom + 1f)
    }

    private fun assertInside(
        child: androidx.compose.ui.geometry.Rect,
        parent: androidx.compose.ui.geometry.Rect,
        description: String
    ) {
        assertTrue(
            "$description should not start above shared content; child=$child parent=$parent",
            child.top >= parent.top - 1f
        )
        assertTrue(
            "$description should not extend below the bottom bar; child=$child parent=$parent",
            child.bottom <= parent.bottom + 1f
        )
        assertTrue(
            "$description should fit horizontally; child=$child parent=$parent",
            child.left >= parent.left - 1f && child.right <= parent.right + 1f
        )
    }

    private fun scrollRouteUntilVisible(tag: String) {
        val history = mutableListOf<String>()
        repeat(16) {
            val viewport = composeRule.onNodeWithTag("weather_route_content")
                .fetchSemanticsNode().boundsInRoot
            val nodes = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes()
            val listNode = composeRule.onNodeWithTag("weather_route_content").fetchSemanticsNode()
            val scrollRange = listNode.config[SemanticsProperties.VerticalScrollAxisRange]
            history += "scroll=${scrollRange.value()} bounds=${nodes.map { it.boundsInRoot }}"
            if (nodes.any { node ->
                    val bounds = node.boundsInRoot
                    bounds.bottom > viewport.top && bounds.top < viewport.bottom
                }
            ) {
                composeRule.onNodeWithTag(tag).assertIsDisplayed()
                return
            }
            composeRule.onNodeWithTag("weather_route_content").performTouchInput {
                swipe(
                    start = Offset(480f, 650f),
                    end = Offset(480f, 500f),
                    durationMillis = 200
                )
            }
        }
        val viewport = composeRule.onNodeWithTag("weather_route_content")
            .fetchSemanticsNode()
        val scrollRange = viewport.config[SemanticsProperties.VerticalScrollAxisRange]
        val bounds = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes()
            .map { it.boundsInRoot }
        assertTrue(
            "$tag should be reachable by scrolling; viewport=${viewport.boundsInRoot} nodeBounds=$bounds " +
                "scroll=${scrollRange.value()} max=${scrollRange.maxValue()} history=$history",
            bounds.any { it.bottom > viewport.boundsInRoot.top && it.top < viewport.boundsInRoot.bottom }
        )
    }

    private fun themeName(dark: Boolean) = if (dark) "dark" else "light"

    private class TestRadarRepository(initial: RadarMetadataState) : RadarRepository {
        override val state: StateFlow<RadarMetadataState> = MutableStateFlow(initial)
    }

    private class TestLocationRepository : LocationRepository {
        override val locations: StateFlow<List<WeatherLocation>> = MutableStateFlow(emptyList())
        override val activeLocation: StateFlow<ActiveLocation?> = MutableStateFlow(null)
        override val locationMode: StateFlow<LocationMode> = MutableStateFlow(LocationMode.SAVED_LOCATION)
        override suspend fun add(location: WeatherLocation) = Unit
        override suspend fun update(location: WeatherLocation) = Unit
        override suspend fun delete(id: String) = Unit
        override suspend fun selectSaved(id: String) = Unit
        override suspend fun setTrackMe(enabled: Boolean) = Unit
    }
}
