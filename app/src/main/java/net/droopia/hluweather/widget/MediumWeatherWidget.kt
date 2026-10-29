package net.droopia.hluweather.widget

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.unit.ColorProvider
import net.droopia.hluweather.HluWeatherApplication

internal val MEDIUM_WEATHER_WIDGET_RESPONSIVE_SIZES = setOf(
    DpSize(260.dp, WeatherWidgetSizePolicy.MEDIUM_MIN_HEIGHT_DP.dp),
    DpSize(320.dp, WeatherWidgetSizePolicy.MEDIUM_MIN_HEIGHT_DP.dp),
    DpSize(400.dp, WeatherWidgetSizePolicy.MEDIUM_MIN_HEIGHT_DP.dp),
    DpSize(260.dp, WeatherWidgetSizePolicy.MEDIUM_STANDARD_HEIGHT_DP.dp),
    DpSize(320.dp, WeatherWidgetSizePolicy.MEDIUM_STANDARD_HEIGHT_DP.dp),
    DpSize(400.dp, 160.dp)
)

/** Current conditions and three to five future hours; all data is loaded before composition. */
class MediumWeatherWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(MEDIUM_WEATHER_WIDGET_RESPONSIVE_SIZES)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Loading is cache-only. The application reconciliation/worker paths own weather requests.
        val application = context.applicationContext as HluWeatherApplication
        val snapshot = application.weatherWidgetRepository.loadCachedState()
        provideContent { MediumWeatherWidgetContent(snapshot) }
    }
}

/** Production medium-card composition shared with translated RemoteViews tests. */
@Composable
internal fun MediumWeatherWidgetContent(snapshot: WeatherWidgetSnapshot) {
    val context = LocalContext.current
    val size = LocalSize.current
    val available = snapshot as? WeatherWidgetSnapshot.Available
    val weather = available?.weather
    val fontScale = context.resources.configuration.fontScale
    val headerText = available?.let { it.weather.mediumHeaderUpdateText(it.refresh) }
    val policy = WeatherWidgetSizePolicy.medium(
        widthDp = size.width.value,
        heightDp = size.height.value,
        fontScale = fontScale,
        formattedTemperatureLength = weather?.temperatureText?.length ?: DEFAULT_TEMPERATURE_LENGTH,
        currentConditionLabelLength = weather?.conditionLabel?.length ?: DEFAULT_CONDITION_LENGTH,
        apparentTemperatureLength = weather?.apparentTemperatureText?.length,
        headerTimestampTextLength = headerText?.length ?: 0
    )
    val locationName = snapshot.mediumLocationNameOrNull()
    val displayLocationName = locationName?.let { name ->
        ellipsizeMediumLocationName(name, policy.headerLocationTextWidthDp, context)
    }

    WeatherWidgetCard(
        snapshot = snapshot,
        locationDisplayName = displayLocationName,
        headerUpdateText = headerText.takeIf { policy.showHeaderTimestamp },
        verticalPaddingDp = policy.cardVerticalPaddingDp,
        headerUpdateContentDescription = available?.let { current ->
            val currentWeather = current.weather
            buildString {
                if (currentWeather.isStale || current.refresh.lastAttemptFailed) append("Stale weather. ")
                append("Last weather update ${currentWeather.lastWeatherUpdateText}")
            }
        }
    ) { palette ->
        Column(
            modifier = GlanceModifier.fillMaxWidth().padding(top = policy.headerToBodyGapDp.dp)
        ) {
            when (snapshot) {
                is WeatherWidgetSnapshot.Available -> {
                    val hours = snapshot.weather.hours.take(policy.hourlyColumnCount)
                    val hourlyColumnWidth = if (hours.isEmpty()) {
                        0f
                    } else {
                        (
                            size.width.value - MEDIUM_HORIZONTAL_INSETS_DP -
                                policy.currentWeatherWidthDp - hours.size * MEDIUM_DIVIDER_WIDTH_DP
                            ).coerceAtLeast(0f) / hours.size
                    }
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .height(policy.bodyHeightDp.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MediumCurrentWeather(
                            weather = snapshot.weather,
                            palette = palette,
                            policy = policy,
                            context = context,
                            fontScale = fontScale,
                            widthDp = if (hours.isEmpty()) {
                                (size.width.value - MEDIUM_HORIZONTAL_INSETS_DP).coerceAtLeast(0f)
                            } else {
                                policy.currentWeatherWidthDp
                            },
                            modifier = if (hours.isEmpty()) {
                                GlanceModifier.fillMaxWidth().padding(horizontal = MEDIUM_CELL_PADDING_DP.dp)
                            } else {
                                GlanceModifier.width(policy.currentWeatherWidthDp.dp)
                                    .padding(horizontal = MEDIUM_CELL_PADDING_DP.dp)
                            }
                        )
                        if (hours.isNotEmpty()) {
                            hours.forEach { hour ->
                                Row(
                                    modifier = GlanceModifier.width(
                                        (hourlyColumnWidth + MEDIUM_DIVIDER_WIDTH_DP).dp
                                    ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    MediumWeatherDivider(policy.bodyHeightDp, palette.divider)
                                    WeatherWidgetHourlyCell(
                                        hour = hour,
                                        palette = palette,
                                        iconSizeDp = policy.hourlyIconSizeDp,
                                        timeFontSizeSp = policy.hourlyTimeFontSizeSp,
                                        temperatureFontSizeSp = policy.hourlyTemperatureFontSizeSp,
                                        probabilityFontSizeSp = policy.hourlyProbabilityFontSizeSp,
                                        probabilityDropletSizeDp = policy.hourlyProbabilityDropletSizeDp,
                                        modifier = GlanceModifier.width(hourlyColumnWidth.dp)
                                            .padding(horizontal = MEDIUM_CELL_PADDING_DP.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                is WeatherWidgetSnapshot.NoLocation,
                is WeatherWidgetSnapshot.NoWeather -> {
                    WeatherWidgetEmptyContent(snapshot, palette)
                    WeatherWidgetStatus(snapshot, palette, compact = true)
                }
            }
        }
    }
}

@Composable
private fun MediumCurrentWeather(
    weather: WeatherWidgetState,
    palette: WeatherWidgetPalette,
    policy: MediumWeatherWidgetSize,
    context: Context,
    fontScale: Float,
    widthDp: Float,
    modifier: GlanceModifier
) {
    val contentWidth = (widthDp - MEDIUM_CELL_PADDING_DP * 2f).coerceAtLeast(0f)
    val conditionText = ellipsizeMediumText(
        text = weather.conditionLabel,
        widthDp = contentWidth,
        fontSizeSp = policy.currentConditionFontSizeSp,
        fontScale = fontScale,
        context = context
    )
    WeatherWidgetCurrentWeather(
        weather = weather,
        palette = palette,
        iconSizeDp = policy.currentIconSizeDp,
        temperatureFontSizeSp = policy.currentTemperatureFontSizeSp,
        showCondition = policy.showCurrentCondition,
        showApparentTemperature = policy.showCurrentApparentTemperature,
        conditionFontSizeSp = policy.currentConditionFontSizeSp,
        apparentTemperatureFontSizeSp = policy.currentApparentTemperatureFontSizeSp,
        conditionDisplayText = conditionText,
        secondaryItemGapDp = MEDIUM_SECONDARY_GAP_DP,
        modifier = modifier
    )
}

@Composable
private fun MediumWeatherDivider(heightDp: Float, color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = GlanceModifier
            .width(MEDIUM_DIVIDER_WIDTH_DP.dp)
            .height(heightDp.dp)
            .background(ColorProvider(color))
    ) {}
}

private fun WeatherWidgetSnapshot.mediumLocationNameOrNull(): String? = when (this) {
    is WeatherWidgetSnapshot.NoLocation -> null
    is WeatherWidgetSnapshot.NoWeather -> request.locationName
    is WeatherWidgetSnapshot.Available -> weather.locationName
}

private fun WeatherWidgetState.mediumHeaderUpdateText(refresh: WidgetRefreshStatus): String = buildString {
    append(
        when {
            isStale || refresh.lastAttemptFailed -> "Stale · "
            refresh.isRefreshing -> "Updating · "
            else -> "Updated "
        }
    )
    append(lastWeatherUpdateCompactText)
}

/** Glance Text has no overflow setting; measure and end-ellipsize to the reserved header width. */
private fun ellipsizeMediumLocationName(name: String, widthDp: Float, context: Context): String =
    ellipsizeMediumText(name, widthDp, LOCATION_FONT_SIZE_SP, context.resources.configuration.fontScale, context)

private fun ellipsizeMediumText(
    text: String,
    widthDp: Float,
    fontSizeSp: Float,
    fontScale: Float,
    context: Context
): String {
    val density = context.resources.displayMetrics.density
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = fontSizeSp * density * fontScale
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    return TextUtils.ellipsize(
        text,
        paint,
        widthDp.coerceAtLeast(0f) * density,
        TextUtils.TruncateAt.END
    ).toString()
}

private const val DEFAULT_TEMPERATURE_LENGTH = 4
private const val DEFAULT_CONDITION_LENGTH = 13
private const val MEDIUM_HORIZONTAL_INSETS_DP = 16f
private const val MEDIUM_CELL_PADDING_DP = 4f
private const val MEDIUM_SECONDARY_GAP_DP = 2f
private const val MEDIUM_DIVIDER_WIDTH_DP = 1f
private const val LOCATION_FONT_SIZE_SP = 14f
