package net.droopia.hluweather.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.util.TimeZone
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
class HluNavHostTest {

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
    fun navigates_from_weather_to_settings_and_back() {
        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                HluNavHost()
            }
        }

        composeRule.onNodeWithText("HluWeatherApp").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("HluWeatherApp").assertIsDisplayed()
    }
}
