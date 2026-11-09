package net.droopia.hluweather.data.cache

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import net.droopia.hluweather.data.model.EuropeanAqiScale
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
    fun resolvedAndFallbackZonesSurviveSavedForecastRoundTrips() = runTest {
        val store = dataStore(backgroundScope)
        val cache = ForecastCache(store)
        val key = ForecastCacheKey(WeatherProvider.MET_NO, "saved:zone")
        val resolved = fixedForecast().copy(timezone = "Europe/Belgrade")
        cache.put(key, resolved)
        assertEquals(resolved, ForecastCache(store).get(key))
        val fallback = resolved.copy(timezone = "UTC", timezoneIsUtcFallback = true, daily = emptyList())
        cache.put(key, fallback)
        assertEquals(fallback, ForecastCache(store).get(key))
    }

    @Test
    fun cachedZoneUnknownToDeviceBecomesLabeledUtcWithoutLocalDailySummaries() = runTest {
        val store = dataStore(backgroundScope)
        val cache = ForecastCache(store)
        val key = ForecastCacheKey(WeatherProvider.MET_NO, "saved:unknown-zone")
        cache.put(key, fixedForecast().copy(timezone = "Unknown/Future"))
        val restored = requireNotNull(cache.get(key))
        assertEquals("UTC", restored.timezone)
        assertTrue(restored.timezoneIsUtcFallback)
        assertTrue(restored.daily.isEmpty())
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
    fun visual_crossing_particulates_and_categorical_aqi_scale_round_trip_through_cache() = runTest {
        val dataStore = dataStore(backgroundScope)
        val key = ForecastCacheKey(WeatherProvider.VISUAL_CROSSING, "saved:svilajnac")
        val base = fixedForecast().copy(provider = WeatherProvider.VISUAL_CROSSING)
        val forecast = base.copy(
            current = base.current.copy(
                europeanAqiScale = EuropeanAqiScale.CATEGORICAL_1_TO_6
            ),
            hourly = base.hourly.mapIndexed { index, hour ->
                hour.copy(
                    europeanAqi = if (index == 0) 3.0 else null,
                    europeanAqiScale = EuropeanAqiScale.CATEGORICAL_1_TO_6,
                    pm2_5 = if (index == 0) 8.2 else null,
                    pm10 = if (index == 0) 12.5 else null
                )
            }
        )

        ForecastCache(dataStore).put(key, forecast)

        assertEquals(forecast, ForecastCache(dataStore).get(key))
    }

    @Test
    fun cached_numeric_aqi_without_a_scale_remains_a_numeric_index() = runTest {
        val store = dataStore(backgroundScope)
        val key = ForecastCacheKey(WeatherProvider.OPEN_METEO, "saved:numeric-aqi")
        val base = fixedForecast()
        val forecast = base.copy(
            current = base.current.copy(europeanAqi = 42.0),
            hourly = base.hourly.map { it.copy(europeanAqi = 42.0) }
        )
        val entriesKey = stringPreferencesKey("weather.forecast_cache")

        ForecastCache(store).put(key, forecast)

        assertTrue("europeanAqiScale" !in requireNotNull(store.data.first()[entriesKey]))
        val restored = requireNotNull(ForecastCache(store).get(key))
        assertEquals(EuropeanAqiScale.NUMERIC_INDEX, restored.current.europeanAqiScale)
        assertEquals(EuropeanAqiScale.NUMERIC_INDEX, restored.hourly.first().europeanAqiScale)
        assertEquals(42.0, restored.current.europeanAqi!!, 0.0)
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

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun observation_is_read_only_distinct_and_tracks_put_and_clear() = runTest {
        val store = dataStore(backgroundScope)
        val cache = ForecastCache(store)
        val key = ForecastCacheKey(WeatherProvider.OPEN_METEO, "saved:svilajnac")
        val emissions = Channel<net.droopia.hluweather.data.model.WeatherForecast?>(Channel.UNLIMITED)
        backgroundScope.launch { cache.observe(key).collect { emissions.send(it) } }
        assertNull(emissions.receive())
        cache.put(key, fixedForecast())
        assertEquals(fixedForecast(), emissions.receive())
        val otherKey = key.copy(locationKey = "saved:other")
        cache.put(otherKey, fixedForecast())
        runCurrent()
        assertEquals(listOf(key, otherKey), cache.entries())

        cache.get(key) // LRU reordering is not a weather change.
        assertEquals(listOf(otherKey, key), cache.entries())
        runCurrent()
        assertTrue(emissions.tryReceive().isFailure)

        store.edit { it[stringPreferencesKey("unrelated")] = "changed" }
        store.data.first()
        runCurrent()
        assertTrue(emissions.tryReceive().isFailure)
        cache.clear()
        assertNull(emissions.receive())
    }

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
