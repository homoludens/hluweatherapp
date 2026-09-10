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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainActivityInsetsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeTestActivity>()

    @Test
    fun weather_content_respects_dispatched_system_bar_insets() {
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
        val systemBars = WindowInsets.Builder()
            .setInsets(
                WindowInsets.Type.statusBars(),
                Insets.of(0, statusBarInset, 0, 0)
            )
            .setInsets(
                WindowInsets.Type.navigationBars(),
                Insets.of(0, 0, 0, navigationBarInset)
            )
            .build()

        composeRule.activity.window.decorView.dispatchApplyWindowInsets(systemBars)
        composeRule.waitForIdle()

        val rootBottom = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.bottom
        val settingsTop = composeRule.onNodeWithContentDescription("Settings")
            .fetchSemanticsNode()
            .boundsInRoot
            .top

        assertTrue(
            "Settings control must be below the status bar",
            settingsTop >= statusBarInset
        )
        assertModeAboveNavigationBar("weather_scroll", rootBottom, navigationBarInset)

        composeRule.onNodeWithText("Daily").performClick()
        assertModeAboveNavigationBar("daily_list", rootBottom, navigationBarInset)

        composeRule.onNodeWithText("Map").performClick()
        assertModeAboveNavigationBar("map_placeholder", rootBottom, navigationBarInset)
    }

    private fun assertModeAboveNavigationBar(
        tag: String,
        rootBottom: Float,
        navigationBarInset: Int
    ) {
        val modeBottom = composeRule.onNodeWithTag(tag)
            .fetchSemanticsNode()
            .boundsInRoot
            .bottom

        assertTrue(
            "$tag must be above the navigation bar",
            modeBottom <= rootBottom - navigationBarInset
        )
    }
}
