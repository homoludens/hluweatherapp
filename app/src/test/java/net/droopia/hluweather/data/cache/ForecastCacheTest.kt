package net.droopia.hluweather.data.cache

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import net.droopia.hluweather.data.model.WeatherProvider
import net.droopia.hluweather.data.repository.Svilajnac
import net.droopia.hluweather.data.repository.buildMockForecast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ForecastCacheTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun missing_entry_returns_null() = runTest {
        val cache = cache(backgroundScope)

        assertNull(cache.get(ForecastCacheKey(WeatherProvider.MET_NO, "saved:belgrade")))
    }

    @Test
    fun successful_write_can_be_read_after_cache_recreation() = runTest {
        val dataStore = dataStore(backgroundScope)
        val key = ForecastCacheKey(WeatherProvider.OPEN_METEO, "saved:svilajnac")
        val forecast = buildMockForecast(Svilajnac)

        ForecastCache(dataStore).put(key, forecast)

        assertEquals(forecast, ForecastCache(dataStore).get(key))
    }

    @Test
    fun provider_is_part_of_the_cache_key() = runTest {
        val cache = cache(backgroundScope)
        val key = ForecastCacheKey(WeatherProvider.OPEN_METEO, "saved:belgrade")

        cache.put(key, buildMockForecast(Svilajnac))

        assertNull(cache.get(key.copy(provider = WeatherProvider.MET_NO)))
    }

    @Test
    fun eviction_keeps_twenty_entries_and_recently_used_entry() = runTest {
        val cache = cache(backgroundScope)
        val keys = (0 until 20).map { index ->
            ForecastCacheKey(WeatherProvider.OPEN_METEO, "saved:$index")
        }
        keys.forEach { key -> cache.put(key, buildMockForecast(Svilajnac)) }
        assertNotNull(cache.get(keys.first()))

        val newestKey = ForecastCacheKey(WeatherProvider.OPEN_METEO, "saved:20")
        cache.put(newestKey, buildMockForecast(Svilajnac))

        assertEquals(20, cache.entries().size)
        assertNotNull(cache.get(keys.first()))
        assertNull(cache.get(keys[1]))
        assertTrue(cache.entries().contains(newestKey))
    }

    @Test
    fun clear_removes_all_entries() = runTest {
        val cache = cache(backgroundScope)
        cache.put(
            ForecastCacheKey(WeatherProvider.OPEN_METEO, "saved:svilajnac"),
            buildMockForecast(Svilajnac)
        )

        cache.clear()

        assertTrue(cache.entries().isEmpty())
    }

    private fun cache(scope: CoroutineScope): ForecastCache = ForecastCache(dataStore(scope))

    private fun dataStore(scope: CoroutineScope) = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { temporaryFolder.newFile("cache.preferences_pb") }
    )
}
