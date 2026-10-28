package net.droopia.hluweather.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherWidgetSizePolicyTest {

    @Test
    fun small_policy_keeps_primary_content_and_refresh_inside_supported_minimums() {
        val cases = listOf(
            SmallCase(110f, 110f, 1f, "22°C".length),
            SmallCase(110f, 110f, 2f, "−123°F".length),
            SmallCase(128f, 110f, 1.5f, "123°F".length),
            SmallCase(160f, 120f, 2f, "−273°C".length),
            SmallCase(220f, 160f, 2f, "−123°F".length),
            SmallCase(300f, 220f, 1.5f, "123°F".length)
        )

        cases.forEach { case ->
            val policy = WeatherWidgetSizePolicy.small(
                widthDp = case.widthDp,
                heightDp = case.heightDp,
                fontScale = case.fontScale,
                formattedTemperatureLength = case.temperatureLength
            )

            assertTrue("location width for $case", policy.locationWidthDp > 0f)
            assertEquals(WeatherWidgetSizePolicy.REFRESH_TOUCH_TARGET_DP, policy.refreshTouchTargetDp)
            assertTrue("primary width for $case", policy.primaryContentWidthDp <= policy.primaryContentAvailableWidthDp + EPSILON)
            assertTrue("primary height for $case", policy.primaryContentHeightDp <= policy.primaryContentAvailableHeightDp + EPSILON)
        }
    }

    @Test
    fun small_policy_uses_secondary_content_only_after_primary_and_hides_feels_before_condition() {
        val minimumHeight = WeatherWidgetSizePolicy.small(
            widthDp = 200f,
            heightDp = 120f,
            fontScale = 1f,
            formattedTemperatureLength = "23°C".length,
            apparentTemperatureLength = "24°C".length
        )
        val supportedMinimum = WeatherWidgetSizePolicy.small(
            widthDp = WeatherWidgetSizePolicy.SMALL_MIN_WIDTH_DP,
            heightDp = WeatherWidgetSizePolicy.SMALL_MIN_HEIGHT_DP,
            fontScale = 1f,
            formattedTemperatureLength = "23°C".length,
            apparentTemperatureLength = "24°C".length
        )
        val normalHeight = WeatherWidgetSizePolicy.small(
            widthDp = 200f,
            heightDp = 160f,
            fontScale = 1f,
            formattedTemperatureLength = "23°C".length,
            apparentTemperatureLength = "24°C".length
        )
        val shorterThanMinimum = WeatherWidgetSizePolicy.small(
            widthDp = WeatherWidgetSizePolicy.SMALL_MIN_WIDTH_DP,
            heightDp = 100f,
            fontScale = 1f,
            formattedTemperatureLength = "23°C".length,
            apparentTemperatureLength = "24°C".length
        )

        assertTrue(minimumHeight.showCondition)
        assertFalse(minimumHeight.showApparentTemperature)
        assertFalse(supportedMinimum.showCondition)
        assertFalse(supportedMinimum.showApparentTemperature)
        assertTrue(normalHeight.showCondition)
        assertTrue(normalHeight.showApparentTemperature)
        assertFalse(shorterThanMinimum.showCondition)
        assertFalse(shorterThanMinimum.showApparentTemperature)
    }

    @Test
    fun small_policy_reduces_temperature_for_font_scale_and_signed_extreme_values() {
        val tripleDigitFahrenheit = WeatherWidgetSizePolicy.small(
            widthDp = WeatherWidgetSizePolicy.SMALL_MIN_WIDTH_DP,
            heightDp = WeatherWidgetSizePolicy.SMALL_MIN_HEIGHT_DP,
            fontScale = 2f,
            formattedTemperatureLength = "123°F".length
        )
        val signedExtremeFahrenheit = WeatherWidgetSizePolicy.small(
            widthDp = WeatherWidgetSizePolicy.SMALL_MIN_WIDTH_DP,
            heightDp = WeatherWidgetSizePolicy.SMALL_MIN_HEIGHT_DP,
            fontScale = 2f,
            formattedTemperatureLength = "−123°F".length
        )
        val standardScale = WeatherWidgetSizePolicy.small(
            widthDp = WeatherWidgetSizePolicy.SMALL_MIN_WIDTH_DP,
            heightDp = WeatherWidgetSizePolicy.SMALL_MIN_HEIGHT_DP,
            fontScale = 1f,
            formattedTemperatureLength = "−123°F".length
        )

        assertTrue(signedExtremeFahrenheit.temperatureFontSizeSp < tripleDigitFahrenheit.temperatureFontSizeSp)
        assertTrue(signedExtremeFahrenheit.temperatureFontSizeSp < standardScale.temperatureFontSizeSp)
        assertTrue(signedExtremeFahrenheit.primaryContentWidthDp <= signedExtremeFahrenheit.primaryContentAvailableWidthDp + EPSILON)

        val narrow = WeatherWidgetSizePolicy.small(
            widthDp = WeatherWidgetSizePolicy.SMALL_MIN_WIDTH_DP,
            heightDp = 160f,
            fontScale = 1f,
            formattedTemperatureLength = "−123°F".length
        )
        val wider = WeatherWidgetSizePolicy.small(
            widthDp = 160f,
            heightDp = 160f,
            fontScale = 1f,
            formattedTemperatureLength = "−123°F".length
        )

        assertTrue(narrow.iconSizeDp < wider.iconSizeDp)
        assertTrue(narrow.temperatureFontSizeSp < wider.temperatureFontSizeSp)
    }

    @Test
    fun medium_policy_selects_three_four_and_five_columns_at_responsive_width_bands() {
        val cases = listOf(
            MediumCase(259f, 1f, 3, WeatherWidgetSizeClass.MINIMUM),
            MediumCase(260f, 1f, 3, WeatherWidgetSizeClass.COMPACT),
            MediumCase(319f, 1f, 3, WeatherWidgetSizeClass.COMPACT),
            MediumCase(320f, 1f, 4, WeatherWidgetSizeClass.NORMAL),
            MediumCase(399f, 1f, 4, WeatherWidgetSizeClass.NORMAL),
            MediumCase(400f, 1f, 5, WeatherWidgetSizeClass.WIDE),
            MediumCase(400f, 1.5f, 3, WeatherWidgetSizeClass.COMPACT),
            MediumCase(480f, 1.5f, 4, WeatherWidgetSizeClass.NORMAL),
            MediumCase(600f, 1.5f, 5, WeatherWidgetSizeClass.WIDE)
        )

        cases.forEach { case ->
            val policy = WeatherWidgetSizePolicy.medium(
                widthDp = case.widthDp,
                heightDp = 180f,
                fontScale = case.fontScale,
                formattedTemperatureLength = "−123°F".length
            )

            assertEquals("column count for $case", case.columns, policy.hourlyColumnCount)
            assertEquals("size class for $case", case.sizeClass, policy.sizeClass)
            assertTrue(policy.hourlyColumnCount in 3..5)
            assertEquals(WeatherWidgetSizePolicy.REFRESH_TOUCH_TARGET_DP, policy.refreshTouchTargetDp)
            assertEquals(0.33f, policy.currentWeatherFraction, EPSILON)
            assertTrue(policy.currentWeatherWidthDp < policy.hourlyColumnWidthDp * policy.hourlyColumnCount)
        }
    }

    @Test
    fun medium_policy_fits_current_and_hourly_primary_content_at_supported_minimums() {
        val cases = listOf(
            MediumCase(WeatherWidgetSizePolicy.MEDIUM_MIN_WIDTH_DP, 1f, 3, WeatherWidgetSizeClass.COMPACT),
            MediumCase(WeatherWidgetSizePolicy.MEDIUM_MIN_WIDTH_DP, 1.5f, 3, WeatherWidgetSizeClass.COMPACT),
            MediumCase(WeatherWidgetSizePolicy.MEDIUM_MIN_WIDTH_DP, 2f, 3, WeatherWidgetSizeClass.COMPACT),
            MediumCase(320f, 2f, 3, WeatherWidgetSizeClass.COMPACT)
        )

        cases.forEach { case ->
            val policy = WeatherWidgetSizePolicy.medium(
                widthDp = case.widthDp,
                heightDp = WeatherWidgetSizePolicy.MEDIUM_MIN_HEIGHT_DP,
                fontScale = case.fontScale,
                formattedTemperatureLength = "−123°F".length
            )

            assertEquals(case.columns, policy.hourlyColumnCount)
            assertTrue("current width for $case", policy.currentPrimaryContentWidthDp <= policy.currentContentWidthDp + EPSILON)
            assertTrue("current height for $case", policy.currentPrimaryContentHeightDp <= policy.bodyHeightDp + EPSILON)
            assertTrue("hourly width for $case", policy.hourlyTemperatureTextWidthDp <= policy.hourlyTextAvailableWidthDp + EPSILON)
            assertTrue("hourly height for $case", policy.hourlyPrimaryContentHeightDp <= policy.bodyHeightDp + EPSILON)
            val probabilityHeight = policy.hourlyProbabilityFontSizeSp * case.fontScale * MEDIUM_LINE_HEIGHT
            assertTrue("probability remains visible for $case", policy.hourlyProbabilityFontSizeSp > 0f)
            assertTrue("probability text stays readable for $case", policy.hourlyProbabilityFontSizeSp * case.fontScale >= MIN_READABLE_TEXT_DP)
            assertTrue(
                "probability row fits after hourly primary content for $case",
                policy.hourlyPrimaryContentHeightDp + SECONDARY_GAP_DP + probabilityHeight <=
                    policy.bodyHeightDp + EPSILON
            )
            assertTrue(
                "droplet and probability fit in the hourly column for $case",
                policy.hourlyProbabilityDropletSizeDp + DROPLET_GAP_DP +
                    policy.hourlyProbabilityTextAvailableWidthDp <= policy.hourlyTextAvailableWidthDp + EPSILON
            )
            assertTrue("current condition remains present at minimum for $case", policy.showCurrentCondition)
        }
    }

    @Test
    fun medium_policy_shows_compact_update_time_only_when_header_and_location_still_fit() {
        val minimum = WeatherWidgetSizePolicy.medium(
            widthDp = WeatherWidgetSizePolicy.MEDIUM_MIN_WIDTH_DP,
            heightDp = WeatherWidgetSizePolicy.MEDIUM_MIN_HEIGHT_DP,
            fontScale = 1f,
            formattedTemperatureLength = 4,
            headerTimestampTextLength = 20
        )
        val largeFontMinimum = WeatherWidgetSizePolicy.medium(
            widthDp = WeatherWidgetSizePolicy.MEDIUM_MIN_WIDTH_DP,
            heightDp = WeatherWidgetSizePolicy.MEDIUM_MIN_HEIGHT_DP,
            fontScale = 2f,
            formattedTemperatureLength = 4,
            headerTimestampTextLength = 20
        )

        assertTrue(minimum.showHeaderTimestamp)
        assertTrue(minimum.headerLocationTextWidthDp >= 40f)
        assertFalse(largeFontMinimum.showHeaderTimestamp)
        assertTrue(largeFontMinimum.headerLocationTextWidthDp >= 40f)
    }

    private data class SmallCase(
        val widthDp: Float,
        val heightDp: Float,
        val fontScale: Float,
        val temperatureLength: Int
    )

    private data class MediumCase(
        val widthDp: Float,
        val fontScale: Float,
        val columns: Int,
        val sizeClass: WeatherWidgetSizeClass
    )

    private companion object {
        const val EPSILON = 0.01f
        const val MEDIUM_LINE_HEIGHT = 1.15f
        const val SECONDARY_GAP_DP = 2f
        const val DROPLET_GAP_DP = 2f
        const val MIN_READABLE_TEXT_DP = 7f
    }
}
