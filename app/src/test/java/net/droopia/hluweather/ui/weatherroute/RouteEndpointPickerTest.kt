package net.droopia.hluweather.ui.weatherroute

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import net.droopia.hluweather.ComposeTestActivity
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.RouteEndpoint
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.repository.PlaceSearchProvider
import net.droopia.hluweather.data.repository.PlaceSearchResult
import net.droopia.hluweather.ui.theme.HluWeatherTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RouteEndpointPickerTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeTestActivity>()

    private val triesteResult = PlaceSearchResult(
        "Trieste, Friuli Venezia Giulia, Italy",
        GeoPoint(45.6495, 13.7768)
    )
    private val savedBelgrade = WeatherLocation("belgrade", "Belgrade", 44.8176, 20.4633)

    @Test
    fun search_provider_and_saved_location_actions_update_the_picker() {
        var selectedResult: PlaceSearchResult? = null
        val state = mutableStateOf(
            WeatherRouteUiState(
                activeSearchSlot = RouteEndpointSlot.START,
                searchResults = listOf(triesteResult)
            )
        )

        render(
            state = state,
            onSearchQueryChanged = { slot, query ->
                state.value = state.value.copy(activeSearchSlot = slot, searchQuery = query)
            },
            onSearchProviderChanged = { provider ->
                state.value = state.value.copy(searchProvider = provider)
            },
            onSearchResultSelected = { result ->
                selectedResult = result
                state.value = state.value.copy(
                    start = RouteEndpoint(result.label, result.point),
                    activeSearchSlot = null,
                    searchQuery = "",
                    searchResults = emptyList()
                )
            }
        )

        composeRule.onNodeWithTag("route_start_search").performTextInput("Trieste")
        composeRule.waitForIdle()
        composeRule.onNodeWithText(triesteResult.label).performClick()
        composeRule.waitForIdle()
        assertEquals(triesteResult, selectedResult)
        composeRule.onNodeWithTag("route_start_label")
            .assertTextContains("Trieste", substring = true)
        composeRule.onNodeWithTag("route_search_provider_open_meteo").performClick()
        composeRule.waitForIdle()
        assertEquals(PlaceSearchProvider.OPEN_METEO, state.value.searchProvider)
        composeRule.onNodeWithTag("route_end_saved_locations").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("route_saved_location_belgrade").assertIsDisplayed()

        assertEquals(PlaceSearchProvider.OPEN_METEO, state.value.searchProvider)
    }

    @Test
    fun unfinished_search_does_not_replace_endpoint_and_destination_has_no_current_location_action() {
        val state = mutableStateOf(
            WeatherRouteUiState(
                start = RouteEndpoint(triesteResult.label, triesteResult.point),
                activeSearchSlot = RouteEndpointSlot.START
            )
        )

        render(
            state = state,
            onSearchQueryChanged = { slot, query ->
                state.value = state.value.copy(activeSearchSlot = slot, searchQuery = query)
            }
        )

        composeRule.onNodeWithTag("route_start_search").performTextInput("unfinished")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("route_start_label")
            .assertTextContains("Trieste", substring = true)
        composeRule.onNodeWithText("Use my location").assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithTag("route_end_current_location")
                .fetchSemanticsNodes().isEmpty()
        )
    }

    @Test
    fun map_confirmation_returns_the_camera_idle_point_as_an_endpoint() {
        var selectedEndpoint: RouteEndpoint? = null

        render(
            onMapEndpointSelected = { _, endpoint -> selectedEndpoint = endpoint }
        )

        composeRule.onNodeWithTag("route_end_map_picker").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("route_map_picker_map").assertIsDisplayed()
        composeRule.onNodeWithTag("route_map_picker_confirm").performClick()
        composeRule.waitForIdle()

        assertEquals(RouteEndpoint("Selected map point", GeoPoint(46.05, 14.51)), selectedEndpoint)
    }

    private fun render(
        state: MutableState<WeatherRouteUiState> = mutableStateOf(WeatherRouteUiState()),
        savedLocations: List<WeatherLocation> = listOf(savedBelgrade),
        onSearchQueryChanged: (RouteEndpointSlot, String) -> Unit = { _, _ -> },
        onSearchProviderChanged: (PlaceSearchProvider) -> Unit = {},
        onSearchResultSelected: (PlaceSearchResult) -> Unit = {},
        onSavedLocationSelected: (RouteEndpointSlot, WeatherLocation) -> Unit = { _, _ -> },
        onCurrentLocationSelected: (RouteEndpointSlot) -> Unit = {},
        onMapEndpointSelected: (RouteEndpointSlot, RouteEndpoint) -> Unit = { _, _ -> }
    ) {
        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                RouteEndpointPicker(
                    state = state.value,
                    savedLocations = savedLocations,
                    onSearchQueryChanged = { slot, query ->
                        onSearchQueryChanged(slot, query)
                    },
                    onSearchProviderChanged = { provider ->
                        onSearchProviderChanged(provider)
                    },
                    onSearchResultSelected = { result ->
                        onSearchResultSelected(result)
                    },
                    onSavedLocationSelected = onSavedLocationSelected,
                    onCurrentLocationSelected = onCurrentLocationSelected,
                    onMapEndpointSelected = onMapEndpointSelected,
                    mapContent = { _, _, onCameraIdle ->
                        LaunchedEffect(Unit) {
                            onCameraIdle(GeoPoint(46.05, 14.51))
                        }
                        Box(Modifier.fillMaxSize().testTag("route_map_picker_map"))
                    }
                )
            }
        }
    }
}
