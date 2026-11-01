package net.droopia.hluweather.ui.weather

import android.graphics.Insets
import android.view.WindowInsets
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import kotlin.math.roundToInt
import net.droopia.hluweather.ComposeTestActivity
import net.droopia.hluweather.data.repository.MockWeatherRepository
import net.droopia.hluweather.data.repository.Svilajnac
import net.droopia.hluweather.ui.app.HluWeatherApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34, 35], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainActivityInsetsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeTestActivity>()

    @Test
    fun main_shell_respects_system_insets_without_double_padding() {
        val weatherViewModel = WeatherViewModel(MockWeatherRepository(), Svilajnac)
        composeRule.activity.enableEdgeToEdge()
        composeRule.setContent {
            HluWeatherApp(
                weatherViewModel = weatherViewModel,
                weatherMapContent = { _, _, _, _, _ ->
                    Box(Modifier.fillMaxSize().testTag("map_placeholder"))
                }
            )
        }

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("weather_scroll").fetchSemanticsNodes().isNotEmpty()
        }

        val density = composeRule.activity.resources.displayMetrics.density
        val statusBarInset = (48 * density).roundToInt()
        val navigationBarInset = (40 * density).roundToInt()
        val cutoutInset = (24 * density).roundToInt()
        val systemBars = WindowInsets.Builder()
            .setInsets(
                WindowInsets.Type.statusBars(),
                Insets.of(0, statusBarInset, 0, 0)
            )
            .setInsets(
                WindowInsets.Type.navigationBars(),
                Insets.of(0, 0, 0, navigationBarInset)
            )
            .setInsets(
                WindowInsets.Type.displayCutout(),
                Insets.of(cutoutInset, 0, cutoutInset, 0)
            )
            .build()

        composeRule.activity.window.decorView.dispatchApplyWindowInsets(systemBars)
        composeRule.waitForIdle()

        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val rootBottom = rootBounds.bottom
        val settingsTop = composeRule.onNodeWithContentDescription("Settings")
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        val headerContent = composeRule.onNodeWithTag("main_header_content")
            .fetchSemanticsNode().boundsInRoot
        val bottomNavigation = composeRule.onNodeWithTag("main_bottom_navigation")
            .fetchSemanticsNode().boundsInRoot
        val hourlyTab = composeRule.onNodeWithTag("main_destination_hourly")
            .fetchSemanticsNode().boundsInRoot
        val routeTab = composeRule.onNodeWithTag("main_destination_route")
            .fetchSemanticsNode().boundsInRoot

        assertTrue(
            "Settings control must be below the status bar",
            settingsTop >= statusBarInset
        )
        assertTrue("The compact header must follow the status bar", headerContent.top >= statusBarInset)
        assertTrue("Header content must avoid the display cutout", headerContent.left >= cutoutInset)
        assertTrue("Bottom navigation must avoid the left display cutout", hourlyTab.left >= cutoutInset)
        assertTrue(
            "Bottom navigation must avoid the right display cutout",
            routeTab.right <= rootBounds.right - cutoutInset
        )
        assertTrue("The bottom bar must include the navigation inset", bottomNavigation.bottom <= rootBottom)
        assertModeAboveBottomNavigation("weather_scroll", bottomNavigation.top)

        composeRule.onNodeWithText("Daily").performClick()
        assertModeAboveBottomNavigation("daily_list", bottomNavigation.top)

        composeRule.onNodeWithText("Map").performClick()
        assertModeAboveBottomNavigation("map_placeholder", bottomNavigation.top)
    }

    private fun assertModeAboveBottomNavigation(
        tag: String,
        navigationTop: Float
    ) {
        val modeBottom = composeRule.onNodeWithTag(tag)
            .fetchSemanticsNode()
            .boundsInRoot
            .bottom

        assertEquals("$tag must not have duplicate bottom inset padding", navigationTop, modeBottom, 1f)
    }
}
