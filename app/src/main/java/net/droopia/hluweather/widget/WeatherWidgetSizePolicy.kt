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
    val showApparentTemperature: Boolean,
    val showStatus: Boolean
)

/** Medium-widget presentation choices, including a fixed current block and three to five columns. */
data class MediumWeatherWidgetSize(
    val sizeClass: WeatherWidgetSizeClass,
    val hourlyColumnCount: Int,
    val currentWeatherFraction: Float,
    val currentWeatherWidthDp: Float,
    val hourlyColumnWidthDp: Float,
    val refreshTouchTargetDp: Int,
    val showHeaderTimestamp: Boolean,
    val headerLocationTextWidthDp: Float,
    val headerTimestampTextWidthDp: Float,
    val cardHorizontalPaddingDp: Float,
    val cardVerticalPaddingDp: Float,
    val headerToBodyGapDp: Float,
    val bodyHeightDp: Float,
    val currentIconSizeDp: Float,
    val currentTemperatureFontSizeSp: Float,
    val currentContentWidthDp: Float,
    val currentTemperatureAvailableWidthDp: Float,
    val currentPrimaryContentWidthDp: Float,
    val currentPrimaryContentHeightDp: Float,
    val currentConditionFontSizeSp: Float,
    val showCurrentCondition: Boolean,
    val currentApparentTemperatureFontSizeSp: Float,
    val showCurrentApparentTemperature: Boolean,
    val hourlyIconSizeDp: Float,
    val hourlyTimeFontSizeSp: Float,
    val hourlyTemperatureFontSizeSp: Float,
    val hourlyTemperatureTextWidthDp: Float,
    val hourlyTextAvailableWidthDp: Float,
    val hourlyPrimaryContentHeightDp: Float,
    val hourlyProbabilityFontSizeSp: Float,
    val hourlyProbabilityTextAvailableWidthDp: Float,
    val hourlyProbabilityDropletSizeDp: Float
)

/**
 * Pure layout policy for the future small and medium Glance layouts.
 *
 * Callers pass available size in dp, Android font scale, and formatted temperature length. Medium
 * callers also supply a line-height multiplier measured from Android's TextView. The policy keeps
 * primary content ahead of secondary text and adapts medium columns without introducing scrolling.
 */
object WeatherWidgetSizePolicy {
    /** Minimum dimensions that the small provider should advertise when its layout is added. */
    const val SMALL_MIN_WIDTH_DP = 110f
    const val SMALL_MIN_HEIGHT_DP = 110f

    /** Minimum width supports the three-column medium layout at its smallest supported size. */
    const val MEDIUM_MIN_WIDTH_DP = 260f
    // The hourly strip always includes time, icon, temperature, and probability. This height
    // leaves enough translated RemoteViews space for those four rows even at larger font scales.
    const val MEDIUM_MIN_HEIGHT_DP = 110f
    const val MEDIUM_STANDARD_HEIGHT_DP = 144f

    const val REFRESH_TOUCH_TARGET_DP = 48

    private const val SMALL_HORIZONTAL_PADDING_DP = 8f
    private const val VERTICAL_PADDING_DP = 8f
    private const val HEADER_HEIGHT_DP = REFRESH_TOUCH_TARGET_DP.toFloat()
    private const val HEADER_TO_BODY_GAP_DP = 4f
    private const val PRIMARY_ROW_GAP_DP = 4f
    private const val SECONDARY_ROW_GAP_DP = 2f
    private const val ICON_TO_TEMPERATURE_GAP_DP = 4f
    private const val LOCATION_ICON_SIZE_DP = 18f
    private const val MEDIUM_DIVIDER_WIDTH_DP = 1f
    private const val MEDIUM_CELL_PADDING_DP = 4f
    private const val MEDIUM_CARD_HORIZONTAL_PADDING_DP = 4f
    private const val MEDIUM_MIN_VERTICAL_PADDING_DP = 2f
    private const val MEDIUM_MAX_VERTICAL_PADDING_DP = 4f
    private const val MEDIUM_MIN_HEADER_TO_BODY_GAP_DP = 2f
    private const val MEDIUM_HEADER_GAP_DP = 4f
    private const val MEDIUM_HEADER_TIMESTAMP_FONT_SP = 9f
    private const val MEDIUM_MIN_LOCATION_TEXT_WIDTH_DP = 40f
    private const val HOURLY_DROPLET_SIZE_DP = 8f
    private const val HOURLY_DROPLET_GAP_DP = 2f
    private const val AVERAGE_TEMPERATURE_GLYPH_WIDTH = 0.62f
    private const val AVERAGE_LABEL_GLYPH_WIDTH = 0.55f
    private const val AVERAGE_HEADER_GLYPH_WIDTH = 0.62f
    private const val DEFAULT_TEXT_LINE_HEIGHT_MULTIPLIER = 1.2f
    private const val SMALL_STATUS_FONT_SP = 10f

    /**
     * Resolve small-widget priorities. The temperature length includes any unit suffix shown by
     * the caller (for example, `"−123°F"` has length six).
     */
    fun small(
        widthDp: Float,
        heightDp: Float,
        fontScale: Float,
        formattedTemperatureLength: Int,
        apparentTemperatureLength: Int? = null,
        hasStatus: Boolean = false
    ): SmallWeatherWidgetSize {
        val width = widthDp.nonNegative()
        val height = heightDp.nonNegative()
        val scale = fontScale.positiveOrOne()
        val sizeClass = smallSizeClass(width, height)
        val innerWidth = (width - SMALL_HORIZONTAL_PADDING_DP * 2f).coerceAtLeast(0f)
        val locationWidth = (
            width - SMALL_HORIZONTAL_PADDING_DP * 2f - REFRESH_TOUCH_TARGET_DP - LOCATION_ICON_SIZE_DP
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
        val temperatureHeightLimit = bodyHeight / (DEFAULT_TEXT_LINE_HEIGHT_MULTIPLIER * scale)
        val temperatureFontSize = minOf(temperatureTarget, temperatureWidthLimit, temperatureHeightLimit)
            .coerceAtLeast(0f)
        val temperatureHeight = lineHeight(temperatureFontSize, scale, DEFAULT_TEXT_LINE_HEIGHT_MULTIPLIER)
        val primaryHeight = max(iconSize, temperatureHeight)
        val secondaryHeight = (
            height - VERTICAL_PADDING_DP * 2f - HEADER_HEIGHT_DP - HEADER_TO_BODY_GAP_DP -
                primaryHeight - PRIMARY_ROW_GAP_DP
            ).coerceAtLeast(0f)

        val conditionFontSize = 12f
        val conditionHeight = lineHeight(conditionFontSize, scale, DEFAULT_TEXT_LINE_HEIGHT_MULTIPLIER)
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
        val apparentHeight = lineHeight(apparentFontSize, scale, DEFAULT_TEXT_LINE_HEIGHT_MULTIPLIER)
        val showApparentTemperature = showCondition && apparentFitsWidth &&
            secondaryHeight >= conditionHeight + SECONDARY_ROW_GAP_DP + apparentHeight
        val shownSecondaryHeight = when {
            showApparentTemperature -> conditionHeight + SECONDARY_ROW_GAP_DP + apparentHeight
            showCondition -> conditionHeight
            else -> 0f
        }
        val statusHeight = lineHeight(
            SMALL_STATUS_FONT_SP,
            scale,
            DEFAULT_TEXT_LINE_HEIGHT_MULTIPLIER
        )
        val statusGap = if (shownSecondaryHeight > 0f) SECONDARY_ROW_GAP_DP else 0f
        val showStatus = hasStatus &&
            secondaryHeight + PRIMARY_ROW_GAP_DP >= shownSecondaryHeight + statusGap + statusHeight

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
            showApparentTemperature = showApparentTemperature,
            showStatus = showStatus
        )
    }

    /** Resolve medium column count and text/icon sizes without using a scrolling layout. */
    fun medium(
        widthDp: Float,
        heightDp: Float,
        fontScale: Float,
        formattedTemperatureLength: Int,
        currentConditionLabelLength: Int = 13,
        apparentTemperatureLength: Int? = null,
        headerTimestampTextLength: Int = 0,
        textLineHeightMultiplier: Float = DEFAULT_TEXT_LINE_HEIGHT_MULTIPLIER
    ): MediumWeatherWidgetSize {
        val width = widthDp.nonNegative()
        val height = heightDp.nonNegative()
        val scale = fontScale.positiveOrOne()
        val compactHeightProgress = (
            (height - MEDIUM_MIN_HEIGHT_DP) /
                (MEDIUM_STANDARD_HEIGHT_DP - MEDIUM_MIN_HEIGHT_DP)
            ).coerceIn(0f, 1f)
        val cardVerticalPadding = interpolate(
            MEDIUM_MIN_VERTICAL_PADDING_DP,
            MEDIUM_MAX_VERTICAL_PADDING_DP,
            compactHeightProgress
        )
        val headerToBodyGap = interpolate(
            MEDIUM_MIN_HEADER_TO_BODY_GAP_DP,
            MEDIUM_HEADER_GAP_DP,
            compactHeightProgress
        )
        val compactHeight = height < MEDIUM_STANDARD_HEIGHT_DP
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
        val usableWidth = (width - MEDIUM_CARD_HORIZONTAL_PADDING_DP * 2f).coerceAtLeast(0f)
        val currentFraction = CURRENT_WEATHER_FRACTION
        val currentWidth = usableWidth * currentFraction
        val hourlyWidth = (
            usableWidth - currentWidth - columnCount * MEDIUM_DIVIDER_WIDTH_DP
            ).coerceAtLeast(0f)
        val hourlyColumnWidth = hourlyWidth / columnCount
        val hourlyTextWidth = (hourlyColumnWidth - MEDIUM_CELL_PADDING_DP * 2f).coerceAtLeast(0f)
        val headerTimestampWidth = headerTimestampTextLength.coerceAtLeast(0) *
            AVERAGE_HEADER_GLYPH_WIDTH * MEDIUM_HEADER_TIMESTAMP_FONT_SP * scale
        val headerLocationWidthWithTimestamp = (
            width - MEDIUM_CARD_HORIZONTAL_PADDING_DP * 2f - REFRESH_TOUCH_TARGET_DP - LOCATION_ICON_SIZE_DP -
                MEDIUM_HEADER_GAP_DP * 2f - headerTimestampWidth
            ).coerceAtLeast(0f)
        val showHeaderTimestamp = headerTimestampTextLength > 0 &&
            headerLocationWidthWithTimestamp >= MEDIUM_MIN_LOCATION_TEXT_WIDTH_DP
        val headerLocationWidth = if (showHeaderTimestamp) {
            headerLocationWidthWithTimestamp
        } else {
            (width - MEDIUM_CARD_HORIZONTAL_PADDING_DP * 2f - REFRESH_TOUCH_TARGET_DP - LOCATION_ICON_SIZE_DP)
                .coerceAtLeast(0f)
        }
        val bodyHeight = (
            height - cardVerticalPadding * 2f - HEADER_HEIGHT_DP - headerToBodyGap
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
        val currentTemperatureHeightLimit = bodyHeight / (textLineHeightMultiplier * scale)
        val currentTemperatureFont = minOf(
            currentTemperatureTarget,
            currentTemperatureWidthLimit,
            currentTemperatureHeightLimit
        ).coerceAtLeast(0f)
        val currentTemperatureHeight = lineHeight(
            currentTemperatureFont,
            scale,
            textLineHeightMultiplier
        )
        val currentPrimaryHeight = max(currentIconSize, currentTemperatureHeight)
        val currentSecondaryHeight = (
            bodyHeight - currentPrimaryHeight - PRIMARY_ROW_GAP_DP
            ).coerceAtLeast(0f)
        val currentConditionFont = minOf(
            12f,
            currentContentWidth /
                (currentConditionLabelLength.coerceAtLeast(1) * AVERAGE_LABEL_GLYPH_WIDTH * scale),
            currentSecondaryHeight / (textLineHeightMultiplier * scale)
        ).coerceAtLeast(0f)
        val currentConditionHeight = lineHeight(currentConditionFont, scale, textLineHeightMultiplier)
        val showCurrentCondition = currentConditionFont > 0f &&
            currentSecondaryHeight >= currentConditionHeight
        val currentApparentFont = apparentTemperatureLength?.let { apparentLength ->
            min(
                11f,
                currentContentWidth /
                    ((apparentLength.coerceAtLeast(1) + FEELS_LIKE_LABEL_CHARACTERS) *
                        AVERAGE_LABEL_GLYPH_WIDTH * scale)
            )
        } ?: 0f
        val showCurrentApparentTemperature = showCurrentCondition &&
            currentApparentFont >= MIN_SECONDARY_FONT_SP &&
            currentSecondaryHeight >= currentConditionHeight + SECONDARY_ROW_GAP_DP +
            lineHeight(currentApparentFont, scale, textLineHeightMultiplier)

        val hourlyIconTarget = if (compactHeight) 8f else 18f
        val hourlyIconSize = minOf(hourlyIconTarget, hourlyColumnWidth * 0.35f, bodyHeight * 0.35f)
            .coerceAtLeast(0f)
        val hourlyTimeWidthLimit = hourlyTextWidth / (HOUR_LABEL_CHARACTERS * AVERAGE_LABEL_GLYPH_WIDTH * scale)
        val hourlyTimeTarget = if (compactHeight) 8f / scale else 10f
        val hourlyTimeFont = minOf(hourlyTimeTarget, 12f / scale, hourlyTimeWidthLimit).coerceAtLeast(0f)
        val hourlyTimeHeight = lineHeight(hourlyTimeFont, scale, textLineHeightMultiplier)
        val hourlyAvailableForTemperature = (
            bodyHeight - hourlyTimeHeight - hourlyIconSize - SECONDARY_ROW_GAP_DP * 2f
            ).coerceAtLeast(0f)
        val hourlyTemperatureWidthLimit = hourlyTextWidth /
            (temperatureLength * AVERAGE_TEMPERATURE_GLYPH_WIDTH * scale)
        val hourlyTemperatureHeightLimit = hourlyAvailableForTemperature /
            (textLineHeightMultiplier * scale)
        val hourlyTemperatureTarget = if (compactHeight) 10f / scale else 14f
        val hourlyTemperatureFont = minOf(
            hourlyTemperatureTarget,
            hourlyTemperatureWidthLimit,
            hourlyTemperatureHeightLimit
        ).coerceAtLeast(0f)
        val hourlyTemperatureHeight = lineHeight(
            hourlyTemperatureFont,
            scale,
            textLineHeightMultiplier
        )
        val hourlyPrimaryHeight = hourlyTimeHeight + hourlyIconSize + hourlyTemperatureHeight +
            SECONDARY_ROW_GAP_DP * 2f
        val probabilityTextWidth = (
            hourlyTextWidth - HOURLY_DROPLET_SIZE_DP - HOURLY_DROPLET_GAP_DP
            ).coerceAtLeast(0f)
        val probabilityWidthLimit = probabilityTextWidth /
            (PRECIPITATION_LABEL_CHARACTERS * AVERAGE_PERCENT_GLYPH_WIDTH * scale)
        val probabilityHeightLimit = (
            bodyHeight - hourlyPrimaryHeight - SECONDARY_ROW_GAP_DP
            ).coerceAtLeast(0f) / (textLineHeightMultiplier * scale)
        val probabilityTarget = (if (compactHeight) 8f else 10f) / scale
        val probabilityFont = minOf(probabilityTarget, probabilityWidthLimit, probabilityHeightLimit)
            .coerceAtLeast(0f)

        return MediumWeatherWidgetSize(
            sizeClass = sizeClass,
            hourlyColumnCount = columnCount,
            currentWeatherFraction = currentFraction,
            currentWeatherWidthDp = currentWidth,
            hourlyColumnWidthDp = hourlyColumnWidth,
            refreshTouchTargetDp = REFRESH_TOUCH_TARGET_DP,
            showHeaderTimestamp = showHeaderTimestamp,
            headerLocationTextWidthDp = headerLocationWidth,
            headerTimestampTextWidthDp = if (showHeaderTimestamp) headerTimestampWidth else 0f,
            cardHorizontalPaddingDp = MEDIUM_CARD_HORIZONTAL_PADDING_DP,
            cardVerticalPaddingDp = cardVerticalPadding,
            headerToBodyGapDp = headerToBodyGap,
            bodyHeightDp = bodyHeight,
            currentIconSizeDp = currentIconSize,
            currentTemperatureFontSizeSp = currentTemperatureFont,
            currentContentWidthDp = currentContentWidth,
            currentTemperatureAvailableWidthDp = currentTemperatureAvailableWidth,
            currentPrimaryContentWidthDp = currentIconSize + ICON_TO_TEMPERATURE_GAP_DP +
                temperatureLength * AVERAGE_TEMPERATURE_GLYPH_WIDTH * currentTemperatureFont * scale,
            currentPrimaryContentHeightDp = currentPrimaryHeight,
            currentConditionFontSizeSp = currentConditionFont,
            showCurrentCondition = showCurrentCondition,
            currentApparentTemperatureFontSizeSp = currentApparentFont,
            showCurrentApparentTemperature = showCurrentApparentTemperature,
            hourlyIconSizeDp = hourlyIconSize,
            hourlyTimeFontSizeSp = hourlyTimeFont,
            hourlyTemperatureFontSizeSp = hourlyTemperatureFont,
            hourlyTemperatureTextWidthDp = temperatureLength * AVERAGE_TEMPERATURE_GLYPH_WIDTH *
                hourlyTemperatureFont * scale,
            hourlyTextAvailableWidthDp = hourlyTextWidth,
            hourlyPrimaryContentHeightDp = hourlyPrimaryHeight,
            hourlyProbabilityFontSizeSp = probabilityFont,
            hourlyProbabilityTextAvailableWidthDp = probabilityTextWidth,
            hourlyProbabilityDropletSizeDp = HOURLY_DROPLET_SIZE_DP
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

    private fun interpolate(start: Float, end: Float, progress: Float): Float =
        start + (end - start) * progress

    private fun Float.nonNegative(): Float =
        if (isFinite()) coerceAtLeast(0f) else 0f

    private fun Float.positiveOrOne(): Float =
        if (isFinite() && this > 0f) this else 1f

    private const val FEELS_LIKE_LABEL_CHARACTERS = 11
    private const val HOUR_LABEL_CHARACTERS = 5
    private const val PRECIPITATION_LABEL_CHARACTERS = 4
    private const val AVERAGE_PERCENT_GLYPH_WIDTH = 0.62f
    private const val MIN_SECONDARY_FONT_SP = 7f
    private const val CURRENT_WEATHER_FRACTION = 0.33f
    private const val FOUR_COLUMN_WIDTH_DP = 320f
    private const val FIVE_COLUMN_WIDTH_DP = 400f
}
