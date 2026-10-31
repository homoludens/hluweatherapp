package net.droopia.hluweather.widget

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.graphics.drawable.BitmapDrawable
import androidx.test.core.app.ApplicationProvider
import androidx.glance.GlanceId
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.composeForPreview
import androidx.glance.appwidget.provideContent
import androidx.compose.ui.graphics.toArgb
import kotlin.math.roundToInt
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import net.droopia.hluweather.data.model.ActiveLocation
import net.droopia.hluweather.data.model.CurrentWeather
import net.droopia.hluweather.data.model.DayForecast
import net.droopia.hluweather.data.model.HourForecast
import net.droopia.hluweather.data.model.LocationMode
import net.droopia.hluweather.data.model.WeatherCondition
import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherProvider
import net.droopia.hluweather.data.repository.ForecastLoad
import net.droopia.hluweather.data.repository.LocationRepository
import net.droopia.hluweather.data.repository.WeatherRepository
import net.droopia.hluweather.ui.settings.PersistedSettings
import net.droopia.hluweather.ui.settings.SettingsRepository
import net.droopia.hluweather.ui.settings.TemperatureUnit
import net.droopia.hluweather.data.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import net.droopia.hluweather.ui.theme.DarkHluColors
import net.droopia.hluweather.ui.theme.LightHluColors
import net.droopia.hluweather.ui.theme.hluMaterialColorScheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SmallWeatherWidgetTest {

    @Test
    fun production_widget_uses_finite_responsive_sizes_from_the_small_size_policy() {
        val widget = SmallWeatherWidget()

        assertTrue(widget.sizeMode is SizeMode.Responsive)
        assertEquals(
            SMALL_WEATHER_WIDGET_RESPONSIVE_SIZES,
            (widget.sizeMode as SizeMode.Responsive).sizes
        )
    }

    @Test
    fun small_production_card_has_a_transparent_background() {
        val view = render(availableSnapshot(), widthDp = 200f, heightDp = 160f)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        assertEquals("small widget is transparent behind its content", 0, bitmap.getPixel(2, view.height / 2) ushr 24)
    }

    @Test
    fun translated_production_layout_preserves_small_weather_priorities_at_minimum_compact_and_normal_sizes() {
        val cases = listOf(
            LayoutCase(110f, 110f, 1f, ThemeMode.LIGHT),
            LayoutCase(110f, 128f, 1f, ThemeMode.LIGHT),
            LayoutCase(110f, 160f, 1f, ThemeMode.LIGHT),
            LayoutCase(110f, 160f, 1.3f, ThemeMode.DARK),
            LayoutCase(160f, 128f, 1f, ThemeMode.SYSTEM, systemDark = true),
            LayoutCase(160f, 128f, 1.3f, ThemeMode.LIGHT),
            LayoutCase(200f, 160f, 1f, ThemeMode.DARK)
        )

        cases.forEach { case ->
            val view = render(
                availableSnapshot(settings = PersistedSettings(themeMode = case.theme)),
                case.widthDp,
                case.heightDp,
                case.fontScale,
                case.systemDark
            )

            val policy = WeatherWidgetSizePolicy.small(case.widthDp, case.heightDp, case.fontScale, 4, 4)
            assertFalse(hasText(view, "Zaječar"))
            assertFalse(hasDescription(view, "Weather for Zaječar"))
            assertText(view, "23°C")
            assertEquals(policy.showCondition, hasText(view, "Clear sky"))
            assertEquals(policy.showApparentTemperature, hasText(view, "Feels like 24°C"))
            assertTrue("weather icon remains visible for $case", hasWeatherBitmap(view))
            assertFalse(hasDescription(view, "Refresh weather"))
            assertPrimaryContentFits(view, case.widthDp, case.heightDp)
            assertFalse("small widget never shows hourly content", hasText(view, "22:00"))
            assertFalse("small widget never shows precipitation columns", hasText(view, "60%"))
        }
    }

    @Config(sdk = [28, 34])
    @Test
    fun system_configuration_change_rethemes_small_widget_but_explicit_preferences_win() {
        val system = availableSnapshot(settings = PersistedSettings(themeMode = ThemeMode.SYSTEM))
        assertRenderedTheme(render(system, 200f, 160f, systemDark = false), dark = false)
        assertRenderedTheme(render(system, 200f, 160f, systemDark = true), dark = true)

        val light = availableSnapshot(settings = PersistedSettings(themeMode = ThemeMode.LIGHT))
        assertRenderedTheme(render(light, 200f, 160f, systemDark = true), dark = false)

        val dark = availableSnapshot(settings = PersistedSettings(themeMode = ThemeMode.DARK))
        assertRenderedTheme(render(dark, 200f, 160f, systemDark = false), dark = true)
    }

    @Test
    fun location_and_refresh_are_absent_for_available_and_missing_weather() {
        val longName = "A very long location name"
        val available = render(
            availableSnapshot(locationName = longName),
            widthDp = 110f,
            heightDp = 110f
        )
        val noWeather = render(
            WeatherWidgetSnapshot.NoWeather(PersistedSettings(), request(longName), WidgetRefreshStatus()),
            widthDp = 110f,
            heightDp = 110f
        )
        listOf(available, noWeather).forEach { view ->
            assertFalse(hasDescription(view, "Weather for $longName"))
            assertFalse(hasDescription(view, "Refresh weather"))
            assertFalse(findViews(view).filterIsInstance<TextView>().any { it.text.contains(longName) })
        }
        assertText(noWeather, "No weather available")
        assertPrimaryContentFits(available, widthDp = 110f, heightDp = 110f)
    }

    @Test
    fun absent_apparent_temperature_is_omitted_without_replacing_the_condition() {
        val view = render(
            availableSnapshot(apparentTemperature = null),
            widthDp = 200f,
            heightDp = 160f
        )

        assertText(view, "Clear sky")
        assertFalse(findViews(view).filterIsInstance<TextView>().any {
            it.text.toString().startsWith("Feels like")
        })
    }

    @Test
    fun signed_extreme_and_three_digit_temperatures_fit_at_large_font_scale_in_both_themes() {
        listOf(
            "−123°F" to ThemeMode.DARK,
            "123°F" to ThemeMode.LIGHT
        ).forEach { (temperature, theme) ->
            val view = render(
                availableSnapshot(
                    temperature = temperature,
                    apparentTemperature = "124°F",
                    settings = PersistedSettings(temperatureUnit = TemperatureUnit.FAHRENHEIT, themeMode = theme)
                ),
                widthDp = 110f,
                heightDp = 110f,
                fontScale = 2f
            )

            assertText(view, temperature)
            assertPrimaryContentFits(view, widthDp = 110f, heightDp = 110f)
            assertFalse(hasDescription(view, "Refresh weather"))
        }
    }

    @Test
    fun stale_cached_weather_is_rendered_before_any_live_weather_request() = runBlocking {
        val location = WeatherLocation("zajecar", "Zaječar", 43.9, 20.3)
        val forecast = WeatherForecast(
            location = location,
            provider = WeatherProvider.OPEN_METEO,
            fetchedAt = Instant.parse("2026-10-28T10:17:00Z"),
            current = CurrentWeather(
                temperature = 23.0,
                apparentTemperature = 24.0,
                humidity = null,
                dewPoint = null,
                precipitation = null,
                condition = WeatherCondition.CLEAR,
                isDay = true
            ),
            hourly = emptyList<HourForecast>(),
            daily = emptyList<DayForecast>(),
            moonPhase = 0.0,
            timezone = "Europe/Belgrade"
        )
        val weather = CacheOnlyWeatherRepository(ForecastLoad(forecast, isStale = true))
        val repository = WeatherWidgetRepository(
            settingsRepository = FixedSettingsRepository(
                PersistedSettings(temperatureUnit = TemperatureUnit.FAHRENHEIT, themeMode = ThemeMode.DARK)
            ),
            locationRepository = FixedLocationRepository(location),
            weatherRepository = weather
        )

        val snapshot = repository.loadCachedState()
        val view = render(snapshot, widthDp = 200f, heightDp = 160f)

        assertText(view, "73°F")
        assertText(view, "Stale weather")
        assertPrimaryContentFits(view, widthDp = 200f, heightDp = 160f)
        assertEquals(1, weather.cacheReads)
        assertEquals(0, weather.liveRequests)
    }

    @Test
    fun failed_refresh_keeps_cached_fahrenheit_weather_and_reports_failure_in_remote_views() {
        val snapshot = availableSnapshot(
            temperature = "73°F",
            apparentTemperature = "74°F",
            settings = PersistedSettings(
                temperatureUnit = TemperatureUnit.FAHRENHEIT,
                themeMode = ThemeMode.DARK
            )
        ).copy(
            refresh = WidgetRefreshStatus(
                endedAt = Instant.parse("2026-10-28T12:00:00Z"),
                lastAttemptFailed = true
            )
        )

        listOf(160f to false, 200f to true).forEach { (height, showFeelsLike) ->
            val view = render(snapshot, widthDp = 200f, heightDp = height)
            assertText(view, "73°F")
            assertText(view, "Clear sky")
            assertEquals(showFeelsLike, hasText(view, "Feels like 74°F"))
            assertText(view, "Update failed")
            assertPrimaryContentFits(view, widthDp = 200f, heightDp = height)
        }
    }

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    private fun render(
        snapshot: WeatherWidgetSnapshot,
        widthDp: Float,
        heightDp: Float,
        fontScale: Float = 1f,
        systemDark: Boolean = false
    ): View {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val configuration = Configuration(baseContext.resources.configuration).apply {
            this.fontScale = fontScale
            val nightMode = if (systemDark) {
                Configuration.UI_MODE_NIGHT_YES
            } else {
                Configuration.UI_MODE_NIGHT_NO
            }
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
        }
        val context = baseContext.createConfigurationContext(configuration)
        val density = context.resources.displayMetrics.density
        val providerInfo = AppWidgetProviderInfo().apply {
            minWidth = (widthDp * density).roundToInt()
            minHeight = (heightDp * density).roundToInt()
        }
        val remoteViews = runBlocking {
            PreviewSmallWeatherWidget(snapshot).composeForPreview(
                context,
                AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
                providerInfo
            )
        }
        val view = remoteViews.apply(context, FrameLayout(context))
        val widthPx = (widthDp * density).roundToInt()
        val heightPx = (heightDp * density).roundToInt()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, widthPx, heightPx)
        return view
    }

    private fun assertPrimaryContentFits(root: View, widthDp: Float, heightDp: Float) {
        val density = root.resources.displayMetrics.density
        val widthPx = (widthDp * density).roundToInt()
        val heightPx = (heightDp * density).roundToInt()
        val temperature = findViews(root).filterIsInstance<TextView>()
            .first { it.text.toString().contains("°") }
        val textWidth = temperature.width - temperature.compoundPaddingLeft - temperature.compoundPaddingRight
        assertTrue("primary temperature must have positive width", textWidth > 0)
        assertTrue(
            "primary temperature should fit without clipping: '${temperature.text}' measured=${temperature.paint.measureText(temperature.text.toString())} width=$textWidth",
            temperature.paint.measureText(temperature.text.toString()) <= textWidth + 1f
        )
        assertEquals(1, temperature.lineCount)

        findViews(root).filterIsInstance<TextView>().forEach { text ->
            val bounds = absoluteBounds(text, root)
            assertTrue("$text left edge remains in the card", bounds.left >= 0)
            assertTrue("$text right edge remains in the card", bounds.right <= widthPx)
            assertTrue("$text top edge remains in the card", bounds.top >= 0)
            assertTrue("$text bottom edge remains in the card", bounds.bottom <= heightPx)
        }
        assertRenderedContentFitsWithoutOverlap(root)
    }

    private fun hasWeatherBitmap(root: View): Boolean = findViews(root)
        .filterIsInstance<ImageView>()
        .any { it.drawable is BitmapDrawable }

    private fun assertRenderedTheme(root: View, dark: Boolean) {
        val temperature = findViews(root).filterIsInstance<TextView>()
            .first { it.text.toString().contains("°") }
        assertEquals(hluMaterialColorScheme(dark).onSurface.toArgb(), temperature.currentTextColor)

        val colors = if (dark) DarkHluColors else LightHluColors
        val expectedIconTint = net.droopia.hluweather.ui.components
            .weatherIconSpec(WeatherCondition.CLEAR, true, colors).tint.toArgb()
        assertTrue(
            "small widget icon should use the ${if (dark) "dark" else "light"} theme tint",
            findViews(root).filterIsInstance<ImageView>().mapNotNull {
                (it.drawable as? BitmapDrawable)?.bitmap
            }.any { bitmap -> bitmap.hasPixelColor(expectedIconTint) }
        )
    }

    private fun android.graphics.Bitmap.hasPixelColor(expectedColor: Int): Boolean {
        for (y in 0 until height) {
            for (x in 0 until width) {
                val color = getPixel(x, y)
                if (color ushr 24 > 32 && (color and 0x00FFFFFF) == (expectedColor and 0x00FFFFFF)) {
                    return true
                }
            }
        }
        return false
    }

    private fun assertText(root: View, expected: String) {
        assertTrue("expected translated TextView '$expected'", hasText(root, expected))
    }

    private fun hasText(root: View, expected: String): Boolean = findViews(root)
        .filterIsInstance<TextView>()
        .any { it.text.toString() == expected }

    private fun hasDescription(root: View, expected: String): Boolean = findViews(root)
        .any { it.contentDescription == expected }

    private fun absoluteBounds(view: View, root: View): Bounds {
        var left = view.left
        var top = view.top
        var parent = view.parent
        while (parent is View && parent !== root) {
            left += parent.left
            top += parent.top
            parent = parent.parent
        }
        return Bounds(left, top, left + view.width, top + view.height)
    }

    private fun findViews(root: View): List<View> = buildList {
        add(root)
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) addAll(findViews(root.getChildAt(index)))
        }
    }

    private fun availableSnapshot(
        locationName: String = "Zaječar",
        temperature: String = "23°C",
        apparentTemperature: String? = "24°C",
        settings: PersistedSettings = PersistedSettings()
    ) = WeatherWidgetSnapshot.Available(
        settings = settings,
        request = request(locationName),
        weather = WeatherWidgetState(
            locationName = locationName,
            temperatureText = temperature,
            temperatureContentDescription = temperature,
            apparentTemperatureText = apparentTemperature,
            apparentTemperatureContentDescription = apparentTemperature,
            condition = WeatherCondition.CLEAR,
            conditionLabel = "Clear sky",
            isDay = true,
            hours = listOf(
                WidgetHour(
                    time = Instant.parse("2026-10-28T21:00:00Z"),
                    timeText = "22:00",
                    temperatureText = "20°C",
                    temperatureContentDescription = "20 degrees C",
                    condition = WeatherCondition.CLEAR,
                    conditionLabel = "Clear sky",
                    isDay = true,
                    precipitationProbability = 60,
                    precipitationProbabilityText = "60%",
                    precipitationProbabilityContentDescription = "Rain probability 60 percent"
                )
            ),
            lastWeatherUpdate = Instant.parse("2026-10-28T10:17:00Z"),
            lastWeatherUpdateText = "Wed, Oct 28, 2026 • 11:17",
            lastWeatherUpdateLocalTimeText = "11:17",
            lastWeatherUpdateCompactText = "Oct 28 11:17",
            isStale = false
        )
    )

    private fun request(locationName: String) = WeatherWidgetRequest(
        provider = WeatherProvider.OPEN_METEO,
        location = ActiveLocation.Saved(
            WeatherLocation(locationName.lowercase(), locationName, 43.9, 20.3)
        ),
        locationName = locationName
    )

    private data class LayoutCase(
        val widthDp: Float,
        val heightDp: Float,
        val fontScale: Float,
        val theme: ThemeMode,
        val systemDark: Boolean = false
    )

    private data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

    private class PreviewSmallWeatherWidget(
        private val snapshot: WeatherWidgetSnapshot
    ) : GlanceAppWidget() {
        override val sizeMode: SizeMode = SizeMode.Responsive(SMALL_WEATHER_WIDGET_RESPONSIVE_SIZES)

        override suspend fun provideGlance(context: Context, id: GlanceId) = provideSnapshot()

        override suspend fun providePreview(context: Context, widgetCategory: Int) = provideSnapshot()

        private suspend fun provideSnapshot() {
            provideContent { SmallWeatherWidgetContent(snapshot) }
        }
    }

    private class FixedSettingsRepository(
        settingsValue: PersistedSettings
    ) : SettingsRepository {
        override val settings: Flow<PersistedSettings> = flowOf(settingsValue)
        override suspend fun save(settings: PersistedSettings) = Unit
    }

    private class FixedLocationRepository(
        private val location: WeatherLocation
    ) : LocationRepository {
        override val locations: Flow<List<WeatherLocation>> = flowOf(listOf(location))
        override val activeLocation: Flow<ActiveLocation?> = flowOf(ActiveLocation.Saved(location))
        override val locationMode: Flow<LocationMode> = flowOf(LocationMode.SAVED_LOCATION)
        override val lastKnownCurrentLocation: Flow<ActiveLocation.Current?> = flowOf(null)

        override suspend fun add(location: WeatherLocation) = Unit
        override suspend fun update(location: WeatherLocation) = Unit
        override suspend fun delete(id: String) = Unit
        override suspend fun selectSaved(id: String) = Unit
        override suspend fun setTrackMe(enabled: Boolean) = Unit
    }

    private class CacheOnlyWeatherRepository(
        private val cached: ForecastLoad
    ) : WeatherRepository {
        var cacheReads = 0
            private set
        var liveRequests = 0
            private set

        override suspend fun getForecast(
            provider: WeatherProvider,
            location: ActiveLocation
        ): ForecastLoad {
            liveRequests++
            error("Widget rendering must never request live weather")
        }

        override suspend fun getLastCachedForecast(
            provider: WeatherProvider,
            location: ActiveLocation
        ): ForecastLoad {
            cacheReads++
            return cached
        }

        override suspend fun clearCache() = Unit
    }
}
