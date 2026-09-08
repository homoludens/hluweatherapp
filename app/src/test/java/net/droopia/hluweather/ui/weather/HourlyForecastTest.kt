package net.droopia.hluweather.ui.weather

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import java.util.TimeZone
import kotlinx.datetime.Instant
import net.droopia.hluweather.data.repository.Svilajnac
import net.droopia.hluweather.data.repository.buildMockForecast
import net.droopia.hluweather.ComposeTestActivity
import net.droopia.hluweather.ui.theme.HluWeatherTheme
import org.junit.Before
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
                    onDaySelected = {}
                )
            }
        }

        composeRule.onNodeWithText("Hourly Forecast").assertIsDisplayed()
        composeRule.onNodeWithText("Time").assertIsDisplayed()
        composeRule.onNodeWithText("Weather").assertIsDisplayed()
        composeRule.onNodeWithText("Temp.").assertIsDisplayed()
        composeRule.onNodeWithText("Dew point").assertIsDisplayed()
        composeRule.onNodeWithText("Hum.").assertIsDisplayed()
        composeRule.onNodeWithText("Precip.").assertIsDisplayed()
        composeRule.onNodeWithText("00h").assertIsDisplayed()
        composeRule.onNodeWithTag("hourly_table").assertIsDisplayed()
    }
}
