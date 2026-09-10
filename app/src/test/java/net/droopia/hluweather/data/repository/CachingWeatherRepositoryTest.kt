package net.droopia.hluweather.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.coroutines.test.runTest
import net.droopia.hluweather.data.cache.ForecastCache
import net.droopia.hluweather.data.cache.ForecastCacheKey
import net.droopia.hluweather.data.cache.ForecastCacheStore
import net.droopia.hluweather.data.model.ActiveLocation
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CachingWeatherRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun successful_live_result_is_cached_as_fresh() = runTest {
        val forecast = forecast(WeatherProvider.OPEN_METEO)
        val source = FakeSource(WeatherProvider.OPEN_METEO, forecast)
        val cache = cache(backgroundScope)
        val repository = repository(source, cache)

        val load = repository.getForecast(
            WeatherProvider.OPEN_METEO,
            ActiveLocation.Saved(Svilajnac)
        )

        assertEquals(forecast, load.forecast)
        assertFalse(load.isStale)
        assertEquals(
            forecast,
            cache.get(ForecastCacheKey(WeatherProvider.OPEN_METEO, "saved:svilajnac"))
        )
    }

    @Test
    fun current_location_uses_rounded_coordinate_key() = runTest {
        val source = FakeSource(WeatherProvider.OPEN_METEO, forecast(WeatherProvider.OPEN_METEO))
        val cache = cache(backgroundScope)
        val repository = repository(source, cache)

        repository.getForecast(
            WeatherProvider.OPEN_METEO,
            ActiveLocation.Current(GeoPoint(44.81761, 20.46331))
        )

        assertEquals(
            source.forecast.copy(
                location = WeatherLocation(
                    id = "current",
                    name = "Current location",
                    latitude = 44.81761,
                    longitude = 20.46331
                )
            ),
            cache.get(
                ForecastCacheKey(
                    WeatherProvider.OPEN_METEO,
                    "current:44.818:20.463"
                )
            )
        )
    }

    @Test
    fun live_failure_uses_only_matching_provider_cache_as_stale() = runTest {
        val openForecast = forecast(WeatherProvider.OPEN_METEO)
        val cache = cache(backgroundScope)
        val openSource = FakeSource(WeatherProvider.OPEN_METEO, openForecast)
        repository(openSource, cache).getForecast(
            WeatherProvider.OPEN_METEO,
            ActiveLocation.Saved(Svilajnac)
        )
        openSource.failure = WeatherRepositoryException("offline")
        val metSource = FakeSource(
            WeatherProvider.MET_NO,
            forecast(WeatherProvider.MET_NO),
            WeatherRepositoryException("offline")
        )
        val repository = CachingWeatherRepository(
            sources = mapOf(
                WeatherProvider.OPEN_METEO to openSource,
                WeatherProvider.MET_NO to metSource
            ),
            cache = cache
        )

        val load = repository.getForecast(
            WeatherProvider.OPEN_METEO,
            ActiveLocation.Saved(Svilajnac)
        )

        assertEquals(openForecast, load.forecast)
        assertTrue(load.isStale)
        var error: Throwable? = null
        try {
            repository.getForecast(WeatherProvider.MET_NO, ActiveLocation.Saved(Svilajnac))
        } catch (thrown: Throwable) {
            error = thrown
        }
        assertTrue(error is WeatherRepositoryException)
    }

    @Test
    fun live_failure_without_cache_is_rethrown() = runTest {
        val failure = WeatherRepositoryException("offline")
        val repository = repository(
            FakeSource(WeatherProvider.OPEN_METEO, forecast(WeatherProvider.OPEN_METEO), failure),
            cache(backgroundScope)
        )

        var error: Throwable? = null
        try {
            repository.getForecast(WeatherProvider.OPEN_METEO, ActiveLocation.Saved(Svilajnac))
        } catch (thrown: Throwable) {
            error = thrown
        }
        assertTrue(error is WeatherRepositoryException)
    }

    @Test
    fun cache_write_failure_returns_the_live_result_without_stale_fallback() = runTest {
        val liveForecast = forecast(WeatherProvider.OPEN_METEO, "2026-09-10T12:00:00Z")
        val olderForecast = forecast(WeatherProvider.OPEN_METEO, "2026-09-09T12:00:00Z")
        val cache = FailingCache(olderForecast)
        val repository = CachingWeatherRepository(
            sources = mapOf(
                WeatherProvider.OPEN_METEO to FakeSource(WeatherProvider.OPEN_METEO, liveForecast)
            ),
            cache = cache
        )

        val load = repository.getForecast(
            WeatherProvider.OPEN_METEO,
            ActiveLocation.Saved(Svilajnac)
        )

        assertEquals(liveForecast, load.forecast)
        assertFalse(load.isStale)
        assertEquals(0, cache.getCalls)
    }

    @Test
    fun source_cancellation_is_propagated_without_cache_lookup() = runTest {
        val cancellation = kotlinx.coroutines.CancellationException("cancelled")
        val cache = FailingCache(forecast(WeatherProvider.OPEN_METEO))
        val repository = repository(
            FakeSource(WeatherProvider.OPEN_METEO, forecast(WeatherProvider.OPEN_METEO), cancellation),
            cache
        )

        var thrown: Throwable? = null
        try {
            repository.getForecast(WeatherProvider.OPEN_METEO, ActiveLocation.Saved(Svilajnac))
        } catch (error: Throwable) {
            thrown = error
        }

        assertSame(cancellation, thrown)
        assertEquals(0, cache.getCalls)
    }

    @Test
    fun opposite_provider_cache_entry_is_not_used_for_a_live_failure() = runTest {
        val openForecast = forecast(WeatherProvider.OPEN_METEO)
        val cache = InMemoryCache()
        cache.put(
            ForecastCacheKey(WeatherProvider.OPEN_METEO, "saved:svilajnac"),
            openForecast
        )
        val metFailure = WeatherRepositoryException("offline")
        val repository = CachingWeatherRepository(
            sources = mapOf(
                WeatherProvider.MET_NO to FakeSource(
                    WeatherProvider.MET_NO,
                    forecast(WeatherProvider.MET_NO),
                    metFailure
                )
            ),
            cache = cache
        )

        var thrown: Throwable? = null
        try {
            repository.getForecast(WeatherProvider.MET_NO, ActiveLocation.Saved(Svilajnac))
        } catch (error: Throwable) {
            thrown = error
        }

        assertSame(metFailure, thrown)
    }

    @Test
    fun unsupported_provider_is_not_routed_to_another_source() = runTest {
        val source = FakeSource(WeatherProvider.OPEN_METEO, forecast(WeatherProvider.OPEN_METEO))
        val repository = repository(source, cache(backgroundScope))

        var error: Throwable? = null
        try {
            repository.getForecast(WeatherProvider.MET_NO, ActiveLocation.Saved(Svilajnac))
        } catch (thrown: Throwable) {
            error = thrown
        }
        assertTrue(error is WeatherRepositoryException)
        assertEquals(0, source.calls)
    }

    private fun repository(source: FakeSource, cache: ForecastCacheStore) = CachingWeatherRepository(
        sources = mapOf(source.provider to source),
        cache = cache
    )

    private fun cache(scope: CoroutineScope) = ForecastCache(
        PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { temporaryFolder.newFile("cache.preferences_pb") }
        )
    )

    private fun forecast(
        provider: WeatherProvider,
        fetchedAt: String = "2026-09-10T12:00:00Z"
    ): WeatherForecast = buildMockForecast(
        Svilajnac,
        Instant.parse(fetchedAt),
        TimeZone.of("UTC")
    ).copy(provider = provider)

    private class FakeSource(
        override val provider: WeatherProvider,
        val forecast: WeatherForecast,
        var failure: Throwable? = null
    ) : WeatherSource {
        var calls = 0

        override suspend fun getForecast(location: net.droopia.hluweather.data.model.WeatherLocation): WeatherForecast {
            calls++
            failure?.let { throw it }
            return forecast.copy(location = location)
        }
    }

    private class FailingCache(
        private val fallback: WeatherForecast
    ) : ForecastCacheStore {
        var getCalls = 0

        override suspend fun get(key: ForecastCacheKey): WeatherForecast? {
            getCalls++
            return fallback
        }

        override suspend fun put(key: ForecastCacheKey, forecast: WeatherForecast) {
            error("cache write failed")
        }

        override suspend fun entries() = emptyList<ForecastCacheKey>()

        override suspend fun clear() = Unit
    }

    private class InMemoryCache : ForecastCacheStore {
        private val values = mutableMapOf<ForecastCacheKey, WeatherForecast>()

        override suspend fun get(key: ForecastCacheKey) = values[key]

        override suspend fun put(key: ForecastCacheKey, forecast: WeatherForecast) {
            values[key] = forecast
        }

        override suspend fun entries() = values.keys.toList()

        override suspend fun clear() = values.clear().let { Unit }
    }
}
