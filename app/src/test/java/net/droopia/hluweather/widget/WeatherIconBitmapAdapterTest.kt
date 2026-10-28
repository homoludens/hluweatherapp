package net.droopia.hluweather.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.DisplayMetrics
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.glance.BitmapImageProvider
import net.droopia.hluweather.ComposeTestActivity
import net.droopia.hluweather.data.model.WeatherCondition
import net.droopia.hluweather.ui.components.weatherIconSpec
import net.droopia.hluweather.ui.theme.DarkHluColors
import net.droopia.hluweather.ui.theme.HluWeatherTheme
import net.droopia.hluweather.ui.theme.LightHluColors
import net.droopia.hluweather.ui.theme.hluMaterialColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WeatherIconBitmapAdapterTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeTestActivity>()

    @Test
    fun weather_and_utility_vectors_match_compose_output_at_widget_icon_sizes() {
        val vector = mutableStateOf(Icons.Default.WbSunny)
        val tint = mutableStateOf(Color.Black)
        val sizeDp = mutableStateOf(48)
        val darkTheme = mutableStateOf(false)

        composeRule.setContent {
            HluWeatherTheme(darkTheme = darkTheme.value) {
                Icon(
                    imageVector = vector.value,
                    contentDescription = null,
                    tint = tint.value,
                    modifier = Modifier
                        .size(sizeDp.value.dp)
                        .background(TEST_BACKGROUND)
                        .testTag(ICON_TAG)
                )
            }
        }

        val density = composeRule.activity.resources.displayMetrics.density
        for (useDarkTheme in listOf(false, true)) {
            val colors = if (useDarkTheme) DarkHluColors else LightHluColors
            val utilityTint = hluMaterialColorScheme(useDarkTheme).primary
            val partlyCloudy = weatherIconSpec(WeatherCondition.PARTLY_CLOUDY, true, colors)
            val clearNight = weatherIconSpec(WeatherCondition.CLEAR, false, colors)
            val icons = listOf(
                IconCase(
                    label = "partly cloudy weather",
                    vector = partlyCloudy.imageVector,
                    tint = partlyCloudy.tint
                ),
                IconCase(
                    label = "clear night weather",
                    vector = clearNight.imageVector,
                    tint = clearNight.tint
                ),
                IconCase("location utility", Icons.Default.LocationOn, utilityTint),
                IconCase("refresh utility", Icons.Default.Refresh, utilityTint),
                IconCase("precipitation utility", Icons.Default.WaterDrop, utilityTint)
            )

            for (icon in icons) {
                for (targetSizeDp in listOf(48, 24)) {
                    val expectedSizePx = (targetSizeDp * density).roundToInt()
                    val provider = WeatherIconBitmapAdapter.imageProvider(
                        imageVector = icon.vector,
                        tint = icon.tint,
                        widthPx = expectedSizePx,
                        heightPx = expectedSizePx,
                        density = density
                    )
                    assertTrue(
                        "${icon.label} should use a bitmap provider",
                        provider is BitmapImageProvider
                    )
                    val bitmap = (provider as BitmapImageProvider).bitmap

                    composeRule.runOnIdle {
                        darkTheme.value = useDarkTheme
                        vector.value = icon.vector
                        tint.value = icon.tint
                        sizeDp.value = targetSizeDp
                    }
                    composeRule.waitForIdle()

                    val expected = composeRule.onNodeWithTag(ICON_TAG).captureToImage()
                    assertEquals("${icon.label} width", expectedSizePx, expected.width)
                    assertEquals("${icon.label} height", expectedSizePx, expected.height)
                    assertEquals(Bitmap.Config.ARGB_8888, bitmap.config)
                    assertEquals(
                        (density * DisplayMetrics.DENSITY_DEFAULT).roundToInt(),
                        bitmap.density
                    )

                    val expectedPixels = expected.toPixelMap()
                    val compositedBitmap = Bitmap.createBitmap(
                        expectedSizePx,
                        expectedSizePx,
                        Bitmap.Config.ARGB_8888
                    ).apply {
                        Canvas(this).apply {
                            drawColor(TEST_BACKGROUND.toArgb())
                            drawBitmap(bitmap, 0f, 0f, null)
                        }
                    }
                    val expectedTint = icon.tint.toArgb() and RGB_MASK
                    var visiblePixelCount = 0
                    var opaquePixelCount = 0
                    for (x in 0 until expectedSizePx) {
                        for (y in 0 until expectedSizePx) {
                            val expectedPixel = expectedPixels[x, y].toArgb()
                            val actualPixel = bitmap.getPixel(x, y)
                            assertPixelsEqualWithinOneChannelValue(
                                message = "${icon.label} pixel ($x,$y)",
                                expectedPixel,
                                compositedBitmap.getPixel(x, y)
                            )
                            if ((actualPixel ushr 24) != 0) {
                                visiblePixelCount++
                            }
                            if ((actualPixel ushr 24) == 0xFF) {
                                opaquePixelCount++
                                assertEquals(
                                    "${icon.label} tint at ($x,$y)",
                                    expectedTint,
                                    actualPixel and RGB_MASK
                                )
                            }
                        }
                    }
                    assertTrue(
                        "${icon.label} should remain readable at $targetSizeDp dp",
                        visiblePixelCount > expectedSizePx
                    )
                    assertTrue(
                        "${icon.label} should contain fully tinted pixels",
                        opaquePixelCount > 0
                    )
                }
            }
        }
    }

    @Test
    fun bitmap_cache_keys_all_render_inputs_and_evicts_old_entries() {
        val vector = Icons.Default.LocationOn
        val first = WeatherIconBitmapAdapter.bitmap(vector, Color.Red, 32, 32, 1f)

        assertSame(first, WeatherIconBitmapAdapter.bitmap(vector, Color.Red, 32, 32, 1f))
        assertNotSame(first, WeatherIconBitmapAdapter.bitmap(vector, Color.Blue, 32, 32, 1f))
        assertNotSame(first, WeatherIconBitmapAdapter.bitmap(vector, Color.Red, 32, 32, 2f))
        assertNotSame(first, WeatherIconBitmapAdapter.bitmap(vector, Color.Red, 24, 24, 1f))
        assertNotSame(
            first,
            WeatherIconBitmapAdapter.bitmap(Icons.Default.Refresh, Color.Red, 32, 32, 1f)
        )

        repeat(WeatherIconBitmapAdapter.MAX_CACHE_ENTRIES + 1) { index ->
            val value = index / 40f
            WeatherIconBitmapAdapter.bitmap(
                imageVector = vector,
                tint = Color(red = value, green = 1f - value, blue = 0.5f),
                widthPx = 20,
                heightPx = 20,
                density = 1f
            )
        }

        assertEquals(
            WeatherIconBitmapAdapter.MAX_CACHE_ENTRIES,
            WeatherIconBitmapAdapter.cachedBitmapCount
        )
    }

    private data class IconCase(
        val label: String,
        val vector: ImageVector,
        val tint: Color
    )

    private fun assertPixelsEqualWithinOneChannelValue(
        message: String,
        expected: Int,
        actual: Int
    ) {
        // Native Compose and bitmap compositing can round antialiased edges by one channel value.
        for (shift in listOf(24, 16, 8, 0)) {
            val expectedChannel = (expected ushr shift) and 0xFF
            val actualChannel = (actual ushr shift) and 0xFF
            assertTrue(
                "$message channel $shift: expected $expectedChannel but was $actualChannel",
                kotlin.math.abs(expectedChannel - actualChannel) <= 1
            )
        }
    }

    private companion object {
        const val ICON_TAG = "bitmap-icon-reference"
        const val RGB_MASK = 0x00FFFFFF
        val TEST_BACKGROUND = Color(0xFFFAFAFA)
    }
}
