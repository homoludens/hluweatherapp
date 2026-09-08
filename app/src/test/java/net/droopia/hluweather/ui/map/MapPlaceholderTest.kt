package net.droopia.hluweather.ui.map

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
class MapPlaceholderTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeTestActivity>()

    @Test
    fun renders_map_placeholder() {
        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                MapPlaceholder()
            }
        }

        composeRule.onNodeWithTag("map_placeholder").assertIsDisplayed()
        composeRule.onNodeWithText("Map preview").assertIsDisplayed()
    }
}
