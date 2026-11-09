package net.droopia.hluweather.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
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

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun late_success_for_edited_location_cannot_replace_newer_cache_entry() = runTest {
        val gate = CompletableDeferred<Unit>()
        val edited = Svilajnac.copy(latitude = Svilajnac.latitude + 1)
        val source = object : WeatherSource {
            override val provider = WeatherProvider.OPEN_METEO
            override suspend fun getForecast(location: WeatherLocation): WeatherForecast {
                if (location == Svilajnac) gate.await()
                return forecast(provider).copy(location = location)
            }
        }
        val cache = InMemoryCache()
        val repository = CachingWeatherRepository(mapOf(source.provider to source), cache)
        val old = async { repository.getForecast(source.provider, ActiveLocation.Saved(Svilajnac)) }
        runCurrent()
        assertEquals(1, repository.inFlightKeyCount())
        repository.getForecast(source.provider, ActiveLocation.Saved(edited))
        assertEquals(1, repository.inFlightKeyCount())
        gate.complete(Unit)
        old.await()
        assertEquals(0, repository.inFlightKeyCount())
        assertEquals(edited, repository.getLastCachedForecast(source.provider, ActiveLocation.Saved(edited))?.forecast?.location)
        assertEquals(null, repository.getLastCachedForecast(source.provider, ActiveLocation.Saved(Svilajnac)))
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun cancelled_source_that_returns_anyway_does_not_write_cache() = runTest {
        val gate = CompletableDeferred<Unit>()
        val source = object : WeatherSource {
            override val provider = WeatherProvider.OPEN_METEO
            override suspend fun getForecast(location: WeatherLocation): WeatherForecast {
                try { gate.await() } catch (_: CancellationException) { }
                return forecast(provider).copy(location = location)
            }
        }
        val cache = InMemoryCache()
        val repository = CachingWeatherRepository(mapOf(source.provider to source), cache)
        val job = launch { repository.getForecast(source.provider, ActiveLocation.Saved(Svilajnac)) }
        runCurrent()
        job.cancelAndJoin()
        assertTrue(cache.entries().isEmpty())
        assertEquals(0, repository.inFlightKeyCount())
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun cancelled_newer_request_still_blocks_old_write_until_all_requests_finish_and_key_can_be_reused() = runTest {
        val gates = List(2) { CompletableDeferred<Unit>() }
        var calls = 0
        val source = object : WeatherSource {
            override val provider = WeatherProvider.OPEN_METEO
            override suspend fun getForecast(location: WeatherLocation): WeatherForecast {
                val call = calls++
                if (call < gates.size) gates[call].await()
                return forecast(provider, "2026-09-10T12:00:0${call}Z").copy(location = location)
            }
        }
        val cache = InMemoryCache()
        val repository = CachingWeatherRepository(mapOf(source.provider to source), cache)
        val location = ActiveLocation.Saved(Svilajnac)
        val old = async { repository.getForecast(source.provider, location) }
        runCurrent()
        val newer = launch { repository.getForecast(source.provider, location) }
        runCurrent()
        newer.cancelAndJoin()
        assertEquals(1, repository.inFlightKeyCount())
        gates[0].complete(Unit)
        old.await()
        assertTrue(cache.entries().isEmpty())
        assertEquals(0, repository.inFlightKeyCount())
        val reused = repository.getForecast(source.provider, location)
        assertEquals(reused.forecast, cache.get(ForecastCacheKey(source.provider, "saved:${Svilajnac.id}")))
        assertEquals(0, repository.inFlightKeyCount())
    }

    @Test
    fun completed_current_location_cells_do_not_accumulate_in_flight_keys() = runTest {
        val source = FakeSource(WeatherProvider.OPEN_METEO, forecast(WeatherProvider.OPEN_METEO))
        val repository = repository(source, InMemoryCache())
        repeat(100) { index ->
            repository.getForecast(source.provider, ActiveLocation.Current(GeoPoint(40.0 + index * 0.01, 20.0)))
            assertEquals(0, repository.inFlightKeyCount())
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun newer_completion_then_reuse_while_old_request_is_active_keeps_latest_token() = runTest {
        val oldGate = CompletableDeferred<Unit>()
        var calls = 0
        val source = object : WeatherSource {
            override val provider = WeatherProvider.OPEN_METEO
            override suspend fun getForecast(location: WeatherLocation): WeatherForecast {
                val call = calls++
                if (call == 0) oldGate.await()
                return forecast(provider, "2026-09-10T12:00:0${call}Z").copy(location = location)
            }
        }
        val cache = InMemoryCache()
        val repository = CachingWeatherRepository(mapOf(source.provider to source), cache)
        val location = ActiveLocation.Saved(Svilajnac)
        val old = async { repository.getForecast(source.provider, location) }
        runCurrent()
        repository.getForecast(source.provider, location)
        assertEquals(1, repository.inFlightKeyCount())
        val latest = repository.getForecast(source.provider, location)
        oldGate.complete(Unit)
        old.await()
        assertEquals(latest.forecast, cache.get(ForecastCacheKey(source.provider, "saved:${Svilajnac.id}")))
        assertEquals(0, repository.inFlightKeyCount())
    }

    @Test
    fun failed_request_releases_its_in_flight_key() = runTest {
        val source = FakeSource(WeatherProvider.OPEN_METEO, forecast(WeatherProvider.OPEN_METEO), WeatherRepositoryException("offline"))
        val repository = repository(source, InMemoryCache())
        try {
            repository.getForecast(source.provider, ActiveLocation.Saved(Svilajnac))
            org.junit.Assert.fail("Expected failure")
        } catch (_: WeatherRepositoryException) { }
        assertEquals(0, repository.inFlightKeyCount())
    }

    @Test
    fun live_result_with_wrong_location_is_rejected_before_caching() = runTest {
        val source = object : WeatherSource {
            override val provider = WeatherProvider.OPEN_METEO
            override suspend fun getForecast(location: WeatherLocation) = forecast(provider).copy(
                location = location.copy(latitude = location.latitude + 1)
            )
        }
        val cache = InMemoryCache()
        val repository = CachingWeatherRepository(mapOf(source.provider to source), cache)
        try {
            repository.getForecast(source.provider, ActiveLocation.Saved(Svilajnac))
            org.junit.Assert.fail("Expected identity mismatch")
        } catch (_: WeatherRepositoryException) { }
        assertTrue(cache.entries().isEmpty())
    }

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
    fun cache_read_accepts_entries_up_to_one_hour_old() = runTest {
        val now = Instant.parse("2026-09-10T12:00:00Z")

        listOf(
            now - 59.minutes,
            now - 1.hours
        ).forEach { fetchedAt ->
            val source = FakeSource(WeatherProvider.OPEN_METEO, forecast(WeatherProvider.OPEN_METEO))
            val cache = InMemoryCache()
            val cachedForecast = forecast(WeatherProvider.OPEN_METEO, fetchedAt.toString())
            cache.put(
                ForecastCacheKey(WeatherProvider.OPEN_METEO, "saved:svilajnac"),
                cachedForecast
            )
            val repository = CachingWeatherRepository(
                sources = mapOf(WeatherProvider.OPEN_METEO to source),
                cache = cache,
                now = { now }
            )

            val load = repository.getCachedForecast(
                WeatherProvider.OPEN_METEO,
                ActiveLocation.Saved(Svilajnac)
            )

            assertEquals(cachedForecast, load?.forecast)
            assertEquals(false, load?.isStale)
            assertEquals(0, source.calls)
        }
    }

    @Test
    fun cache_read_rejects_entries_older_than_one_hour() = runTest {
        val now = Instant.parse("2026-09-10T12:00:00Z")
        val source = FakeSource(WeatherProvider.OPEN_METEO, forecast(WeatherProvider.OPEN_METEO))
        val cache = InMemoryCache()
        cache.put(
            ForecastCacheKey(WeatherProvider.OPEN_METEO, "saved:svilajnac"),
            forecast(WeatherProvider.OPEN_METEO, (now - 1.hours - 1.seconds).toString())
        )
        val repository = CachingWeatherRepository(
            sources = mapOf(WeatherProvider.OPEN_METEO to source),
            cache = cache,
            now = { now }
        )

        assertEquals(
            null,
            repository.getCachedForecast(
                WeatherProvider.OPEN_METEO,
                ActiveLocation.Saved(Svilajnac)
            )
        )
        assertEquals(0, source.calls)
    }

    @Test
    fun cache_read_rejects_different_provider_or_saved_location() = runTest {
        val now = Instant.parse("2026-09-10T12:00:00Z")
        val source = FakeSource(WeatherProvider.OPEN_METEO, forecast(WeatherProvider.OPEN_METEO))
        val cache = InMemoryCache()
        cache.put(
            ForecastCacheKey(WeatherProvider.OPEN_METEO, "saved:svilajnac"),
            forecast(WeatherProvider.OPEN_METEO, (now - 1.minutes).toString())
        )
        val repository = CachingWeatherRepository(
            sources = mapOf(WeatherProvider.OPEN_METEO to source),
            cache = cache,
            now = { now }
        )

        assertEquals(
            null,
            repository.getCachedForecast(
                WeatherProvider.MET_NO,
                ActiveLocation.Saved(Svilajnac)
            )
        )
        assertEquals(
            null,
            repository.getCachedForecast(
                WeatherProvider.OPEN_METEO,
                ActiveLocation.Saved(Svilajnac.copy(latitude = 44.9))
            )
        )
        assertEquals(0, source.calls)
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
    fun cache_read_failure_preserves_the_live_failure() = runTest {
        val liveFailure = WeatherRepositoryException("offline")
        val repository = repository(
            FakeSource(WeatherProvider.OPEN_METEO, forecast(WeatherProvider.OPEN_METEO), liveFailure),
            ReadFailingCache(IllegalStateException("cache unavailable"))
        )

        var error: Throwable? = null
        try {
            repository.getForecast(WeatherProvider.OPEN_METEO, ActiveLocation.Saved(Svilajnac))
        } catch (thrown: Throwable) {
            error = thrown
        }

        assertSame(liveFailure, error)
    }

    @Test
    fun cache_cancellation_is_propagated_when_reading_after_live_failure() = runTest {
        val cancellation = kotlinx.coroutines.CancellationException("cache read cancelled")
        val repository = repository(
            FakeSource(
                WeatherProvider.OPEN_METEO,
                forecast(WeatherProvider.OPEN_METEO),
                WeatherRepositoryException("offline")
            ),
            ReadFailingCache(cancellation)
        )

        var error: Throwable? = null
        try {
            repository.getForecast(WeatherProvider.OPEN_METEO, ActiveLocation.Saved(Svilajnac))
        } catch (thrown: Throwable) {
            error = thrown
        }

        assertSame(cancellation, error)
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

    @Test
    fun new_provider_selection_routes_to_its_source_and_uses_separate_cache_identity() = runTest {
        val providers = listOf(WeatherProvider.VISUAL_CROSSING, WeatherProvider.BRIGHT_SKY)
        val sources = providers.associateWith { provider ->
            FakeSource(provider, forecast(provider))
        }
        val cache = InMemoryCache()
        val repository = CachingWeatherRepository(sources, cache)
        val location = ActiveLocation.Saved(Svilajnac)

        providers.forEach { provider ->
            val load = repository.getForecast(provider, location)
            assertEquals(provider, load.forecast.provider)
            assertEquals(false, load.isStale)
            assertEquals(
                load.forecast,
                cache.get(ForecastCacheKey(provider, "saved:${Svilajnac.id}"))
            )
        }

        assertEquals(1, sources.getValue(WeatherProvider.VISUAL_CROSSING).calls)
        assertEquals(1, sources.getValue(WeatherProvider.BRIGHT_SKY).calls)
        assertEquals(
            providers.map { ForecastCacheKey(it, "saved:${Svilajnac.id}") }.toSet(),
            cache.entries().toSet()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun mismatched_source_provider_is_rejected_at_construction() {
        CachingWeatherRepository(
            sources = mapOf(
                WeatherProvider.OPEN_METEO to FakeSource(
                    WeatherProvider.MET_NO,
                    forecast(WeatherProvider.MET_NO)
                )
            ),
            cache = InMemoryCache()
        )
    }

    @Test
    fun cached_forecast_with_mismatched_embedded_provider_is_rejected() = runTest {
        val liveFailure = WeatherRepositoryException("offline")
        val cache = InMemoryCache()
        cache.put(
            ForecastCacheKey(WeatherProvider.OPEN_METEO, "saved:svilajnac"),
            forecast(WeatherProvider.MET_NO)
        )
        val repository = repository(
            FakeSource(WeatherProvider.OPEN_METEO, forecast(WeatherProvider.OPEN_METEO), liveFailure),
            cache
        )

        var error: Throwable? = null
        try {
            repository.getForecast(WeatherProvider.OPEN_METEO, ActiveLocation.Saved(Svilajnac))
        } catch (thrown: Throwable) {
            error = thrown
        }

        assertSame(liveFailure, error)
    }

    @Test
    fun live_forecast_with_mismatched_embedded_provider_is_rejected_before_caching() = runTest {
        val cache = InMemoryCache()
        val repository = repository(
            FakeSource(
                WeatherProvider.OPEN_METEO,
                forecast(WeatherProvider.MET_NO)
            ),
            cache
        )

        var error: Throwable? = null
        try {
            repository.getForecast(
                WeatherProvider.OPEN_METEO,
                ActiveLocation.Saved(Svilajnac)
            )
        } catch (thrown: Throwable) {
            error = thrown
        }

        assertTrue(error is WeatherRepositoryException)
        assertTrue(cache.entries().isEmpty())
    }

    @Test
    fun edited_saved_location_coordinates_do_not_use_old_cache_when_offline() = runTest {
        val oldLocation = Svilajnac
        val editedLocation = oldLocation.copy(latitude = 44.9, longitude = 20.6)
        val liveFailure = WeatherRepositoryException("offline")
        val cache = InMemoryCache()
        cache.put(
            ForecastCacheKey(WeatherProvider.OPEN_METEO, "saved:${oldLocation.id}"),
            forecast(WeatherProvider.OPEN_METEO).copy(location = oldLocation)
        )
        val repository = repository(
            FakeSource(WeatherProvider.OPEN_METEO, forecast(WeatherProvider.OPEN_METEO), liveFailure),
            cache
        )

        var error: Throwable? = null
        try {
            repository.getForecast(
                WeatherProvider.OPEN_METEO,
                ActiveLocation.Saved(editedLocation)
            )
        } catch (thrown: Throwable) {
            error = thrown
        }

        assertSame(liveFailure, error)
    }

    @Test
    fun saved_location_with_mismatched_altitude_does_not_use_old_cache_when_offline() = runTest {
        val requestedLocation = Svilajnac
        val cachedLocation = requestedLocation.copy(altitude = requestedLocation.altitude!! + 1)
        val liveFailure = WeatherRepositoryException("offline")
        val cache = InMemoryCache()
        cache.put(
            ForecastCacheKey(WeatherProvider.OPEN_METEO, "saved:${requestedLocation.id}"),
            forecast(WeatherProvider.OPEN_METEO).copy(location = cachedLocation)
        )
        val repository = repository(
            FakeSource(WeatherProvider.OPEN_METEO, forecast(WeatherProvider.OPEN_METEO), liveFailure),
            cache
        )

        var error: Throwable? = null
        try {
            repository.getForecast(
                WeatherProvider.OPEN_METEO,
                ActiveLocation.Saved(requestedLocation)
            )
        } catch (thrown: Throwable) {
            error = thrown
        }

        assertSame(liveFailure, error)
    }

    @Test
    fun current_location_with_matching_rounded_cache_key_uses_stale_cache() = runTest {
        val requestedPoint = GeoPoint(44.81761, 20.46331)
        val cachedPoint = GeoPoint(44.81760, 20.46330)
        val cachedForecast = forecast(WeatherProvider.OPEN_METEO).copy(
            location = WeatherLocation(
                id = "current",
                name = "Current location",
                latitude = cachedPoint.latitude,
                longitude = cachedPoint.longitude
            )
        )
        val cache = InMemoryCache()
        cache.put(
            ForecastCacheKey(WeatherProvider.OPEN_METEO, "current:44.818:20.463"),
            cachedForecast
        )
        val repository = repository(
            FakeSource(
                WeatherProvider.OPEN_METEO,
                forecast(WeatherProvider.OPEN_METEO),
                WeatherRepositoryException("offline")
            ),
            cache
        )

        val load = repository.getForecast(
            WeatherProvider.OPEN_METEO,
            ActiveLocation.Current(requestedPoint)
        )

        assertTrue(load.isStale)
        assertEquals(cachedForecast, load.forecast)
    }

    @Test
    fun current_location_stale_cache_requires_matching_altitude_including_nullability() = runTest {
        val requestedPoint = GeoPoint(44.81761, 20.46331)
        val liveFailure = WeatherRepositoryException("offline")
        val cache = InMemoryCache()
        cache.put(
            ForecastCacheKey(WeatherProvider.OPEN_METEO, "current:44.818:20.463"),
            forecast(WeatherProvider.OPEN_METEO).copy(
                location = WeatherLocation(
                    id = "current",
                    name = "Current location",
                    latitude = requestedPoint.latitude,
                    longitude = requestedPoint.longitude,
                    altitude = null
                )
            )
        )
        val repository = repository(
            FakeSource(
                WeatherProvider.OPEN_METEO,
                forecast(WeatherProvider.OPEN_METEO),
                liveFailure
            ),
            cache
        )

        var error: Throwable? = null
        try {
            repository.getForecast(
                WeatherProvider.OPEN_METEO,
                ActiveLocation.Current(requestedPoint, altitude = 100)
            )
        } catch (thrown: Throwable) {
            error = thrown
        }

        assertSame(liveFailure, error)

        cache.put(
            ForecastCacheKey(WeatherProvider.OPEN_METEO, "current:44.818:20.463"),
            forecast(WeatherProvider.OPEN_METEO).copy(
                location = WeatherLocation(
                    id = "current",
                    name = "Current location",
                    latitude = requestedPoint.latitude,
                    longitude = requestedPoint.longitude,
                    altitude = 100
                )
            )
        )
        val matchingLoad = repository.getForecast(
            WeatherProvider.OPEN_METEO,
            ActiveLocation.Current(requestedPoint, altitude = 100)
        )

        assertTrue(matchingLoad.isStale)
        assertEquals(100, matchingLoad.forecast.location.altitude)

        cache.put(
            ForecastCacheKey(WeatherProvider.OPEN_METEO, "current:44.818:20.463"),
            forecast(WeatherProvider.OPEN_METEO).copy(
                location = matchingLoad.forecast.location
            )
        )
        error = null
        try {
            repository.getForecast(
                WeatherProvider.OPEN_METEO,
                ActiveLocation.Current(requestedPoint)
            )
        } catch (thrown: Throwable) {
            error = thrown
        }

        assertSame(liveFailure, error)
    }

    @Test
    fun selected_source_provider_is_validated_again_at_use_time() = runTest {
        val source = FakeSource(WeatherProvider.OPEN_METEO, forecast(WeatherProvider.OPEN_METEO))
        val repository = repository(source, InMemoryCache())
        source.provider = WeatherProvider.MET_NO

        var error: Throwable? = null
        try {
            repository.getForecast(WeatherProvider.OPEN_METEO, ActiveLocation.Saved(Svilajnac))
        } catch (thrown: Throwable) {
            error = thrown
        }

        assertTrue(error is IllegalArgumentException)
        assertEquals(0, source.calls)
    }

    private fun repository(source: FakeSource, cache: ForecastCacheStore) = CachingWeatherRepository(
        sources = mapOf(source.provider to source),
        cache = cache
    )

    @Test
    fun last_cached_read_accepts_old_matching_data_without_network_and_checks_identity() = runTest {
        val now = Instant.parse("2026-09-10T12:00:00Z")
        val cached = forecast(WeatherProvider.OPEN_METEO, (now - 2.hours).toString())
        val source = FakeSource(WeatherProvider.OPEN_METEO, cached)
        val cache = InMemoryCache()
        cache.put(ForecastCacheKey(source.provider, "saved:${Svilajnac.id}"), cached)
        val repository = CachingWeatherRepository(mapOf(source.provider to source), cache, { now })
        val selected = ActiveLocation.Saved(Svilajnac)
        assertEquals(ForecastLoad(cached, true), repository.getLastCachedForecast(source.provider, selected))
        assertEquals(null, repository.getCachedForecast(source.provider, selected))
        listOf(
            Svilajnac.copy(latitude = 50.0),
            Svilajnac.copy(altitude = null),
            Svilajnac.copy(id = "other")
        ).forEach { location ->
            assertEquals(null, repository.getLastCachedForecast(source.provider, ActiveLocation.Saved(location)))
        }
        assertEquals(null, repository.getLastCachedForecast(WeatherProvider.MET_NO, selected))
        assertEquals(0, source.calls)
    }

    @Test
    fun last_cached_read_marks_data_stale_only_after_one_hour() = runTest {
        val now = Instant.parse("2026-09-10T12:00:00Z")
        val source = FakeSource(WeatherProvider.OPEN_METEO, forecast(WeatherProvider.OPEN_METEO))
        val cache = InMemoryCache()
        val repository = CachingWeatherRepository(mapOf(source.provider to source), cache, { now })
        val key = ForecastCacheKey(source.provider, "saved:${Svilajnac.id}")

        listOf(
            (now - 1.hours) to false,
            (now - 1.hours - 1.seconds) to true
        ).forEach { (fetchedAt, isStale) ->
            val cached = forecast(source.provider, fetchedAt.toString())
            cache.put(key, cached)

            assertEquals(
                ForecastLoad(cached, isStale),
                repository.getLastCachedForecast(source.provider, ActiveLocation.Saved(Svilajnac))
            )
        }
        assertEquals(0, source.calls)
    }

    @Test
    fun last_cached_read_propagates_cancellation() = runTest {
        val cancellation = kotlinx.coroutines.CancellationException("cancelled")
        val repository = repository(
            FakeSource(WeatherProvider.OPEN_METEO, forecast(WeatherProvider.OPEN_METEO)),
            ReadFailingCache(cancellation)
        )
        var thrown: Throwable? = null
        try {
            repository.getLastCachedForecast(WeatherProvider.OPEN_METEO, ActiveLocation.Saved(Svilajnac))
        } catch (error: Throwable) { thrown = error }
        assertSame(cancellation, thrown)
    }

    @Test
    fun cache_observation_validates_provider_and_location_without_network() = runTest {
        val cache = cache(backgroundScope)
        val source = FakeSource(WeatherProvider.OPEN_METEO, forecast(WeatherProvider.OPEN_METEO))
        val repository = CachingWeatherRepository(mapOf(source.provider to source), cache)
        val key = ForecastCacheKey(source.provider, "saved:${Svilajnac.id}")
        val location = ActiveLocation.Saved(Svilajnac)

        listOf(
            forecast(WeatherProvider.MET_NO),
            forecast(WeatherProvider.OPEN_METEO).copy(
                location = Svilajnac.copy(latitude = Svilajnac.latitude + 1.0)
            ),
            forecast(WeatherProvider.OPEN_METEO).copy(
                location = Svilajnac.copy(altitude = Svilajnac.altitude!! + 1)
            )
        ).forEach { mismatchedForecast ->
            cache.put(key, mismatchedForecast)
            assertEquals(null, repository.observeCachedForecast(source.provider, location).first())
        }

        val matchingForecast = forecast(WeatherProvider.OPEN_METEO)
        cache.put(key, matchingForecast)
        assertEquals(matchingForecast, repository.observeCachedForecast(source.provider, location).first())
        assertEquals(0, source.calls)
    }

    @Test
    fun cache_observation_propagates_errors_and_cancellation() = runTest {
        listOf(
            IllegalStateException("cache unavailable"),
            kotlinx.coroutines.CancellationException("observation cancelled")
        ).forEach { failure ->
            val repository = CachingWeatherRepository(
                sources = mapOf(
                    WeatherProvider.OPEN_METEO to FakeSource(
                        WeatherProvider.OPEN_METEO,
                        forecast(WeatherProvider.OPEN_METEO)
                    )
                ),
                cache = ObservationFailingCache(failure)
            )
            var thrown: Throwable? = null

            try {
                repository.observeCachedForecast(
                    WeatherProvider.OPEN_METEO,
                    ActiveLocation.Saved(Svilajnac)
                ).first()
            } catch (error: Throwable) {
                thrown = error
            }

            assertSame(failure, thrown)
        }
    }

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
        override var provider: WeatherProvider,
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

    private class ReadFailingCache(
        private val failure: Throwable
    ) : ForecastCacheStore {
        override suspend fun get(key: ForecastCacheKey): WeatherForecast? = throw failure

        override suspend fun put(key: ForecastCacheKey, forecast: WeatherForecast) = Unit

        override suspend fun entries() = emptyList<ForecastCacheKey>()

        override suspend fun clear() = Unit
    }

    private class ObservationFailingCache(
        private val failure: Throwable
    ) : ForecastCacheStore {
        override fun observe(key: ForecastCacheKey): Flow<WeatherForecast?> = flow {
            throw failure
        }

        override suspend fun get(key: ForecastCacheKey): WeatherForecast? = null

        override suspend fun put(key: ForecastCacheKey, forecast: WeatherForecast) = Unit

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
