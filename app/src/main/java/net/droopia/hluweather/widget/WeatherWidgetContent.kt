package net.droopia.hluweather.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import net.droopia.hluweather.MainActivity
import net.droopia.hluweather.R
import net.droopia.hluweather.data.model.WeatherCondition
import net.droopia.hluweather.data.model.ThemeMode
import net.droopia.hluweather.ui.components.weatherIconSpec
import net.droopia.hluweather.ui.settings.PersistedSettings
import net.droopia.hluweather.ui.theme.DarkHluColors
import net.droopia.hluweather.ui.theme.HluColors
import net.droopia.hluweather.ui.theme.LightHluColors
import net.droopia.hluweather.ui.theme.hluMaterialColorScheme

/** Palette resolved from the saved preference and system configuration without touching a window. */
data class WeatherWidgetPalette internal constructor(
    val weatherColors: HluColors,
    val cardSurface: Color,
    val primary: Color,
    val mainText: Color,
    val secondaryText: Color,
    val divider: Color
)

/** The same card and header are used by both widget sizes; content remains size-specific. */
@Composable
fun WeatherWidgetCard(
    snapshot: WeatherWidgetSnapshot,
    modifier: GlanceModifier = GlanceModifier,
    locationDisplayName: String? = null,
    headerUpdateText: String? = null,
    headerUpdateContentDescription: String? = null,
    headerUpdateTextWidthDp: Float? = null,
    verticalPaddingDp: Float = 8f,
    content: @Composable (WeatherWidgetPalette) -> Unit
) {
    val context = LocalContext.current
    val palette = weatherWidgetPalette(
        snapshot.settings,
        isSystemDark = context.resources.configuration.isNightMode()
    )
    val locationName = when (snapshot) {
        is WeatherWidgetSnapshot.NoLocation -> null
        is WeatherWidgetSnapshot.NoWeather -> snapshot.request.locationName
        is WeatherWidgetSnapshot.Available -> snapshot.weather.locationName
    }
    val isRefreshing = when (snapshot) {
        is WeatherWidgetSnapshot.NoLocation -> false
        is WeatherWidgetSnapshot.NoWeather -> snapshot.refresh.isRefreshing
        is WeatherWidgetSnapshot.Available -> snapshot.refresh.isRefreshing
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                ImageProvider(R.drawable.weather_widget_card_surface),
                contentScale = ContentScale.FillBounds,
                colorFilter = ColorFilter.tint(ColorProvider(palette.cardSurface))
            )
            .clickable(openMainWeatherAction(context))
            .padding(horizontal = 8.dp, vertical = verticalPaddingDp.dp)
    ) {
        WeatherWidgetHeader(
            locationName = locationName,
            isRefreshing = isRefreshing,
            palette = palette,
            displayLocationName = locationDisplayName,
            updateText = headerUpdateText,
            updateContentDescription = headerUpdateContentDescription,
            updateTextWidthDp = headerUpdateTextWidthDp
        )
        content(palette)
    }
}

/** Location and refresh row shared by both widgets. The refresh target remains its own action. */
@Composable
fun WeatherWidgetHeader(
    locationName: String?,
    isRefreshing: Boolean,
    palette: WeatherWidgetPalette,
    modifier: GlanceModifier = GlanceModifier,
    displayLocationName: String? = null,
    updateText: String? = null,
    updateContentDescription: String? = null,
    updateTextWidthDp: Float? = null
) {
    val context = LocalContext.current
    val label = displayLocationName ?: locationName ?: "Choose a location"
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = GlanceModifier
                .defaultWeight()
                .clickable(openMainWeatherAction(context)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WeatherWidgetMaterialIcon(
                imageVector = Icons.Filled.LocationOn,
                tint = palette.weatherColors.cloudAccent,
                sizeDp = 18f,
                contentDescription = null
            )
            WeatherWidgetAdaptiveText(
                text = label,
                role = WeatherWidgetAdaptiveTextRole.MEDIUM,
                color = palette.mainText,
                fontSizeSp = LOCATION_FONT_SIZE_SP,
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxWidth(),
                contentDescription = locationName?.let { "Weather for $it" }
                    ?: "Open HluWeather to choose a location"
            )
        }
        if (updateText != null) {
            WeatherWidgetAdaptiveText(
                text = updateText,
                role = WeatherWidgetAdaptiveTextRole.TINY_REGULAR,
                color = palette.secondaryText,
                fontSizeSp = HEADER_UPDATE_FONT_SIZE_SP,
                modifier = GlanceModifier
                    .then(
                        updateTextWidthDp?.let { GlanceModifier.width(it.dp) }
                            ?: GlanceModifier
                    )
                    .padding(horizontal = 4.dp),
                contentDescription = updateContentDescription ?: updateText
            )
        }
        WeatherWidgetRefreshAction(palette, isRefreshing)
    }
}

/** Standard 48 dp refresh target; its callback only enqueues background work. */
@Composable
fun WeatherWidgetRefreshAction(
    palette: WeatherWidgetPalette,
    isRefreshing: Boolean,
    modifier: GlanceModifier = GlanceModifier
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(actionRunCallback<RefreshWeatherAction>())
            .semantics { contentDescription = "Refresh weather" },
        contentAlignment = Alignment.Center
    ) {
        WeatherWidgetMaterialIcon(
            imageVector = Icons.Filled.Refresh,
            tint = if (isRefreshing) palette.secondaryText else palette.primary,
            sizeDp = 20f,
            contentDescription = null
        )
    }
}

/** Renders the exact vector and tint selected for the app's Compose weather icon. */
@Composable
fun WeatherWidgetWeatherIcon(
    condition: WeatherCondition,
    isDay: Boolean?,
    palette: WeatherWidgetPalette,
    sizeDp: Float,
    modifier: GlanceModifier = GlanceModifier,
    contentDescription: String? = null
) {
    val spec = weatherIconSpec(condition, isDay, palette.weatherColors)
    WeatherWidgetVectorIcon(
        imageVector = spec.imageVector,
        tint = spec.tint,
        sizeDp = sizeDp,
        modifier = modifier,
        contentDescription = contentDescription
    )
}

/** Current-weather block shared by small and medium layouts; callers supply responsive sizes. */
@Composable
fun WeatherWidgetCurrentWeather(
    weather: WeatherWidgetState,
    palette: WeatherWidgetPalette,
    iconSizeDp: Float,
    temperatureFontSizeSp: Float,
    showCondition: Boolean = true,
    showApparentTemperature: Boolean = true,
    conditionFontSizeSp: Float = 12f,
    apparentTemperatureFontSizeSp: Float = 11f,
    conditionDisplayText: String = weather.conditionLabel,
    secondaryItemGapDp: Float = 0f,
    textLineHeightMultiplier: Float = DEFAULT_TEXT_LINE_HEIGHT_MULTIPLIER,
    modifier: GlanceModifier = GlanceModifier
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WeatherWidgetWeatherIcon(
                condition = weather.condition,
                isDay = weather.isDay,
                palette = palette,
                sizeDp = iconSizeDp,
                modifier = GlanceModifier.padding(end = 4.dp)
            )
            WeatherWidgetAdaptiveText(
                text = weather.temperatureText,
                role = WeatherWidgetAdaptiveTextRole.PRIMARY,
                color = palette.mainText,
                fontSizeSp = temperatureFontSizeSp.coerceAtLeast(1f),
                lineHeightMultiplier = textLineHeightMultiplier,
                modifier = GlanceModifier.defaultWeight(),
                contentDescription = weather.temperatureContentDescription
            )
        }
        if (showCondition) {
            WeatherWidgetAdaptiveText(
                text = conditionDisplayText,
                role = WeatherWidgetAdaptiveTextRole.MEDIUM,
                color = palette.mainText,
                fontSizeSp = conditionFontSizeSp.coerceAtLeast(0.1f),
                lineHeightMultiplier = textLineHeightMultiplier,
                modifier = GlanceModifier.fillMaxWidth().padding(top = secondaryItemGapDp.dp),
                contentDescription = weather.conditionLabel
            )
        }
        if (showApparentTemperature && weather.apparentTemperatureText != null) {
            WeatherWidgetAdaptiveText(
                text = "Feels like ${weather.apparentTemperatureText}",
                role = WeatherWidgetAdaptiveTextRole.REGULAR,
                color = palette.secondaryText,
                fontSizeSp = apparentTemperatureFontSizeSp.coerceAtLeast(1f),
                lineHeightMultiplier = textLineHeightMultiplier,
                modifier = GlanceModifier.fillMaxWidth().padding(top = secondaryItemGapDp.dp),
                contentDescription = weather.apparentTemperatureContentDescription
                    ?.let { "Feels like $it" }
                    ?: "Feels like ${weather.apparentTemperatureText}"
            )
        }
    }
}

/** Shared medium-widget hour cell. Its weather glyph is described once; utility glyphs are silent. */
@Composable
fun WeatherWidgetHourlyCell(
    hour: WidgetHour,
    palette: WeatherWidgetPalette,
    iconSizeDp: Float,
    timeFontSizeSp: Float,
    temperatureFontSizeSp: Float,
    probabilityFontSizeSp: Float,
    probabilityDropletSizeDp: Float,
    textLineHeightMultiplier: Float = DEFAULT_TEXT_LINE_HEIGHT_MULTIPLIER,
    modifier: GlanceModifier = GlanceModifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        WeatherWidgetAdaptiveText(
            text = hour.timeText,
            role = WeatherWidgetAdaptiveTextRole.COMPACT_REGULAR,
            color = palette.secondaryText,
            fontSizeSp = timeFontSizeSp.coerceAtLeast(1f),
            lineHeightMultiplier = textLineHeightMultiplier,
            modifier = GlanceModifier.fillMaxWidth(),
            contentDescription = hour.timeText
        )
        WeatherWidgetWeatherIcon(
            condition = hour.condition,
            isDay = hour.isDay,
            palette = palette,
            sizeDp = iconSizeDp,
            modifier = GlanceModifier.padding(top = 2.dp),
            contentDescription = "${hour.timeText}, ${hour.conditionLabel}"
        )
        WeatherWidgetAdaptiveText(
            text = hour.temperatureText,
            role = WeatherWidgetAdaptiveTextRole.MEDIUM_CENTER,
            color = palette.mainText,
            fontSizeSp = temperatureFontSizeSp.coerceAtLeast(1f),
            lineHeightMultiplier = textLineHeightMultiplier,
            modifier = GlanceModifier.fillMaxWidth().padding(top = 2.dp),
            contentDescription = hour.temperatureContentDescription
        )
        Row(
            modifier = GlanceModifier.padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WeatherWidgetMaterialIcon(
                imageVector = Icons.Filled.WaterDrop,
                tint = palette.primary,
                sizeDp = probabilityDropletSizeDp,
                contentDescription = null
            )
            WeatherWidgetAdaptiveText(
                text = hour.precipitationProbabilityText,
                role = WeatherWidgetAdaptiveTextRole.COMPACT_MEDIUM_CENTER,
                color = palette.primary,
                fontSizeSp = probabilityFontSizeSp.coerceAtLeast(0.1f),
                lineHeightMultiplier = textLineHeightMultiplier,
                modifier = GlanceModifier.defaultWeight(),
                contentDescription = hour.precipitationProbabilityContentDescription
            )
        }
    }
}

/** Honest empty-state copy; it reads the supplied snapshot and never starts data work. */
@Composable
fun WeatherWidgetEmptyContent(
    snapshot: WeatherWidgetSnapshot,
    palette: WeatherWidgetPalette,
    modifier: GlanceModifier = GlanceModifier
) {
    val text = when (snapshot) {
        is WeatherWidgetSnapshot.NoLocation -> "Open HluWeather to choose a location"
        is WeatherWidgetSnapshot.NoWeather -> "No weather available for ${snapshot.request.locationName}"
        is WeatherWidgetSnapshot.Available -> return
    }
    Text(
        text = text,
        modifier = modifier.semantics { contentDescription = text },
        style = widgetTextStyle(palette.secondaryText, EMPTY_STATE_FONT_SIZE_SP.sp),
        maxLines = 2
    )
}

/** Reports refresh state and stale timestamps without replacing the forecast's fetched time. */
@Composable
fun WeatherWidgetStatus(
    snapshot: WeatherWidgetSnapshot,
    palette: WeatherWidgetPalette,
    showUpdateTime: Boolean = false,
    compact: Boolean = false,
    modifier: GlanceModifier = GlanceModifier
) {
    val status = when (snapshot) {
        is WeatherWidgetSnapshot.NoLocation -> null
        is WeatherWidgetSnapshot.NoWeather -> when {
            snapshot.refresh.isRefreshing -> if (compact) "Refreshing" else "Refreshing weather"
            snapshot.refresh.lastAttemptFailed -> if (compact) "Update failed" else "Weather update failed"
            else -> null
        }
        is WeatherWidgetSnapshot.Available -> when {
            compact && snapshot.refresh.isRefreshing && snapshot.weather.isStale -> "Refreshing · stale"
            compact && snapshot.refresh.isRefreshing -> "Refreshing"
            compact && snapshot.weather.isStale -> "Stale weather"
            compact && snapshot.refresh.lastAttemptFailed -> "Update failed"
            snapshot.refresh.isRefreshing && snapshot.weather.isStale ->
                "Refreshing · cached weather is stale"
            snapshot.refresh.isRefreshing -> "Refreshing weather"
            snapshot.weather.isStale -> "Stale · updated ${snapshot.weather.lastWeatherUpdateText}"
            snapshot.refresh.lastAttemptFailed -> "Refresh failed · updated ${snapshot.weather.lastWeatherUpdateText}"
            showUpdateTime -> "Updated ${snapshot.weather.lastWeatherUpdateText}"
            else -> null
        }
    } ?: return

    WeatherWidgetAdaptiveText(
        text = status,
        role = WeatherWidgetAdaptiveTextRole.COMPACT_REGULAR,
        color = palette.secondaryText,
        fontSizeSp = STATUS_FONT_SIZE_SP,
        modifier = modifier.fillMaxWidth(),
        contentDescription = status
    )
}

@Composable
private fun WeatherWidgetMaterialIcon(
    imageVector: ImageVector,
    tint: Color,
    sizeDp: Float,
    contentDescription: String?
) {
    WeatherWidgetVectorIcon(
        imageVector = imageVector,
        tint = tint,
        sizeDp = sizeDp,
        contentDescription = contentDescription
    )
}

@Composable
private fun WeatherWidgetVectorIcon(
    imageVector: ImageVector,
    tint: Color,
    sizeDp: Float,
    modifier: GlanceModifier = GlanceModifier,
    contentDescription: String?
) {
    val density = LocalContext.current.resources.displayMetrics.density
    val pixels = (sizeDp.coerceAtLeast(1f) * density)
        .toInt()
        .coerceIn(1, WeatherIconBitmapAdapter.MAX_ICON_DIMENSION_PX)
    val bitmapProvider = WeatherIconBitmapAdapter.imageProvider(
        imageVector = imageVector,
        tint = tint,
        widthPx = pixels,
        heightPx = pixels,
        density = density
    )
    Image(
        provider = bitmapProvider,
        contentDescription = contentDescription,
        modifier = modifier.size(sizeDp.coerceAtLeast(1f).dp),
        contentScale = ContentScale.Fit
    )
}

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
@Composable
private fun WeatherWidgetAdaptiveText(
    text: String,
    role: WeatherWidgetAdaptiveTextRole,
    color: Color,
    fontSizeSp: Float,
    modifier: GlanceModifier = GlanceModifier,
    contentDescription: String? = text,
    lineHeightMultiplier: Float = DEFAULT_TEXT_LINE_HEIGHT_MULTIPLIER,
) {
    val context = LocalContext.current
    val fontScale = context.resources.configuration.fontScale
        .takeIf { it.isFinite() && it > 0f } ?: 1f
    val lineBoxHeightDp = (fontSizeSp.coerceAtLeast(0.1f) * fontScale *
        lineHeightMultiplier.coerceAtLeast(MIN_AUTO_TEXT_LINE_HEIGHT_MULTIPLIER))
        .coerceAtLeast(MIN_ADAPTIVE_TEXT_BOX_HEIGHT_DP)
    val remoteViews = RemoteViews(context.packageName, role.layoutId).apply {
        setTextViewText(R.id.weather_widget_adaptive_text, text)
        setTextViewTextSize(
            R.id.weather_widget_adaptive_text,
            TypedValue.COMPLEX_UNIT_SP,
            fontSizeSp.coerceAtLeast(0.1f)
        )
        setTextColor(R.id.weather_widget_adaptive_text, color.toArgb())
        setContentDescription(R.id.weather_widget_adaptive_text, contentDescription)
    }
    AndroidRemoteViews(remoteViews, modifier = modifier.height(lineBoxHeightDp.dp))
}

private enum class WeatherWidgetAdaptiveTextRole(val layoutId: Int) {
    PRIMARY(R.layout.weather_widget_adaptive_text_primary),
    MEDIUM(R.layout.weather_widget_adaptive_text_medium),
    MEDIUM_CENTER(R.layout.weather_widget_adaptive_text_medium_center),
    REGULAR(R.layout.weather_widget_adaptive_text_regular),
    COMPACT_MEDIUM_CENTER(R.layout.weather_widget_adaptive_text_compact_medium_center),
    COMPACT_REGULAR(R.layout.weather_widget_adaptive_text_compact_regular),
    COMPACT_REGULAR_CENTER(R.layout.weather_widget_adaptive_text_compact_regular_center),
    TINY_REGULAR(R.layout.weather_widget_adaptive_text_tiny_regular)
}

private fun widgetTextStyle(
    color: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontWeight: FontWeight = FontWeight.Normal
) = TextStyle(
    color = ColorProvider(color),
    fontSize = fontSize,
    fontWeight = fontWeight,
    fontFamily = FontFamily.SansSerif
)

internal fun weatherWidgetPalette(
    settings: PersistedSettings,
    isSystemDark: Boolean
): WeatherWidgetPalette {
    val darkTheme = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val weatherColors = if (darkTheme) DarkHluColors else LightHluColors
    val material = hluMaterialColorScheme(darkTheme)
    return WeatherWidgetPalette(
        weatherColors = weatherColors,
        cardSurface = weatherColors.weatherCard,
        primary = material.primary,
        mainText = material.onSurface,
        secondaryText = material.onSurfaceVariant,
        divider = material.outlineVariant
    )
}

private fun Configuration.isNightMode(): Boolean =
    uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

private const val LOCATION_FONT_SIZE_SP = 14f
private const val HEADER_UPDATE_FONT_SIZE_SP = 9f
private const val EMPTY_STATE_FONT_SIZE_SP = 13f
private const val STATUS_FONT_SIZE_SP = 10f
private const val DEFAULT_TEXT_LINE_HEIGHT_MULTIPLIER = 1.3f
private const val MIN_AUTO_TEXT_LINE_HEIGHT_MULTIPLIER = 1.3f
private const val MIN_ADAPTIVE_TEXT_BOX_HEIGHT_DP = 9f

private fun openMainWeatherAction(context: Context): Action = actionStartActivity(
    Intent(context, MainActivity::class.java).addFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    )
)
