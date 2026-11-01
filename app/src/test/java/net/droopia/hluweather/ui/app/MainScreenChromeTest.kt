package net.droopia.hluweather.ui.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import net.droopia.hluweather.ComposeTestActivity
import net.droopia.hluweather.navigation.MainDestination
import net.droopia.hluweather.ui.theme.HluWeatherTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
class MainScreenChromeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeTestActivity>()

    @Test
    fun header_is_compact_and_contains_only_the_title_and_settings_action() {
        var settingsClicked = false
        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                MainScreenHeader(onSettingsClick = { settingsClicked = true })
            }
        }

        composeRule.onNodeWithText("HluWeatherApp").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Settings")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        assertTrue(settingsClicked)
        composeRule.onNodeWithText("Simple weather. Clear view.").assertDoesNotExist()

        val headerHeight = composeRule.onNodeWithTag("main_header_content")
            .fetchSemanticsNode().boundsInRoot.height
        assertEquals(60.dp, with(composeRule.density) { headerHeight.toDp() })
    }

    @Test
    fun bottom_bar_has_four_labeled_destinations_and_a_selected_indicator_state() {
        var selected = MainDestination.HOURLY
        var selectedIndicatorColor = Color.Unspecified
        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                selectedIndicatorColor = MaterialTheme.colorScheme.primaryContainer
                MainBottomNavigationBar(
                    selected = selected,
                    onDestinationSelected = { selected = it }
                )
            }
        }

        listOf("Hourly", "Daily", "Map", "Route").forEach {
            composeRule.onNodeWithText(it).assertIsDisplayed()
        }
        composeRule.onNodeWithTag("main_destination_hourly").assertIsSelected()
        composeRule.onNodeWithTag("main_destination_route").assertIsNotSelected().performClick()
        composeRule.waitForIdle()
        assertEquals(MainDestination.ROUTE, selected)

        val barImage = composeRule.onNodeWithTag("main_bottom_navigation").captureToImage()
        assertTrue(barImage.containsColor(selectedIndicatorColor))
    }

    @Test
    fun light_and_dark_headers_keep_the_gradient_and_controls_visible() {
        val darkTheme = mutableStateOf(false)
        val lightGradient: Pair<Color, Color>
        composeRule.setContent {
            HluWeatherTheme(darkTheme = darkTheme.value) {
                ColumnForHeaderAndNavigation()
            }
        }
        composeRule.onNodeWithText("HluWeatherApp").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Settings").assertIsDisplayed()
        lightGradient = headerEdgeColors()

        composeRule.runOnIdle { darkTheme.value = true }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("HluWeatherApp").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Settings").assertIsDisplayed()
        listOf("Hourly", "Daily", "Map", "Route").forEach {
            composeRule.onNodeWithText(it).assertIsDisplayed()
        }
        composeRule.onNodeWithTag("main_destination_hourly").assertIsSelected()
        val darkGradient = headerEdgeColors()

        assertNotEquals(lightGradient, darkGradient)
        assertNotEquals(darkGradient.first, darkGradient.second)
    }

    @Composable
    private fun ColumnForHeaderAndNavigation() {
        Column {
            MainScreenHeader(onSettingsClick = {})
            MainBottomNavigationBar(
                selected = MainDestination.HOURLY,
                onDestinationSelected = {}
            )
        }
    }

    private fun headerEdgeColors(): Pair<Color, Color> {
        val image = composeRule.onNodeWithTag("main_header").captureToImage()
        return image.pixelColor(2, 2) to image.pixelColor(2, image.height - 3)
    }

    private fun ImageBitmap.pixelColor(x: Int, y: Int): Color {
        val pixels = IntArray(width * height)
        readPixels(pixels)
        return Color(pixels[y * width + x])
    }

    private fun ImageBitmap.containsColor(color: Color): Boolean {
        val pixels = IntArray(width * height)
        readPixels(pixels)
        return pixels.any { Color(it) == color }
    }
}
