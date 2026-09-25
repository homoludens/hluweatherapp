package net.droopia.hluweather.ui.weather

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.material3.LocalContentColor
import java.util.TimeZone
import java.time.ZoneId
import kotlin.time.Instant
import net.droopia.hluweather.data.hourText
import net.droopia.hluweather.data.repository.Svilajnac
import net.droopia.hluweather.data.repository.buildMockForecast
import net.droopia.hluweather.ComposeTestActivity
import net.droopia.hluweather.data.model.WeatherCondition
import net.droopia.hluweather.data.model.label
import net.droopia.hluweather.data.percentText
import net.droopia.hluweather.data.precipitationText
import net.droopia.hluweather.data.temperatureValueText
import net.droopia.hluweather.ui.settings.PrecipitationUnit
import net.droopia.hluweather.ui.settings.TemperatureUnit
import net.droopia.hluweather.ui.settings.HourlyTableColumn
import net.droopia.hluweather.ui.settings.WindDirectionDisplay
import net.droopia.hluweather.ui.settings.defaultHourlyTableColumns
import net.droopia.hluweather.ui.theme.HluWeatherTheme
import org.junit.Before
import org.junit.Assert.assertFalse
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
class HourlyForecastTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeTestActivity>()

    @Before
    fun setDefaultTimeZoneToUtc() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @Test
    fun renders_hourly_table_header_and_rows() {
        val forecast = buildMockForecast(
            location = Svilajnac,
            baseTime = Instant.fromEpochSeconds(0L)
        )

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                HourlyForecast(
                    forecast = forecast,
                selectedDayIndex = 0,
                    onDaySelected = {},
                    now = forecast.hourly.first().time
                )
            }
        }

        assertTrue(
            composeRule.onAllNodesWithText("Hourly Forecast")
                .fetchSemanticsNodes()
                .isEmpty()
        )
        composeRule.onNodeWithText("Time").assertIsDisplayed()
        composeRule.onNodeWithText("Icon").assertIsDisplayed()
        composeRule.onNodeWithText("Temp.").assertIsDisplayed()
        composeRule.onNodeWithText("Dew point").assertIsDisplayed()
        composeRule.onNodeWithText("Hum.").assertIsDisplayed()
        composeRule.onNodeWithText("Precip.").assertIsDisplayed()
        composeRule.onNodeWithTag("hourly_day_start_0").assertIsDisplayed()
        composeRule.onNodeWithTag("hourly_table").assertIsDisplayed()
    }

    @Test
    fun weather_icon_is_shown_but_weather_text_is_hidden_by_default() {
        val forecast = buildMockForecast(
            location = Svilajnac,
            baseTime = Instant.fromEpochSeconds(0L)
        )
        val firstHour = forecast.hourly.first()

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                HourlyForecast(
                    forecast = forecast,
                    selectedDayIndex = 0,
                    onDaySelected = {},
                    now = firstHour.time
                )
            }
        }

        assertTrue(
            composeRule.onAllNodesWithText(firstHour.condition.label())
                .fetchSemanticsNodes()
                .isEmpty()
        )
        composeRule.onNodeWithText("Icon").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription(firstHour.condition.label()).onFirst().assertIsDisplayed()
    }

    @Test
    fun column_header_remains_sticky_after_scrolling_past_day_strip() {
        val forecast = buildMockForecast(
            location = Svilajnac,
            baseTime = Instant.fromEpochSeconds(0L)
        )

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                HourlyForecast(
                    forecast = forecast,
                    selectedDayIndex = 0,
                    onDaySelected = {},
                    now = forecast.hourly.first().time
                )
            }
        }

        composeRule.onNodeWithTag("hourly_table").performScrollToIndex(20)

        composeRule.onNodeWithTag("hourly_column_header").assertIsDisplayed()
    }

    @Test
    fun uses_forecast_timezone_for_hourly_day_and_time_display() {
        val forecast = buildMockForecast(
            location = Svilajnac,
            baseTime = Instant.parse("2026-09-02T00:00:00Z")
        ).copy(timezone = "America/New_York")

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                HourlyForecast(
                    forecast = forecast,
                    selectedDayIndex = 0,
                    onDaySelected = {},
                    now = forecast.hourly.first().time
                )
            }
        }

        composeRule.onAllNodesWithText("Tue, Sep 1").onFirst().assertIsDisplayed()
        composeRule.onNodeWithText("20h").assertIsDisplayed()
    }

    @Test
    fun renders_hourly_values_in_selected_units() {
        val forecast = buildMockForecast(
            location = Svilajnac,
            baseTime = Instant.fromEpochSeconds(0L)
        )

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                HourlyForecast(
                    forecast = forecast,
                    selectedDayIndex = 0,
                    onDaySelected = {},
                    temperatureUnit = TemperatureUnit.FAHRENHEIT,
                    precipitationUnit = PrecipitationUnit.INCH,
                    now = forecast.hourly.first().time
                )
            }
        }

        composeRule.onAllNodesWithText("63°F").onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithText("0 in").onFirst().assertIsDisplayed()
    }

    @Test
    fun renders_precipitation_probability_as_a_percentage() {
        val forecast = buildMockForecast(
            location = Svilajnac,
            baseTime = Instant.fromEpochSeconds(0L)
        )
        val firstHour = forecast.hourly.first().copy(precipitationProbability = 65)

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                HourlyForecast(
                    forecast = forecast.copy(hourly = listOf(firstHour)),
                    selectedDayIndex = 0,
                    onDaySelected = {},
                    hourlyTableColumns = setOf(HourlyTableColumn.PRECIPITATION_PROBABILITY),
                    now = firstHour.time
                )
            }
        }

        composeRule.onNodeWithText("Precip. %").assertIsDisplayed()
        composeRule.onNodeWithText("65%").assertIsDisplayed()
    }

    @Test
    fun large_font_hourly_condition_wraps_inside_the_weather_column() {
        val hour = buildMockForecast(Svilajnac, Instant.fromEpochSeconds(0L)).hourly.first()
            .copy(windDirectionDegrees = 0.0)
            .copy(condition = WeatherCondition.PARTLY_CLOUDY)

        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                HluWeatherTheme(darkTheme = false) {
                    Box(
                        modifier = androidx.compose.ui.Modifier
                            .width(320.dp)
                            .fillMaxHeight()
                            .testTag("hourly_row_width")
                    ) {
                        ForecastRow(
                            weather = hour,
                            displayZone = java.time.ZoneId.of("UTC"),
                            columns = defaultHourlyTableColumns + HourlyTableColumn.WEATHER_TEXT
                        )
                    }
                }
            }
        }

        val node = composeRule.onNodeWithText("Partly cloudy").fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        assertTrue("Condition must expose a text layout result", node.config.contains(SemanticsActions.GetTextLayoutResult))
        assertTrue(node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results) == true)
        assertFalse("Condition must wrap rather than truncate at 2x font scale", results.single().hasVisualOverflow)
    }

    @Test
    fun unavailable_optional_values_are_shown_as_dash() {
        val hour = buildMockForecast(Svilajnac, Instant.fromEpochSeconds(0L)).hourly.first()
            .copy(
                dewPoint = null,
                humidity = null,
                condition = WeatherCondition.UNKNOWN,
                windSpeedKmh = null,
                windDirectionDegrees = null,
                evapotranspiration = null
            )

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                ForecastRow(
                    weather = hour,
                    displayZone = ZoneId.of("UTC"),
                    columns = setOf(
                        HourlyTableColumn.DEW_POINT,
                        HourlyTableColumn.WEATHER_ICON,
                        HourlyTableColumn.WEATHER_TEXT,
                        HourlyTableColumn.RELATIVE_HUMIDITY,
                        HourlyTableColumn.WIND_SPEED,
                        HourlyTableColumn.WIND_DIRECTION,
                        HourlyTableColumn.EVAPOTRANSPIRATION
                    )
                )
            }
        }

        assertTrue(composeRule.onAllNodesWithText("-").fetchSemanticsNodes().size >= 7)
    }

    @Test
    fun air_quality_columns_show_headers_and_values() {
        val forecast = buildMockForecast(Svilajnac, Instant.fromEpochSeconds(0L))
        val firstHour = forecast.hourly.first().copy(
            europeanAqi = 42.4,
            pm2_5 = 12.5,
            pm10 = 18.75
        )

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                HourlyForecast(
                    forecast = forecast.copy(hourly = listOf(firstHour)),
                    selectedDayIndex = 0,
                    onDaySelected = {},
                    hourlyTableColumns = setOf(
                        HourlyTableColumn.EUROPEAN_AQI,
                        HourlyTableColumn.PM2_5,
                        HourlyTableColumn.PM10
                    ),
                    now = firstHour.time
                )
            }
        }

        composeRule.onNodeWithText("AQI").assertIsDisplayed()
        composeRule.onNodeWithText("PM2.5").assertIsDisplayed()
        composeRule.onNodeWithText("PM10").assertIsDisplayed()
        composeRule.onNodeWithText("42").assertIsDisplayed()
        composeRule.onNodeWithText("12.5").assertIsDisplayed()
        composeRule.onNodeWithText("18.75").assertIsDisplayed()
    }

    @Test
    fun null_air_quality_columns_show_dash_cells() {
        val hour = buildMockForecast(Svilajnac, Instant.fromEpochSeconds(0L)).hourly.first()

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                ForecastRow(
                    weather = hour,
                    displayZone = ZoneId.of("UTC"),
                    columns = setOf(
                        HourlyTableColumn.EUROPEAN_AQI,
                        HourlyTableColumn.PM2_5,
                        HourlyTableColumn.PM10
                    )
                )
            }
        }

        assertTrue(composeRule.onAllNodesWithText("-").fetchSemanticsNodes().size >= 3)
    }

    @Test
    fun wind_direction_uses_the_selected_display_mode() {
        val hour = buildMockForecast(Svilajnac, Instant.fromEpochSeconds(0L)).hourly.first()
            .copy(windDirectionDegrees = 337.4)

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                ForecastRow(
                    weather = hour,
                    displayZone = ZoneId.of("UTC"),
                    windDirectionDisplay = WindDirectionDisplay.EIGHT_POINT,
                    columns = setOf(HourlyTableColumn.WIND_DIRECTION)
                )
            }
        }

        composeRule.onNodeWithText("NW").assertIsDisplayed()
    }

    @Test
    fun arrow_wind_direction_is_rendered_as_an_icon() {
        val hour = buildMockForecast(Svilajnac, Instant.fromEpochSeconds(0L)).hourly.first()
            .copy(windDirectionDegrees = 0.0)

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                ForecastRow(
                    weather = hour,
                    displayZone = ZoneId.of("UTC"),
                    windDirectionDisplay = WindDirectionDisplay.ARROW,
                    columns = setOf(HourlyTableColumn.WIND_DIRECTION)
                )
            }
        }

        composeRule.onNodeWithTag("hourly_wind_direction_arrow").assertIsDisplayed()
    }

    @Test
    fun dark_theme_hourly_body_values_use_readable_foreground_colors() {
        val forecast = buildMockForecast(
            location = Svilajnac,
            baseTime = Instant.fromEpochSeconds(0L)
        )
        val firstHour = forecast.hourly.first()

        composeRule.setContent {
            HluWeatherTheme(darkTheme = true) {
                CompositionLocalProvider(LocalContentColor provides Color.Magenta) {
                    HourlyForecast(
                        forecast = forecast,
                        selectedDayIndex = 0,
                        onDaySelected = {},
                        now = forecast.hourly.first().time,
                        hourlyTableColumns = defaultHourlyTableColumns + HourlyTableColumn.WEATHER_TEXT
                    )
                }
            }
        }

        val bodyTexts = listOf(
            firstHour.time.hourText(ZoneId.of(forecast.timezone)),
            firstHour.condition.label(),
            firstHour.temperature.temperatureValueText(TemperatureUnit.CELSIUS),
            firstHour.dewPoint.temperatureValueText(TemperatureUnit.CELSIUS),
            firstHour.humidity.percentText(),
            firstHour.precipitation.precipitationText(PrecipitationUnit.MM)
        )

        bodyTexts.forEach { text ->
            val image = composeRule.onAllNodesWithText(text).onFirst().captureToImage()
            assertTrue("$text should have a readable dark-theme foreground", image.hasReadableForeground())
            assertFalse("$text should not inherit the magenta test content color", image.containsColor(Color.Magenta))
        }
    }

    private fun ImageBitmap.containsColor(expected: Color): Boolean {
        val pixels = IntArray(width * height)
        readPixels(pixels)
        return pixels.any { Color(it) == expected }
    }

    private fun ImageBitmap.hasReadableForeground(): Boolean {
        val pixels = IntArray(width * height)
        readPixels(pixels)
        return pixels.any {
            val color = Color(it)
            color.red > 0.6f && color.green > 0.6f && color.blue > 0.6f
        }
    }
}
