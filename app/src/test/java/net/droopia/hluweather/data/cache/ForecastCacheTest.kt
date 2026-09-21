package net.droopia.hluweather.data.cache

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.coroutines.flow.first
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
        val forecast = fixedForecast()

        ForecastCache(dataStore).put(key, forecast)

        assertEquals(forecast, ForecastCache(dataStore).get(key))
    }

    @Test
    fun air_quality_values_round_trip_through_cache() = runTest {
        val dataStore = dataStore(backgroundScope)
        val key = ForecastCacheKey(WeatherProvider.OPEN_METEO, "saved:svilajnac")
        val forecast = fixedForecast().let { original ->
            original.copy(
                current = original.current.copy(
                    europeanAqi = 42.0,
                    pm10 = 12.5,
                    pm2_5 = 8.2
                ),
                hourly = original.hourly.map {
                    it.copy(
                        europeanAqi = 42.0,
                        pm10 = 12.5,
                        pm2_5 = 8.2
                    )
                }
            )
        }

        ForecastCache(dataStore).put(key, forecast)

        assertEquals(forecast, ForecastCache(dataStore).get(key))
    }

    @Test
    fun old_serialized_entry_without_air_quality_values_remains_readable() = runTest {
        val dataStore = dataStore(backgroundScope)
        val key = ForecastCacheKey(WeatherProvider.OPEN_METEO, "saved:svilajnac")
        val cache = ForecastCache(dataStore)
        cache.put(key, fixedForecast())
        val entriesKey = stringPreferencesKey("weather.forecast_cache")
        val oldSerializedEntry = requireNotNull(dataStore.data.first()[entriesKey])
        assertTrue("\"europeanAqi\"" !in oldSerializedEntry)
        assertTrue("\"pm10\"" !in oldSerializedEntry)
        assertTrue("\"pm2_5\"" !in oldSerializedEntry)

        dataStore.edit { preferences -> preferences[entriesKey] = oldSerializedEntry }

        val restored = requireNotNull(cache.get(key))
        assertNull(restored.current.europeanAqi)
        assertNull(restored.current.pm10)
        assertNull(restored.current.pm2_5)
        assertTrue(restored.hourly.all { hour ->
            hour.europeanAqi == null && hour.pm10 == null && hour.pm2_5 == null
        })
    }

    @Test
    fun provider_is_part_of_the_cache_key() = runTest {
        val cache = cache(backgroundScope)
        val key = ForecastCacheKey(WeatherProvider.OPEN_METEO, "saved:belgrade")

        cache.put(key, fixedForecast())

        assertNull(cache.get(key.copy(provider = WeatherProvider.MET_NO)))
    }

    @Test
    fun eviction_keeps_twenty_entries_and_recently_used_entry() = runTest {
        val cache = cache(backgroundScope)
        val keys = (0 until 20).map { index ->
            ForecastCacheKey(WeatherProvider.OPEN_METEO, "saved:$index")
        }
        keys.forEach { key -> cache.put(key, fixedForecast()) }
        assertNotNull(cache.get(keys.first()))

        val newestKey = ForecastCacheKey(WeatherProvider.OPEN_METEO, "saved:20")
        cache.put(newestKey, fixedForecast())

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
            fixedForecast()
        )

        cache.clear()

        assertTrue(cache.entries().isEmpty())
    }

    private fun cache(scope: CoroutineScope): ForecastCache = ForecastCache(dataStore(scope))

    private fun dataStore(scope: CoroutineScope) = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { temporaryFolder.newFile("cache.preferences_pb") }
    )

    private fun fixedForecast() = buildMockForecast(
        Svilajnac,
        Instant.parse("2026-09-10T12:00:00Z"),
        TimeZone.of("UTC")
    ).let { forecast ->
        forecast.copy(
            hourly = forecast.hourly.map {
                it.copy(
                    windSpeedKmh = 18.0,
                    windDirectionDegrees = 225.0,
                    evapotranspiration = 0.2
                )
            }
        )
    }
}
