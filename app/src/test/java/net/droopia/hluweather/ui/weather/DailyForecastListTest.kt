package net.droopia.hluweather.ui.weather

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
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
        composeRule.onAllNodesWithText("16° – 30°").onFirst().assertIsDisplayed()
    }
}
