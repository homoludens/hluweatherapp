package net.droopia.hluweather.ui.weather

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import net.droopia.hluweather.data.model.ForecastMode
import net.droopia.hluweather.ComposeTestActivity
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
    fun expanded_tab_labels_stay_on_one_line_at_narrow_width() {
        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                Box(Modifier.width(320.dp)) {
                    WeatherHero(
                        selected = ForecastMode.HOURLY,
                        onSelected = {},
                        onSettingsClick = {}
                    )
                }
            }
        }

        val layoutResults = mutableListOf<TextLayoutResult>()
        val textNode = composeRule.onNodeWithText("Hourly").fetchSemanticsNode()
        assertTrue(textNode.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layoutResults) == true)
        assertEquals(1, layoutResults.single().lineCount)
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
    fun regular_hero_does_not_paint_the_old_decorative_moon() {
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

        val heroImage = composeRule.onNodeWithTag("weather_hero").captureToImage()
        val moonCenterX = heroImage.width - with(composeRule.density) { 106.dp.roundToPx() }
        val moonCenterY = with(composeRule.density) { 106.dp.roundToPx() }

        assertEquals(
            false,
            heroImage.pixelColor(moonCenterX, moonCenterY) == Color(0xFFFFF0BD)
        )
    }

    @Test
    fun compact_hero_remains_96_dp() {
        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                WeatherHero(
                    selected = ForecastMode.HOURLY,
                    onSelected = {},
                    onSettingsClick = {},
                    compact = true,
                    modifier = androidx.compose.ui.Modifier.testTag("compact_weather_hero")
                )
            }
        }

        val heroHeight = composeRule.onNodeWithTag("compact_weather_hero")
            .fetchSemanticsNode()
            .boundsInRoot
            .height

        assertEquals(96.dp, with(composeRule.density) { heroHeight.toDp() })
    }

    private fun ImageBitmap.pixelColor(x: Int, y: Int): Color {
        val pixels = IntArray(width * height)
        readPixels(pixels)
        return Color(pixels[y * width + x])
    }
}
