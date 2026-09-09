package net.droopia.hluweather.ui.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.droopia.hluweather.ComposeTestActivity
import net.droopia.hluweather.ui.theme.HluWeatherTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HluWeatherAppTest {

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
    fun displays_app_title() {
        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                HluWeatherApp()
            }
        }
        composeRule.onNodeWithText("HluWeatherApp").assertIsDisplayed()
    }

    @Test
    fun selecting_theme_updates_app_state() {
        composeRule.setContent {
            HluWeatherApp()
        }

        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithTag("settings_scroll").performScrollToIndex(3)
        composeRule.onNodeWithText("Light").performClick()
        composeRule.onNodeWithText("Dark").performClick()
        composeRule.onNodeWithTag("settings_theme_dark").assertIsSelected()
    }

    @Test
    fun app_root_uses_the_factory_backed_settings_view_model() {
        composeRule.setContent {
            HluWeatherApp()
        }

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("weather_scroll")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag("weather_scroll").performScrollToIndex(0)
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithContentDescription("Settings")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Settings")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithText("Settings").assertIsDisplayed()

        composeRule.onNodeWithTag("settings_scroll").performScrollToIndex(3)
        composeRule.onNodeWithText("Light").performClick()
        composeRule.onNodeWithTag("settings_theme_light").assertIsSelected()
    }
}
