package net.droopia.hluweather.data.cache

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import net.droopia.hluweather.data.model.EuropeanAqiScale
import net.droopia.hluweather.data.model.ActiveLocation
import net.droopia.hluweather.data.model.CurrentWeatherProvenance
import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherProvider
import net.droopia.hluweather.data.repository.CachingWeatherRepository
import net.droopia.hluweather.data.repository.Svilajnac
import net.droopia.hluweather.data.repository.WeatherSource
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
    fun current_weather_provenance_and_valid_time_round_trip_through_cache() = runTest {
        val dataStore = dataStore(backgroundScope)
        val key = ForecastCacheKey(WeatherProvider.BRIGHT_SKY, "saved:bright-sky")
        val base = fixedForecast()
        val forecast = base.copy(
            provider = WeatherProvider.BRIGHT_SKY,
            current = base.current.copy(
                provenance = CurrentWeatherProvenance.FORECAST,
                validTime = Instant.parse("2026-09-10T12:00:00Z")
            )
        )
        val observationKey = key.copy(locationKey = "saved:bright-sky-observation")
        val observation = forecast.copy(
            current = forecast.current.copy(
                provenance = CurrentWeatherProvenance.OBSERVATION,
                validTime = Instant.parse("2026-09-10T11:45:00Z")
            )
        )

        val cache = ForecastCache(dataStore)
        cache.put(key, forecast)
        cache.put(observationKey, observation)

        assertEquals(forecast, cache.get(key))
        assertEquals(observation, cache.get(observationKey))
    }

    @Test
    fun pre_scale_numeric_aqi_fixture_remains_numeric_after_datastore_recreation() = runTest {
        val file = temporaryFolder.newFile("legacy-forecast-cache.preferences_pb")
        val entriesKey = stringPreferencesKey("weather.forecast_cache")
        val key = ForecastCacheKey(WeatherProvider.OPEN_METEO, "saved:svilajnac")
        val fixture = preChangeAqiFixture()

        assertTrue("europeanAqiScale" !in fixture)
        val firstScope = processScope()
        try {
            processDataStore(firstScope, file).edit { it[entriesKey] = fixture }
        } finally {
            stopProcessScope(firstScope)
        }

        val reopenedScope = processScope()
        try {
            val reopenedStore = processDataStore(reopenedScope, file)
            val reopenedCache = ForecastCache(reopenedStore)
            val restored = requireNotNull(reopenedCache.get(key))
            assertEquals(WeatherProvider.OPEN_METEO, restored.provider)
            assertEquals(Svilajnac, restored.location)
            assertEquals(42.0, restored.current.europeanAqi!!, 0.0)
            assertEquals(12.5, restored.current.pm10!!, 0.0)
            assertEquals(8.2, restored.current.pm2_5!!, 0.0)
            assertEquals(EuropeanAqiScale.NUMERIC_INDEX, restored.current.europeanAqiScale)
            assertEquals(43.0, restored.hourly.single().europeanAqi!!, 0.0)
            assertEquals(EuropeanAqiScale.NUMERIC_INDEX, restored.hourly.single().europeanAqiScale)

            reopenedCache.put(key, restored)
            assertTrue("europeanAqiScale" !in requireNotNull(reopenedStore.data.first()[entriesKey]))
        } finally {
            stopProcessScope(reopenedScope)
        }
    }

    @Test
    fun unknown_aqi_scale_makes_only_aqi_unavailable_without_discarding_forecast() = runTest {
        val store = dataStore(backgroundScope)
        val key = ForecastCacheKey(WeatherProvider.OPEN_METEO, "saved:svilajnac")
        val fixtureEntry = preChangeAqiFixtureEntry()
        val originalForecast = fixtureEntry.getValue("forecast").jsonObject
        val current = originalForecast.getValue("current").jsonObject
            .withField("europeanAqiScale", JsonPrimitive("FUTURE_SCALE"))
        val hourly = originalForecast.getValue("hourly").jsonArray.map { hour ->
            hour.jsonObject.withField("europeanAqiScale", JsonPrimitive("FUTURE_SCALE"))
        }
        val forecast = originalForecast
            .withField("current", current)
            .withField("hourly", JsonArray(hourly))
        store.edit { it[stringPreferencesKey("weather.forecast_cache")] =
            JsonArray(listOf(fixtureEntry.withField("forecast", forecast))).toString()
        }

        val restored = requireNotNull(ForecastCache(store).get(key))

        assertNull(restored.current.europeanAqi)
        assertEquals(EuropeanAqiScale.NUMERIC_INDEX, restored.current.europeanAqiScale)
        assertNull(restored.hourly.single().europeanAqi)
        assertEquals(EuropeanAqiScale.NUMERIC_INDEX, restored.hourly.first().europeanAqiScale)
        assertEquals(23.5, restored.current.temperature, 0.0)
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
        assertTrue("\"provenance\"" !in oldSerializedEntry)
        assertTrue("\"validTime\"" !in oldSerializedEntry)

        dataStore.edit { preferences -> preferences[entriesKey] = oldSerializedEntry }

        val restored = requireNotNull(cache.get(key))
        assertNull(restored.current.europeanAqi)
        assertNull(restored.current.pm10)
        assertNull(restored.current.pm2_5)
        assertEquals(CurrentWeatherProvenance.UNSPECIFIED, restored.current.provenance)
        assertNull(restored.current.validTime)
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
    fun new_providers_keep_separate_cache_identity_after_datastore_recreation() = runTest {
        val file = temporaryFolder.newFile("new-provider-forecast-cache.preferences_pb")
        val providers = listOf(WeatherProvider.VISUAL_CROSSING, WeatherProvider.BRIGHT_SKY)
        val forecasts = providers.associateWith { provider ->
            val base = fixedForecast().copy(provider = provider)
            if (provider == WeatherProvider.VISUAL_CROSSING) {
                base.copy(
                    current = base.current.copy(
                        europeanAqi = 3.0,
                        europeanAqiScale = EuropeanAqiScale.CATEGORICAL_1_TO_6
                    ),
                    hourly = base.hourly.map {
                        it.copy(
                            europeanAqi = 3.0,
                            europeanAqiScale = EuropeanAqiScale.CATEGORICAL_1_TO_6
                        )
                    }
                )
            } else {
                base.copy(
                    current = base.current.copy(
                        provenance = CurrentWeatherProvenance.OBSERVATION,
                        validTime = Instant.parse("2026-09-10T11:45:00Z")
                    )
                )
            }
        }
        val cacheKey = providers.associateWith { provider ->
            ForecastCacheKey(provider, "saved:${Svilajnac.id}")
        }

        val firstScope = processScope()
        try {
            val cache = ForecastCache(processDataStore(firstScope, file))
            forecasts.forEach { (provider, forecast) -> cache.put(cacheKey.getValue(provider), forecast) }
        } finally {
            stopProcessScope(firstScope)
        }

        val reopenedScope = processScope()
        try {
            val reloadedCache = ForecastCache(processDataStore(reopenedScope, file))
            forecasts.forEach { (provider, forecast) ->
                assertEquals(forecast, reloadedCache.get(cacheKey.getValue(provider)))
            }
            assertNull(reloadedCache.get(ForecastCacheKey(WeatherProvider.OPEN_METEO, "saved:${Svilajnac.id}")))
            assertEquals(cacheKey.values.toSet(), reloadedCache.entries().toSet())

            val offlineBrightSky = object : WeatherSource {
                override val provider = WeatherProvider.BRIGHT_SKY
                override suspend fun getForecast(location: WeatherLocation): WeatherForecast =
                    error("Bright Sky is offline")
            }
            val repository = CachingWeatherRepository(
                sources = mapOf(WeatherProvider.BRIGHT_SKY to offlineBrightSky),
                cache = reloadedCache
            )
            val stale = repository.getForecast(
                WeatherProvider.BRIGHT_SKY,
                ActiveLocation.Saved(Svilajnac)
            )
            assertEquals(forecasts.getValue(WeatherProvider.BRIGHT_SKY), stale.forecast)
            assertEquals(WeatherProvider.BRIGHT_SKY, stale.forecast.provider)
            assertTrue(stale.isStale)
        } finally {
            stopProcessScope(reopenedScope)
        }
    }

    @Test
    fun unknown_cache_fields_and_provider_values_do_not_hide_known_entries() = runTest {
        val store = dataStore(backgroundScope)
        val cache = ForecastCache(store)
        val key = ForecastCacheKey(WeatherProvider.OPEN_METEO, "saved:svilajnac")
        val entry = preChangeAqiFixtureEntry()
        val originalForecast = entry.getValue("forecast").jsonObject
        val current = originalForecast.getValue("current").jsonObject
            .withField("futureCurrentField", JsonPrimitive(true))
        val hourly = originalForecast.getValue("hourly").jsonArray.map { hour ->
            hour.jsonObject.withField("futureHourlyField", JsonPrimitive("ignored"))
        }
        val known = entry
            .withField("futureCacheEntryField", JsonPrimitive(1))
            .withField(
                "forecast",
                originalForecast
                    .withField("futureForecastField", JsonPrimitive("ignored"))
                    .withField("current", current)
                    .withField("hourly", JsonArray(hourly))
            )
        val unknownProvider = entry.withField("provider", JsonPrimitive("FUTURE_PROVIDER"))
        val entriesKey = stringPreferencesKey("weather.forecast_cache")
        store.edit { it[entriesKey] = JsonArray(listOf(known, unknownProvider)).toString() }

        assertEquals(listOf(key), cache.entries())
        assertEquals(WeatherProvider.OPEN_METEO, requireNotNull(cache.get(key)).provider)
    }

    @Test
    fun corrupt_serialized_cache_is_treated_as_empty() = runTest {
        val store = dataStore(backgroundScope)
        val cache = ForecastCache(store)
        store.edit { it[stringPreferencesKey("weather.forecast_cache")] = "[{not valid json" }

        assertNull(cache.get(ForecastCacheKey(WeatherProvider.OPEN_METEO, "saved:svilajnac")))
        assertTrue(cache.entries().isEmpty())
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

    private fun processScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun processDataStore(scope: CoroutineScope, file: java.io.File) =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    private suspend fun stopProcessScope(scope: CoroutineScope) {
        scope.cancel()
        scope.coroutineContext[Job]?.join()
    }

    private fun preChangeAqiFixture(): String = requireNotNull(
        javaClass.classLoader?.getResourceAsStream("cache-fixtures/forecast-cache-before-aqi-scale.json")
    ).bufferedReader().use { it.readText() }

    private fun preChangeAqiFixtureEntry(): JsonObject =
        Json.parseToJsonElement(preChangeAqiFixture()).jsonArray.single().jsonObject

    private fun JsonObject.withField(name: String, value: JsonElement): JsonObject =
        JsonObject(this + (name to value))

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
