package net.droopia.hluweather.ui.weather

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextLayoutResult
import kotlinx.datetime.Instant
import net.droopia.hluweather.data.repository.Svilajnac
import net.droopia.hluweather.data.repository.buildMockForecast
import net.droopia.hluweather.ComposeTestActivity
import net.droopia.hluweather.ui.settings.PrecipitationUnit
import net.droopia.hluweather.ui.settings.TemperatureUnit
import net.droopia.hluweather.ui.theme.HluWeatherTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        composeRule.onAllNodesWithText("21°C").onFirst().assertIsDisplayed()
        composeRule.onNodeWithText("Clear sky").assertIsDisplayed()
        composeRule.onNodeWithText("51%").assertIsDisplayed()
        composeRule.onNodeWithText("0 mm").assertIsDisplayed()
    }

    @Test
    fun formats_fetched_time_in_the_forecast_timezone() {
        val forecast = buildMockForecast(
            location = Svilajnac,
            baseTime = Instant.parse("2026-09-02T00:00:00Z")
        ).copy(timezone = "America/New_York")

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                CurrentWeatherCard(
                    location = forecast.location,
                    forecast = forecast
                )
            }
        }

        composeRule.onNodeWithText("Tue, Sep 1, 2026 • 20:00").assertIsDisplayed()
    }

    @Test
    fun renders_current_values_in_selected_units() {
        val forecast = buildMockForecast(
            location = Svilajnac,
            baseTime = Instant.fromEpochSeconds(0L)
        )

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                CurrentWeatherCard(
                    location = forecast.location,
                    forecast = forecast,
                    temperatureUnit = TemperatureUnit.FAHRENHEIT,
                    precipitationUnit = PrecipitationUnit.INCH
                )
            }
        }

        composeRule.onAllNodesWithText("70°F").onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithText("0 in").onFirst().assertIsDisplayed()
    }

    @Test
    fun location_row_exposes_a_merged_change_location_action() {
        val forecast = buildMockForecast(Svilajnac, Instant.fromEpochSeconds(0L))

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                CurrentWeatherCard(location = forecast.location, forecast = forecast)
            }
        }

        composeRule.onNodeWithTag("current_location")
            .assertHasClickAction()
            .assertContentDescriptionEquals("Change location, Svilajnac")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    @Test
    fun large_font_current_values_fit_inside_a_constrained_card() {
        val forecast = buildMockForecast(Svilajnac, Instant.fromEpochSeconds(0L))

        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                HluWeatherTheme(darkTheme = false) {
                    LazyColumn(
                        modifier = androidx.compose.ui.Modifier
                            .width(320.dp)
                            .fillMaxHeight()
                            .testTag("current_card_width")
                    ) {
                        item { CurrentWeatherCard(location = forecast.location, forecast = forecast) }
                    }
                }
            }
        }

        listOf("Clear sky").forEach { text ->
            val nodes = composeRule.onAllNodesWithText(text).fetchSemanticsNodes()
            assertTrue("$text must be present", nodes.isNotEmpty())
            assertTextFitsWidth(text, nodes.first())
        }
        assertTextFitsWidth(
            "current temperature",
            composeRule.onNodeWithTag("current_temperature").fetchSemanticsNode()
        )
    }

    private fun assertTextFitsWidth(
        text: String,
        node: androidx.compose.ui.semantics.SemanticsNode
    ) {
        val results = mutableListOf<TextLayoutResult>()
        assertTrue("$text must expose a text layout result", node.config.contains(SemanticsActions.GetTextLayoutResult))
        assertTrue(node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results) == true)
        val result = results.single()
        assertFalse("$text must not clip horizontally", result.didOverflowWidth)
    }
}
