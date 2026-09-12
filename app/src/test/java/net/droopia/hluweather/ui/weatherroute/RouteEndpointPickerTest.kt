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
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.click
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.geometry.Offset
import net.droopia.hluweather.ComposeTestActivity
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.RouteEndpoint
import net.droopia.hluweather.data.model.WeatherLocation
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
    fun search_and_saved_location_actions_update_the_picker_without_provider_controls() {
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
        composeRule.onNodeWithTag("route_start_saved_locations").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("route_saved_location_belgrade").assertIsDisplayed()

        assertTrue(
            composeRule.onAllNodesWithTag("route_search_provider_open_meteo")
                .fetchSemanticsNodes().isEmpty()
        )
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
    fun dropdown_is_capped_at_five_results_and_endpoint_block_has_no_vertical_scroll() {
        val results = (1..6).map { index ->
            PlaceSearchResult("Result $index", GeoPoint(44.0 + index, 21.0 + index))
        }
        val state = mutableStateOf(
            WeatherRouteUiState(
                activeSearchSlot = RouteEndpointSlot.START,
                searchResults = results
            )
        )

        render(state = state, slot = RouteEndpointSlot.START)

        composeRule.onAllNodesWithText("Result", substring = true).assertCountEquals(5)
        assertTrue(
            composeRule.onNodeWithTag("route_start_endpoint")
                .fetchSemanticsNode()
                .config
                .contains(SemanticsActions.ScrollBy)
                .not()
        )
    }

    @Test
    fun map_confirmation_returns_the_camera_idle_point_as_an_endpoint() {
        var selectedEndpoint: RouteEndpoint? = null

        render(
            slot = RouteEndpointSlot.END,
            onMapEndpointSelected = { _, endpoint -> selectedEndpoint = endpoint }
        )

        composeRule.onNodeWithTag("route_end_map_picker").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("route_map_picker_map").assertIsDisplayed()
        assertTrue(
            composeRule.onNodeWithTag("route_map_picker_overlay")
                .fetchSemanticsNode().boundsInRoot.height > 440f
        )
        composeRule.onNodeWithTag("route_map_picker_confirm").performClick()
        composeRule.waitForIdle()

        assertEquals(RouteEndpoint("Selected map point", GeoPoint(46.05, 14.51)), selectedEndpoint)
    }

    @Test
    fun saved_location_scrim_blocks_underlying_actions_and_cancel_dismisses_overlay() {
        var currentLocationClicks = 0

        render(
            onCurrentLocationSelected = { currentLocationClicks++ }
        )

        composeRule.onNodeWithTag("route_start_saved_locations").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("route_saved_locations_scrim").assertIsDisplayed()

        val scrim = composeRule.onNodeWithTag("route_saved_locations_scrim")
        val scrimBounds = scrim.fetchSemanticsNode().boundsInRoot
        assertTrue(scrimBounds.height > 440f)
        scrim.performTouchInput {
            click(Offset(1f, 1f))
        }
        assertEquals(0, currentLocationClicks)

        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()
        assertTrue(
            composeRule.onAllNodesWithTag("route_saved_locations_scrim")
                .fetchSemanticsNodes().isEmpty()
        )
        composeRule.onNodeWithTag("route_start_current_location").performClick()
        assertEquals(1, currentLocationClicks)
    }

    private fun render(
        state: MutableState<WeatherRouteUiState> = mutableStateOf(WeatherRouteUiState()),
        savedLocations: List<WeatherLocation> = listOf(savedBelgrade),
        slot: RouteEndpointSlot = RouteEndpointSlot.START,
        onSearchQueryChanged: (RouteEndpointSlot, String) -> Unit = { _, _ -> },
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
                    slot = slot,
                    onSearchQueryChanged = { slot, query ->
                        onSearchQueryChanged(slot, query)
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
