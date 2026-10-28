package net.droopia.hluweather.widget

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import net.droopia.hluweather.data.cache.ForecastCache
import net.droopia.hluweather.data.cache.ForecastCacheKey
import net.droopia.hluweather.data.cache.ForecastCacheStore
import net.droopia.hluweather.data.model.ActiveLocation
import net.droopia.hluweather.data.model.CurrentWeather
import net.droopia.hluweather.data.model.DayForecast
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.HourForecast
import net.droopia.hluweather.data.model.LocationMode
import net.droopia.hluweather.data.model.ThemeMode
import net.droopia.hluweather.data.model.WeatherCondition
import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherProvider
import net.droopia.hluweather.data.repository.CachingWeatherRepository
import net.droopia.hluweather.data.repository.ForecastLoad
import net.droopia.hluweather.data.repository.LocationRepository
import net.droopia.hluweather.data.repository.WeatherRepository
import net.droopia.hluweather.data.repository.WeatherSource
import net.droopia.hluweather.ui.settings.PersistedSettings
import net.droopia.hluweather.ui.settings.SettingsRepository
import net.droopia.hluweather.ui.settings.TemperatureUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WeatherWidgetStateRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun stale_result_ends_loading_preserves_timestamp_and_does_not_leak_to_another_identity() = runTest {
        val now = Instant.parse("2026-10-28T12:00:00Z")
        val file = temporaryFolder.newFile("refresh-status.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val metadata = DataStoreWidgetRefreshMetadataStore(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
            val old = forecast(fetchedAt = now - 2.hours)
            val repository = widgetRepository(locations = listOf(SAVED_LOCATION), active = ActiveLocation.Saved(SAVED_LOCATION),
                weatherRepository = FakeWeatherRepository(ForecastLoad(old)), clock = fixedClock(now), metadata = metadata)
            val request = repository.resolveRequest()!!
            val attempt = repository.beginRefresh(request)
            val loading = repository.loadCachedState() as WeatherWidgetSnapshot.Available
            assertTrue(loading.refresh.isRefreshing)
            assertEquals(old.fetchedAt, loading.weather.lastWeatherUpdate)
            repository.finishRefresh(request, attempt, ForecastLoad(old, isStale = true))
            val failed = repository.loadCachedState() as WeatherWidgetSnapshot.Available
            assertFalse(failed.refresh.isRefreshing)
            assertTrue(failed.refresh.lastAttemptFailed)
            assertTrue(failed.weather.isStale)
            assertEquals(old.fetchedAt, failed.weather.lastWeatherUpdate)
            val other = request.copy(provider = WeatherProvider.MET_NO)
            assertEquals(WidgetRefreshStatus(), metadata.read(other.identity) { now })
            val moved = request.copy(location = ActiveLocation.Saved(SAVED_LOCATION.copy(altitude = 138)))
            assertEquals(WidgetRefreshStatus(), metadata.read(moved.identity) { now })
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }

    @Test
    fun interrupted_attempt_survives_recreation_then_expires_and_old_completion_cannot_end_new_attempt() = runTest {
        var now = Instant.parse("2026-10-28T12:00:00Z")
        val file = temporaryFolder.newFile("interrupted.preferences_pb")
        val request = WeatherWidgetRequest(WeatherProvider.OPEN_METEO, ActiveLocation.Saved(SAVED_LOCATION), "label")
        val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val first = DataStoreWidgetRefreshMetadataStore(PreferenceDataStoreFactory.create(scope = scope1, produceFile = { file }))
        val oldAttempt = first.start(request.identity, now)
        scope1.coroutineContext[Job]!!.cancelAndJoin()
        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val restarted = DataStoreWidgetRefreshMetadataStore(PreferenceDataStoreFactory.create(scope = scope2, produceFile = { file }))
            assertTrue(restarted.read(request.identity) { now }.isRefreshing)
            now += 15.minutes
            val expired = restarted.read(request.identity) { now }
            assertFalse(expired.isRefreshing)
            assertTrue(expired.lastAttemptFailed)
            val newAttempt = restarted.start(request.identity, now)
            restarted.finish(request.identity, oldAttempt, now, false)
            assertTrue(restarted.read(request.identity) { now }.isRefreshing)
            restarted.finish(request.identity, newAttempt, now, false)
            val finished = restarted.read(request.identity) { now }
            assertFalse(finished.isRefreshing)
            assertFalse(finished.lastAttemptFailed)
            assertEquals(now, finished.endedAt)
        } finally { scope2.coroutineContext[Job]!!.cancelAndJoin() }
    }

    @Test
    fun missing_weather_exposes_only_matching_attempt_and_mismatched_result_is_failure() = runTest {
        val now = Instant.parse("2026-10-28T12:00:00Z")
        val file = temporaryFolder.newFile("empty-status.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val metadata = DataStoreWidgetRefreshMetadataStore(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
            val repository = widgetRepository(locations = listOf(SAVED_LOCATION), active = ActiveLocation.Saved(SAVED_LOCATION),
                clock = fixedClock(now), metadata = metadata)
            val request = repository.resolveRequest()!!
            val attempt = repository.beginRefresh(request)
            assertTrue((repository.loadCachedState() as WeatherWidgetSnapshot.NoWeather).refresh.isRefreshing)
            repository.finishRefresh(request, attempt, ForecastLoad(forecast(provider = WeatherProvider.MET_NO)))
            val failed = repository.loadCachedState() as WeatherWidgetSnapshot.NoWeather
            assertTrue(failed.refresh.lastAttemptFailed)
            assertFalse(failed.refresh.isRefreshing)
            val other = SAVED_LOCATION.copy(id = "other", name = "Other")
            val otherRepository = widgetRepository(locations = listOf(other), active = ActiveLocation.Saved(other),
                clock = fixedClock(now), metadata = metadata)
            assertEquals(WidgetRefreshStatus(), (otherRepository.loadCachedState() as WeatherWidgetSnapshot.NoWeather).refresh)
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }

    @Test
    fun metadata_storage_failure_leaves_cached_weather_usable() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            // A directory cannot be used as the preferences file.
            val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { temporaryFolder.root })
            val metadata = DataStoreWidgetRefreshMetadataStore(store)
            val old = forecast()
            val repository = widgetRepository(locations = listOf(SAVED_LOCATION), active = ActiveLocation.Saved(SAVED_LOCATION),
                weatherRepository = FakeWeatherRepository(ForecastLoad(old, true)), metadata = metadata)
            val request = repository.resolveRequest()!!
            val attempt = repository.beginRefresh(request)
            repository.finishRefresh(request, attempt, null)
            val snapshot = repository.loadCachedState() as WeatherWidgetSnapshot.Available
            assertEquals(old.fetchedAt, snapshot.weather.lastWeatherUpdate)
            assertTrue(snapshot.weather.isStale)
            assertEquals(WidgetRefreshStatus(), snapshot.refresh)
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }

    @Test
    fun failed_forecast_persistence_does_not_create_hidden_widget_cache() = runTest {
        val now = Instant.parse("2026-10-28T12:00:00Z")
        val cached = forecast(fetchedAt = now - 2.hours)
        val live = forecast(fetchedAt = now)
        val cache = object : ForecastCacheStore {
            var saved: WeatherForecast? = cached
            override suspend fun get(key: ForecastCacheKey) = saved
            override suspend fun put(key: ForecastCacheKey, forecast: WeatherForecast) { error("disk unavailable") }
            override suspend fun entries() = emptyList<ForecastCacheKey>()
            override suspend fun clear() { saved = null }
        }
        val source = object : WeatherSource {
            override val provider = WeatherProvider.OPEN_METEO
            override suspend fun getForecast(location: WeatherLocation) = live
        }
        val weather = CachingWeatherRepository(mapOf(source.provider to source), cache, now = { now })
        val repository = widgetRepository(locations = listOf(SAVED_LOCATION), active = ActiveLocation.Saved(SAVED_LOCATION),
            weatherRepository = weather, clock = fixedClock(now))
        val request = repository.resolveRequest()!!
        val attempt = repository.beginRefresh(request)
        val result = weather.getForecast(request.provider, request.location)
        assertFalse(result.isStale)
        assertEquals(live, result.forecast)
        repository.finishRefresh(request, attempt, result)
        val state = repository.loadCachedState() as WeatherWidgetSnapshot.Available
        assertEquals(cached.fetchedAt, state.weather.lastWeatherUpdate)
        assertTrue(state.weather.isStale)
        weather.clearCache()
        assertTrue(repository.loadCachedState() is WeatherWidgetSnapshot.NoWeather)
    }

    @Test
    fun selection_change_during_cache_read_does_not_publish_obsolete_weather_or_status() = runTest {
        val first = SAVED_LOCATION
        val second = first.copy(id = "second", name = "Second")
        val active = MutableStateFlow<ActiveLocation?>(ActiveLocation.Saved(first))
        val locations = object : LocationRepository {
            override val locations = flowOf(listOf(first, second))
            override val activeLocation = active
            override val locationMode = flowOf(LocationMode.SAVED_LOCATION)
            override suspend fun add(location: WeatherLocation) = Unit
            override suspend fun update(location: WeatherLocation) = Unit
            override suspend fun delete(id: String) = Unit
            override suspend fun selectSaved(id: String) = Unit
            override suspend fun setTrackMe(enabled: Boolean) = Unit
        }
        val weather = object : WeatherRepository {
            override suspend fun getLastCachedForecast(provider: WeatherProvider, location: ActiveLocation): ForecastLoad {
                active.value = ActiveLocation.Saved(second)
                return ForecastLoad(forecast())
            }
            override suspend fun getForecast(provider: WeatherProvider, location: ActiveLocation): ForecastLoad = error("no network")
            override suspend fun clearCache() = Unit
        }
        val repository = WeatherWidgetRepository(FakeSettingsRepository(PersistedSettings()), locations, weather)
        val state = repository.loadCachedState() as WeatherWidgetSnapshot.NoWeather
        assertEquals(second.id, (state.request.location as ActiveLocation.Saved).location.id)
        assertEquals(WidgetRefreshStatus(), state.refresh)
    }

    @Test
    fun metadata_store_propagates_cancellation() = runTest {
        val cancelledStore = object : androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> {
            override val data: Flow<androidx.datastore.preferences.core.Preferences> = kotlinx.coroutines.flow.flow { throw CancellationException("cancel") }
            override suspend fun updateData(transform: suspend (androidx.datastore.preferences.core.Preferences) -> androidx.datastore.preferences.core.Preferences): androidx.datastore.preferences.core.Preferences =
                throw CancellationException("cancel")
        }
        val metadata = DataStoreWidgetRefreshMetadataStore(cancelledStore)
        val identity = WeatherWidgetRequest(WeatherProvider.OPEN_METEO, ActiveLocation.Saved(SAVED_LOCATION), "label").identity
        val now = Instant.parse("2026-10-28T12:00:00Z")
        try {
            metadata.start(identity, now)
            fail("Cancellation was swallowed")
        } catch (_: CancellationException) { }
        try {
            metadata.read(identity) { now }
            fail("Cancellation was swallowed")
        } catch (_: CancellationException) { }
        try {
            metadata.finish(identity, WidgetRefreshAttempt("token"), now, true)
            fail("Cancellation was swallowed")
        } catch (_: CancellationException) { }
    }

    @Test
    fun persisted_old_forecast_renders_after_repository_recreation_without_network() = runTest {
        val now = Instant.parse("2026-10-28T12:00:00Z")
        val oldForecast = forecast(fetchedAt = now - 2.hours)
        val cacheFile = temporaryFolder.newFile("widget-forecast.preferences_pb")
        val writerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val writerCache = ForecastCache(
                PreferenceDataStoreFactory.create(
                    scope = writerScope,
                    produceFile = { cacheFile }
                )
            )
            writerCache.put(
                ForecastCacheKey(oldForecast.provider, "saved:${SAVED_LOCATION.id}"),
                oldForecast
            )
        } finally {
            writerScope.cancel()
        }

        val restartedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val restartedCache = ForecastCache(
                PreferenceDataStoreFactory.create(
                    scope = restartedScope,
                    produceFile = { cacheFile }
                )
            )
            val blockedNetwork = CountingWeatherSource(
                provider = WeatherProvider.OPEN_METEO,
                failureMessage = "Network must not be used while rendering"
            )
            val restartedWeatherRepository = CachingWeatherRepository(
                sources = mapOf(WeatherProvider.OPEN_METEO to blockedNetwork),
                cache = restartedCache,
                now = { now }
            )
            val renamedLocation = SAVED_LOCATION.copy(name = "New Zaječar name")
            val settings = PersistedSettings(
                provider = WeatherProvider.OPEN_METEO,
                themeMode = ThemeMode.DARK,
                temperatureUnit = TemperatureUnit.FAHRENHEIT
            )
            val state = widgetRepository(
                settings = settings,
                locations = listOf(renamedLocation),
                active = ActiveLocation.Saved(renamedLocation),
                weatherRepository = restartedWeatherRepository,
                clock = fixedClock(now)
            ).loadCachedState()

            assertTrue(state is WeatherWidgetSnapshot.Available)
            state as WeatherWidgetSnapshot.Available
            assertEquals(WeatherProvider.OPEN_METEO, state.request.provider)
            assertEquals("New Zaječar name", state.weather.locationName)
            assertEquals("73°F", state.weather.temperatureText)
            assertEquals(ThemeMode.DARK, state.settings.themeMode)
            assertEquals(TemperatureUnit.FAHRENHEIT, state.settings.temperatureUnit)
            assertTrue(state.weather.isStale)
            assertEquals(oldForecast.fetchedAt, state.weather.lastWeatherUpdate)
            assertEquals(0, blockedNetwork.calls)
        } finally {
            restartedScope.cancel()
        }
    }

    @Test
    fun missing_location_is_distinct_and_skips_cache_lookup() = runTest {
        val weatherRepository = FakeWeatherRepository(ForecastLoad(forecast()))
        val state = widgetRepository(weatherRepository = weatherRepository).loadCachedState()

        assertTrue(state is WeatherWidgetSnapshot.NoLocation)
        assertEquals(0, weatherRepository.cacheReads)
        assertEquals(0, weatherRepository.liveCalls)
    }

    @Test
    fun selected_location_without_matching_weather_is_actionable_no_weather_state() = runTest {
        val selected = SAVED_LOCATION.copy(name = "Renamed location")
        val settings = PersistedSettings(themeMode = ThemeMode.LIGHT)
        val weatherRepository = FakeWeatherRepository(cachedForecast = null)

        val state = widgetRepository(
            settings = settings,
            locations = listOf(selected),
            active = ActiveLocation.Saved(selected),
            weatherRepository = weatherRepository
        ).loadCachedState()

        assertTrue(state is WeatherWidgetSnapshot.NoWeather)
        state as WeatherWidgetSnapshot.NoWeather
        assertEquals("Renamed location", state.request.locationName)
        assertEquals(settings, state.settings)
        assertEquals(1, weatherRepository.cacheReads)
        assertEquals(0, weatherRepository.liveCalls)
    }

    @Test
    fun mismatched_cached_provider_coordinates_or_altitude_are_never_presented() = runTest {
        val mismatchedForecasts = listOf(
            forecast().copy(provider = WeatherProvider.MET_NO),
            forecast().copy(location = SAVED_LOCATION.copy(id = "another-location")),
            forecast().copy(location = SAVED_LOCATION.copy(latitude = SAVED_LOCATION.latitude + 0.01)),
            forecast().copy(location = SAVED_LOCATION.copy(altitude = SAVED_LOCATION.altitude!! + 1))
        )

        mismatchedForecasts.forEach { cachedForecast ->
            val weatherRepository = FakeWeatherRepository(ForecastLoad(cachedForecast, isStale = true))
            val state = widgetRepository(
                locations = listOf(SAVED_LOCATION),
                active = ActiveLocation.Saved(SAVED_LOCATION),
                weatherRepository = weatherRepository
            ).loadCachedState()

            assertTrue("Mismatched forecast was exposed: $cachedForecast", state is WeatherWidgetSnapshot.NoWeather)
            assertEquals(0, weatherRepository.liveCalls)
        }
    }

    @Test
    fun current_location_cache_identity_uses_rounded_coordinates_and_altitude() = runTest {
        val requested = ActiveLocation.Current(GeoPoint(43.9001, 20.3001), altitude = 137)
        val roundedMatch = forecast(
            location = WeatherLocation(
                id = "current",
                name = "Saved cache label",
                latitude = 43.9002,
                longitude = 20.3002,
                altitude = 137
            )
        )
        val matchingState = widgetRepository(
            mode = LocationMode.TRACK_ME,
            active = requested,
            weatherRepository = FakeWeatherRepository(ForecastLoad(roundedMatch))
        ).loadCachedState()

        assertTrue(matchingState is WeatherWidgetSnapshot.Available)
        assertEquals("Current location", (matchingState as WeatherWidgetSnapshot.Available).weather.locationName)

        val wrongAltitude = roundedMatch.copy(
            location = roundedMatch.location.copy(altitude = 138)
        )
        val mismatchedState = widgetRepository(
            mode = LocationMode.TRACK_ME,
            active = requested,
            weatherRepository = FakeWeatherRepository(ForecastLoad(wrongAltitude))
        ).loadCachedState()
        assertTrue(mismatchedState is WeatherWidgetSnapshot.NoWeather)
    }

    private fun widgetRepository(
        settings: PersistedSettings = PersistedSettings(),
        locations: List<WeatherLocation> = emptyList(),
        active: ActiveLocation? = null,
        mode: LocationMode = LocationMode.SAVED_LOCATION,
        weatherRepository: WeatherRepository = FakeWeatherRepository(cachedForecast = null),
        clock: Clock = fixedClock(Instant.parse("2026-10-28T12:00:00Z")),
        metadata: WidgetRefreshMetadataStore = NoWidgetRefreshMetadataStore
    ) = WeatherWidgetRepository(
        settingsRepository = FakeSettingsRepository(settings),
        locationRepository = FakeLocationRepository(locations, active, mode),
        weatherRepository = weatherRepository,
        clock = clock,
        refreshMetadata = metadata
    )

    private fun forecast(
        provider: WeatherProvider = WeatherProvider.OPEN_METEO,
        location: WeatherLocation = SAVED_LOCATION,
        fetchedAt: Instant = Instant.parse("2026-10-28T10:00:00Z")
    ) = WeatherForecast(
        location = location,
        provider = provider,
        fetchedAt = fetchedAt,
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

    private fun fixedClock(instant: Instant): Clock = object : Clock {
        override fun now(): Instant = instant
    }

    private class FakeSettingsRepository(
        settingsValue: PersistedSettings
    ) : SettingsRepository {
        override val settings: Flow<PersistedSettings> = flowOf(settingsValue)
        override suspend fun save(settings: PersistedSettings) = Unit
    }

    private class FakeLocationRepository(
        locationsValue: List<WeatherLocation>,
        activeLocationValue: ActiveLocation?,
        locationModeValue: LocationMode
    ) : LocationRepository {
        override val locations: Flow<List<WeatherLocation>> = flowOf(locationsValue)
        override val activeLocation: Flow<ActiveLocation?> = flowOf(activeLocationValue)
        override val locationMode: Flow<LocationMode> = flowOf(locationModeValue)

        override suspend fun add(location: WeatherLocation) = Unit
        override suspend fun update(location: WeatherLocation) = Unit
        override suspend fun delete(id: String) = Unit
        override suspend fun selectSaved(id: String) = Unit
        override suspend fun setTrackMe(enabled: Boolean) = Unit
    }

    private class FakeWeatherRepository(
        private val cachedForecast: ForecastLoad?
    ) : WeatherRepository {
        var cacheReads = 0
            private set
        var liveCalls = 0
            private set

        override suspend fun getForecast(
            provider: WeatherProvider,
            location: ActiveLocation
        ): ForecastLoad {
            liveCalls++
            error("Widget state loading must never request live weather")
        }

        override suspend fun getLastCachedForecast(
            provider: WeatherProvider,
            location: ActiveLocation
        ): ForecastLoad? {
            cacheReads++
            return cachedForecast
        }

        override suspend fun clearCache() = Unit
    }

    private class CountingWeatherSource(
        override val provider: WeatherProvider,
        private val failureMessage: String
    ) : WeatherSource {
        var calls = 0
            private set

        override suspend fun getForecast(location: WeatherLocation): WeatherForecast {
            calls++
            error(failureMessage)
        }
    }

    private companion object {
        val SAVED_LOCATION = WeatherLocation(
            id = "zajecar",
            name = "Zaječar",
            latitude = 43.9,
            longitude = 20.3,
            altitude = 137
        )
    }
}
