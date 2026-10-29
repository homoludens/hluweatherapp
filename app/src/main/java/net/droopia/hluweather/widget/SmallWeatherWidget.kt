package net.droopia.hluweather.widget

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
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
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Instant
import net.droopia.hluweather.HluWeatherApplication
import net.droopia.hluweather.data.model.ActiveLocation
import net.droopia.hluweather.data.model.WeatherCondition
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherProvider
import net.droopia.hluweather.ui.settings.PersistedSettings
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview


@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 110, heightDp = 110)
@Preview(widthDp = 160, heightDp = 160)
@Composable
private fun SmallWeatherWidgetPreview() {
    SmallWeatherWidgetContent(snapshot = previewWeatherSnapshot())
}

/** Sample values for editing widget layouts in Android Studio. */
internal fun previewWeatherSnapshot(): WeatherWidgetSnapshot.Available =
    WeatherWidgetSnapshot.Available(
        settings = PersistedSettings(),
        request = WeatherWidgetRequest(
            provider = WeatherProvider.OPEN_METEO,
            location = ActiveLocation.Saved(
                WeatherLocation("zajecar", "Zaječar", 43.9, 20.3)
            ),
            locationName = "Zaječar"
        ),
        weather = WeatherWidgetState(
            locationName = "Zaječar",
            temperatureText = "23°C",
            temperatureContentDescription = "23 degrees Celsius",
            apparentTemperatureText = "24°C",
            apparentTemperatureContentDescription = "Feels like 24 degrees Celsius",
            condition = WeatherCondition.CLEAR,
            conditionLabel = "Clear sky",
            isDay = true,
            hours = (1..5).map { hour ->
                WidgetHour(
                    time = Instant.parse("2026-10-28T10:17:00Z"),
                    timeText = "${11 + hour}:00",
                    temperatureText = "${23 - hour}°C",
                    temperatureContentDescription = "${23 - hour} degrees Celsius",
                    condition = WeatherCondition.CLEAR,
                    conditionLabel = "Clear sky",
                    isDay = true,
                    precipitationProbability = 10,
                    precipitationProbabilityText = "10%",
                    precipitationProbabilityContentDescription = "Rain probability 10 percent"
                )
            },
            lastWeatherUpdate = Instant.parse("2026-10-28T10:17:00Z"),
            lastWeatherUpdateText = "Wed, Oct 28 • 11:17",
            lastWeatherUpdateCompactText = "Oct 28 11:17",
            isStale = false
        )
    )

internal val SMALL_WEATHER_WIDGET_RESPONSIVE_SIZES = setOf(
    DpSize(110.dp, 110.dp),
    // Launcher cells can be narrow and tall. Width and height must both fit for Glance/the
    // host to select a variant, so include tall layouts without requiring a wider widget.
    DpSize(110.dp, 128.dp),
    DpSize(110.dp, 160.dp),
    DpSize(160.dp, 128.dp),
    DpSize(160.dp, 160.dp),
    DpSize(200.dp, 160.dp),
    DpSize(280.dp, 184.dp)
)

/** Current conditions only; app-owned reconciliation remains responsible for missing/due weather. */
class SmallWeatherWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(SMALL_WEATHER_WIDGET_RESPONSIVE_SIZES)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Loading is strictly cache-only. Do not enqueue from render: worker updates can re-render
        // stale or unavailable states, and doing so here would create a refresh feedback loop.
        val application = context.applicationContext as HluWeatherApplication
        val repository = application.weatherWidgetRepository
        val snapshot = repository.loadCachedState()
        val renderConfiguration = application.widgetRenderConfiguration
        provideContent { ObservedSmallWeatherWidget(repository, snapshot, renderConfiguration) }
    }
}

@Composable
internal fun ObservedSmallWeatherWidget(
    repository: WeatherWidgetRepository,
    initial: WeatherWidgetSnapshot,
    renderConfigurations: StateFlow<WidgetRenderConfiguration>
) {
    val updates = remember(repository) { repository.observeSnapshots() }
    val current by updates.collectAsState(initial)
    val renderConfiguration by renderConfigurations.collectAsState(renderConfigurations.value)
    WidgetRenderConfigurationContext(renderConfiguration) {
        SmallWeatherWidgetContent(current)
    }
}

/** Production small-card composition shared with translated RemoteViews tests. */
@Composable
internal fun SmallWeatherWidgetContent(snapshot: WeatherWidgetSnapshot) {
    val context = LocalContext.current
    val size = LocalSize.current
    val weather = (snapshot as? WeatherWidgetSnapshot.Available)?.weather
    val fontScale = context.resources.configuration.fontScale
    val policy = WeatherWidgetSizePolicy.small(
        widthDp = size.width.value,
        heightDp = size.height.value,
        fontScale = fontScale,
        formattedTemperatureLength = weather?.temperatureText?.length ?: DEFAULT_TEMPERATURE_LENGTH,
        apparentTemperatureLength = weather?.apparentTemperatureText?.length,
        hasStatus = snapshot.hasSmallStatus()
    )
    val locationName = snapshot.locationNameOrNull()
    val displayLocationName = locationName?.let { name ->
        ellipsizeLocationName(name, policy.locationWidthDp, context)
    }

    WeatherWidgetCard(
        snapshot = snapshot,
        locationDisplayName = displayLocationName
    ) { palette ->
        Column(modifier = GlanceModifier.fillMaxWidth().padding(top = 4.dp)) {
            when (snapshot) {
                is WeatherWidgetSnapshot.Available -> {
                    WeatherWidgetCurrentWeather(
                        weather = snapshot.weather,
                        palette = palette,
                        iconSizeDp = policy.iconSizeDp,
                        temperatureFontSizeSp = policy.temperatureFontSizeSp,
                        conditionFontSizeSp = policy.conditionFontSizeSp,
                        apparentTemperatureFontSizeSp = policy.apparentTemperatureFontSizeSp,
                        showCondition = policy.showCondition,
                        showApparentTemperature = policy.showApparentTemperature,
                        modifier = GlanceModifier.fillMaxWidth()
                    )
                    if (policy.showStatus) {
                        WeatherWidgetStatus(snapshot, palette, compact = true)
                    }
                }

                is WeatherWidgetSnapshot.NoLocation,
                is WeatherWidgetSnapshot.NoWeather -> {
                    WeatherWidgetEmptyContent(snapshot, palette)
                    if (snapshot is WeatherWidgetSnapshot.NoWeather && snapshot.hasSmallStatus()) {
                        WeatherWidgetStatus(snapshot, palette, compact = true)
                    }
                }
            }
        }
    }
}

private fun WeatherWidgetSnapshot.locationNameOrNull(): String? = when (this) {
    is WeatherWidgetSnapshot.NoLocation -> null
    is WeatherWidgetSnapshot.NoWeather -> request.locationName
    is WeatherWidgetSnapshot.Available -> weather.locationName
}

private fun WeatherWidgetSnapshot.hasSmallStatus(): Boolean = when (this) {
    is WeatherWidgetSnapshot.NoLocation -> false
    is WeatherWidgetSnapshot.NoWeather -> refresh.isRefreshing || refresh.lastAttemptFailed
    is WeatherWidgetSnapshot.Available -> weather.isStale || refresh.isRefreshing || refresh.lastAttemptFailed
}

/** Glance Text has a max-line limit but no overflow setting; pre-ellipsize to the reserved width. */
private fun ellipsizeLocationName(name: String, widthDp: Float, context: Context): String {
    val density = context.resources.displayMetrics.density
    val fontScale = context.resources.configuration.fontScale
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = LOCATION_FONT_SIZE_SP * density * fontScale
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    return TextUtils.ellipsize(
        name,
        paint,
        widthDp.coerceAtLeast(0f) * density,
        TextUtils.TruncateAt.END
    ).toString()
}

private const val DEFAULT_TEMPERATURE_LENGTH = 4
private const val LOCATION_FONT_SIZE_SP = 14f
