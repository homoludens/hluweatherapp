package net.droopia.hluweather.widget

import kotlin.math.max
import kotlin.math.min

/** Finite layout variants that can be translated to Glance responsive sizes by widget layouts. */
enum class WeatherWidgetSizeClass {
    MINIMUM,
    COMPACT,
    NORMAL,
    WIDE
}

/** Small-widget presentation choices, independent of Glance and Android resources. */
data class SmallWeatherWidgetSize(
    val sizeClass: WeatherWidgetSizeClass,
    val locationWidthDp: Float,
    val refreshTouchTargetDp: Int,
    val iconSizeDp: Float,
    val temperatureFontSizeSp: Float,
    val primaryContentWidthDp: Float,
    val primaryContentAvailableWidthDp: Float,
    val primaryContentHeightDp: Float,
    val primaryContentAvailableHeightDp: Float,
    val conditionFontSizeSp: Float,
    val showCondition: Boolean,
    val apparentTemperatureFontSizeSp: Float,
    val showApparentTemperature: Boolean
)

/** Medium-widget presentation choices, including a fixed current block and three to five columns. */
data class MediumWeatherWidgetSize(
    val sizeClass: WeatherWidgetSizeClass,
    val hourlyColumnCount: Int,
    val currentWeatherFraction: Float,
    val currentWeatherWidthDp: Float,
    val hourlyColumnWidthDp: Float,
    val refreshTouchTargetDp: Int,
    val bodyHeightDp: Float,
    val currentIconSizeDp: Float,
    val currentTemperatureFontSizeSp: Float,
    val currentContentWidthDp: Float,
    val currentTemperatureAvailableWidthDp: Float,
    val currentPrimaryContentWidthDp: Float,
    val currentPrimaryContentHeightDp: Float,
    val showCurrentCondition: Boolean,
    val showCurrentApparentTemperature: Boolean,
    val hourlyIconSizeDp: Float,
    val hourlyTimeFontSizeSp: Float,
    val hourlyTemperatureFontSizeSp: Float,
    val hourlyTemperatureTextWidthDp: Float,
    val hourlyTextAvailableWidthDp: Float,
    val hourlyPrimaryContentHeightDp: Float,
    val showHourlyProbability: Boolean
)

/**
 * Pure layout policy for the future small and medium Glance layouts.
 *
 * Callers pass the actual available size in dp, the effective Android font scale, and the length
 * of the formatted temperature they intend to render. The policy keeps the small widget's
 * location, weather icon, temperature, and independent refresh target ahead of secondary text;
 * the medium widget adapts its column count and text sizes without introducing scrolling.
 */
object WeatherWidgetSizePolicy {
    /** Minimum dimensions that the small provider should advertise when its layout is added. */
    const val SMALL_MIN_WIDTH_DP = 110f
    const val SMALL_MIN_HEIGHT_DP = 110f

    /** Minimum width supports the three-column medium layout at its smallest supported size. */
    const val MEDIUM_MIN_WIDTH_DP = 260f
    const val MEDIUM_MIN_HEIGHT_DP = 110f

    const val REFRESH_TOUCH_TARGET_DP = 48

    private const val HORIZONTAL_PADDING_DP = 8f
    private const val VERTICAL_PADDING_DP = 8f
    private const val HEADER_HEIGHT_DP = REFRESH_TOUCH_TARGET_DP.toFloat()
    private const val HEADER_TO_BODY_GAP_DP = 4f
    private const val PRIMARY_ROW_GAP_DP = 4f
    private const val SECONDARY_ROW_GAP_DP = 2f
    private const val ICON_TO_TEMPERATURE_GAP_DP = 4f
    private const val MEDIUM_DIVIDER_WIDTH_DP = 1f
    private const val MEDIUM_CELL_PADDING_DP = 4f
    private const val AVERAGE_TEMPERATURE_GLYPH_WIDTH = 0.62f
    private const val AVERAGE_LABEL_GLYPH_WIDTH = 0.55f
    private const val SMALL_TEXT_LINE_HEIGHT = 1.2f
    private const val MEDIUM_TEXT_LINE_HEIGHT = 1.15f

    /**
     * Resolve small-widget priorities. The temperature length includes any unit suffix shown by
     * the caller (for example, `"−123°F"` has length six).
     */
    fun small(
        widthDp: Float,
        heightDp: Float,
        fontScale: Float,
        formattedTemperatureLength: Int,
        apparentTemperatureLength: Int? = null
    ): SmallWeatherWidgetSize {
        val width = widthDp.nonNegative()
        val height = heightDp.nonNegative()
        val scale = fontScale.positiveOrOne()
        val sizeClass = smallSizeClass(width, height)
        val innerWidth = (width - HORIZONTAL_PADDING_DP * 2f).coerceAtLeast(0f)
        val locationWidth = (
            width - HORIZONTAL_PADDING_DP * 2f - REFRESH_TOUCH_TARGET_DP - ICON_TO_TEMPERATURE_GAP_DP * 2f
            ).coerceAtLeast(0f)
        val bodyHeight = (
            height - VERTICAL_PADDING_DP * 2f - HEADER_HEIGHT_DP - HEADER_TO_BODY_GAP_DP
            ).coerceAtLeast(0f)

        val iconTarget = when (sizeClass) {
            WeatherWidgetSizeClass.MINIMUM -> 28f
            WeatherWidgetSizeClass.COMPACT -> 34f
            WeatherWidgetSizeClass.NORMAL -> 40f
            WeatherWidgetSizeClass.WIDE -> 44f
        }
        val temperatureTarget = when (sizeClass) {
            WeatherWidgetSizeClass.MINIMUM -> 24f
            WeatherWidgetSizeClass.COMPACT -> 28f
            WeatherWidgetSizeClass.NORMAL -> 32f
            WeatherWidgetSizeClass.WIDE -> 34f
        }
        val iconSize = minOf(iconTarget, bodyHeight, max(20f, innerWidth * 0.28f))
        val temperatureLength = formattedTemperatureLength.coerceAtLeast(1)
        val temperatureAvailableWidth = (
            innerWidth - iconSize - ICON_TO_TEMPERATURE_GAP_DP
            ).coerceAtLeast(0f)
        val temperatureWidthLimit = temperatureAvailableWidth /
            (temperatureLength * AVERAGE_TEMPERATURE_GLYPH_WIDTH * scale)
        val temperatureHeightLimit = bodyHeight / (SMALL_TEXT_LINE_HEIGHT * scale)
        val temperatureFontSize = minOf(temperatureTarget, temperatureWidthLimit, temperatureHeightLimit)
            .coerceAtLeast(0f)
        val temperatureHeight = lineHeight(temperatureFontSize, scale, SMALL_TEXT_LINE_HEIGHT)
        val primaryHeight = max(iconSize, temperatureHeight)
        val secondaryHeight = (
            height - VERTICAL_PADDING_DP * 2f - HEADER_HEIGHT_DP - HEADER_TO_BODY_GAP_DP -
                primaryHeight - PRIMARY_ROW_GAP_DP
            ).coerceAtLeast(0f)

        val conditionFontSize = 12f
        val conditionHeight = lineHeight(conditionFontSize, scale, SMALL_TEXT_LINE_HEIGHT)
        val showCondition = secondaryHeight >= conditionHeight
        val apparentFontSize = apparentTemperatureLength
            ?.let { length ->
                val estimatedLabelLength = length.coerceAtLeast(1) + FEELS_LIKE_LABEL_CHARACTERS
                min(
                    11f,
                    innerWidth / (estimatedLabelLength * AVERAGE_LABEL_GLYPH_WIDTH * scale)
                )
            }
            ?.coerceAtLeast(0f)
            ?: 0f
        val apparentFitsWidth = apparentTemperatureLength != null && apparentFontSize >= MIN_SECONDARY_FONT_SP
        val apparentHeight = lineHeight(apparentFontSize, scale, SMALL_TEXT_LINE_HEIGHT)
        val showApparentTemperature = showCondition && apparentFitsWidth &&
            secondaryHeight >= conditionHeight + SECONDARY_ROW_GAP_DP + apparentHeight

        return SmallWeatherWidgetSize(
            sizeClass = sizeClass,
            locationWidthDp = locationWidth,
            refreshTouchTargetDp = REFRESH_TOUCH_TARGET_DP,
            iconSizeDp = iconSize,
            temperatureFontSizeSp = temperatureFontSize,
            primaryContentWidthDp = iconSize + ICON_TO_TEMPERATURE_GAP_DP +
                temperatureLength * AVERAGE_TEMPERATURE_GLYPH_WIDTH * temperatureFontSize * scale,
            primaryContentAvailableWidthDp = innerWidth,
            primaryContentHeightDp = primaryHeight,
            primaryContentAvailableHeightDp = bodyHeight,
            conditionFontSizeSp = conditionFontSize,
            showCondition = showCondition,
            apparentTemperatureFontSizeSp = apparentFontSize,
            showApparentTemperature = showApparentTemperature
        )
    }

    /** Resolve medium column count and text/icon sizes without using a scrolling layout. */
    fun medium(
        widthDp: Float,
        heightDp: Float,
        fontScale: Float,
        formattedTemperatureLength: Int
    ): MediumWeatherWidgetSize {
        val width = widthDp.nonNegative()
        val height = heightDp.nonNegative()
        val scale = fontScale.positiveOrOne()
        val normalizedWidth = width / scale
        val columnCount = when {
            normalizedWidth >= FIVE_COLUMN_WIDTH_DP -> 5
            normalizedWidth >= FOUR_COLUMN_WIDTH_DP -> 4
            else -> 3
        }
        val sizeClass = when {
            width < MEDIUM_MIN_WIDTH_DP -> WeatherWidgetSizeClass.MINIMUM
            columnCount == 3 -> WeatherWidgetSizeClass.COMPACT
            columnCount == 4 -> WeatherWidgetSizeClass.NORMAL
            else -> WeatherWidgetSizeClass.WIDE
        }
        val usableWidth = (width - HORIZONTAL_PADDING_DP * 2f).coerceAtLeast(0f)
        val currentFraction = CURRENT_WEATHER_FRACTION
        val currentWidth = usableWidth * currentFraction
        val hourlyWidth = (
            usableWidth - currentWidth - columnCount * MEDIUM_DIVIDER_WIDTH_DP
            ).coerceAtLeast(0f)
        val hourlyColumnWidth = hourlyWidth / columnCount
        val hourlyTextWidth = (hourlyColumnWidth - MEDIUM_CELL_PADDING_DP * 2f).coerceAtLeast(0f)
        val bodyHeight = (
            height - VERTICAL_PADDING_DP * 2f - HEADER_HEIGHT_DP - HEADER_TO_BODY_GAP_DP
            ).coerceAtLeast(0f)
        val temperatureLength = formattedTemperatureLength.coerceAtLeast(1)

        val currentIconTarget = when (sizeClass) {
            WeatherWidgetSizeClass.MINIMUM -> 18f
            WeatherWidgetSizeClass.COMPACT -> 20f
            WeatherWidgetSizeClass.NORMAL -> 22f
            WeatherWidgetSizeClass.WIDE -> 24f
        }
        val currentIconSize = min(currentIconTarget, bodyHeight * 0.55f)
        val currentContentWidth = (currentWidth - MEDIUM_CELL_PADDING_DP * 2f).coerceAtLeast(0f)
        val currentTemperatureAvailableWidth = (
            currentContentWidth - currentIconSize - ICON_TO_TEMPERATURE_GAP_DP
            ).coerceAtLeast(0f)
        val currentTemperatureTarget = when (sizeClass) {
            WeatherWidgetSizeClass.MINIMUM -> 18f
            WeatherWidgetSizeClass.COMPACT -> 20f
            WeatherWidgetSizeClass.NORMAL -> 22f
            WeatherWidgetSizeClass.WIDE -> 24f
        }
        val currentTemperatureWidthLimit = currentTemperatureAvailableWidth /
            (temperatureLength * AVERAGE_TEMPERATURE_GLYPH_WIDTH * scale)
        val currentTemperatureHeightLimit = bodyHeight / (SMALL_TEXT_LINE_HEIGHT * scale)
        val currentTemperatureFont = minOf(
            currentTemperatureTarget,
            currentTemperatureWidthLimit,
            currentTemperatureHeightLimit
        ).coerceAtLeast(0f)
        val currentTemperatureHeight = lineHeight(
            currentTemperatureFont,
            scale,
            SMALL_TEXT_LINE_HEIGHT
        )
        val currentPrimaryHeight = max(currentIconSize, currentTemperatureHeight)
        val currentSecondaryHeight = (
            bodyHeight - currentPrimaryHeight - PRIMARY_ROW_GAP_DP
            ).coerceAtLeast(0f)
        val currentConditionFont = 12f
        val currentConditionHeight = lineHeight(currentConditionFont, scale, SMALL_TEXT_LINE_HEIGHT)
        val showCurrentCondition = currentSecondaryHeight >= currentConditionHeight
        val currentApparentFont = min(
            11f,
            currentContentWidth /
                ((temperatureLength + FEELS_LIKE_LABEL_CHARACTERS) * AVERAGE_LABEL_GLYPH_WIDTH * scale)
        )
        val showCurrentApparentTemperature = showCurrentCondition &&
            currentApparentFont >= MIN_SECONDARY_FONT_SP &&
            currentSecondaryHeight >= currentConditionHeight + SECONDARY_ROW_GAP_DP +
            lineHeight(currentApparentFont, scale, SMALL_TEXT_LINE_HEIGHT)

        val hourlyIconSize = minOf(18f, hourlyColumnWidth * 0.35f, bodyHeight * 0.35f)
            .coerceAtLeast(0f)
        val hourlyTimeWidthLimit = hourlyTextWidth / (HOUR_LABEL_CHARACTERS * AVERAGE_LABEL_GLYPH_WIDTH * scale)
        val hourlyTimeFont = minOf(10f, 12f / scale, hourlyTimeWidthLimit).coerceAtLeast(0f)
        val hourlyTimeHeight = lineHeight(hourlyTimeFont, scale, MEDIUM_TEXT_LINE_HEIGHT)
        val hourlyAvailableForTemperature = (
            bodyHeight - hourlyTimeHeight - hourlyIconSize - SECONDARY_ROW_GAP_DP * 2f
            ).coerceAtLeast(0f)
        val hourlyTemperatureWidthLimit = hourlyTextWidth /
            (temperatureLength * AVERAGE_TEMPERATURE_GLYPH_WIDTH * scale)
        val hourlyTemperatureHeightLimit = hourlyAvailableForTemperature /
            (MEDIUM_TEXT_LINE_HEIGHT * scale)
        val hourlyTemperatureFont = minOf(
            14f,
            hourlyTemperatureWidthLimit,
            hourlyTemperatureHeightLimit
        ).coerceAtLeast(0f)
        val hourlyTemperatureHeight = lineHeight(
            hourlyTemperatureFont,
            scale,
            MEDIUM_TEXT_LINE_HEIGHT
        )
        val hourlyPrimaryHeight = hourlyTimeHeight + hourlyIconSize + hourlyTemperatureHeight +
            SECONDARY_ROW_GAP_DP * 2f
        val probabilityWidthLimit = hourlyTextWidth /
            (PRECIPITATION_LABEL_CHARACTERS * AVERAGE_LABEL_GLYPH_WIDTH * scale)
        val probabilityHeightLimit = (
            bodyHeight - hourlyPrimaryHeight - SECONDARY_ROW_GAP_DP
            ).coerceAtLeast(0f) / (MEDIUM_TEXT_LINE_HEIGHT * scale)
        val probabilityFont = minOf(10f, probabilityWidthLimit, probabilityHeightLimit)
            .coerceAtLeast(0f)
        val showHourlyProbability = probabilityFont >= MIN_SECONDARY_FONT_SP

        return MediumWeatherWidgetSize(
            sizeClass = sizeClass,
            hourlyColumnCount = columnCount,
            currentWeatherFraction = currentFraction,
            currentWeatherWidthDp = currentWidth,
            hourlyColumnWidthDp = hourlyColumnWidth,
            refreshTouchTargetDp = REFRESH_TOUCH_TARGET_DP,
            bodyHeightDp = bodyHeight,
            currentIconSizeDp = currentIconSize,
            currentTemperatureFontSizeSp = currentTemperatureFont,
            currentContentWidthDp = currentContentWidth,
            currentTemperatureAvailableWidthDp = currentTemperatureAvailableWidth,
            currentPrimaryContentWidthDp = currentIconSize + ICON_TO_TEMPERATURE_GAP_DP +
                temperatureLength * AVERAGE_TEMPERATURE_GLYPH_WIDTH * currentTemperatureFont * scale,
            currentPrimaryContentHeightDp = currentPrimaryHeight,
            showCurrentCondition = showCurrentCondition,
            showCurrentApparentTemperature = showCurrentApparentTemperature,
            hourlyIconSizeDp = hourlyIconSize,
            hourlyTimeFontSizeSp = hourlyTimeFont,
            hourlyTemperatureFontSizeSp = hourlyTemperatureFont,
            hourlyTemperatureTextWidthDp = temperatureLength * AVERAGE_TEMPERATURE_GLYPH_WIDTH *
                hourlyTemperatureFont * scale,
            hourlyTextAvailableWidthDp = hourlyTextWidth,
            hourlyPrimaryContentHeightDp = hourlyPrimaryHeight,
            showHourlyProbability = showHourlyProbability
        )
    }

    private fun smallSizeClass(width: Float, height: Float): WeatherWidgetSizeClass = when {
        width < 150f || height < 128f -> WeatherWidgetSizeClass.MINIMUM
        width < 210f || height < 152f -> WeatherWidgetSizeClass.COMPACT
        width < 280f || height < 184f -> WeatherWidgetSizeClass.NORMAL
        else -> WeatherWidgetSizeClass.WIDE
    }

    private fun lineHeight(fontSizeSp: Float, scale: Float, multiplier: Float): Float =
        fontSizeSp * scale * multiplier

    private fun Float.nonNegative(): Float =
        if (isFinite()) coerceAtLeast(0f) else 0f

    private fun Float.positiveOrOne(): Float =
        if (isFinite() && this > 0f) this else 1f

    private const val FEELS_LIKE_LABEL_CHARACTERS = 11
    private const val HOUR_LABEL_CHARACTERS = 5
    private const val PRECIPITATION_LABEL_CHARACTERS = 4
    private const val MIN_SECONDARY_FONT_SP = 7f
    private const val CURRENT_WEATHER_FRACTION = 0.33f
    private const val FOUR_COLUMN_WIDTH_DP = 320f
    private const val FIVE_COLUMN_WIDTH_DP = 400f
}
