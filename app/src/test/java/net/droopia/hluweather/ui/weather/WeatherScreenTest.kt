package net.droopia.hluweather.ui.weather

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.swipe
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import net.droopia.hluweather.data.repository.MockWeatherRepository
import net.droopia.hluweather.ComposeTestActivity
import net.droopia.hluweather.ui.theme.HluWeatherTheme
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WeatherScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeTestActivity>()

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun renders_hourly_content_by_default() {
        val viewModel = WeatherViewModel(MockWeatherRepository())

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                WeatherScreen(
                    viewModel = viewModel,
                    onSettingsClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("weather_scroll").assertIsDisplayed()
        composeRule.onNodeWithText("Svilajnac").assertIsDisplayed()
    }

    @Test
    fun switches_to_daily_content() {
        val viewModel = WeatherViewModel(MockWeatherRepository())

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                WeatherScreen(
                    viewModel = viewModel,
                    onSettingsClick = {}
                )
            }
        }

        composeRule.onNodeWithText("Daily").performClick()
        composeRule.onNodeWithTag("daily_list").assertIsDisplayed()
    }

    @Test
    fun switches_to_map_placeholder() {
        val viewModel = WeatherViewModel(MockWeatherRepository())

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                WeatherScreen(
                    viewModel = viewModel,
                    onSettingsClick = {}
                )
            }
        }

        composeRule.onNodeWithText("Map").performClick()
        composeRule.onNodeWithTag("map_placeholder").assertIsDisplayed()
    }

    @Test
    fun settings_icon_reports_click() {
        val viewModel = WeatherViewModel(MockWeatherRepository())
        var settingsClicked = false

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                WeatherScreen(
                    viewModel = viewModel,
                    onSettingsClick = { settingsClicked = true }
                )
            }
        }

        composeRule.onNodeWithContentDescription("Settings").performClick()
        assertTrue(settingsClicked)
    }

    @Test
    fun scrolling_hourly_collapses_header_to_icons_and_days() {
        val viewModel = WeatherViewModel(
            MockWeatherRepository(baseTime = Instant.fromEpochSeconds(0L))
        )

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                WeatherScreen(
                    viewModel = viewModel,
                    onSettingsClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("weather_scroll").performScrollToIndex(20)
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Hourly").assertIsDisplayed()
        assertTrue(
            composeRule.onNodeWithContentDescription("Hourly")
                .fetchSemanticsNode()
                .config[SemanticsProperties.Selected]
        )
        composeRule.onNodeWithTag("hourly_day_strip").assertIsDisplayed()
        composeRule.onNodeWithTag("weather_scroll").assertIsDisplayed()
        val compactHero = composeRule.onNodeWithTag("compact_weather_hero")
            .fetchSemanticsNode()
            .boundsInRoot
        val dayStrip = composeRule.onNodeWithTag("hourly_day_strip")
            .fetchSemanticsNode()
            .boundsInRoot
        assertTrue(
            "Day strip must start below the compact hero",
            dayStrip.top >= compactHero.bottom
        )
        assertTextOutsideViewport("HluWeatherApp")
        assertTextOutsideViewport("Hourly")
        assertTextOutsideViewport("Svilajnac")
    }

    @Test
    fun tiny_hourly_scroll_does_not_show_compact_header_early() {
        val viewModel = WeatherViewModel(MockWeatherRepository())

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                WeatherScreen(
                    viewModel = viewModel,
                    onSettingsClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("weather_scroll").performTouchInput {
            swipe(
                start = Offset(200f, 300f),
                end = Offset(200f, 276f),
                durationMillis = 100
            )
        }
        composeRule.waitForIdle()

        assertTrue(
            composeRule.onAllNodesWithContentDescription("Hourly")
                .fetchSemanticsNodes()
                .isEmpty()
        )
    }

    private fun assertTextOutsideViewport(text: String) {
        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val nodes = composeRule.onAllNodesWithText(text).fetchSemanticsNodes()

        assertTrue(
            "$text should not remain visible after collapsing",
            nodes.all { node ->
                node.boundsInRoot.bottom <= rootBounds.top ||
                    node.boundsInRoot.top >= rootBounds.bottom
            }
        )
    }
}
