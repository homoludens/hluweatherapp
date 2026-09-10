package net.droopia.hluweather.ui.weather

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import net.droopia.hluweather.data.model.ForecastMode
import net.droopia.hluweather.ComposeTestActivity
import net.droopia.hluweather.ui.theme.HluWeatherTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WeatherHeroTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeTestActivity>()

    @Test
    fun renders_title_and_tabs() {
        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                WeatherHero(
                    selected = ForecastMode.HOURLY,
                    onSelected = {},
                    onSettingsClick = {}
                )
            }
        }

        composeRule.onNodeWithText("HluWeatherApp").assertIsDisplayed()
        composeRule.onNodeWithText("Simple weather. Clear view.").assertIsDisplayed()
        composeRule.onNodeWithText("Hourly").assertIsDisplayed()
        composeRule.onNodeWithText("Daily").assertIsDisplayed()
        composeRule.onNodeWithText("Map").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Settings").assertIsDisplayed()
    }

    @Test
    fun tab_click_reports_selected_mode() {
        var selected: ForecastMode? = null

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                WeatherHero(
                    selected = ForecastMode.HOURLY,
                    onSelected = { selected = it },
                    onSettingsClick = {}
                )
            }
        }

        composeRule.onNodeWithText("Daily").performClick()
        assertEquals(ForecastMode.DAILY, selected)
    }

    @Test
    fun regular_hero_has_reduced_height() {
        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                WeatherHero(
                    selected = ForecastMode.HOURLY,
                    onSelected = {},
                    onSettingsClick = {},
                    modifier = androidx.compose.ui.Modifier.testTag("weather_hero")
                )
            }
        }

        val heroHeight = composeRule.onNodeWithTag("weather_hero")
            .fetchSemanticsNode()
            .boundsInRoot
            .height

        assertEquals(176.dp, with(composeRule.density) { heroHeight.toDp() })
    }

    @Test
    fun regular_hero_has_no_decorative_moon() {
        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                WeatherHero(
                    selected = ForecastMode.HOURLY,
                    onSelected = {},
                    onSettingsClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("weather_hero_moon").assertDoesNotExist()
    }
}
