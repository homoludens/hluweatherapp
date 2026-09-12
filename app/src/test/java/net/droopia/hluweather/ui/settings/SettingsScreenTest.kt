package net.droopia.hluweather.ui.settings

import android.app.TimePickerDialog
import android.content.DialogInterface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import net.droopia.hluweather.ComposeTestActivity
import net.droopia.hluweather.data.model.ThemeMode
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherProvider
import net.droopia.hluweather.ui.theme.HluWeatherTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeTestActivity>()

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun renders_reference_sections() {
        renderSettings()

        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Customize your weather experience").assertIsDisplayed()
        composeRule.onNodeWithText("Weather Provider").assertIsDisplayed()
        composeRule.onNodeWithText("Locations").assertIsDisplayed()
        composeRule.onNodeWithText("Appearance").assertIsDisplayed()
        scrollTo(4)
        composeRule.onNodeWithText("Units").assertIsDisplayed()
        scrollTo(5)
        composeRule.onNodeWithText("Notifications").assertIsDisplayed()
        scrollTo(6)
        composeRule.onNodeWithText("Data & Cache").assertIsDisplayed()
    }

    @Test
    fun settings_title_uses_light_foreground_in_dark_theme() {
        renderSettings(darkTheme = true, inheritedContentColor = Color.Black)

        assertTrue(
            composeRule.onNodeWithText("Settings")
                .captureToImage()
                .hasLightForeground()
        )
    }

    @Test
    fun notifications_show_best_effort_summary_time_without_trip_alerts() {
        renderSettings()

        scrollTo(5)
        composeRule.onNodeWithText("Daily summary time").assertIsDisplayed()
        composeRule.onNodeWithText("08:00").assertIsDisplayed()
        composeRule.onNodeWithText("Best effort; delivery may be delayed by Android.").assertIsDisplayed()
        composeRule.onNodeWithText("Thunderstorm alerts only").assertIsDisplayed()
        composeRule.onNodeWithText("Trip alerts").assertDoesNotExist()
    }

    @Test
    fun clicking_summary_time_row_opens_picker_and_reports_selected_time() {
        var selectedTime: LocalTime? = null
        renderSettings(onDailySummaryTimeChange = { selectedTime = it })

        scrollTo(5)
        composeRule.onNodeWithTag("settings_daily_summary_time").performClick()

        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        check(dialog is TimePickerDialog)
        dialog.updateTime(9, 15)
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()

        assertEquals(LocalTime(9, 15), selectedTime)
    }

    @Test
    fun summary_time_row_exposes_one_merged_action() {
        renderSettings()

        scrollTo(5)
        composeRule.onNodeWithTag("settings_daily_summary_time")
            .assertHasClickAction()
            .assertContentDescriptionEquals("Daily summary time, 08:00")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    @Test
    fun enabled_notifications_show_a_system_settings_recovery_action_when_blocked() {
        var settingsClicks = 0
        renderSettings(
            state = { testSettingsState.copy(weatherAlerts = true) },
            notificationsPermissionGranted = false,
            onOpenNotificationSettings = { settingsClicks++ }
        )

        scrollTo(5)
        composeRule.onNodeWithText("Notifications are blocked").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_notification_permission").performClick()

        assertEquals(1, settingsClicks)
    }

    @Test
    fun provider_and_appearance_controls_report_changes() {
        val state = mutableStateOf(testSettingsState)

        renderSettings(
            state = { state.value },
            onProviderChange = { state.value = state.value.copy(provider = it) },
            onThemeChange = { state.value = state.value.copy(themeMode = it) }
        )

        composeRule.onNodeWithText("MET.no").performClick()
        composeRule.onNodeWithTag("settings_provider_met_no").assertIsSelected()
        composeRule.onNodeWithText("Dark").performClick()
        composeRule.onNodeWithTag("settings_theme_dark").assertIsSelected()

        assertEquals(WeatherProvider.MET_NO, state.value.provider)
        assertEquals(ThemeMode.DARK, state.value.themeMode)
    }

    @Test
    fun provider_information_reports_provider() {
        var provider: WeatherProvider? = null

        renderSettings(onProviderInfoClick = { provider = it })

        composeRule.onNodeWithContentDescription("Information about Open-Meteo").performClick()

        assertEquals(WeatherProvider.OPEN_METEO, provider)
    }

    @Test
    fun selected_controls_expose_tab_semantics() {
        renderSettings(
            state = { testSettingsState.copy(
                provider = WeatherProvider.MET_NO,
                themeMode = ThemeMode.DARK,
                temperatureUnit = TemperatureUnit.FAHRENHEIT,
                windUnit = WindUnit.MPH,
                distanceUnit = DistanceUnit.MILES,
                precipitationUnit = PrecipitationUnit.INCH,
                selectedLocationId = "belgrade"
            ) }
        )

        composeRule.onNodeWithTag("settings_provider_met_no").assertIsSelected()
        composeRule.onNodeWithTag("settings_theme_dark").assertIsSelected()
        composeRule.onNodeWithTag("settings_temperature_fahrenheit").assertIsSelected()
        composeRule.onNodeWithTag("settings_wind_mph").assertIsSelected()
        composeRule.onNodeWithTag("settings_distance_miles").assertIsSelected()
        composeRule.onNodeWithTag("settings_precipitation_inch").assertIsSelected()
        composeRule.onNodeWithTag("settings_location_belgrade").assertIsSelected()
    }

    @Test
    fun track_me_units_notifications_and_actions_report_changes() {
        var trackMe: Boolean? = null
        var temperature: TemperatureUnit? = null
        var wind: WindUnit? = null
        var distance: DistanceUnit? = null
        var precipitation: PrecipitationUnit? = null
        var weatherAlerts: Boolean? = null
        var dailySummary: Boolean? = null
        var selectedLocation: WeatherLocation? = null
        var locationMenu: WeatherLocation? = null
        var addLocationClicks = 0
        var clearCacheClicks = 0

        renderSettings(
            onTrackMeChange = { trackMe = it },
            onLocationSelect = { selectedLocation = it },
            onLocationMenuClick = { locationMenu = it },
            onAddLocationClick = { addLocationClicks++ },
            onTemperatureUnitChange = { temperature = it },
            onWindUnitChange = { wind = it },
            onDistanceUnitChange = { distance = it },
            onPrecipitationUnitChange = { precipitation = it },
            onWeatherAlertsChange = { weatherAlerts = it },
            onDailySummaryChange = { dailySummary = it },
            onClearCacheClick = { clearCacheClicks++ }
        )

        composeRule.onNodeWithTag("settings_track_me").performClick()
        scrollTo(4)
        composeRule.onNodeWithText("°F").performClick()
        composeRule.onNodeWithText("mph").performClick()
        composeRule.onNodeWithText("mi").performClick()
        composeRule.onNodeWithText("in").performClick()

        scrollTo(5)
        composeRule.onNodeWithTag("settings_weather_alerts").performClick()
        composeRule.onNodeWithTag("settings_daily_summary").performClick()

        scrollTo(2)
        composeRule.onNodeWithTag("settings_location_belgrade").performClick()
        composeRule.onNodeWithContentDescription("Location options Belgrade").performClick()
        composeRule.onNodeWithText("Add Location").performClick()
        scrollTo(6)
        composeRule.onNodeWithText("Clear cache").performClick()

        assertEquals(true, trackMe)
        assertEquals(TemperatureUnit.FAHRENHEIT, temperature)
        assertEquals(WindUnit.MPH, wind)
        assertEquals(DistanceUnit.MILES, distance)
        assertEquals(PrecipitationUnit.INCH, precipitation)
        assertEquals(true, weatherAlerts)
        assertEquals(true, dailySummary)
        assertEquals("belgrade", selectedLocation?.id)
        assertEquals("belgrade", locationMenu?.id)
        assertEquals(1, addLocationClicks)
        assertEquals(1, clearCacheClicks)
    }

    @Test
    fun back_button_reports_click() {
        var backClicks = 0
        renderSettings(onBackClick = { backClicks++ })

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertEquals(1, backClicks)
    }

    private fun renderSettings(
        state: () -> SettingsUiState = { testSettingsState },
        onBackClick: () -> Unit = {},
        onProviderChange: (WeatherProvider) -> Unit = {},
        onProviderInfoClick: (WeatherProvider) -> Unit = {},
        onTrackMeChange: (Boolean) -> Unit = {},
        onLocationSelect: (WeatherLocation) -> Unit = {},
        onLocationMenuClick: (WeatherLocation) -> Unit = {},
        onAddLocationClick: () -> Unit = {},
        onThemeChange: (ThemeMode) -> Unit = {},
        onTemperatureUnitChange: (TemperatureUnit) -> Unit = {},
        onWindUnitChange: (WindUnit) -> Unit = {},
        onDistanceUnitChange: (DistanceUnit) -> Unit = {},
        onPrecipitationUnitChange: (PrecipitationUnit) -> Unit = {},
        onWeatherAlertsChange: (Boolean) -> Unit = {},
        onDailySummaryChange: (Boolean) -> Unit = {},
        onDailySummaryTimeChange: (LocalTime) -> Unit = {},
        notificationsPermissionGranted: Boolean = true,
        onOpenNotificationSettings: () -> Unit = {},
        onClearCacheClick: () -> Unit = {},
        darkTheme: Boolean = false,
        inheritedContentColor: Color? = null
    ) {
        composeRule.setContent {
            HluWeatherTheme(darkTheme = darkTheme) {
                val content: @Composable () -> Unit = {
                    SettingsScreen(
                        state = state(),
                        onBackClick = onBackClick,
                        onProviderChange = onProviderChange,
                        onProviderInfoClick = onProviderInfoClick,
                        onTrackMeChange = onTrackMeChange,
                        onLocationSelect = onLocationSelect,
                        onLocationMenuClick = onLocationMenuClick,
                        onAddLocationClick = onAddLocationClick,
                        onThemeChange = onThemeChange,
                        onTemperatureUnitChange = onTemperatureUnitChange,
                        onWindUnitChange = onWindUnitChange,
                        onDistanceUnitChange = onDistanceUnitChange,
                        onPrecipitationUnitChange = onPrecipitationUnitChange,
                        onWeatherAlertsChange = onWeatherAlertsChange,
                        onDailySummaryChange = onDailySummaryChange,
                        onDailySummaryTimeChange = onDailySummaryTimeChange,
                        notificationsPermissionGranted = notificationsPermissionGranted,
                        onOpenNotificationSettings = onOpenNotificationSettings,
                        onClearCacheClick = onClearCacheClick
                    )
                }
                if (inheritedContentColor != null) {
                    CompositionLocalProvider(LocalContentColor provides inheritedContentColor) {
                        content()
                    }
                } else {
                    content()
                }
            }
        }
    }

    private fun ImageBitmap.hasLightForeground(): Boolean {
        val pixels = IntArray(width * height)
        readPixels(pixels)
        return pixels.any {
            val color = Color(it)
            color.red > 0.6f && color.green > 0.6f && color.blue > 0.6f
        }
    }

    private fun scrollTo(index: Int) {
        composeRule.onNodeWithTag("settings_scroll").performScrollToIndex(index)
    }

    companion object {
        private val testSettingsState = SettingsUiState(
            locations = listOf(
                WeatherLocation("svilajnac", "Svilajnac", 44.2380, 21.1970),
                WeatherLocation("belgrade", "Belgrade", 44.8176, 20.4633)
            ),
            selectedLocationId = "svilajnac"
        )
    }
}
