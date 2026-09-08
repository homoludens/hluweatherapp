package net.droopia.hluweather.ui.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
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
class HluWeatherAppTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeTestActivity>()

    @Test
    fun displays_app_title() {
        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                HluWeatherApp()
            }
        }

        composeRule.onNodeWithText("HluWeatherApp").assertIsDisplayed()
    }
}
