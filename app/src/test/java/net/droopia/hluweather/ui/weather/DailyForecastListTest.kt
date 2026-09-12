package net.droopia.hluweather.ui.weather

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import java.util.TimeZone
import kotlinx.datetime.Instant
import net.droopia.hluweather.data.repository.Svilajnac
import net.droopia.hluweather.data.repository.buildMockForecast
import net.droopia.hluweather.ComposeTestActivity
import net.droopia.hluweather.ui.settings.PrecipitationUnit
import net.droopia.hluweather.ui.settings.TemperatureUnit
import net.droopia.hluweather.ui.theme.HluWeatherTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DailyForecastListTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeTestActivity>()

    @Before
    fun setDefaultTimeZoneToUtc() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @Test
    fun renders_daily_rows() {
        val forecast = buildMockForecast(
            location = Svilajnac,
            baseTime = Instant.fromEpochSeconds(0L)
        )

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                DailyForecastList(
                    forecast = forecast,
                    onDaySelected = {}
                )
            }
        }

        composeRule.onNodeWithTag("daily_list").assertIsDisplayed()
        composeRule.onAllNodesWithText("Clear sky").onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithText("16°C – 30°C").onFirst().assertIsDisplayed()
    }

    @Test
    fun renders_daily_values_in_selected_units() {
        val forecast = buildMockForecast(
            location = Svilajnac,
            baseTime = Instant.fromEpochSeconds(0L)
        )

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                DailyForecastList(
                    forecast = forecast,
                    onDaySelected = {},
                    temperatureUnit = TemperatureUnit.FAHRENHEIT,
                    precipitationUnit = PrecipitationUnit.INCH
                )
            }
        }

        composeRule.onAllNodesWithText("61°F – 86°F").onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithText("0 in").onFirst().assertIsDisplayed()
    }

    @Test
    fun bold_daily_temperatures_use_light_foreground_in_dark_theme() {
        val forecast = buildMockForecast(
            location = Svilajnac,
            baseTime = Instant.fromEpochSeconds(0L)
        )

        composeRule.setContent {
            HluWeatherTheme(darkTheme = true) {
                CompositionLocalProvider(LocalContentColor provides Color.Black) {
                    DailyForecastList(
                        forecast = forecast,
                        onDaySelected = {}
                    )
                }
            }
        }

        assertTrue(
            composeRule.onAllNodesWithText("16°C – 30°C")
                .onFirst()
                .captureToImage()
                .hasLightForeground()
        )
    }

    private fun ImageBitmap.hasLightForeground(): Boolean {
        val pixels = IntArray(width * height)
        readPixels(pixels)
        return pixels.any {
            val color = Color(it)
            color.red > 0.6f && color.green > 0.6f && color.blue > 0.6f
        }
    }
}
