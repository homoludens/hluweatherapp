package net.droopia.hluweather.widget

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.droopia.hluweather.data.cache.ForecastCache
import net.droopia.hluweather.data.cache.ForecastCacheKey
import net.droopia.hluweather.data.model.ActiveLocation
import net.droopia.hluweather.data.model.CurrentWeather
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.LocationMode
import net.droopia.hluweather.data.model.ThemeMode
import net.droopia.hluweather.data.model.WeatherCondition
import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherProvider
import net.droopia.hluweather.data.repository.CachingWeatherRepository
import net.droopia.hluweather.data.repository.LocationRepository
import net.droopia.hluweather.data.repository.WeatherSource
import net.droopia.hluweather.ui.settings.PersistedSettings
import net.droopia.hluweather.ui.settings.SettingsRepository
import net.droopia.hluweather.ui.settings.TemperatureUnit
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WeatherWidgetReconciliationTest {
    private val start = Instant.parse("2026-10-28T12:00:00Z")
    private val first = WeatherLocation("first", "First", 43.0, 20.0, 10)
    private val second = WeatherLocation("second", "Second", 44.0, 21.0, 20)

    @Test
    fun cache_presentation_status_lru_and_unrelated_writes_do_not_fetch_or_loop() = runTest {
        val f = Fixture(this)
        f.put(first)
        f.put(second) // first is not the LRU tail; rendering will move it.
        f.start()
        runCurrent()
        assertEquals(0, f.enqueues)
        assertEquals(0, f.calls.size)
        val initial = f.renders.size
        f.data.edit { it[stringPreferencesKey("unrelated")] = "changed" }
        f.settings.value = f.settings.value.copy(weatherAlerts = true)
        f.cache.get(f.key(second))
        f.cache.get(f.key(first))
        runCurrent()
        assertEquals(initial, f.renders.size)

        f.settings.value = f.settings.value.copy(temperatureUnit = TemperatureUnit.FAHRENHEIT, themeMode = ThemeMode.DARK)
        runCurrent()
        val fahrenheit = f.renders.last() as WeatherWidgetSnapshot.Available
        assertEquals("73°F", fahrenheit.weather.temperatureText)
        assertEquals(ThemeMode.DARK, fahrenheit.settings.themeMode)
        val renamed = first.copy(name = "Renamed")
        f.locations.value = listOf(renamed, second)
        f.active.value = ActiveLocation.Saved(renamed)
        runCurrent()
        assertEquals("Renamed", (f.renders.last() as WeatherWidgetSnapshot.Available).weather.locationName)
        f.put(renamed, temperature = 10.0)
        runCurrent()
        assertEquals("50°F", (f.renders.last() as WeatherWidgetSnapshot.Available).weather.temperatureText)
        assertEquals(0, f.enqueues)
        assertTrue(f.calls.isEmpty())

        val request = f.widgets.resolveRequest()!!
        val attempt = f.widgets.beginRefresh(request)
        runCurrent()
        assertTrue((f.renders.last() as WeatherWidgetSnapshot.Available).refresh.isRefreshing)
        f.widgets.finishRefresh(request, attempt, null)
        runCurrent()
        assertTrue((f.renders.last() as WeatherWidgetSnapshot.Available).refresh.lastAttemptFailed)
        val stable = f.renders.size
        f.data.edit { it[stringPreferencesKey("unrelated")] = "again" }
        runCurrent()
        assertEquals(stable, f.renders.size)
        assertEquals(0, f.enqueues)
    }

    @Test
    fun identity_changes_render_and_old_running_request_eventually_yields_to_latest() = runTest {
        val f = Fixture(this)
        val gate = CompletableDeferred<Unit>()
        f.gate = gate
        f.autoWork = true
        f.start()
        runCurrent()
        assertEquals(listOf(WeatherProvider.OPEN_METEO to "first"), f.calls)
        f.active.value = ActiveLocation.Saved(second)
        f.settings.value = f.settings.value.copy(provider = WeatherProvider.MET_NO)
        runCurrent()
        val pending = f.renders.last() as WeatherWidgetSnapshot.NoWeather
        assertEquals("Second", pending.request.locationName)
        assertEquals(WeatherProvider.MET_NO, pending.request.provider)
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf(WeatherProvider.OPEN_METEO to "first", WeatherProvider.MET_NO to "second"), f.calls)
        val latest = f.renders.last() as WeatherWidgetSnapshot.Available
        assertEquals("Second", latest.weather.locationName)
        assertEquals(WeatherProvider.MET_NO, latest.request.provider)
        assertTrue(f.renders.dropWhile { it !is WeatherWidgetSnapshot.NoWeather || it.request.provider != WeatherProvider.MET_NO }
            .none { it is WeatherWidgetSnapshot.Available && it.request.provider == WeatherProvider.OPEN_METEO })
    }

    @Test
    fun no_widgets_cancel_collectors_and_inventory_restart_reconciles_passive_location() = runTest {
        val f = Fixture(this)
        f.installed.value = false
        f.start()
        runCurrent()
        assertEquals(0, f.settings.subscriptionCount.value)
        f.settings.value = f.settings.value.copy(provider = WeatherProvider.MET_NO)
        f.mode.value = LocationMode.TRACK_ME
        f.active.value = null
        f.passive.value = ActiveLocation.Current(GeoPoint(45.0, 22.0), 5)
        runCurrent()
        assertTrue(f.renders.isEmpty())
        assertEquals(0, f.enqueues)
        f.installed.value = true
        runCurrent()
        assertEquals(1, f.enqueues)
        assertTrue((f.renders.last() as WeatherWidgetSnapshot.NoWeather).request.location is ActiveLocation.Current)
        f.installed.value = false
        runCurrent()
        assertEquals(0, f.settings.subscriptionCount.value)
        val renders = f.renders.size
        f.settings.value = f.settings.value.copy(themeMode = ThemeMode.LIGHT)
        runCurrent()
        assertEquals(renders, f.renders.size)
    }

    @Test
    fun request_changes_fetch_only_missing_or_due_and_location_fallback_is_immediate() = runTest {
        val f = Fixture(this)
        f.put(first)
        f.put(second)
        f.start()
        runCurrent()
        assertEquals(0, f.enqueues)
        f.active.value = ActiveLocation.Saved(second)
        runCurrent()
        assertEquals("Second", (f.renders.last() as WeatherWidgetSnapshot.Available).weather.locationName)
        assertEquals(0, f.enqueues)
        advanceTimeBy(60 * 60 * 1000L + 1)
        f.locations.value = listOf(first)
        f.active.value = ActiveLocation.Saved(first)
        runCurrent()
        assertEquals("First", (f.renders.last() as WeatherWidgetSnapshot.Available).weather.locationName)
        assertEquals(1, f.enqueues)
        f.mode.value = LocationMode.TRACK_ME
        f.active.value = ActiveLocation.Current(GeoPoint(45.00011, 22.0), 5)
        runCurrent()
        assertEquals(2, f.enqueues)
        val count = f.renders.size
        f.active.value = ActiveLocation.Current(GeoPoint(45.00012, 22.0), 5)
        runCurrent()
        assertEquals(2, f.enqueues)
        assertEquals(count, f.renders.size)
        f.active.value = ActiveLocation.Current(GeoPoint(45.0, 22.0), 6)
        runCurrent()
        assertEquals(3, f.enqueues)
    }

    @Test
    fun corrupt_cache_and_interrupted_status_expire_without_polling_or_refetch() = runTest {
        val f = Fixture(this)
        f.data.edit { it[intPreferencesKey("weather.forecast_cache")] = 123 }
        val request = f.widgets.resolveRequest()!!
        f.widgets.beginRefresh(request)
        f.start()
        runCurrent()
        assertEquals(1, f.enqueues)
        assertTrue((f.renders.last() as WeatherWidgetSnapshot.NoWeather).refresh.isRefreshing)
        val count = f.renders.size
        advanceTimeBy(15 * 60 * 1000L)
        runCurrent()
        assertEquals(count + 1, f.renders.size)
        assertFalse((f.renders.last() as WeatherWidgetSnapshot.NoWeather).refresh.isRefreshing)
        assertTrue((f.renders.last() as WeatherWidgetSnapshot.NoWeather).refresh.lastAttemptFailed)
        assertEquals(1, f.enqueues)
        advanceTimeBy(60 * 60 * 1000L)
        runCurrent()
        assertEquals(count + 1, f.renders.size)
    }

    @Test
    fun corrupt_serialized_cache_recovers_on_foreground_write_without_another_enqueue() = runTest {
        val f = Fixture(this)
        f.data.edit { it[stringPreferencesKey("weather.forecast_cache")] = "not json" }
        f.start()
        runCurrent()
        assertTrue(f.renders.last() is WeatherWidgetSnapshot.NoWeather)
        assertEquals(1, f.enqueues)
        f.put(first)
        runCurrent()
        assertEquals("First", (f.renders.last() as WeatherWidgetSnapshot.Available).weather.locationName)
        assertEquals(1, f.enqueues)
        assertTrue(f.calls.isEmpty())
    }

    @Test
    fun active_snapshot_observation_expires_loading_and_cancels_old_attempt_timers() = runTest {
        val f = Fixture(this)
        val snapshots = mutableListOf<WeatherWidgetSnapshot>()
        val observation = backgroundScope.launch { f.widgets.observeSnapshots().collect { snapshots += it } }
        runCurrent()
        val request = f.widgets.resolveRequest()!!
        val attempt = f.widgets.beginRefresh(request)
        runCurrent()
        assertTrue((snapshots.last() as WeatherWidgetSnapshot.NoWeather).refresh.isRefreshing)
        f.widgets.finishRefresh(request, attempt, null)
        runCurrent()
        val afterFinish = snapshots.size
        advanceTimeBy(15 * 60 * 1000L)
        runCurrent()
        assertEquals(afterFinish, snapshots.size)
        f.widgets.beginRefresh(request)
        runCurrent()
        assertTrue((snapshots.last() as WeatherWidgetSnapshot.NoWeather).refresh.isRefreshing)
        advanceTimeBy(15 * 60 * 1000L)
        runCurrent()
        assertFalse((snapshots.last() as WeatherWidgetSnapshot.NoWeather).refresh.isRefreshing)
        assertTrue((snapshots.last() as WeatherWidgetSnapshot.NoWeather).refresh.lastAttemptFailed)
        observation.cancel()
        runCurrent()
        assertEquals(0, f.settings.subscriptionCount.value)
        assertTrue(f.calls.isEmpty())
    }

    @Test
    fun active_snapshot_observation_ignores_unrelated_and_lru_writes_without_a_loop() = runTest {
        val f = Fixture(this)
        f.put(first)
        f.put(second)
        val snapshots = mutableListOf<WeatherWidgetSnapshot>()
        backgroundScope.launch { f.widgets.observeSnapshots().collect { snapshots += it } }
        runCurrent()
        assertTrue(snapshots.last() is WeatherWidgetSnapshot.Available)
        val count = snapshots.size
        f.cache.get(f.key(second))
        f.cache.get(f.key(first))
        f.data.edit { it[stringPreferencesKey("unrelated")] = "changed" }
        f.settings.value = f.settings.value.copy(weatherAlerts = true)
        runCurrent()
        assertEquals(count, snapshots.size)
        assertTrue(f.calls.isEmpty())
    }

    private inner class Fixture(private val scope: TestScope) {
        val installed = MutableStateFlow(true)
        val settings = MutableStateFlow(PersistedSettings())
        val active = MutableStateFlow<ActiveLocation?>(ActiveLocation.Saved(first))
        val passive = MutableStateFlow<ActiveLocation.Current?>(null)
        val mode = MutableStateFlow(LocationMode.SAVED_LOCATION)
        val locations = MutableStateFlow(listOf(first, second))
        val data = MemoryDataStore()
        val cache = ForecastCache(data)
        val clock = object : Clock { override fun now() = start + scope.testScheduler.currentTime.milliseconds }
        val calls = mutableListOf<Pair<WeatherProvider, String>>()
        var gate: CompletableDeferred<Unit>? = null
        var autoWork = false
        var enqueues = 0
        val renders = mutableListOf<WeatherWidgetSnapshot>()
        val settingsRepo = object : SettingsRepository {
            override val settings = this@Fixture.settings
            override suspend fun save(settings: PersistedSettings) { this@Fixture.settings.value = settings }
        }
        val locationRepo = object : LocationRepository {
            override val locations = this@Fixture.locations
            override val activeLocation = active
            override val lastKnownCurrentLocation = passive
            override val locationMode = mode
            override suspend fun add(location: WeatherLocation) = Unit
            override suspend fun update(location: WeatherLocation) = Unit
            override suspend fun delete(id: String) = Unit
            override suspend fun selectSaved(id: String) = Unit
            override suspend fun setTrackMe(enabled: Boolean) = Unit
        }
        fun source(provider: WeatherProvider) = object : WeatherSource {
            override val provider = provider
            override suspend fun getForecast(location: WeatherLocation): WeatherForecast {
                calls += provider to location.id
                if (location.id == first.id) gate?.await()
                return forecast(location, provider)
            }
        }
        val weather = CachingWeatherRepository(WeatherProvider.entries.associateWith(::source), cache, clock::now)
        val widgets = WeatherWidgetRepository(settingsRepo, locationRepo, weather, clock, DataStoreWidgetRefreshMetadataStore(data))
        val coordinator = WeatherWidgetRefreshCoordinator(widgets, weather) { renders += widgets.loadCachedState() }
        fun start() = startWidgetReconciliation(scope.backgroundScope, installed, widgets, {
            enqueues++
            if (autoWork) scope.backgroundScope.launch { coordinator.refresh(false) }
        }, { renders += widgets.loadCachedState() }, clock)
        fun key(location: WeatherLocation) = ForecastCacheKey(WeatherProvider.OPEN_METEO, "saved:${location.id}")
        suspend fun put(location: WeatherLocation, temperature: Double = 23.0) = cache.put(key(location), forecast(location, temperature = temperature))
        fun forecast(location: WeatherLocation, provider: WeatherProvider = WeatherProvider.OPEN_METEO, temperature: Double = 23.0) = WeatherForecast(
            location, provider, clock.now(), CurrentWeather(temperature, 24.0, null, null, null, WeatherCondition.CLEAR, true),
            emptyList(), emptyList(), 0.0, "UTC"
        )
    }

    private class MemoryDataStore : DataStore<Preferences> {
        override val data = MutableStateFlow<Preferences>(emptyPreferences())
        private val mutex = Mutex()
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = mutex.withLock {
            transform(data.value).also { data.value = it }
        }
    }
}
