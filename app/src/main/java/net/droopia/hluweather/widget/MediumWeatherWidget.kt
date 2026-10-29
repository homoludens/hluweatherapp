package net.droopia.hluweather.widget

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import kotlinx.coroutines.flow.StateFlow
import net.droopia.hluweather.HluWeatherApplication

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 260, heightDp = 110)
@Preview(widthDp = 400, heightDp = 160)
@Composable
private fun MediumWeatherWidgetPreview() {
    MediumWeatherWidgetContent(previewWeatherSnapshot())
}

internal val MEDIUM_WEATHER_WIDGET_RESPONSIVE_SIZES = setOf(
    DpSize(260.dp, WeatherWidgetSizePolicy.MEDIUM_MIN_HEIGHT_DP.dp),
    DpSize(320.dp, WeatherWidgetSizePolicy.MEDIUM_MIN_HEIGHT_DP.dp),
    DpSize(400.dp, WeatherWidgetSizePolicy.MEDIUM_MIN_HEIGHT_DP.dp),
    DpSize(260.dp, WeatherWidgetSizePolicy.MEDIUM_STANDARD_HEIGHT_DP.dp),
    DpSize(320.dp, WeatherWidgetSizePolicy.MEDIUM_STANDARD_HEIGHT_DP.dp),
    DpSize(400.dp, 160.dp)
)

/** Current conditions and three to five future hours, observed from the app's cache. */
class MediumWeatherWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(MEDIUM_WEATHER_WIDGET_RESPONSIVE_SIZES)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Loading is cache-only. The application reconciliation/worker paths own weather requests.
        val application = context.applicationContext as HluWeatherApplication
        val repository = application.weatherWidgetRepository
        val snapshot = repository.loadCachedState()
        val renderConfiguration = application.widgetRenderConfiguration
        provideContent { ObservedMediumWeatherWidget(repository, snapshot, renderConfiguration) }
    }
}

@Composable
internal fun ObservedMediumWeatherWidget(
    repository: WeatherWidgetRepository,
    initial: WeatherWidgetSnapshot,
    renderConfigurations: StateFlow<WidgetRenderConfiguration>
) {
    val updates = remember(repository) { repository.observeSnapshots() }
    val current by updates.collectAsState(initial)
    val renderConfiguration by renderConfigurations.collectAsState(renderConfigurations.value)
    WidgetRenderConfigurationContext(renderConfiguration) {
        MediumWeatherWidgetContent(current)
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
    val textLineHeightMultiplier = remember(context, fontScale) {
        mediumTextViewLineHeightMultiplier(context, fontScale)
    }
    val formattedTemperatureLength = weather?.let { current ->
        maxOf(
            current.temperatureText.length,
            current.hours.maxOfOrNull { it.temperatureText.length } ?: 0
        )
    } ?: DEFAULT_TEMPERATURE_LENGTH
    val headerText = available?.let { it.weather.mediumHeaderUpdateText(it.refresh) }
    val policy = WeatherWidgetSizePolicy.medium(
        widthDp = size.width.value,
        heightDp = size.height.value,
        fontScale = fontScale,
        formattedTemperatureLength = formattedTemperatureLength,
        currentConditionLabelLength = weather?.conditionLabel?.length ?: DEFAULT_CONDITION_LENGTH,
        apparentTemperatureLength = weather?.apparentTemperatureText?.length,
        headerTimestampTextLength = headerText?.length ?: 0,
        textLineHeightMultiplier = textLineHeightMultiplier
    )
    val locationName = snapshot.mediumLocationNameOrNull()
    val displayLocationName = locationName?.let { name ->
        ellipsizeMediumLocationName(name, policy.headerLocationTextWidthDp, context)
    }

    WeatherWidgetCard(
        snapshot = snapshot,
        locationDisplayName = displayLocationName,
        headerUpdateText = headerText.takeIf { policy.showHeaderTimestamp },
        headerUpdateTextWidthDp = policy.headerTimestampTextWidthDp.takeIf { policy.showHeaderTimestamp },
        verticalPaddingDp = policy.cardVerticalPaddingDp,
        horizontalPaddingDp = policy.cardHorizontalPaddingDp,
        drawCardSurface = false,
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
                            size.width.value - policy.cardHorizontalPaddingDp * 2f -
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
                            textLineHeightMultiplier = textLineHeightMultiplier,
                            widthDp = if (hours.isEmpty()) {
                                (size.width.value - policy.cardHorizontalPaddingDp * 2f).coerceAtLeast(0f)
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
                                        textLineHeightMultiplier = textLineHeightMultiplier,
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
    textLineHeightMultiplier: Float,
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
        textLineHeightMultiplier = textLineHeightMultiplier,
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

/** Measures the actual single-line TextView line box, including Android's font padding. */
private fun mediumTextViewLineHeightMultiplier(context: Context, fontScale: Float): Float {
    val fontSizeSp = 12f
    val density = context.resources.displayMetrics.density
    val textView = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        includeFontPadding = true
        maxLines = 1
        text = "Ag"
    }
    textView.measure(
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    )
    textView.layout(0, 0, textView.measuredWidth, textView.measuredHeight)
    val textLayout = requireNotNull(textView.layout)
    val lineHeightPx = textView.compoundPaddingTop + textLayout.getLineBottom(0) +
        textView.compoundPaddingBottom
    val scale = if (fontScale.isFinite() && fontScale > 0f) fontScale else 1f
    return lineHeightPx / (fontSizeSp * scale * density)
}

private const val DEFAULT_TEMPERATURE_LENGTH = 4
private const val DEFAULT_CONDITION_LENGTH = 13
private const val MEDIUM_CELL_PADDING_DP = 4f
private const val MEDIUM_SECONDARY_GAP_DP = 2f
private const val MEDIUM_DIVIDER_WIDTH_DP = 1f
private const val LOCATION_FONT_SIZE_SP = 14f
