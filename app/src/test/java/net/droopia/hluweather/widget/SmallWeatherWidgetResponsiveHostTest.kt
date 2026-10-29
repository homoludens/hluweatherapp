package net.droopia.hluweather.widget

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import androidx.glance.EmittableWithChildren
import androidx.glance.appwidget.AppWidgetId
import androidx.glance.appwidget.AppWidgetSession
import androidx.glance.session.runSession
import androidx.test.core.app.ApplicationProvider
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.droopia.hluweather.HluWeatherApplication
import net.droopia.hluweather.data.cache.ForecastCache
import net.droopia.hluweather.data.cache.ForecastCacheKey
import net.droopia.hluweather.data.model.CurrentWeather
import net.droopia.hluweather.data.model.HourForecast
import net.droopia.hluweather.data.model.ThemeMode
import net.droopia.hluweather.data.model.WeatherCondition
import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherProvider
import net.droopia.hluweather.data.repository.ForecastLoad
import net.droopia.hluweather.data.repository.applicationDataStore
import net.droopia.hluweather.ui.settings.TemperatureUnit
import net.droopia.hluweather.ui.theme.hluMaterialColorScheme
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers.ClassParameter

/** Real provider/session plus framework host selection, rather than an arbitrary preview size. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = HluWeatherApplication::class, qualifiers = "420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SmallWeatherWidgetResponsiveHostTest {
    @Test
    fun recorded_narrow_tall_host_selects_a_composition_with_secondary_weather() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<HluWeatherApplication>()
        val originalSettings = app.settingsRepository.settings.first()
        // The real application store may already contain non-default settings. Seed a
        // conflicting unit first so this fixture proves it selects its own starting state.
        app.settingsRepository.save(originalSettings.copy(
            provider = WeatherProvider.OPEN_METEO,
            temperatureUnit = TemperatureUnit.FAHRENHEIT,
            themeMode = ThemeMode.DARK
        ))
        assertEquals(TemperatureUnit.FAHRENHEIT, app.settingsRepository.settings.first().temperatureUnit)
        app.settingsRepository.save(originalSettings.copy(
            provider = WeatherProvider.OPEN_METEO,
            temperatureUnit = TemperatureUnit.CELSIUS,
            themeMode = ThemeMode.LIGHT
        ))
        assertEquals(TemperatureUnit.CELSIUS, app.settingsRepository.settings.first().temperatureUnit)
        val name = "Zajecar, a very long location name for ellipsis"
        val location = WeatherLocation("responsive-host", name, 43.9, 20.3)
        app.locationRepository.add(location)
        app.locationRepository.selectSaved(location.id)
        val forecast = WeatherForecast(
            location = location,
            provider = WeatherProvider.OPEN_METEO,
            fetchedAt = Clock.System.now(),
            current = CurrentWeather(15.0, 13.0, null, null, null, WeatherCondition.CLOUDY, true),
            hourly = listOf(HourForecast(
                time = Clock.System.now() + 1.hours,
                temperature = 14.0,
                apparentTemperature = null,
                humidity = null,
                dewPoint = null,
                precipitation = null,
                precipitationProbability = 99,
                condition = WeatherCondition.CLOUDY,
                isDay = true
            )),
            daily = emptyList(),
            moonPhase = 0.0,
            timezone = "Europe/Belgrade"
        )
        val cache = ForecastCache(app.applicationDataStore)
        val key = ForecastCacheKey(WeatherProvider.OPEN_METEO, "saved:${location.id}")
        cache.put(key, forecast)

        // 368×484 px is the recorded API 35 host, before padding, at 420 dpi.
        // Its option bundle was not recorded; model both zero and 8 dp per-edge padding.
        val session = RecordingSession(app, options(124f, 168f))
        val job = launch(Dispatchers.Default) { session.runSession(app) }
        try {
            assertEquals(2.625f, app.resources.displayMetrics.density, 0.001f)
            val remote = session.awaitRemote()
            for (paddingDp in listOf(0f, 8f)) {
                val host = host(app, remote, widthDp = 140.19f, heightDp = 184.38f, paddingDp = paddingDp)
                assertWeather(host, name, "15°C", condition = true, feels = "Feels like 13°C")
            }

            // Resize the same host and the same multi-sized RemoteViews, not a new preview.
            val resized = host(app, remote, 140.19f, 184.38f, 0f)
            resize(resized, 110f, 110f)
            assertWeather(resized, name, "15°C", condition = false, feels = null)
            resize(resized, 110f, 128f)
            assertWeather(resized, name, "15°C", condition = true, feels = null)
            resize(resized, 160f, 160f)
            assertWeather(resized, name, "15°C", condition = true, feels = "Feels like 13°C")
            resize(resized, 140.19f, 184.38f)
            assertWeather(resized, name, "15°C", condition = true, feels = "Feels like 13°C")

            val request = requireNotNull(app.weatherWidgetRepository.resolveRequest())
            val normalAttempt = app.weatherWidgetRepository.beginRefresh(request)
            val normalLoading = session.awaitRemote { hasText(host(app, it, 140.19f, 184.38f, 8f), "Refreshing") }
            val normalLoadingHost = host(app, normalLoading, 110f, 160f, 0f)
            assertWeather(normalLoadingHost, name, "15°C", true, "Feels like 13°C")
            assertText(normalLoadingHost, "Refreshing")
            app.weatherWidgetRepository.finishRefresh(request, normalAttempt, ForecastLoad(forecast, false))

            // Reapply the *same* RemoteViews in the same host, with only its resources changed.
            // No app callback, newly composed RemoteViews or worker is used for these applies.
            val largeConfig = Configuration(app.resources.configuration).apply { fontScale = 1.3f }
            val largeContext = app.createConfigurationContext(largeConfig)
            val hostContext = app.createConfigurationContext(Configuration(app.resources.configuration).apply { fontScale = 1f })
            val cachedHost = host(hostContext, remote, 140.19f, 184.38f, 8f)
            val originalContent = cachedHost.getChildAt(0)
            assertWeather(cachedHost, name, "15°C", true, "Feels like 13°C")
            updateHostFont(cachedHost, remote, largeConfig)
            assertSame("framework reuses the cached host content", originalContent, cachedHost.getChildAt(0))
            assertWeather(cachedHost, name, "15°C", true, "Feels like 13°C")
            assertTrue("temperature uses native host auto-sizing", texts(cachedHost).first { it.text.toString() == "15°C" }
                .autoSizeTextType == TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM)
            updateHostFont(cachedHost, remote, Configuration(largeConfig).apply { fontScale = 1f })
            assertSame(originalContent, cachedHost.getChildAt(0))
            assertWeather(cachedHost, name, "15°C", true, "Feels like 13°C")

            // Active-session reconfiguration can reduce rows for a genuinely short host.
            app.onConfigurationChanged(largeConfig)
            val activeLarge = session.awaitRemote { candidate ->
                val compact = host(largeContext, candidate, 160f, 128f, 0f)
                hasText(compact, "15°C") && !hasText(compact, "Cloudy")
            }
            assertWeather(host(largeContext, activeLarge, 160f, 128f, 0f), name, "15°C", false, null)
            assertWeather(host(largeContext, activeLarge, 110f, 110f, 0f), name, "15°C", false, null)
            assertWeather(host(largeContext, activeLarge, 110f, 128f, 0f), name, "15°C", true, null)
            assertWeather(host(largeContext, activeLarge, 140.19f, 184.38f, 8f), name, "15°C", true, "Feels like 13°C")

            // Loading and stale/failure statuses must survive the selected normal host variant.
            val attempt = app.weatherWidgetRepository.beginRefresh(request)
            val loading = session.awaitRemote { hasText(host(largeContext, it, 140.19f, 184.38f, 8f), "Refreshing") }
            val loadingHost = host(largeContext, loading, 140.19f, 184.38f, 8f)
            assertWeather(loadingHost, name, "15°C", true, "Feels like 13°C")
            assertText(loadingHost, "Refreshing")
            val exactlyTall = host(largeContext, loading, 110f, 160f, 0f)
            assertWeather(exactlyTall, name, "15°C", true, "Feels like 13°C")
            assertText(exactlyTall, "Refreshing")
            val compactLoading = host(largeContext, loading, 160f, 128f, 0f)
            assertWeather(compactLoading, name, "15°C", false, null)
            app.weatherWidgetRepository.finishRefresh(request, attempt, null)
            val stale = session.awaitRemote { hasText(host(largeContext, it, 140.19f, 184.38f, 8f), "Stale weather") }
            val staleHost = host(largeContext, stale, 140.19f, 184.38f, 8f)
            assertWeather(staleHost, name, "15°C", true, "Feels like 13°C")
            assertText(staleHost, "Stale weather")

            // A future weather timestamp models clock skew and exposes the distinct failure label.
            val future = forecast.copy(fetchedAt = Clock.System.now() + 1.hours)
            cache.put(key, future)
            val failure = session.awaitRemote { hasText(host(largeContext, it, 140.19f, 184.38f, 8f), "Update failed") }
            val failureHost = host(largeContext, failure, 140.19f, 184.38f, 8f)
            assertWeather(failureHost, name, "15°C", true, "Feels like 13°C")
            assertText(failureHost, "Update failed")

            // Signed Fahrenheit, dark theme and long name use the same selected production variant.
            val recovery = app.weatherWidgetRepository.beginRefresh(request)
            app.weatherWidgetRepository.finishRefresh(request, recovery, ForecastLoad(future, false))
            cache.put(key, forecast.copy(
                fetchedAt = Clock.System.now(),
                current = forecast.current.copy(temperature = -86.111111, apparentTemperature = -85.555556)
            ))
            val settings = app.settingsRepository.settings.first()
            app.settingsRepository.save(settings.copy(temperatureUnit = TemperatureUnit.FAHRENHEIT, themeMode = ThemeMode.DARK))
            val fahrenheit = session.awaitRemote { hasText(host(largeContext, it, 140.19f, 184.38f, 8f), "-123°F") }
            val fahrenheitHost = host(largeContext, fahrenheit, 140.19f, 184.38f, 8f)
            assertWeather(fahrenheitHost, name, "-123°F", true, "Feels like -122°F")
            assertEquals(hluMaterialColorScheme(true).onSurface.toArgb(),
                texts(fahrenheitHost).first { it.text.toString() == "-123°F" }.currentTextColor)

            cache.put(key, forecast.copy(
                fetchedAt = Clock.System.now(),
                current = forecast.current.copy(temperature = -86.111111, apparentTemperature = null)
            ))
            val noApparent = session.awaitRemote {
                val view = host(largeContext, it, 140.19f, 184.38f, 8f)
                hasText(view, "-123°F") && texts(view).none { text -> text.text.toString().startsWith("Feels like") }
            }
            assertWeather(host(largeContext, noApparent, 140.19f, 184.38f, 8f), name, "-123°F", true, null)
        } finally {
            job.cancelAndJoin()
            app.settingsRepository.save(originalSettings)
        }
    }

    private class RecordingSession(context: Context, options: Bundle) : AppWidgetSession(
        widget = SmallWeatherWidget(),
        id = AppWidgetId(-2101),
        initialOptions = options,
        lambdaReceiver = ComponentName(context, SmallWeatherWidgetReceiver::class.java),
        shouldPublish = false
    ) {
        private val updates = Channel<RemoteViews>(Channel.UNLIMITED)

        override suspend fun processEmittableTree(context: Context, root: EmittableWithChildren): Boolean {
            val result = super.processEmittableTree(context, root)
            val field = AppWidgetSession::class.java.getDeclaredField("lastRemoteViews").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val remote = (field.get(this) as MutableStateFlow<RemoteViews?>).value
            if (result && remote != null) updates.send(remote)
            return result
        }

        suspend fun awaitRemote(matches: (RemoteViews) -> Boolean = { true }): RemoteViews = withTimeout(15_000) {
            while (true) {
                val remote = updates.receive()
                if (matches(remote)) return@withTimeout remote
            }
            error("unreachable")
        }
    }

    private fun options(widthDp: Float, heightDp: Float) = Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp.toInt())
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp.toInt())
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp.toInt())
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp.toInt())
        putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(SizeF(widthDp, heightDp)))
    }

    private fun host(context: Context, remote: RemoteViews, widthDp: Float, heightDp: Float, paddingDp: Float): AppWidgetHostView {
        val density = context.resources.displayMetrics.density
        val padding = (paddingDp * density).roundToInt()
        return AppWidgetHostView(context).apply {
            setAppWidget(-2101, null)
            setPadding(padding, padding, padding, padding)
            resize(this, widthDp, heightDp)
            // Robolectric's default shadow blindly reapplies to the host itself and ignores
            // responsive sizes. Run the framework host implementation instead.
            updateHost(this, remote)
            resize(this, widthDp, heightDp)
        }
    }

    private fun updateHost(host: AppWidgetHostView, remote: RemoteViews) {
        Shadow.directlyOn<Void, AppWidgetHostView>(
            host, AppWidgetHostView::class.java, "updateAppWidget",
            ClassParameter.from(RemoteViews::class.java, remote)
        )
    }

    @Suppress("DEPRECATION")
    private fun updateHostFont(host: AppWidgetHostView, remote: RemoteViews, configuration: Configuration) {
        val width = host.width
        val height = host.height
        val resources = host.context.resources
        resources.updateConfiguration(configuration, resources.displayMetrics)
        host.dispatchConfigurationChanged(configuration)
        updateHost(host, remote)
        host.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        host.layout(0, 0, width, height)
        assertEquals(configuration.fontScale, host.resources.configuration.fontScale, 0f)
    }

    private fun resize(host: AppWidgetHostView, widthDp: Float, heightDp: Float) {
        val density = host.resources.displayMetrics.density
        val width = (widthDp * density).roundToInt()
        val height = (heightDp * density).roundToInt()
        host.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        host.layout(0, 0, width, height)
    }

    private fun assertWeather(host: View, name: String, temperature: String, condition: Boolean, feels: String?) {
        assertText(host, temperature)
        val temperatureView = texts(host).first { it.text.toString() == temperature }
        assertTrue("temperature retains spoken units", temperatureView.contentDescription.toString()
            .endsWith("degrees ${temperature.last()}"))
        assertTrue("primary weather bitmap remains visible", views(host).filterIsInstance<ImageView>()
            .mapNotNull { (it.drawable as? BitmapDrawable)?.bitmap }
            .any { it.width >= 22f * host.resources.displayMetrics.density })
        assertEquals("condition priority", condition, hasText(host, "Cloudy"))
        if (condition) assertEquals("Cloudy", texts(host).first { it.text.toString() == "Cloudy" }.contentDescription)
        val apparent = texts(host).firstOrNull { it.text.toString().startsWith("Feels like") }
        assertEquals("apparent-temperature priority", feels, apparent?.text?.toString())
        assertFalse("small widget has no hourly strip", hasText(host, "99%"))
        assertLocationAndRefresh(host, name)
        val card = if (host is AppWidgetHostView) host.getChildAt(0) else host
        assertRenderedContentFitsWithoutOverlap(card)
        assertTextInkFits(host)
    }

    private fun assertLocationAndRefresh(host: View, name: String) {
        val location = texts(host).first { it.contentDescription == "Weather for $name" }
        val refresh = views(host).first { it.contentDescription == "Refresh weather" }
        assertTrue("long location is ellipsized", location.text.endsWith("…"))
        assertTrue("refresh remains a 48 dp target", refresh.width >= 48f * host.resources.displayMetrics.density - 1f)
        assertTrue("refresh remains 48 dp tall", refresh.height >= 48f * host.resources.displayMetrics.density - 1f)
        assertTrue("location must not overlap refresh", bounds(location, host).right <= bounds(refresh, host).left)
    }

    private fun assertTextInkFits(root: View) {
        texts(root).filter { it.text.isNotBlank() }.forEach { text ->
            val ink = Rect()
            text.paint.getTextBounds(text.text, 0, text.text.length, ink)
            val top = text.compoundPaddingTop
            val bottom = text.height - text.compoundPaddingBottom
            assertTrue("${text.text} line box exceeds TextView", top + text.layout.getLineBottom(0) <= bottom)
            assertTrue("${text.text} ink above TextView", text.baseline + ink.top >= top)
            assertTrue("${text.text} ink below TextView", text.baseline + ink.bottom <= bottom)
        }
    }

    private fun assertText(root: View, expected: String) {
        assertTrue("expected $expected; saw ${texts(root).map { it.text }}", hasText(root, expected))
    }

    private fun hasText(root: View, expected: String) = texts(root).any { it.text.toString() == expected }

    private fun texts(root: View) = views(root).filterIsInstance<TextView>()

    private fun views(root: View): List<View> = buildList {
        add(root)
        if (root is ViewGroup) for (i in 0 until root.childCount) addAll(views(root.getChildAt(i)))
    }

    private fun bounds(view: View, root: View): Rect {
        var x = view.left
        var y = view.top
        var parent = view.parent
        while (parent is View && parent !== root) {
            x += parent.left
            y += parent.top
            parent = parent.parent
        }
        return Rect(x, y, x + view.width, y + view.height)
    }
}
