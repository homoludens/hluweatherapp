package net.droopia.hluweather.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
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
import kotlinx.datetime.Instant
import net.droopia.hluweather.data.device.DeviceLocationSource
import net.droopia.hluweather.data.model.DrivingRoute
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.junit.Assert.assertEquals

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
