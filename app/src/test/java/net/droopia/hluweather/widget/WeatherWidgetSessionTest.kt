package net.droopia.hluweather.widget

import android.content.ComponentName
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.glance.EmittableWithChildren
import androidx.glance.GlanceId
import androidx.glance.appwidget.AppWidgetId
import androidx.glance.appwidget.AppWidgetSession
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.session.runSession
import androidx.test.core.app.ApplicationProvider
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import net.droopia.hluweather.data.cache.ForecastCache
import net.droopia.hluweather.data.cache.ForecastCacheKey
import net.droopia.hluweather.data.model.ActiveLocation
import net.droopia.hluweather.data.model.CurrentWeather
import net.droopia.hluweather.data.model.LocationMode
import net.droopia.hluweather.data.model.ThemeMode
import net.droopia.hluweather.data.model.WeatherCondition
import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherProvider
import net.droopia.hluweather.data.repository.CachingWeatherRepository
import net.droopia.hluweather.data.repository.ForecastLoad
import net.droopia.hluweather.data.repository.LocationRepository
import net.droopia.hluweather.data.repository.WeatherSource
import net.droopia.hluweather.ui.settings.PersistedSettings
import net.droopia.hluweather.ui.settings.SettingsRepository
import net.droopia.hluweather.ui.settings.TemperatureUnit
import net.droopia.hluweather.ui.theme.hluMaterialColorScheme
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WeatherWidgetSessionTest {
    @Test
    fun same_sessions_translate_cache_status_and_settings_changes_without_network() = runBlocking {
        for (medium in listOf(false, true)) {
            val f = Fixture()
            val initial = f.widgets.loadCachedState()
            val widget = TestWidget(f.widgets, initial, medium)
            val session = RecordingSession(widget, f.context, medium)
            val job = launch(Dispatchers.Default) { session.runSession(f.context) }
            try {
                session.awaitText("No weather available")
                f.put(f.first, 23.0)
                session.awaitText("23°C")
                val request = f.widgets.resolveRequest()!!
                val attempt = f.widgets.beginRefresh(request)
                session.awaitText(if (medium) "Updating" else "Refreshing")
                f.widgets.finishRefresh(request, attempt, ForecastLoad(f.forecast(f.first, 23.0), false))
                session.awaitText(if (medium) "Updated" else "23°C", absent = if (medium) "Updating" else "Refreshing")
                val failed = f.widgets.beginRefresh(request)
                session.awaitText(if (medium) "Updating" else "Refreshing")
                f.widgets.finishRefresh(request, failed, ForecastLoad(f.forecast(f.first, 23.0), true))
                session.awaitText(if (medium) "Stale" else "Stale weather")
                val recovery = f.widgets.beginRefresh(request)
                f.widgets.finishRefresh(request, recovery, ForecastLoad(f.forecast(f.first, 23.0), false))
                session.awaitText(if (medium) "Updated" else "23°C", absent = "Stale")
                val failure = f.widgets.beginRefresh(request)
                session.awaitText(if (medium) "Updating" else "Refreshing")
                f.widgets.finishRefresh(request, failure, null)
                session.awaitText(if (medium) "Stale" else "Stale weather")
                f.settings.value = f.settings.value.copy(temperatureUnit = TemperatureUnit.FAHRENHEIT, themeMode = ThemeMode.DARK)
                val dark = session.awaitText("73°F")
                val temperature = texts(dark).first { it.text.toString() == "73°F" }
                assertEquals(hluMaterialColorScheme(true).onSurface.toArgb(), temperature.currentTextColor)
                val renamed = f.first.copy(name = "Renamed")
                f.locations.value = listOf(renamed, f.second)
                session.awaitText("Renamed")
                f.active.value = ActiveLocation.Saved(f.second)
                session.awaitText("Second", absent = "73°F")
                f.put(f.second, 10.0)
                session.awaitText("50°F")
                f.settings.value = f.settings.value.copy(provider = WeatherProvider.MET_NO)
                session.awaitText("No weather available", absent = "50°F")
                f.put(f.second, 30.0, WeatherProvider.MET_NO)
                session.awaitText("86°F")
                assertEquals("one provideGlance call for the entire session", 1, widget.calls)
                assertEquals(0, f.networkCalls)
            } finally {
                job.cancelAndJoin()
            }
        }
    }

    private class TestWidget(val repo: WeatherWidgetRepository, val initial: WeatherWidgetSnapshot, val medium: Boolean) : GlanceAppWidget() {
        var calls = 0
        override val stateDefinition = null
        override val sizeMode = SizeMode.Responsive(setOf(if (medium) DpSize(400.dp, 160.dp) else DpSize(200.dp, 160.dp)))
        override suspend fun provideGlance(context: Context, id: GlanceId) {
            calls++
            provideContent {
                if (medium) ObservedMediumWeatherWidget(repo, initial) else ObservedSmallWeatherWidget(repo, initial)
            }
        }
    }

    private class RecordingSession(widget: TestWidget, private val context: Context, medium: Boolean) : AppWidgetSession(
        widget = widget,
        id = AppWidgetId(if (medium) -2002 else -2001),
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
        suspend fun awaitText(expected: String, absent: String? = null): View = withTimeout(10_000) {
            while (true) {
                val view = updates.receive().apply(context, FrameLayout(context))
                val text = texts(view).map { it.text.toString() }
                if (text.any { it.contains(expected) } && (absent == null || text.none { it.contains(absent) })) return@withTimeout view
            }
            error("unreachable")
        }
    }

    private class Fixture {
        val context: Context = ApplicationProvider.getApplicationContext()
        val first = WeatherLocation("first", "First", 43.0, 20.0)
        val second = WeatherLocation("second", "Second", 44.0, 21.0)
        val settings = MutableStateFlow(PersistedSettings())
        val locations = MutableStateFlow(listOf(first, second))
        val active = MutableStateFlow<ActiveLocation?>(ActiveLocation.Saved(first))
        val data = object : DataStore<Preferences> {
            override val data = MutableStateFlow<Preferences>(emptyPreferences())
            val mutex = Mutex()
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = mutex.withLock {
                transform(data.value).also { data.value = it }
            }
        }
        val cache = ForecastCache(data)
        val now = Instant.parse("2026-10-28T12:00:00Z")
        val clock = object : Clock { override fun now() = now }
        var networkCalls = 0
        val source = object : WeatherSource {
            override val provider = WeatherProvider.OPEN_METEO
            override suspend fun getForecast(location: WeatherLocation): WeatherForecast {
                networkCalls++
                error("observation must not request weather")
            }
        }
        val weather = CachingWeatherRepository(mapOf(WeatherProvider.OPEN_METEO to source), cache, clock::now)
        val widgets = WeatherWidgetRepository(object : SettingsRepository {
            override val settings = this@Fixture.settings
            override suspend fun save(settings: PersistedSettings) { this@Fixture.settings.value = settings }
        }, object : LocationRepository {
            override val locations = this@Fixture.locations
            override val activeLocation = active
            override val locationMode = MutableStateFlow(LocationMode.SAVED_LOCATION)
            override val lastKnownCurrentLocation = MutableStateFlow<ActiveLocation.Current?>(null)
            override suspend fun add(location: WeatherLocation) = Unit
            override suspend fun update(location: WeatherLocation) = Unit
            override suspend fun delete(id: String) = Unit
            override suspend fun selectSaved(id: String) = Unit
            override suspend fun setTrackMe(enabled: Boolean) = Unit
        }, weather, clock, DataStoreWidgetRefreshMetadataStore(data))
        fun forecast(location: WeatherLocation, temperature: Double) = WeatherForecast(location, WeatherProvider.OPEN_METEO, now,
            CurrentWeather(temperature, 24.0, null, null, null, WeatherCondition.CLEAR, true), emptyList(), emptyList(), 0.0, "UTC")
        suspend fun put(location: WeatherLocation, temperature: Double, provider: WeatherProvider = WeatherProvider.OPEN_METEO) = cache.put(
            ForecastCacheKey(provider, "saved:${location.id}"), forecast(location, temperature).copy(provider = provider)
        )
    }

    companion object {
        private fun texts(view: View): List<TextView> = buildList {
            if (view is TextView) add(view)
            if (view is ViewGroup) for (i in 0 until view.childCount) addAll(texts(view.getChildAt(i)))
        }
    }
}
