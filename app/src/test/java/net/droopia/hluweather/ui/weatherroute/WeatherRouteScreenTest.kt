package net.droopia.hluweather.ui.weatherroute

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertTextContains
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
import net.droopia.hluweather.data.repository.PlaceSearchSource
import net.droopia.hluweather.data.repository.RoutingSource
import net.droopia.hluweather.data.repository.RouteWeatherSource
import net.droopia.hluweather.ui.settings.DistanceUnit
import net.droopia.hluweather.ui.settings.TemperatureUnit
import net.droopia.hluweather.ui.settings.WindUnit
import net.droopia.hluweather.ui.theme.HluWeatherTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
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
    fun invalid_speed_shows_the_speed_validation_message() {
        val viewModel = viewModel().also {
            it.selectSavedLocation(RouteEndpointSlot.START, start)
            it.selectSavedLocation(RouteEndpointSlot.END, end)
            it.onSpeedChanged("49")
        }
        render(viewModel)

        composeRule.onNodeWithTag("route_calculate").performClick()
        composeRule.onNodeWithText("Enter a speed from 50 to 240 km/h").assertIsDisplayed()
    }

    @Test
    fun route_failure_exposes_retry() {
        val viewModel = viewModel(route = ScreenFakeRoutingSource(failure = IllegalStateException("routing down"))).also {
            chooseEndpoints(it)
        }
        render(viewModel)

        composeRule.onNodeWithTag("route_calculate").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("routing down").assertIsDisplayed()
        composeRule.onNodeWithTag("route_retry").assertIsDisplayed()
    }

    @Test
    fun weather_failure_keeps_route_summary_and_exposes_weather_retry() {
        val viewModel = viewModel(weather = ScreenFakeRouteWeatherSource(IllegalStateException("weather down"))).also {
            chooseEndpoints(it)
        }
        render(viewModel)

        composeRule.onNodeWithTag("route_calculate").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("route_summary").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("weather down").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("route_weather_retry").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun route_summary_shows_provider_duration_departure_and_expected_arrival() {
        val viewModel = viewModel().also { chooseEndpoints(it) }
        val departure = now + 1.hours
        val arrival = departure + 1.hours
        render(viewModel)

        composeRule.onNodeWithTag("route_calculate").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("route_summary").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("route_summary_provider")
            .assertTextContains("Routing provider: OSRM")
        composeRule.onNodeWithTag("route_summary_osrm_duration")
            .assertTextContains("OSRM duration: 1h 0min")
        composeRule.onNodeWithTag("route_summary_departure")
            .assertTextContains("Departure: ${departure.dateTimeText()}", substring = true)
        composeRule.onNodeWithTag("route_summary_expected_arrival")
            .assertTextContains("Expected arrival: ${arrival.dateTimeText()}", substring = true)
    }

    private fun render(viewModel: WeatherRouteViewModel) {
        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                WeatherRouteScreen(
                    viewModel = viewModel,
                    savedLocations = listOf(start, end),
                    darkTheme = false,
                    temperatureUnit = TemperatureUnit.CELSIUS,
                    windUnit = WindUnit.KMH,
                    distanceUnit = DistanceUnit.KM,
                    onBackClick = {},
                    mapContent = { _, _, _, _ -> }
                )
            }
        }
    }

    private fun chooseEndpoints(viewModel: WeatherRouteViewModel) {
        viewModel.selectSavedLocation(RouteEndpointSlot.START, start)
        viewModel.selectSavedLocation(RouteEndpointSlot.END, end)
        viewModel.onDepartureChanged(now + 1.hours)
    }

    private fun viewModel(
        route: RoutingSource = ScreenFakeRoutingSource(),
        weather: RouteWeatherSource = ScreenFakeRouteWeatherSource()
    ) = WeatherRouteViewModel(
        routingSource = route,
        placeSearchSources = listOf(ScreenFakePlaceSearchSource()),
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
    private val failure: Throwable? = null
) : RouteWeatherSource {
    override suspend fun enrich(samples: List<RouteWeatherSample>): List<RouteWeatherSample> {
        failure?.let { throw it }
        return samples.map { it.copy(condition = WeatherCondition.CLEAR) }
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
