package net.droopia.hluweather.ui.weather

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import kotlinx.datetime.Instant
import net.droopia.hluweather.data.repository.Svilajnac
import net.droopia.hluweather.data.repository.buildMockForecast
import net.droopia.hluweather.ComposeTestActivity
import net.droopia.hluweather.ui.theme.HluWeatherTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CurrentWeatherCardTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeTestActivity>()

    @Test
    fun renders_current_conditions() {
        val forecast = buildMockForecast(
            location = Svilajnac,
            baseTime = Instant.fromEpochSeconds(0L)
        )

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                CurrentWeatherCard(
                    location = forecast.location,
                    forecast = forecast
                )
            }
        }

        composeRule.onNodeWithText("Svilajnac").assertIsDisplayed()
        composeRule.onNodeWithText("21°").assertIsDisplayed()
        composeRule.onNodeWithText("Clear sky").assertIsDisplayed()
        composeRule.onNodeWithText("51%").assertIsDisplayed()
        composeRule.onNodeWithText("0 mm").assertIsDisplayed()
    }
}
