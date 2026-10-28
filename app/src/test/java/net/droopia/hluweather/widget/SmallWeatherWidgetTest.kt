package net.droopia.hluweather.widget

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.res.Configuration
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

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
    fun translated_production_layout_preserves_small_weather_priorities_at_minimum_compact_and_normal_sizes() {
        val cases = listOf(
            LayoutCase(110f, 110f, 1f, ThemeMode.LIGHT, expectedCondition = false, expectedFeelsLike = false),
            LayoutCase(160f, 128f, 1f, ThemeMode.SYSTEM, expectedCondition = true, expectedFeelsLike = false, systemDark = true),
            LayoutCase(200f, 160f, 1f, ThemeMode.DARK, expectedCondition = true, expectedFeelsLike = true)
        )

        cases.forEach { case ->
            val view = render(
                availableSnapshot(settings = PersistedSettings(themeMode = case.theme)),
                case.widthDp,
                case.heightDp,
                case.fontScale,
                case.systemDark
            )

            if (case.widthDp > 110f) assertText(view, "Zaječar")
            else assertTrue("minimum layout keeps an accessible location label", hasDescription(view, "Weather for Zaječar"))
            assertText(view, "23°C")
            assertEquals(case.expectedCondition, hasText(view, "Clear sky"))
            assertEquals(case.expectedFeelsLike, hasText(view, "Feels like 24°C"))
            assertTrue("weather icon remains visible for $case", hasWeatherBitmap(view))
            assertRefreshTarget(view, case.widthDp)
            assertPrimaryContentFits(view, case.widthDp, case.heightDp)
            assertFalse("small widget never shows hourly content", hasText(view, "22:00"))
            assertFalse("small widget never shows precipitation columns", hasText(view, "60%"))
        }
    }

    @Test
    fun long_location_is_end_ellipsized_and_keeps_its_full_accessible_name_and_refresh_target() {
        val longName = "A very long location name that must never cover the refresh button"
        val view = render(
            availableSnapshot(locationName = longName),
            widthDp = 110f,
            heightDp = 110f
        )
        val location = findViews(view).filterIsInstance<TextView>()
            .first { it.contentDescription == "Weather for $longName" }

        assertTrue("visible location should end with an ellipsis", location.text.endsWith("…"))
        assertEquals("Weather for $longName", location.contentDescription)
        assertRefreshTarget(view, widthDp = 110f)
        val refresh = findViews(view).first { it.contentDescription == "Refresh weather" }
        assertTrue(
            "ellipsized location does not enter the refresh target",
            absoluteBounds(location, view).right <= absoluteBounds(refresh, view).left
        )
        assertPrimaryContentFits(view, widthDp = 110f, heightDp = 110f)
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
            assertRefreshTarget(view, widthDp = 110f)
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
    }

    private fun assertRefreshTarget(root: View, widthDp: Float) {
        val density = root.resources.displayMetrics.density
        val refresh = findViews(root).firstOrNull { it.contentDescription == "Refresh weather" }
        assertNotNull("refresh action must remain visible and independent", refresh)
        val refreshTarget = requireNotNull(refresh)
        assertTrue("refresh target remains separate from the card", refreshTarget !== root)
        assertTrue("refresh target keeps its 48 dp width", refreshTarget.width >= 48f * density - 1f)
        val bounds = absoluteBounds(refreshTarget, root)
        assertTrue("refresh stays inside the right edge", bounds.right <= root.width)
        assertTrue("refresh remains at the top-right", bounds.left >= root.width - 56f * density)
        assertTrue("card root is sized to the tested launcher width", root.width <= widthDp * density + 1f)
    }

    private fun hasWeatherBitmap(root: View): Boolean = findViews(root)
        .filterIsInstance<ImageView>()
        .any { it.drawable is BitmapDrawable }

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
        val expectedCondition: Boolean,
        val expectedFeelsLike: Boolean,
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
