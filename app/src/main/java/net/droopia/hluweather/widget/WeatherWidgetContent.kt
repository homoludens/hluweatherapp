package net.droopia.hluweather.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
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
import androidx.glance.layout.padding
import androidx.glance.layout.size
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
            .padding(8.dp)
    ) {
        WeatherWidgetHeader(
            locationName = locationName,
            isRefreshing = isRefreshing,
            palette = palette,
            displayLocationName = locationDisplayName
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
    displayLocationName: String? = null
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
            Text(
                text = label,
                modifier = GlanceModifier
                    .defaultWeight()
                    .semantics {
                        contentDescription = locationName?.let { "Weather for $it" }
                            ?: "Open HluWeather to choose a location"
                    },
                style = widgetTextStyle(palette.mainText, 14.sp, FontWeight.Medium),
                maxLines = 1
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
            Text(
                text = weather.temperatureText,
                modifier = GlanceModifier
                    .defaultWeight()
                    .semantics { contentDescription = weather.temperatureContentDescription },
                style = widgetTextStyle(
                    palette.mainText,
                    temperatureFontSizeSp.coerceAtLeast(1f).sp,
                    FontWeight.Bold
                ),
                maxLines = 1
            )
        }
        if (showCondition) {
            Text(
                text = weather.conditionLabel,
                style = widgetTextStyle(
                    palette.mainText,
                    conditionFontSizeSp.coerceAtLeast(1f).sp,
                    FontWeight.Medium
                ),
                maxLines = 1
            )
        }
        if (showApparentTemperature && weather.apparentTemperatureText != null) {
            Text(
                text = "Feels like ${weather.apparentTemperatureText}",
                modifier = GlanceModifier.semantics {
                    contentDescription = weather.apparentTemperatureContentDescription
                        ?.let { "Feels like $it" }
                        ?: "Feels like ${weather.apparentTemperatureText}"
                },
                style = widgetTextStyle(
                    palette.secondaryText,
                    apparentTemperatureFontSizeSp.coerceAtLeast(1f).sp
                ),
                maxLines = 1
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
    showProbability: Boolean = true,
    modifier: GlanceModifier = GlanceModifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = hour.timeText,
            style = widgetTextStyle(palette.secondaryText, timeFontSizeSp.coerceAtLeast(1f).sp),
            maxLines = 1
        )
        WeatherWidgetWeatherIcon(
            condition = hour.condition,
            isDay = hour.isDay,
            palette = palette,
            sizeDp = iconSizeDp,
            contentDescription = "${hour.timeText}, ${hour.conditionLabel}"
        )
        Text(
            text = hour.temperatureText,
            modifier = GlanceModifier.semantics {
                contentDescription = hour.temperatureContentDescription
            },
            style = widgetTextStyle(
                palette.mainText,
                temperatureFontSizeSp.coerceAtLeast(1f).sp,
                FontWeight.Medium
            ),
            maxLines = 1
        )
        if (showProbability) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WeatherWidgetMaterialIcon(
                    imageVector = Icons.Filled.WaterDrop,
                    tint = palette.primary,
                    sizeDp = 12f,
                    contentDescription = null
                )
                Text(
                    text = hour.precipitationProbabilityText,
                    modifier = GlanceModifier.semantics {
                        contentDescription = hour.precipitationProbabilityContentDescription
                    },
                    style = widgetTextStyle(palette.primary, 10.sp, FontWeight.Medium),
                    maxLines = 1
                )
            }
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
        style = widgetTextStyle(palette.secondaryText, 13.sp),
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

    Text(
        text = status,
        modifier = modifier.semantics { contentDescription = status },
        style = widgetTextStyle(palette.secondaryText, 10.sp),
        maxLines = 1
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

private fun openMainWeatherAction(context: Context): Action = actionStartActivity(
    Intent(context, MainActivity::class.java).addFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    )
)
