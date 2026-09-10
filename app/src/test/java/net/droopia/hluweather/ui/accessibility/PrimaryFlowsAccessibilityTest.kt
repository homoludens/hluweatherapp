package net.droopia.hluweather.ui.accessibility

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import net.droopia.hluweather.ComposeTestActivity
import net.droopia.hluweather.data.model.ForecastMode
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.ui.settings.SettingsScreen
import net.droopia.hluweather.ui.settings.SettingsUiState
import net.droopia.hluweather.ui.theme.HluWeatherTheme
import net.droopia.hluweather.ui.weather.WeatherHero
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.junit.Assert.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PrimaryFlowsAccessibilityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeTestActivity>()

    @Test
    fun weather_actions_have_labels_selected_state_and_touch_targets() {
        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                WeatherHero(
                    selected = ForecastMode.HOURLY,
                    onSelected = {},
                    onSettingsClick = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("Settings")
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText("Hourly")
            .assertIsSelected()
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText("Map")
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun settings_toggle_rows_merge_label_state_role_and_touch_target() {
        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                SettingsScreen(
                    state = SettingsUiState(
                        locations = listOf(WeatherLocation("svilajnac", "Svilajnac", 44.238, 21.197)),
                        selectedLocationId = "svilajnac"
                    ),
                    onBackClick = {},
                    onProviderChange = {},
                    onProviderInfoClick = {},
                    onTrackMeChange = {},
                    onLocationSelect = {},
                    onLocationMenuClick = {},
                    onAddLocationClick = {},
                    onThemeChange = {},
                    onTemperatureUnitChange = {},
                    onWindUnitChange = {},
                    onDistanceUnitChange = {},
                    onPrecipitationUnitChange = {},
                    onWeatherAlertsChange = {},
                    onDailySummaryChange = {},
                    onDailySummaryTimeChange = {},
                    onClearCacheClick = {}
                )
            }
        }

        composeRule.onNodeWithText("Track me")
            .assertHasClickAction()
            .assertIsOff()
            .assertSwitchRole()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("settings_scroll").performScrollToIndex(5)
        composeRule.onNodeWithText("Weather alerts")
            .assertHasClickAction()
            .assertIsOff()
            .assertSwitchRole()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun settings_action_rows_merge_labels_and_expose_button_role() {
        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                SettingsScreen(
                    state = SettingsUiState(
                        locations = listOf(WeatherLocation("svilajnac", "Svilajnac", 44.238, 21.197)),
                        selectedLocationId = "svilajnac"
                    ),
                    onBackClick = {},
                    onProviderChange = {},
                    onProviderInfoClick = {},
                    onTrackMeChange = {},
                    onLocationSelect = {},
                    onLocationMenuClick = {},
                    onAddLocationClick = {},
                    onThemeChange = {},
                    onTemperatureUnitChange = {},
                    onWindUnitChange = {},
                    onDistanceUnitChange = {},
                    onPrecipitationUnitChange = {},
                    onWeatherAlertsChange = {},
                    onDailySummaryChange = {},
                    onDailySummaryTimeChange = {},
                    onClearCacheClick = {}
                )
            }
        }

        composeRule.onNodeWithText("Add Location")
            .assertHasClickAction()
            .assertButtonRole()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("settings_scroll").performScrollToIndex(6)
        composeRule.onNodeWithText("Clear cache")
            .assertHasClickAction()
            .assertButtonRole()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun large_font_scale_is_preserved_and_settings_text_wraps_without_ellipsis() {
        var capturedFontScale = 0f

        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.5f)) {
                HluWeatherTheme(darkTheme = false) {
                    capturedFontScale = LocalDensity.current.fontScale
                    SettingsScreen(
                        state = SettingsUiState(weatherAlerts = true),
                        onBackClick = {},
                        onProviderChange = {},
                        onProviderInfoClick = {},
                        onTrackMeChange = {},
                        onLocationSelect = {},
                        onLocationMenuClick = {},
                        onAddLocationClick = {},
                        onThemeChange = {},
                        onTemperatureUnitChange = {},
                        onWindUnitChange = {},
                        onDistanceUnitChange = {},
                        onPrecipitationUnitChange = {},
                        onWeatherAlertsChange = {},
                        onDailySummaryChange = {},
                        onDailySummaryTimeChange = {},
                        onClearCacheClick = {}
                    )
                }
            }
        }

        assertEquals(1.5f, capturedFontScale, 0f)
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_scroll").performScrollToIndex(5)
        composeRule.onNodeWithText("Thunderstorm alerts only").assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertSwitchRole() =
        assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertButtonRole() =
        assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
}
