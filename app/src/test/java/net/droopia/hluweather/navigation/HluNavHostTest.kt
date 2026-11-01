package net.droopia.hluweather.navigation

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlin.time.Instant
import net.droopia.hluweather.data.device.DeviceLocationSource
import net.droopia.hluweather.data.dayText
import net.droopia.hluweather.data.model.DrivingRoute
import net.droopia.hluweather.data.model.ForecastMode
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.GpsResult
import net.droopia.hluweather.data.model.RouteEndpoint
import net.droopia.hluweather.data.model.WeatherRouteResult
import net.droopia.hluweather.data.repository.LocationRepository
import net.droopia.hluweather.data.repository.PlaceSearchProvider
import net.droopia.hluweather.data.repository.PlaceSearchSource
import net.droopia.hluweather.data.repository.RouteWeatherSource
import net.droopia.hluweather.data.repository.RoutingSource
import net.droopia.hluweather.data.weatherroute.RouteWeatherSnapshot
import net.droopia.hluweather.ui.settings.PersistedSettings
import net.droopia.hluweather.ui.settings.SettingsRepository
import net.droopia.hluweather.ui.settings.SettingsViewModel
import net.droopia.hluweather.ui.weatherroute.WeatherRouteViewModel
import net.droopia.hluweather.ui.weatherroute.RouteEndpointSlot
import net.droopia.hluweather.ComposeTestActivity
import net.droopia.hluweather.data.repository.MockWeatherRepository
import net.droopia.hluweather.data.repository.Svilajnac
import net.droopia.hluweather.ui.theme.HluWeatherTheme
import net.droopia.hluweather.ui.weather.WeatherViewModel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HluNavHostTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeTestActivity>()

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun navigates_from_weather_to_settings_and_back() {
        val weatherViewModel = WeatherViewModel(MockWeatherRepository(), Svilajnac)
        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                HluNavHost(
                    weatherViewModel = weatherViewModel,
                    locationPickerMapContent = { _, _, _ -> Box(Modifier.fillMaxSize()) }
                )
            }
        }

        composeRule.onNodeWithText("HluWeatherApp").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("HluWeatherApp").assertIsDisplayed()
    }

    @Test
    fun privacy_policy_is_accessible_from_weather_settings() {
        val weatherViewModel = WeatherViewModel(MockWeatherRepository(), Svilajnac)
        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                HluNavHost(
                    weatherViewModel = weatherViewModel,
                    locationPickerMapContent = { _, _, _ -> Box(Modifier.fillMaxSize()) }
                )
            }
        }

        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithTag("settings_scroll").performScrollToIndex(9)
        composeRule.onNodeWithText("About").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_privacy_policy").performClick()

        val intent = requireNotNull(shadowOf(composeRule.activity).nextStartedActivity)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https://homoludens.github.io/hluweatherapp/privacy/", intent.dataString)
    }

    @Test
    fun add_location_opens_the_location_picker() {
        val weatherViewModel = WeatherViewModel(MockWeatherRepository(), Svilajnac)
        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                HluNavHost(
                    weatherViewModel = weatherViewModel,
                    locationPickerMapContent = { _, _, _ -> Box(Modifier.fillMaxSize()) }
                )
            }
        }

        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithTag("settings_scroll").performScrollToIndex(2)
        composeRule.onNodeWithText("Add Location").performClick()

        composeRule.onNodeWithText("Location picker").assertIsDisplayed()
    }

    @Test
    fun settings_units_reach_weather_content() {
        val weatherViewModel = WeatherViewModel(MockWeatherRepository(), Svilajnac)
        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                HluNavHost(
                    weatherViewModel = weatherViewModel,
                    locationPickerMapContent = { _, _, _ -> Box(Modifier.fillMaxSize()) }
                )
            }
        }

        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithTag("settings_scroll").performScrollToIndex(4)
        composeRule.onNodeWithText("°F").performClick()
        composeRule.onNodeWithText("in").performClick()
        composeRule.onNodeWithTag("settings_scroll").performScrollToIndex(0)
        composeRule.onNodeWithContentDescription("Back").performClick()

        composeRule.onAllNodesWithText("70°F").onFirst().assertIsDisplayed()
    }

    @Test
    fun map_mode_opens_weather_on_route_and_back_returns_to_weather() {
        val weatherViewModel = WeatherViewModel(MockWeatherRepository(), Svilajnac)
        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                HluNavHost(
                    weatherViewModel = weatherViewModel,
                    weatherMapContent = { _, _, _, _, _ -> Box(Modifier.fillMaxSize()) },
                    locationPickerMapContent = { _, _, _ -> Box(Modifier.fillMaxSize()) }
                )
            }
        }

        composeRule.onNodeWithText("Map").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("weather_route_open").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("weather_route_screen").assertIsDisplayed()
        composeRule.onAllNodesWithTag("trip_setup_screen").assertCountEquals(0)
        composeRule.onAllNodesWithTag("trip_overview_screen").assertCountEquals(0)
        composeRule.onAllNodesWithTag("trip_details_screen").assertCountEquals(0)

        composeRule.onNodeWithTag("weather_route_back").performClick()
        composeRule.onNodeWithText("HluWeatherApp").assertIsDisplayed()
    }

    @Test
    fun delayed_settings_do_not_create_a_photon_route_view_model_before_open_meteo_is_loaded() {
        val settingsRepository = DelayedSettingsRepository()
        val settingsViewModel = SettingsViewModel(settingsRepository, EmptyLocationRepository())
        val routeViewModel = delayedRouteViewModel(PlaceSearchProvider.OPEN_METEO)
        val routeFactory = RecordingRouteFactory(routeViewModel)
        val weatherViewModel = WeatherViewModel(MockWeatherRepository(), Svilajnac)

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                HluNavHost(
                    settingsViewModel = settingsViewModel,
                    weatherViewModel = weatherViewModel,
                    routeViewModelFactory = routeFactory,
                    weatherMapContent = { _, _, _, _, _ -> Box(Modifier.fillMaxSize()) },
                    locationPickerMapContent = { _, _, _ -> Box(Modifier.fillMaxSize()) }
                )
            }
        }

        composeRule.onNodeWithText("Map").performClick()
        composeRule.onNodeWithTag("weather_route_open").performClick()
        composeRule.waitForIdle()
        assertEquals(0, routeFactory.createCalls)

        settingsRepository.snapshot.complete(PersistedSettings(placeSearchProvider = PlaceSearchProvider.OPEN_METEO))
        composeRule.waitForIdle()

        assertEquals(1, routeFactory.createCalls)
        assertEquals(PlaceSearchProvider.OPEN_METEO, routeViewModel.state.value.searchProvider)
    }

    @Test
    fun main_navigation_switches_four_destinations_and_back_does_not_repeat_entries() {
        val weatherViewModel = WeatherViewModel(MockWeatherRepository(), Svilajnac)
        val routeFactory = RecordingRouteFactory(delayedRouteViewModel(PlaceSearchProvider.PHOTON))
        renderMainNavigation(weatherViewModel, routeFactory = routeFactory)

        assertMainDestination(MainDestination.HOURLY)
        composeRule.onNodeWithText("Daily").performClick()
        assertMainDestination(MainDestination.DAILY)
        val firstDay = weatherViewModel.state.value.forecast!!.daily.first().date.dayText()
        composeRule.onNodeWithText(firstDay).performClick()
        assertMainDestination(MainDestination.HOURLY)
        composeRule.onNodeWithText("Map").performClick()
        assertMainDestination(MainDestination.MAP)

        composeRule.onNodeWithTag("weather_route_open").performClick()
        assertMainDestination(MainDestination.ROUTE)
        composeRule.runOnIdle {
            mainNavigation.select(MainDestination.ROUTE)
            mainNavigation.select(MainDestination.ROUTE)
            assertEquals(WEATHER, mainNavigation.navController.previousBackStackEntry?.destination?.route)
            mainNavigation.openSettings()
            mainNavigation.openSettings()
            mainNavigation.select(MainDestination.DAILY) // secondary screens do not accept tab selections
            assertEquals(WEATHER_ROUTE, mainNavigation.navController.previousBackStackEntry?.destination?.route)
        }
        assertMainDestination(null)
        pressBack()
        assertMainDestination(MainDestination.ROUTE)
        composeRule.onNodeWithTag("weather_route_screen").assertIsDisplayed()
        pressBack()
        assertMainDestination(MainDestination.MAP)
        pressBack()
        assertMainDestination(MainDestination.HOURLY)
        composeRule.runOnIdle {
            mainNavigation.select(MainDestination.HOURLY)
            assertNull(mainNavigation.navController.previousBackStackEntry)
        }
        assertEquals(1, routeFactory.createCalls)
    }

    @Test
    fun weather_scroll_and_mode_survive_primary_and_secondary_navigation_without_refresh() {
        val repository = MockWeatherRepository()
        val weatherViewModel = WeatherViewModel(repository, Svilajnac)
        renderMainNavigation(weatherViewModel)

        composeRule.onNodeWithTag("weather_scroll").performScrollToIndex(15)
        val hourlyPosition = scrollPosition("weather_scroll")
        val selectedDay = weatherViewModel.state.value.selectedDayIndex
        assertTrue(hourlyPosition > 0f)

        select(MainDestination.DAILY)
        composeRule.onNodeWithTag("daily_list").performScrollToIndex(3)
        val dailyPosition = scrollPosition("daily_list")
        assertTrue(dailyPosition > 0f)
        select(MainDestination.ROUTE)
        select(MainDestination.DAILY)
        assertEquals(dailyPosition, scrollPosition("daily_list"), 0.01f)

        composeRule.runOnIdle { mainNavigation.openSettings() }
        composeRule.onNodeWithTag("settings_scroll").performScrollToIndex(2)
        composeRule.onNodeWithText("Add Location").performClick()
        composeRule.onNodeWithText("Location picker").assertIsDisplayed()
        pressBack()
        composeRule.onNodeWithTag("settings_scroll").assertIsDisplayed()
        pressBack()
        assertMainDestination(MainDestination.DAILY)
        assertEquals(dailyPosition, scrollPosition("daily_list"), 0.01f)

        select(MainDestination.HOURLY)
        assertEquals(hourlyPosition, scrollPosition("weather_scroll"), 0.01f)
        assertEquals(selectedDay, weatherViewModel.state.value.selectedDayIndex)
        composeRule.runOnIdle { mainNavigation.openSettings() }
        pressBack()
        assertMainDestination(MainDestination.HOURLY)
        assertEquals(hourlyPosition, scrollPosition("weather_scroll"), 0.01f)
        assertEquals(1, repository.requests.size)
    }

    @Test
    fun route_owner_and_inputs_survive_back_and_settings_with_updated_search_provider() {
        val settingsRepository = MutableSettingsRepository()
        val settingsViewModel = SettingsViewModel(settingsRepository, EmptyLocationRepository())
        val routeViewModel = delayedRouteViewModel(PlaceSearchProvider.PHOTON)
        val routeFactory = RecordingRouteFactory(routeViewModel)
        renderMainNavigation(
            WeatherViewModel(MockWeatherRepository(), Svilajnac),
            settingsViewModel = settingsViewModel,
            routeFactory = routeFactory
        )

        select(MainDestination.MAP)
        select(MainDestination.ROUTE)
        val endpoint = RouteEndpoint("Kept start", GeoPoint(44.0, 21.0))
        composeRule.runOnIdle { routeViewModel.selectMapEndpoint(RouteEndpointSlot.START, endpoint) }
        composeRule.onNodeWithText("Kept start").assertIsDisplayed()
        composeRule.onNodeWithTag("weather_route_content").performScrollToIndex(2)
        val routePosition = scrollPosition("weather_route_content")
        assertTrue(routePosition > 0f)

        composeRule.runOnIdle { mainNavigation.openSettings() }
        composeRule.onNodeWithTag("settings_scroll").performScrollToIndex(8)
        composeRule.onNodeWithTag("settings_place_search_open_meteo").performClick()
        pressBack()
        assertMainDestination(MainDestination.ROUTE)
        assertEquals(routePosition, scrollPosition("weather_route_content"), 0.01f)
        assertEquals(PlaceSearchProvider.OPEN_METEO, routeViewModel.state.value.searchProvider)

        composeRule.onNodeWithTag("weather_route_back").performClick()
        assertMainDestination(MainDestination.MAP)
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithTag("settings_scroll").performScrollToIndex(8)
        composeRule.onNodeWithTag("settings_place_search_photon").performClick()
        pressBack()
        assertMainDestination(MainDestination.MAP)
        select(MainDestination.ROUTE)
        assertEquals(routePosition, scrollPosition("weather_route_content"), 0.01f)
        composeRule.onNodeWithTag("weather_route_content").performScrollToIndex(0)
        composeRule.onNodeWithText("Kept start").assertIsDisplayed()
        assertEquals(endpoint, routeViewModel.state.value.start)
        assertEquals(PlaceSearchProvider.PHOTON, routeViewModel.state.value.searchProvider)
        assertEquals(1, routeFactory.createCalls)
    }

    @Test
    fun default_weather_owner_is_the_original_weather_navigation_entry() {
        val settingsViewModel = SettingsViewModel(MutableSettingsRepository(), EmptyLocationRepository())
        val routeFactory = RecordingRouteFactory(delayedRouteViewModel(PlaceSearchProvider.PHOTON))
        fun render() {
            composeRule.setContent {
                HluWeatherTheme(darkTheme = false) {
                    val navigation = rememberMainNavigation()
                    SideEffect { mainNavigation = navigation }
                    HluNavHost(
                        settingsViewModel = settingsViewModel,
                        mainNavigation = navigation,
                        routeViewModelFactory = routeFactory,
                        weatherMapContent = { _, _, _, _, _ -> Box(Modifier.fillMaxSize()) }
                    )
                }
            }
            composeRule.waitForIdle()
        }
        render()
        lateinit var weatherViewModel: WeatherViewModel
        composeRule.runOnIdle {
            val weatherOwner = mainNavigation.navController.getBackStackEntry(WEATHER)
            weatherViewModel = ViewModelProvider(weatherOwner, WeatherViewModel.Factory)
                .get(WeatherViewModel::class.java)
            assertSame(weatherViewModel, mainNavigation.weatherViewModel())
            mainNavigation.select(MainDestination.MAP)
            mainNavigation.select(MainDestination.ROUTE)
            mainNavigation.select(MainDestination.DAILY)
            assertSame(weatherViewModel, mainNavigation.weatherViewModel())
        }
        composeRule.runOnIdle { composeRule.activity.recreate() }
        render()
        composeRule.runOnIdle {
            assertSame(weatherViewModel, mainNavigation.weatherViewModel())
            assertEquals(ForecastMode.DAILY, weatherViewModel.state.value.forecastMode)
        }
    }

    @Test
    fun configuration_recreation_restores_secondary_screen_and_saved_route() {
        val repository = MockWeatherRepository()
        val weatherViewModel = WeatherViewModel(repository, Svilajnac)
        val settingsViewModel = SettingsViewModel(MutableSettingsRepository(), EmptyLocationRepository())
        val routeViewModel = delayedRouteViewModel(PlaceSearchProvider.PHOTON)
        val routeFactory = RecordingRouteFactory(routeViewModel)
        renderMainNavigation(weatherViewModel, settingsViewModel, routeFactory)

        select(MainDestination.MAP)
        select(MainDestination.ROUTE)
        val endpoint = RouteEndpoint("Restored start", GeoPoint(44.0, 21.0))
        composeRule.runOnIdle { routeViewModel.selectMapEndpoint(RouteEndpointSlot.START, endpoint) }
        pressBack()
        composeRule.runOnIdle { mainNavigation.openSettings() }
        assertMainDestination(null)

        composeRule.runOnIdle { composeRule.activity.recreate() }
        renderMainNavigation(weatherViewModel, settingsViewModel, routeFactory)
        composeRule.onNodeWithTag("settings_scroll").assertIsDisplayed()
        pressBack()
        assertMainDestination(MainDestination.MAP)
        select(MainDestination.ROUTE)
        composeRule.onNodeWithText("Restored start").assertIsDisplayed()
        assertEquals(endpoint, routeViewModel.state.value.start)
        assertEquals(1, routeFactory.createCalls)
        assertEquals(1, repository.requests.size)
    }

    private lateinit var mainNavigation: MainNavigation
    private var currentMainDestination: MainDestination? = null

    private fun renderMainNavigation(
        weatherViewModel: WeatherViewModel,
        settingsViewModel: SettingsViewModel = SettingsViewModel(
            MutableSettingsRepository(), EmptyLocationRepository()
        ),
        routeFactory: ViewModelProvider.Factory = RecordingRouteFactory(
            delayedRouteViewModel(PlaceSearchProvider.PHOTON)
        )
    ) {
        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                val navigation = rememberMainNavigation(weatherViewModel)
                val destination = navigation.currentDestination()
                SideEffect {
                    mainNavigation = navigation
                    currentMainDestination = destination
                }
                HluNavHost(
                    settingsViewModel = settingsViewModel,
                    weatherViewModel = weatherViewModel,
                    mainNavigation = navigation,
                    routeViewModelFactory = routeFactory,
                    weatherMapContent = { _, _, _, _, _ -> Box(Modifier.fillMaxSize()) },
                    locationPickerMapContent = { _, _, _ -> Box(Modifier.fillMaxSize()) }
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun select(destination: MainDestination) {
        composeRule.runOnIdle { mainNavigation.select(destination) }
        assertMainDestination(destination)
    }

    private fun assertMainDestination(destination: MainDestination?) {
        composeRule.runOnIdle { assertEquals(destination, currentMainDestination) }
    }

    private fun pressBack() {
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    private fun scrollPosition(tag: String): Float = composeRule.onNodeWithTag(tag)
        .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    private class MutableSettingsRepository : SettingsRepository {
        override val settings = MutableStateFlow(PersistedSettings())
        override suspend fun save(settings: PersistedSettings) {
            this.settings.value = settings
        }
    }

    private fun delayedRouteViewModel(provider: PlaceSearchProvider) = WeatherRouteViewModel(
        routingSource = object : RoutingSource {
            override val providerName = "OSRM"
            override suspend fun route(start: GeoPoint, end: GeoPoint) =
                DrivingRoute("OSRM", listOf(start, end), 80_000.0, 3_600.0)
        },
        placeSearchSources = listOf(object : PlaceSearchSource {
            override val provider = provider
            override suspend fun search(query: String) = emptyList<net.droopia.hluweather.data.repository.PlaceSearchResult>()
        }),
        placeSearchProvider = provider,
        routeWeatherSource = object : RouteWeatherSource {
            override suspend fun fetchSnapshot(points: List<GeoPoint>) = RouteWeatherSnapshot(
                points = points,
                hourlyByPoint = points.map { emptyList() },
                fetchedAt = Instant.parse("2026-09-12T09:15:00Z")
            )
        },
        locationRepository = EmptyLocationRepository(),
        deviceLocationSource = object : DeviceLocationSource {
            override suspend fun currentLocation() = GpsResult.Unavailable
        },
        now = { Instant.parse("2026-09-12T09:15:00Z") }
    )

    private class RecordingRouteFactory(
        private val viewModel: WeatherRouteViewModel
    ) : ViewModelProvider.Factory {
        var createCalls = 0

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            createCalls++
            return viewModel as T
        }
    }

    private class DelayedSettingsRepository : SettingsRepository {
        val snapshot = CompletableDeferred<PersistedSettings>()

        override val settings: Flow<PersistedSettings> = flow {
            emit(snapshot.await())
        }

        override suspend fun save(settings: PersistedSettings) = Unit
    }

    private class EmptyLocationRepository : LocationRepository {
        override val locations: Flow<List<net.droopia.hluweather.data.model.WeatherLocation>> =
            MutableStateFlow(emptyList())
        override val activeLocation: StateFlow<net.droopia.hluweather.data.model.ActiveLocation?> =
            MutableStateFlow(null)
        override val locationMode: StateFlow<net.droopia.hluweather.data.model.LocationMode> =
            MutableStateFlow(net.droopia.hluweather.data.model.LocationMode.SAVED_LOCATION)
        override suspend fun add(location: net.droopia.hluweather.data.model.WeatherLocation) = Unit
        override suspend fun update(location: net.droopia.hluweather.data.model.WeatherLocation) = Unit
        override suspend fun delete(id: String) = Unit
        override suspend fun selectSaved(id: String) = Unit
        override suspend fun setTrackMe(enabled: Boolean) = Unit
    }
}
