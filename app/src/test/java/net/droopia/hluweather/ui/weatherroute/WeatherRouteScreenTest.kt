package net.droopia.hluweather.ui.weatherroute

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assert
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.util.TimeZone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.hours
import net.droopia.hluweather.ComposeTestActivity
import net.droopia.hluweather.data.dateTimeText
import net.droopia.hluweather.data.device.DeviceLocationSource
import net.droopia.hluweather.data.model.ActiveLocation
import net.droopia.hluweather.data.model.DrivingRoute
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.GpsResult
import net.droopia.hluweather.data.model.LocationMode
import net.droopia.hluweather.data.model.RouteWeatherSample
import net.droopia.hluweather.data.model.WeatherCondition
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.repository.LocationRepository
import net.droopia.hluweather.data.repository.PlaceSearchProvider
import net.droopia.hluweather.data.repository.PlaceSearchSource
import net.droopia.hluweather.data.repository.RoutingSource
import net.droopia.hluweather.data.repository.RouteWeatherSource
import net.droopia.hluweather.data.weatherroute.RouteWeatherForecastHour
import net.droopia.hluweather.data.weatherroute.RouteWeatherSnapshot
import net.droopia.hluweather.ui.settings.DistanceUnit
import net.droopia.hluweather.ui.settings.PrecipitationUnit
import net.droopia.hluweather.ui.settings.TemperatureUnit
import net.droopia.hluweather.ui.settings.WindUnit
import net.droopia.hluweather.ui.theme.HluWeatherTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WeatherRouteScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeTestActivity>()

    private val now = Instant.parse("2026-09-12T09:15:00Z")
    private val start = WeatherLocation("start", "Start", 45.6495, 13.7768)
    private val end = WeatherLocation("end", "Destination", 44.8176, 20.4633)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun calculate_is_disabled_until_both_endpoints_are_selected() {
        render(viewModel())

        composeRule.onNodeWithTag("route_calculate").assertIsNotEnabled()
    }

    @Test
    fun factory_initializes_settings_place_search_provider_and_keeps_route_weather_source() {
        val routeWeather = ScreenFakeRouteWeatherSource()
        val factory = WeatherRouteViewModel.Factory(
            routingSource = ScreenFakeRoutingSource(),
            placeSearchProvider = PlaceSearchProvider.OPEN_METEO,
            placeSearchSources = listOf(ScreenFakePlaceSearchSource()),
            routeWeatherSource = routeWeather,
            locationRepository = ScreenFakeLocationRepository(),
            deviceLocationSource = ScreenFakeDeviceLocationSource(),
            now = { now }
        )
        val viewModel: WeatherRouteViewModel = factory.create(WeatherRouteViewModel::class.java)

        assertEquals(PlaceSearchProvider.OPEN_METEO, viewModel.state.value.searchProvider)
        chooseEndpoints(viewModel)
        viewModel.calculate()
        composeRule.waitForIdle()

        assertEquals(1, routeWeather.calls)
    }

    @Test
    fun screen_formats_nonzero_route_precipitation_in_inches() {
        val viewModel = viewModel(
            weather = ScreenFakeRouteWeatherSource(precipitationMm = 25.4)
        ).also { chooseEndpoints(it) }
        render(viewModel, precipitationUnit = PrecipitationUnit.INCH)

        composeRule.onNodeWithTag("trip_show_weather").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("weather_route_content").performScrollToIndex(5)

        composeRule.onNodeWithTag("route_weather_table_row_0")
            .assertTextContains("1 in", substring = true)
    }

    @Test
    fun single_page_uses_trip_weather_hierarchy_without_provider_or_legacy_time_controls() {
        val viewModel = viewModel().also {
            it.selectSavedLocation(RouteEndpointSlot.START, start)
            it.selectSavedLocation(RouteEndpointSlot.END, end)
        }
        render(viewModel)

        composeRule.onNodeWithTag("weather_route_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("route_start_search").assertIsDisplayed()
        composeRule.onNodeWithTag("route_end_search").assertIsDisplayed()
        composeRule.onNodeWithTag("trip_show_weather").assertIsDisplayed()
        composeRule.onNodeWithTag("trip_start_time_slider")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo(0f, 0f..72f, 71)
                )
            )
        composeRule.onNodeWithTag("trip_speed_slider")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo(80f, 40f..130f, 89)
                )
            )
        assertLayoutOrder(
            "route_start_search",
            "route_end_search",
            "trip_speed_slider",
            "trip_start_time_slider"
        )
        composeRule.onNodeWithText("Plan your journey. Know the weather ahead.")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("trip_start_time_value")
            .assertTextContains("Sat, 12 Sep 2026 · 09:15")
        composeRule.onNodeWithText("Search provider").assertDoesNotExist()
        composeRule.onNodeWithTag("route_departure_date").assertDoesNotExist()
        composeRule.onNodeWithTag("route_departure_time").assertDoesNotExist()
        composeRule.onNodeWithTag("trip_route_estimates").assertDoesNotExist()
        composeRule.onNodeWithTag("trip_provider_context").assertDoesNotExist()

        composeRule.onNodeWithTag("trip_show_weather").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("weather_route_content").performScrollToIndex(6)
        assertLayoutOrder(
            "weather_route_map",
            "weather_route_table",
            "route_summary"
        )
        composeRule.onNodeWithTag("weather_route_map").assertIsDisplayed()
        composeRule.onNodeWithTag("weather_route_content").performTouchInput {
            repeat(4) { swipeUp() }
        }
        composeRule.onNodeWithTag("weather_route_table").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun invalid_speed_shows_the_speed_validation_message() {
        val viewModel = viewModel().also {
            it.selectSavedLocation(RouteEndpointSlot.START, start)
            it.selectSavedLocation(RouteEndpointSlot.END, end)
            it.onSpeedChanged("39")
        }
        render(viewModel)

        composeRule.onNodeWithTag("trip_show_weather").performClick()
        composeRule.onNodeWithText("Speed must be between 40 and 130 km/h").assertIsDisplayed()
    }

    @Test
    fun route_failure_exposes_retry() {
        val viewModel = viewModel(route = ScreenFakeRoutingSource(failure = IllegalStateException("routing down"))).also {
            chooseEndpoints(it)
        }
        render(viewModel)

        composeRule.onNodeWithTag("trip_show_weather").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Routing failed").assertIsDisplayed()
        composeRule.onNodeWithTag("route_retry").assertIsDisplayed()
    }

    @Test
    fun weather_failure_keeps_route_summary_and_exposes_weather_retry() {
        val viewModel = viewModel(weather = ScreenFakeRouteWeatherSource(IllegalStateException("weather down"))).also {
            chooseEndpoints(it)
        }
        render(viewModel)

        composeRule.onNodeWithTag("trip_show_weather").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("weather_route_content").performScrollToIndex(5)
        composeRule.onNodeWithTag("route_summary").assertIsDisplayed()
        composeRule.onNodeWithTag("route_weather_error")
            .performScrollTo()
            .assertTextContains("Weather unavailable")
        composeRule.onNodeWithTag("route_weather_retry").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun outdated_result_is_not_rendered_as_current() {
        val viewModel = viewModel().also {
            chooseEndpoints(it)
            it.calculate()
        }
        render(viewModel)
        composeRule.waitForIdle()

        viewModel.onSpeedChanged("90")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("weather_route_content").performScrollToIndex(3)
        composeRule.onNodeWithTag("route_result_outdated").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("route_summary").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun route_summary_shows_route_values_without_provider_text() {
        val viewModel = viewModel().also { chooseEndpoints(it) }
        render(viewModel)

        composeRule.onNodeWithTag("trip_show_weather").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("weather_route_content").performScrollToIndex(5)
        composeRule.onNodeWithTag("route_summary").assertIsDisplayed()

        composeRule.onNodeWithTag("route_summary")
            .assertTextContains("80 km", substring = true)
            .assertTextContains("Average speed: 80 km/h", substring = true)
        composeRule.onNodeWithTag("route_summary_provider").assertDoesNotExist()
        composeRule.onNodeWithTag("route_summary_osrm_duration").assertDoesNotExist()
        composeRule.onNodeWithTag("route_summary_departure").assertDoesNotExist()
        composeRule.onNodeWithTag("route_summary_expected_arrival").assertDoesNotExist()
    }

    private fun render(
        viewModel: WeatherRouteViewModel,
        precipitationUnit: PrecipitationUnit = PrecipitationUnit.MM
    ) {
        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                WeatherRouteScreen(
                    viewModel = viewModel,
                    savedLocations = listOf(start, end),
                    darkTheme = false,
                    temperatureUnit = TemperatureUnit.CELSIUS,
                    windUnit = WindUnit.KMH,
                    distanceUnit = DistanceUnit.KM,
                    precipitationUnit = precipitationUnit,
                    onBackClick = {},
                    mapContent = { _, _, _, _ ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                                .testTag("weather_route_map")
                        )
                    }
                )
            }
        }
    }

    private fun assertLayoutOrder(vararg tags: String) {
        val content = composeRule
            .onNodeWithTag("weather_route_content", useUnmergedTree = true)
            .fetchSemanticsNode()
        val semanticTags = mutableListOf<String>()
        fun collect(node: SemanticsNode) {
            if (node.config.contains(SemanticsProperties.TestTag)) {
                semanticTags += node.config[SemanticsProperties.TestTag]
            }
            for (child in node.children) {
                collect(child)
            }
        }
        collect(content)
        val positions = tags.map { tag -> semanticTags.indexOf(tag) }
        assertTrue(positions.all { it >= 0 })
        assertTrue(positions.zipWithNext().all { (first, second) -> first < second })
    }

    private fun chooseEndpoints(viewModel: WeatherRouteViewModel) {
        viewModel.selectSavedLocation(RouteEndpointSlot.START, start)
        viewModel.selectSavedLocation(RouteEndpointSlot.END, end)
        viewModel.onDepartureOffsetChanged(1)
    }

    private fun viewModel(
        route: RoutingSource = ScreenFakeRoutingSource(),
        weather: RouteWeatherSource = ScreenFakeRouteWeatherSource()
    ) = WeatherRouteViewModel(
        routingSource = route,
        placeSearchSources = listOf(ScreenFakePlaceSearchSource()),
        placeSearchProvider = PlaceSearchProvider.PHOTON,
        routeWeatherSource = weather,
        locationRepository = ScreenFakeLocationRepository(),
        deviceLocationSource = ScreenFakeDeviceLocationSource(),
        now = { now }
    )
}

private class ScreenFakePlaceSearchSource : PlaceSearchSource {
    override val provider = net.droopia.hluweather.data.repository.PlaceSearchProvider.PHOTON
    override suspend fun search(query: String) = emptyList<net.droopia.hluweather.data.repository.PlaceSearchResult>()
}

private class ScreenFakeRoutingSource(
    private val failure: Throwable? = null
) : RoutingSource {
    override val providerName = "OSRM"

    override suspend fun route(start: GeoPoint, end: GeoPoint): DrivingRoute {
        failure?.let { throw it }
        return DrivingRoute(
            providerName = providerName,
            polyline = listOf(start, end),
            distanceMeters = 80_000.0,
            providerDurationSeconds = 3_600.0
        )
    }
}

private class ScreenFakeRouteWeatherSource(
    private val failure: Throwable? = null,
    private val precipitationMm: Double = 0.0
) : RouteWeatherSource {
    var calls = 0

    override suspend fun fetchSnapshot(points: List<GeoPoint>): RouteWeatherSnapshot {
        calls++
        failure?.let { throw it }
        return RouteWeatherSnapshot(
            points = points,
            hourlyByPoint = points.map {
                listOf(
                    RouteWeatherForecastHour(
                        time = Instant.parse("2026-09-12T10:00:00Z"),
                        condition = WeatherCondition.CLEAR,
                        temperatureCelsius = 20.0,
                        windSpeedKmh = 10.0,
                        precipitationProbability = 0,
                        precipitationMm = precipitationMm,
                        humidityPercent = 50,
                        isDay = true
                    ),
                    RouteWeatherForecastHour(
                        time = Instant.parse("2026-09-12T11:00:00Z"),
                        condition = WeatherCondition.CLEAR,
                        temperatureCelsius = 20.0,
                        windSpeedKmh = 10.0,
                        precipitationProbability = 0,
                        precipitationMm = precipitationMm,
                        humidityPercent = 50,
                        isDay = true
                    )
                )
            },
            fetchedAt = Instant.parse("2026-09-12T09:15:00Z")
        )
    }
}

private class ScreenFakeDeviceLocationSource : DeviceLocationSource {
    override suspend fun currentLocation() = GpsResult.Unavailable
}

private class ScreenFakeLocationRepository : LocationRepository {
    override val locations: Flow<List<WeatherLocation>> = MutableStateFlow(emptyList())
    override val activeLocation: StateFlow<ActiveLocation?> = MutableStateFlow(null)
    override val locationMode: StateFlow<LocationMode> = MutableStateFlow(LocationMode.SAVED_LOCATION)
    override suspend fun add(location: WeatherLocation) = Unit
    override suspend fun update(location: WeatherLocation) = Unit
    override suspend fun delete(id: String) = Unit
    override suspend fun selectSaved(id: String) = Unit
    override suspend fun setTrackMe(enabled: Boolean) = Unit
}
