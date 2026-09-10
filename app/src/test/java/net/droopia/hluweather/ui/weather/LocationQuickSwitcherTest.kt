package net.droopia.hluweather.ui.weather

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import net.droopia.hluweather.ComposeTestActivity
import net.droopia.hluweather.data.model.WeatherLocation
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
class LocationQuickSwitcherTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeTestActivity>()

    @Test
    fun selecting_saved_location_reports_it_and_keeps_setup_actions_visible() {
        var selectedLocation: WeatherLocation? = null
        var addClicked = false
        var manageClicked = false
        renderSwitcher(
            onLocationSelected = { selectedLocation = it },
            onAddLocationClick = { addClicked = true },
            onManageLocationsClick = { manageClicked = true }
        )

        composeRule.onNodeWithText("Svilajnac").performClick()
        composeRule.onNodeWithText("Add location").assertIsDisplayed()
        composeRule.onNodeWithText("Belgrade").performClick()
        composeRule.onNodeWithText("Add location").performClick()
        composeRule.onNodeWithText("Manage locations").performClick()

        assertEquals("belgrade", selectedLocation?.id)
        assertTrue(addClicked)
        assertTrue(manageClicked)
    }

    @Test
    fun track_me_reports_a_callback_without_owning_location_permission() {
        var trackMeClicked = false
        renderSwitcher(onTrackMeClick = { trackMeClicked = true })

        composeRule.onNodeWithText("Track Me").performClick()

        assertTrue(trackMeClicked)
    }

    @Test
    fun location_rows_expose_merged_radio_actions() {
        renderSwitcher()

        composeRule.onNodeWithText("Svilajnac")
            .assertHasClickAction()
            .assertContentDescriptionEquals("Select location, Svilajnac")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        composeRule.onNodeWithText("Track Me")
            .assertHasClickAction()
            .assertContentDescriptionEquals("Select location, Track Me")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
    }

    private fun renderSwitcher(
        onLocationSelected: (WeatherLocation) -> Unit = {},
        onTrackMeClick: () -> Unit = {},
        onAddLocationClick: () -> Unit = {},
        onManageLocationsClick: () -> Unit = {}
    ) {
        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                LocationQuickSwitcher(
                    locations = testLocations,
                    selectedLocationId = "svilajnac",
                    trackMeSelected = false,
                    onLocationSelected = onLocationSelected,
                    onTrackMeClick = onTrackMeClick,
                    onAddLocationClick = onAddLocationClick,
                    onManageLocationsClick = onManageLocationsClick
                )
            }
        }
    }

    private companion object {
        val testLocations = listOf(
            WeatherLocation("svilajnac", "Svilajnac", 44.2380, 21.1970),
            WeatherLocation("belgrade", "Belgrade", 44.8176, 20.4633)
        )
    }
}
