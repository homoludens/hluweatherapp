package net.droopia.hluweather.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import net.droopia.hluweather.data.cache.ForecastCache
import net.droopia.hluweather.data.cache.ForecastCacheKey
import net.droopia.hluweather.data.model.ActiveLocation
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun repository(source: FakeSource, cache: ForecastCache) = CachingWeatherRepository(
        sources = mapOf(source.provider to source),
        cache = cache
    )

    private fun cache(scope: CoroutineScope) = ForecastCache(
        PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { temporaryFolder.newFile("cache.preferences_pb") }
        )
    )

    private fun forecast(provider: WeatherProvider): WeatherForecast =
        buildMockForecast(Svilajnac).copy(provider = provider)

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
}
