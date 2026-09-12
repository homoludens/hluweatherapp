package net.droopia.hluweather.ui.accessibility

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
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
import androidx.compose.ui.test.getUnclippedBoundsInRoot
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
import org.junit.Assert.assertTrue

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
                    onPlaceSearchProviderChange = {},
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
                    onPlaceSearchProviderChange = {},
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
    fun unit_selector_options_meet_touch_target() {
        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                SettingsScreen(
                    state = SettingsUiState(),
                    onBackClick = {},
                    onProviderChange = {},
                    onProviderInfoClick = {},
                    onPlaceSearchProviderChange = {},
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

        composeRule.onNodeWithTag("settings_scroll").performScrollToIndex(4)
        listOf(
            "settings_temperature_celsius",
            "settings_temperature_fahrenheit",
            "settings_wind_kmh",
            "settings_wind_mph",
            "settings_distance_km",
            "settings_distance_miles",
            "settings_precipitation_mm",
            "settings_precipitation_inch"
        ).forEach { tag ->
            composeRule.onNodeWithTag(tag)
                .assertHasClickAction()
                .assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun large_font_weather_hero_stays_inside_constrained_width() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.5f)) {
                HluWeatherTheme(darkTheme = false) {
                    Box(
                        modifier = androidx.compose.ui.Modifier
                            .width(320.dp)
                            .fillMaxHeight()
                            .testTag("hero_width")
                    ) {
                        WeatherHero(
                            selected = ForecastMode.HOURLY,
                            onSelected = {},
                            onSettingsClick = {}
                        )
                    }
                }
            }
        }

        val container = composeRule.onNodeWithTag("hero_width")
            .getUnclippedBoundsInRoot()
        listOf("HluWeatherApp", "Simple weather. Clear view.", "Hourly", "Daily", "Map")
            .forEach { text ->
                composeRule.onNodeWithText(text).assertIsDisplayed()
                val bounds = composeRule.onNodeWithText(text).getUnclippedBoundsInRoot()
                assertTrue("$text must fit within the constrained hero", bounds.left >= container.left)
                assertTrue("$text must fit within the constrained hero", bounds.right <= container.right)
                assertTrue("$text must fit within the constrained hero", bounds.top >= container.top)
                assertTrue("$text must fit within the constrained hero", bounds.bottom <= container.bottom)
            }
    }

    @Test
    fun large_font_settings_content_wraps_inside_constrained_width() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.5f)) {
                HluWeatherTheme(darkTheme = false) {
                    Box(
                        modifier = androidx.compose.ui.Modifier
                            .width(320.dp)
                            .fillMaxHeight()
                    ) {
                        SettingsScreen(
                            state = SettingsUiState(weatherAlerts = true),
                            onBackClick = {},
                            onProviderChange = {},
                            onProviderInfoClick = {},
                            onPlaceSearchProviderChange = {},
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
        }

        val settings = composeRule.onNodeWithTag("settings_scroll")
        settings.performScrollToIndex(4)
        assertBoundsWithin(settings, "Precipitation")
        assertBoundsWithin(settings, "in")
        settings.performScrollToIndex(5)
        assertBoundsWithin(settings, "Best effort; delivery may be delayed by Android.")
        assertBoundsWithin(settings, "Thunderstorm alerts only")
    }

    @Test
    fun large_font_scale_is_capped_and_settings_text_wraps_without_ellipsis() {
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
                            onPlaceSearchProviderChange = {},
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

        assertEquals(1f, capturedFontScale, 0f)
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_scroll").performScrollToIndex(5)
        composeRule.onNodeWithText("Thunderstorm alerts only").assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertSwitchRole() =
        assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertButtonRole() =
        assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))

    private fun assertBoundsWithin(
        container: androidx.compose.ui.test.SemanticsNodeInteraction,
        text: String
    ) {
        val containerBounds = container.getUnclippedBoundsInRoot()
        val textNode = composeRule.onNodeWithText(text)
        textNode.assertIsDisplayed()
        val bounds = textNode.getUnclippedBoundsInRoot()
        assertTrue("$text must fit within the constrained settings width", bounds.left >= containerBounds.left)
        assertTrue("$text must fit within the constrained settings width", bounds.right <= containerBounds.right)
        assertTrue("$text must fit within the constrained settings height", bounds.top >= containerBounds.top)
        assertTrue("$text must fit within the constrained settings height", bounds.bottom <= containerBounds.bottom)
    }
}
