package net.droopia.hluweather.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Shower
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import net.droopia.hluweather.ComposeTestActivity
import net.droopia.hluweather.data.model.WeatherCondition
import net.droopia.hluweather.ui.theme.DarkHluColors
import net.droopia.hluweather.ui.theme.HluColors
import net.droopia.hluweather.ui.theme.HluWeatherTheme
import net.droopia.hluweather.ui.theme.LightHluColors
import net.droopia.hluweather.ui.theme.LocalHluColors
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WeatherIconSpecTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeTestActivity>()

    @Test
    fun selector_preserves_every_condition_mapping_for_day_night_and_unknown_daylight() {
        for (colors in listOf(LightHluColors, DarkHluColors)) {
            val expectedDaySpecs = expectedDaySpecs(colors)
            assertEquals(WeatherCondition.entries.toSet(), expectedDaySpecs.keys)

            for ((condition, expectedDaySpec) in expectedDaySpecs) {
                assertEquals(expectedDaySpec, weatherIconSpec(condition, true, colors))
                assertEquals(expectedDaySpec, weatherIconSpec(condition, null, colors))

                val expectedNightSpec = if (
                    condition == WeatherCondition.CLEAR ||
                    condition == WeatherCondition.MOSTLY_CLEAR
                ) {
                    WeatherIconSpec(Icons.Default.DarkMode, colors.moonAccent)
                } else {
                    expectedDaySpec
                }
                assertEquals(expectedNightSpec, weatherIconSpec(condition, false, colors))
            }
        }
    }

    @Test
    fun composable_renders_the_shared_icon_spec_for_every_condition_in_both_themes() {
        val darkTheme = mutableStateOf(false)
        val condition = mutableStateOf(WeatherCondition.CLEAR)
        val isDay = mutableStateOf<Boolean?>(true)

        composeRule.setContent {
            HluWeatherTheme(darkTheme = darkTheme.value) {
                IconComparison(condition = condition.value, isDay = isDay.value)
            }
        }

        for (useDarkTheme in listOf(false, true)) {
            for (weatherCondition in WeatherCondition.entries) {
                val daylightValues = if (
                    weatherCondition == WeatherCondition.CLEAR ||
                    weatherCondition == WeatherCondition.MOSTLY_CLEAR
                ) {
                    listOf(true, false, null)
                } else {
                    listOf(true)
                }
                for (daylight in daylightValues) {
                    composeRule.runOnIdle {
                        darkTheme.value = useDarkTheme
                        condition.value = weatherCondition
                        isDay.value = daylight
                    }
                    composeRule.waitForIdle()

                    val composableOutput = composeRule.onNodeWithTag("weather-icon")
                        .captureToImage()
                    val expectedOutput = composeRule.onNodeWithTag("expected-icon")
                        .captureToImage()
                    assertImagesEqual(expectedOutput, composableOutput)
                }
            }
        }
    }

    private fun expectedDaySpecs(colors: HluColors) = mapOf(
        WeatherCondition.CLEAR to WeatherIconSpec(Icons.Default.WbSunny, Color(0xFFFFB300)),
        WeatherCondition.MOSTLY_CLEAR to WeatherIconSpec(Icons.Default.WbSunny, Color(0xFFFFB300)),
        WeatherCondition.PARTLY_CLOUDY to WeatherIconSpec(Icons.Default.WbCloudy, colors.cloudAccent),
        WeatherCondition.CLOUDY to WeatherIconSpec(Icons.Default.Cloud, colors.cloudAccent),
        WeatherCondition.FOG to WeatherIconSpec(Icons.Default.Air, colors.cloudAccent),
        WeatherCondition.DRIZZLE to WeatherIconSpec(Icons.Default.Shower, Color(0xFF5367E8)),
        WeatherCondition.RAIN to WeatherIconSpec(Icons.Default.Shower, Color(0xFF5367E8)),
        WeatherCondition.SNOW to WeatherIconSpec(Icons.Default.AcUnit, Color(0xFFB9C6FF)),
        WeatherCondition.THUNDERSTORM to WeatherIconSpec(Icons.Default.Thunderstorm, Color(0xFFFFB300)),
        WeatherCondition.UNKNOWN to WeatherIconSpec(Icons.Default.Cloud, colors.cloudAccent)
    )

    @Composable
    private fun IconComparison(
        condition: WeatherCondition,
        isDay: Boolean?
    ) {
        val spec = weatherIconSpec(condition, isDay, LocalHluColors.current)
        Row {
            HluWeatherIcon(
                condition = condition,
                isDay = isDay,
                modifier = Modifier.size(48.dp).testTag("weather-icon"),
                contentDescription = "Weather"
            )
            Icon(
                imageVector = spec.imageVector,
                contentDescription = "Weather",
                tint = spec.tint,
                modifier = Modifier.size(48.dp).testTag("expected-icon")
            )
        }
    }

    private fun assertImagesEqual(expected: ImageBitmap, actual: ImageBitmap) {
        assertEquals(expected.width, actual.width)
        assertEquals(expected.height, actual.height)
        val expectedPixels = expected.toPixelMap()
        val actualPixels = actual.toPixelMap()
        for (x in 0 until expected.width) {
            for (y in 0 until expected.height) {
                assertEquals("Pixel ($x, $y)", expectedPixels[x, y], actualPixels[x, y])
            }
        }
    }
}
