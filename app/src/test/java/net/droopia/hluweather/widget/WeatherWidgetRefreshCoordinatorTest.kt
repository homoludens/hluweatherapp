package net.droopia.hluweather.widget

import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.droopia.hluweather.data.model.ActiveLocation
import net.droopia.hluweather.data.model.CurrentWeather
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
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WeatherWidgetRefreshCoordinatorTest {
    private val now = Instant.parse("2026-10-28T12:00:00Z")
    private val first = WeatherLocation("first", "First", 43.0, 20.0, 10)
    private val second = WeatherLocation("second", "Second", 44.0, 21.0, 20)

    @Test
    fun periodic_and_repeated_manual_calls_join_one_fetch_and_keep_old_weather_loading() = runTest {
        val f = Fixture()
        val old = f.forecast(first, fetchedAt = now - 2.hours)
        f.cache[WeatherProvider.OPEN_METEO to first.id] = ForecastLoad(old, true)
        val gate = CompletableDeferred<Unit>()
        f.fetch = { provider, location -> gate.await(); ForecastLoad(f.forecast(location, provider)) }
        val periodic = async { f.coordinator.refresh(false) }
        runCurrent()
        val taps = List(30) { async { f.coordinator.refresh(true) } }
        runCurrent()
        assertEquals(1, f.calls.size)
        val loading = f.widgets.loadCachedState() as WeatherWidgetSnapshot.Available
        assertTrue(loading.refresh.isRefreshing)
        assertEquals(old.fetchedAt, loading.weather.lastWeatherUpdate)
        gate.complete(Unit)
        assertEquals(WidgetRefreshResult.SUCCESS, periodic.await())
        taps.forEach { assertEquals(WidgetRefreshResult.SUCCESS, it.await()) }
        assertEquals(1, f.calls.size)
        assertFalse((f.widgets.loadCachedState() as WeatherWidgetSnapshot.Available).refresh.isRefreshing)
    }

    @Test
    fun changed_location_and_provider_during_old_fetch_refreshes_latest_without_obsolete_publication() = runTest {
        val f = Fixture()
        val gate = CompletableDeferred<Unit>()
        f.fetch = { provider, location ->
            if (location.id == first.id) gate.await()
            ForecastLoad(f.forecast(location, provider))
        }
        val job = async { f.coordinator.refresh(true) }
        runCurrent()
        f.select(second, WeatherProvider.MET_NO)
        val concurrent = async { f.coordinator.refresh(true) }
        runCurrent()
        gate.complete(Unit)
        assertEquals(WidgetRefreshResult.SUCCESS, job.await())
        assertEquals(WidgetRefreshResult.SUCCESS, concurrent.await())
        assertEquals(listOf(WeatherProvider.OPEN_METEO to first.id, WeatherProvider.MET_NO to second.id), f.calls)
        assertTrue(f.cache.containsKey(WeatherProvider.OPEN_METEO to first.id))
        val snapshot = f.widgets.loadCachedState() as WeatherWidgetSnapshot.Available
        assertEquals("Second", snapshot.weather.locationName)
        assertEquals(WeatherProvider.MET_NO, snapshot.request.provider)
        assertFalse(snapshot.refresh.lastAttemptFailed)
        assertTrue(f.publications.drop(1).all {
            it !is WeatherWidgetSnapshot.Available || it.request.identity == snapshot.request.identity
        })
    }

    @Test
    fun latest_fresh_cache_is_reused_after_obsolete_fetch_and_nonmanual_reuses_fresh_cache() = runTest {
        val f = Fixture()
        f.cache[WeatherProvider.MET_NO to second.id] = ForecastLoad(f.forecast(second, WeatherProvider.MET_NO))
        val gate = CompletableDeferred<Unit>()
        f.fetch = { provider, location -> gate.await(); ForecastLoad(f.forecast(location, provider)) }
        val job = async { f.coordinator.refresh(true) }
        runCurrent()
        f.select(second, WeatherProvider.MET_NO)
        gate.complete(Unit)
        assertEquals(WidgetRefreshResult.SUCCESS, job.await())
        assertEquals(1, f.calls.size)
        assertEquals(WidgetRefreshResult.SUCCESS, f.coordinator.refresh(false))
        assertEquals(1, f.calls.size)
    }

    @Test
    fun manual_tap_during_periodic_fresh_cache_lookup_upgrades_once() = runTest {
        val f = Fixture()
        f.cache[WeatherProvider.OPEN_METEO to first.id] = ForecastLoad(f.forecast(first))
        val gate = CompletableDeferred<Unit>()
        f.cacheRead = { gate.await() }
        val periodic = async { f.coordinator.refresh(false) }
        runCurrent()
        val taps = List(10) { async { f.coordinator.refresh(true) } }
        runCurrent()
        gate.complete(Unit)
        assertEquals(WidgetRefreshResult.SUCCESS, periodic.await())
        taps.forEach { assertEquals(WidgetRefreshResult.SUCCESS, it.await()) }
        assertEquals(1, f.calls.size)
    }

    @Test
    fun selection_change_during_loading_publication_is_checked_before_networking() = runTest {
        val f = Fixture()
        f.onPublish = { f.select(second, WeatherProvider.MET_NO) }
        assertEquals(WidgetRefreshResult.SUCCESS, f.coordinator.refresh(true))
        assertEquals(listOf(WeatherProvider.MET_NO to second.id), f.calls)
        val snapshot = f.widgets.loadCachedState() as WeatherWidgetSnapshot.Available
        assertEquals("Second", snapshot.weather.locationName)
        assertFalse(snapshot.refresh.lastAttemptFailed)
    }

    @Test
    fun obsolete_exception_does_not_leak_failure_and_latest_is_fetched() = runTest {
        val f = Fixture()
        val gate = CompletableDeferred<Unit>()
        f.fetch = { provider, location ->
            if (location.id == first.id) { gate.await(); error("offline") }
            ForecastLoad(f.forecast(location, provider))
        }
        val job = async { f.coordinator.refresh(true) }
        runCurrent()
        f.select(second, WeatherProvider.MET_NO)
        gate.complete(Unit)
        assertEquals(WidgetRefreshResult.SUCCESS, job.await())
        val snapshot = f.widgets.loadCachedState() as WeatherWidgetSnapshot.Available
        assertEquals("Second", snapshot.weather.locationName)
        assertFalse(snapshot.refresh.lastAttemptFailed)
        assertEquals(2, f.calls.size)
    }

    @Test
    fun latest_stale_cache_is_refreshed_and_repeated_selection_changes_are_bounded() = runTest {
        val f = Fixture()
        val gates = List(2) { CompletableDeferred<Unit>() }
        f.cache[WeatherProvider.MET_NO to second.id] = ForecastLoad(f.forecast(second, WeatherProvider.MET_NO, now - 2.hours), true)
        f.fetch = { provider, location -> gates[f.calls.size - 1].await(); ForecastLoad(f.forecast(location, provider)) }
        val job = async { f.coordinator.refresh(true) }
        runCurrent()
        f.select(second, WeatherProvider.MET_NO)
        gates[0].complete(Unit)
        runCurrent()
        assertEquals(2, f.calls.size)
        f.select(first, WeatherProvider.MET_NO)
        gates[1].complete(Unit)
        assertEquals(WidgetRefreshResult.SELECTION_CHANGED, job.await())
        assertEquals(2, f.calls.size)
        val snapshot = f.widgets.loadCachedState() as WeatherWidgetSnapshot.NoWeather
        assertEquals("First", snapshot.request.locationName)
        assertFalse(snapshot.refresh.isRefreshing)
        assertFalse(snapshot.refresh.lastAttemptFailed)
    }

    @Test
    fun stale_fallback_and_exception_end_loading_without_losing_cached_timestamp() = runTest {
        val f = Fixture()
        val old = f.forecast(first, fetchedAt = now - 2.hours)
        f.cache[WeatherProvider.OPEN_METEO to first.id] = ForecastLoad(old, true)
        f.fetch = { _, _ -> ForecastLoad(old, true) }
        assertEquals(WidgetRefreshResult.FAILED, f.coordinator.refresh(true))
        var snapshot = f.widgets.loadCachedState() as WeatherWidgetSnapshot.Available
        assertFalse(snapshot.refresh.isRefreshing)
        assertTrue(snapshot.refresh.lastAttemptFailed)
        assertEquals(old.fetchedAt, snapshot.weather.lastWeatherUpdate)
        f.fetch = { _, _ -> error("offline") }
        assertEquals(WidgetRefreshResult.FAILED, f.coordinator.refresh(true))
        snapshot = f.widgets.loadCachedState() as WeatherWidgetSnapshot.Available
        assertFalse(snapshot.refresh.isRefreshing)
        assertTrue(snapshot.refresh.lastAttemptFailed)
        assertEquals(old.fetchedAt, snapshot.weather.lastWeatherUpdate)
    }

    @Test
    fun cancellation_cleans_metadata_propagates_to_joiners_and_next_run_can_start() = runTest {
        val f = Fixture()
        val gate = CompletableDeferred<Unit>()
        f.fetch = { provider, location -> gate.await(); ForecastLoad(f.forecast(location, provider)) }
        val owner = launch { f.coordinator.refresh(true) }
        runCurrent()
        val joiner = async { f.coordinator.refresh(true) }
        runCurrent()
        owner.cancelAndJoin()
        try { joiner.await(); fail("Expected cancellation") } catch (_: CancellationException) { }
        val snapshot = f.widgets.loadCachedState() as WeatherWidgetSnapshot.NoWeather
        assertFalse(snapshot.refresh.isRefreshing)
        assertTrue(snapshot.refresh.lastAttemptFailed)
        gate.complete(Unit)
        assertEquals(WidgetRefreshResult.SUCCESS, f.coordinator.refresh(true))
        assertEquals(2, f.calls.size)
    }

    @Test
    fun cancelling_a_joiner_does_not_cancel_owner_and_manual_bypasses_fresh_cache() = runTest {
        val f = Fixture()
        f.cache[WeatherProvider.OPEN_METEO to first.id] = ForecastLoad(f.forecast(first))
        val gate = CompletableDeferred<Unit>()
        f.fetch = { provider, location -> gate.await(); ForecastLoad(f.forecast(location, provider)) }
        val owner = async { f.coordinator.refresh(true) }
        runCurrent()
        val joiner = launch { f.coordinator.refresh(true) }
        runCurrent()
        joiner.cancelAndJoin()
        assertTrue((f.widgets.loadCachedState() as WeatherWidgetSnapshot.Available).refresh.isRefreshing)
        gate.complete(Unit)
        assertEquals(WidgetRefreshResult.SUCCESS, owner.await())
        assertEquals(1, f.calls.size)
    }

    @Test
    fun no_location_never_fetches_and_mismatched_result_is_failure() = runTest {
        val f = Fixture()
        f.active.value = null
        f.locationsValue.value = emptyList()
        assertEquals(WidgetRefreshResult.NO_LOCATION, f.coordinator.refresh(true))
        assertTrue(f.calls.isEmpty())
        f.locationsValue.value = listOf(first, second)
        f.active.value = ActiveLocation.Saved(first)
        f.fetch = { _, _ -> ForecastLoad(f.forecast(second, WeatherProvider.MET_NO)) }
        assertEquals(WidgetRefreshResult.FAILED, f.coordinator.refresh(true))
        val snapshot = f.widgets.loadCachedState() as WeatherWidgetSnapshot.NoWeather
        assertTrue(snapshot.refresh.lastAttemptFailed)
        assertFalse(snapshot.refresh.isRefreshing)
    }

    private inner class Fixture {
        val settings = MutableStateFlow(PersistedSettings())
        val active = MutableStateFlow<ActiveLocation?>(ActiveLocation.Saved(first))
        val locationsValue = MutableStateFlow(listOf(first, second))
        val cache = mutableMapOf<Pair<WeatherProvider, String>, ForecastLoad>()
        val calls = mutableListOf<Pair<WeatherProvider, String>>()
        val publications = mutableListOf<WeatherWidgetSnapshot>()
        var cacheRead: suspend () -> Unit = {}
        var onPublish: suspend () -> Unit = {}
        var fetch: suspend (WeatherProvider, WeatherLocation) -> ForecastLoad = { provider, location -> ForecastLoad(forecast(location, provider)) }
        private val settingsRepo = object : SettingsRepository {
            override val settings = this@Fixture.settings
            override suspend fun save(settings: PersistedSettings) { this@Fixture.settings.value = settings }
        }
        private val locations = object : LocationRepository {
            override val locations = locationsValue
            override val activeLocation = active
            override val locationMode = flowOf(LocationMode.SAVED_LOCATION)
            override suspend fun add(location: WeatherLocation) = Unit
            override suspend fun update(location: WeatherLocation) = Unit
            override suspend fun delete(id: String) = Unit
            override suspend fun selectSaved(id: String) = Unit
            override suspend fun setTrackMe(enabled: Boolean) = Unit
        }
        private val weather = object : WeatherRepository {
            override suspend fun getForecast(provider: WeatherProvider, location: ActiveLocation): ForecastLoad {
                val saved = (location as ActiveLocation.Saved).location
                calls += provider to saved.id
                val result = fetch(provider, saved)
                if (!result.isStale) cache[provider to saved.id] = result
                return result
            }
            override suspend fun getLastCachedForecast(provider: WeatherProvider, location: ActiveLocation): ForecastLoad? {
                cacheRead()
                return cache[provider to (location as ActiveLocation.Saved).location.id]
            }
            override suspend fun clearCache() { cache.clear() }
        }
        private val metadata = TestMetadata()
        val widgets = WeatherWidgetRepository(settingsRepo, locations, weather, object : Clock { override fun now() = now }, metadata)
        val coordinator = WeatherWidgetRefreshCoordinator(widgets, weather) {
            publications += widgets.loadCachedState()
            onPublish()
        }
        fun select(location: WeatherLocation, provider: WeatherProvider) {
            active.value = ActiveLocation.Saved(location)
            settings.value = settings.value.copy(provider = provider)
        }
        fun forecast(location: WeatherLocation, provider: WeatherProvider = WeatherProvider.OPEN_METEO, fetchedAt: Instant = now) = WeatherForecast(
            location = location, provider = provider, fetchedAt = fetchedAt,
            current = CurrentWeather(23.0, 24.0, null, null, null, WeatherCondition.CLEAR, true),
            hourly = emptyList(), daily = emptyList(), moonPhase = 0.0, timezone = "UTC"
        )
    }

    private class TestMetadata : WidgetRefreshMetadataStore {
        private val statuses = MutableStateFlow<Map<WeatherWidgetRequestIdentity, WidgetRefreshStatus>>(emptyMap())
        override fun observe(identity: WeatherWidgetRequestIdentity, now: () -> Instant) = statuses.map { it[identity] ?: WidgetRefreshStatus() }
        override suspend fun start(identity: WeatherWidgetRequestIdentity, now: Instant): WidgetRefreshAttempt {
            statuses.value = statuses.value + (identity to WidgetRefreshStatus(startedAt = now, isRefreshing = true))
            return WidgetRefreshAttempt("attempt")
        }
        override suspend fun finish(identity: WeatherWidgetRequestIdentity, attempt: WidgetRefreshAttempt, now: Instant, failed: Boolean) {
            val old = statuses.value.getValue(identity)
            statuses.value = statuses.value + (identity to old.copy(endedAt = now, isRefreshing = false, lastAttemptFailed = failed))
        }
    }
}
