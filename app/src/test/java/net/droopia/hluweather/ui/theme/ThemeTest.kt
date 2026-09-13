package net.droopia.hluweather.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import net.droopia.hluweather.ComposeTestActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThemeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeTestActivity>()

    private var capturedHluColors: HluColors? = null
    private var capturedScheme: ColorScheme? = null

    @Test
    fun light_theme_uses_expected_primary_and_hero_colors() {
        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                captureColors { hluColors, colorScheme ->
                    capturedHluColors = hluColors
                    capturedScheme = colorScheme
                }
            }
        }

        assertEquals(Color(0xFF5367E8), capturedScheme?.primary)
        assertEquals(Color(0xFF6779ED), capturedHluColors?.heroTop)
        assertEquals(Color(0xFF4D55C6), capturedHluColors?.heroBottom)
    }

    @Test
    fun dark_theme_uses_expected_primary_and_hero_colors() {
        composeRule.setContent {
            HluWeatherTheme(darkTheme = true) {
                captureColors { hluColors, colorScheme ->
                    capturedHluColors = hluColors
                    capturedScheme = colorScheme
                }
            }
        }

        assertEquals(Color(0xFF9AA7FF), capturedScheme?.primary)
        assertEquals(Color(0xFF071225), capturedHluColors?.heroTop)
        assertEquals(Color(0xFF10264B), capturedHluColors?.heroBottom)
        assertEquals(Color(0xFFF5F7FF), capturedHluColors?.heroText)
        assertEquals(Color(0xFFB8C4EA), capturedHluColors?.heroSecondaryText)
        assertEquals(Color(0xFFF5F7FF), capturedHluColors?.navSelectedText)
        assertEquals(Color(0xFFF5F7FF), capturedHluColors?.daySelectedText)
    }

    @Test
    @Suppress("DEPRECATION")
    fun dark_theme_uses_dark_navigation_bar() {
        composeRule.setContent {
            HluWeatherTheme(darkTheme = true) {}
        }

        composeRule.runOnIdle {
            val window = composeRule.activity.window
            assertEquals(Color(0xFF09111E).toArgb(), window.navigationBarColor)
            assertFalse(
                WindowCompat.getInsetsController(window, window.decorView)
                    .isAppearanceLightNavigationBars
            )
        }
    }

    @Test
    fun theme_caps_large_system_font_scale_at_one() {
        var capturedFontScale = 0f

        composeRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 1f, fontScale = 1.5f)
            ) {
                HluWeatherTheme(darkTheme = false) {
                    capturedFontScale = LocalDensity.current.fontScale
                }
            }
        }

        assertEquals(1f, capturedFontScale, 0f)
    }

    @Composable
    private fun captureColors(
        onColors: (HluColors, ColorScheme) -> Unit
    ) {
        onColors(LocalHluColors.current, MaterialTheme.colorScheme)
    }
}
