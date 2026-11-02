package net.droopia.hluweather.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import net.droopia.hluweather.data.cache.CachedRadarMetadata
import net.droopia.hluweather.data.cache.RadarMetadataCacheStore
import net.droopia.hluweather.data.network.KtorLibreWxrRadarApi
import net.droopia.hluweather.data.network.LibreWxrRadarApi
import net.droopia.hluweather.data.network.LibreWxrRadarApiException
import net.droopia.hluweather.data.network.LibreWxrRadarFrame
import net.droopia.hluweather.data.network.LibreWxrRadarFrameType
import net.droopia.hluweather.data.network.LibreWxrRadarMetadata
import net.droopia.hluweather.data.model.RadarMetadata
import net.droopia.hluweather.data.model.RadarProvider
import net.droopia.hluweather.data.network.RadarMetadataApi
import net.droopia.hluweather.data.network.RainViewerRadarApiException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class RadarRepositoryTest {
    @Test
    fun no_observers_means_no_cache_reads_requests_or_refresh_jobs() = runTest {
        val api = FakeApi { metadata }
        val cache = MemoryCache()
        repository(api, cache)

        advanceTimeBy(60_000)
        runCurrent()

        assertEquals(0, api.calls)
        assertEquals(0, cache.reads)
    }

    @Test
    fun rainviewer_repository_uses_only_its_fresh_provider_cache_until_refresh_time() = runTest {
        val cached = CachedRadarMetadata(rainViewerMetadata, epoch - 9.seconds)
        val api = FakeMetadataApi { rainViewerMetadata.copy(host = "https://new-tiles.example.test") }
        val cache = MemoryCache(cached)
        val repository = providerRepository(RadarProvider.RAINVIEWER, api, cache)
        val observer = observe(repository)
        runCurrent()

        assertEquals(rainViewerMetadata, repository.state.value.metadata)
        assertFalse(repository.state.value.isLoading)
        assertFalse(repository.state.value.isStale)
        assertEquals(0, api.calls)
        assertEquals(listOf(RadarProvider.RAINVIEWER), cache.providersRead)

        advanceTimeBy(999)
        runCurrent()
        assertEquals(0, api.calls)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, api.calls)
        assertEquals("https://new-tiles.example.test", repository.state.value.metadata?.host)
        observer.cancel()
        runCurrent()
    }

    @Test
    fun rainviewer_refresh_failure_retains_only_rainviewer_cached_metadata() = runTest {
        val cached = CachedRadarMetadata(rainViewerMetadata, epoch - 1.hours)
        val api = FakeMetadataApi { throw IOException("offline") }
        val cache = MemoryCache(cached)
        val repository = providerRepository(
            RadarProvider.RAINVIEWER,
            api,
            cache,
            policy.copy(maxRetries = 0)
        )
        val observer = observe(repository)
        runCurrent()

        assertEquals(1, api.calls)
        assertEquals(rainViewerMetadata, repository.state.value.metadata)
        assertTrue(repository.state.value.isStale)
        assertTrue(repository.state.value.error is IOException)
        assertEquals(listOf(RadarProvider.RAINVIEWER), cache.providersRead)
        observer.cancel()
        runCurrent()
    }

    @Test
    fun rainviewer_transient_http_failures_retry_and_recover() = runTest {
        for (status in listOf(429, 503)) {
            val api = FakeMetadataApi { call ->
                if (call == 1) throw RainViewerRadarApiException("temporary", status)
                rainViewerMetadata
            }
            val repository = providerRepository(RadarProvider.RAINVIEWER, api)
            val observer = observe(repository)
            runCurrent()
            assertTrue(repository.state.value.isLoading)

            advanceTimeBy(100)
            runCurrent()

            assertEquals(2, api.calls)
            assertEquals(rainViewerMetadata, repository.state.value.metadata)
            assertNull(repository.state.value.error)
            observer.cancel()
            runCurrent()
        }
    }

    @Test
    fun repository_rejects_metadata_from_the_wrong_provider_without_retry_or_cache_write() = runTest {
        val api = FakeMetadataApi { metadata }
        val cache = MemoryCache()
        val repository = providerRepository(RadarProvider.RAINVIEWER, api, cache)
        val observer = observe(repository)
        runCurrent()

        assertEquals(1, api.calls)
        assertTrue(repository.state.value.error is IllegalStateException)
        assertNull(repository.state.value.metadata)
        assertEquals(0, cache.writes)
        observer.cancel()
        runCurrent()
    }

    @Test
    fun rainviewer_metadata_acquires_the_shared_budget_hook_for_each_attempt() = runTest {
        val acquisitions = mutableListOf<RadarProvider>()
        val api = FakeMetadataApi { call ->
            if (call == 1) throw IOException("retry")
            rainViewerMetadata
        }
        val repository = RadarMetadataRepository(
            provider = RadarProvider.RAINVIEWER,
            api = api,
            cache = MemoryCache(),
            scope = backgroundScope,
            policy = policy,
            now = { epoch },
            dispatcher = StandardTestDispatcher(testScheduler),
            requestBudget = RadarRequestBudget { acquisitions += it }
        )
        val observer = observe(repository)
        runCurrent()
        advanceTimeBy(100)
        runCurrent()

        assertEquals(listOf(RadarProvider.RAINVIEWER, RadarProvider.RAINVIEWER), acquisitions)
        assertEquals(rainViewerMetadata, repository.state.value.metadata)
        observer.cancel()
        runCurrent()
    }

    @Test
    fun last_rainviewer_observer_cancels_its_request_without_starting_librewxr_work() = runTest {
        var cancelled = false
        val rainApi = FakeMetadataApi {
            try {
                awaitCancellation()
            } finally {
                cancelled = true
            }
        }
        val libreApi = FakeMetadataApi { metadata }
        val rainRepository = providerRepository(RadarProvider.RAINVIEWER, rainApi)
        val libreRepository = providerRepository(RadarProvider.LIBREWXR, libreApi)
        val observer = observe(rainRepository)
        runCurrent()

        assertEquals(1, rainApi.calls)
        assertEquals(0, libreApi.calls)
        observer.cancel()
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()

        assertTrue(cancelled)
        assertEquals(1, rainApi.calls)
        assertEquals(0, libreApi.calls)
        assertNull(rainRepository.state.value.error)
    }

    @Test
    fun loading_is_observable_then_live_metadata_is_cached_and_reported_fresh() = runTest {
        val response = CompletableDeferred<LibreWxrRadarMetadata>()
        val cache = MemoryCache()
        val repository = repository(FakeApi { response.await() }, cache)
        observe(repository)
        runCurrent()

        assertTrue(repository.state.value.isLoading)
        assertNull(repository.state.value.metadata)
        assertNull(repository.state.value.error)

        response.complete(metadata)
        runCurrent()

        assertEquals(metadata, repository.state.value.metadata)
        assertEquals(epoch, repository.state.value.fetchedAt)
        assertFalse(repository.state.value.isLoading)
        assertFalse(repository.state.value.isStale)
        assertNull(repository.state.value.error)
        assertEquals(CachedRadarMetadata(metadata, epoch), cache.entry)
    }

    @Test
    fun observers_share_work_and_last_observer_cancels_inflight_request_without_error_or_retry() = runTest {
        var cancelled = false
        val api = FakeApi {
            try {
                awaitCancellation()
            } finally {
                cancelled = true
            }
        }
        val cache = MemoryCache()
        val repository = repository(api, cache)
        val first = observe(repository)
        val second = observe(repository)
        runCurrent()
        assertEquals(1, api.calls)

        first.cancel()
        runCurrent()
        assertFalse(cancelled)
        second.cancel()
        runCurrent()

        assertTrue(cancelled)
        assertNull(repository.state.value.error)
        assertEquals(0, cache.writes)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, api.calls)
    }

    @Test
    fun cancelling_last_observer_cancels_the_actual_ktor_request() = runTest {
        var requestStarted = false
        var requestCancelled = false
        val client = HttpClient(MockEngine) {
            engine {
                dispatcher = StandardTestDispatcher(testScheduler)
                addHandler {
                    requestStarted = true
                    try {
                        awaitCancellation()
                    } finally {
                        requestCancelled = true
                    }
                }
            }
        }
        try {
            val repository = repository(KtorLibreWxrRadarApi(client))
            val observer = observe(repository)
            runCurrent()
            assertTrue(requestStarted)

            observer.cancel()
            runCurrent()

            assertTrue(requestCancelled)
            assertNull(repository.state.value.error)
        } finally {
            client.close()
        }
    }

    @Test
    fun returning_after_cancelled_request_starts_a_new_request_not_a_stuck_loading_job() = runTest {
        val api = FakeApi { call -> if (call == 1) awaitCancellation() else metadata }
        val repository = repository(api)
        val first = observe(repository)
        runCurrent()
        first.cancel()
        runCurrent()

        observe(repository)
        runCurrent()

        assertEquals(2, api.calls)
        assertEquals(metadata, repository.state.value.metadata)
        assertFalse(repository.state.value.isLoading)
    }

    @Test
    fun periodic_refresh_keeps_previous_frames_visible_and_stops_with_observer() = runTest {
        val next = CompletableDeferred<LibreWxrRadarMetadata>()
        val api = FakeApi { call -> if (call == 1) metadata else next.await() }
        val repository = repository(api)
        val observer = observe(repository)
        runCurrent()

        advanceTimeBy(9_999)
        runCurrent()
        assertEquals(1, api.calls)
        advanceTimeBy(1)
        runCurrent()

        assertEquals(2, api.calls)
        assertTrue(repository.state.value.isLoading)
        assertTrue(repository.state.value.isStale)
        assertEquals(metadata, repository.state.value.metadata)
        assertEquals(epoch, repository.state.value.fetchedAt)

        next.complete(newMetadata)
        runCurrent()
        assertEquals(newMetadata, repository.state.value.metadata)
        assertEquals(epoch + 10.seconds, repository.state.value.fetchedAt)
        assertFalse(repository.state.value.isStale)
        assertFalse(repository.state.value.isLoading)

        observer.cancel()
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(2, api.calls)
    }

    @Test
    fun transient_retry_is_exponential_delay_capped_and_attempt_bounded() = runTest {
        val attemptTimes = mutableListOf<Long>()
        val failure = IOException("offline")
        val api = FakeApi {
            attemptTimes += testScheduler.currentTime
            throw failure
        }
        val repository = repository(
            api,
            policy = policy.copy(maxRetries = 4, maxRetryDelay = 250.milliseconds)
        )
        observe(repository)
        runCurrent()
        assertTrue(repository.state.value.isLoading)

        advanceTimeBy(801)
        runCurrent()

        assertEquals(listOf(0L, 100L, 300L, 550L, 800L), attemptTimes)
        assertFailure(failure, repository.state.value.error)
        assertFalse(repository.state.value.isLoading)
        assertNull(repository.state.value.metadata)
        advanceTimeBy(9_998)
        runCurrent()
        assertEquals(5, api.calls)
    }

    @Test
    fun retry_backoff_is_cancelled_when_last_observer_leaves() = runTest {
        val api = FakeApi { throw IOException("offline") }
        val repository = repository(api)
        val observer = observe(repository)
        runCurrent()
        assertEquals(1, api.calls)

        observer.cancel()
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()

        assertEquals(1, api.calls)
        assertNull(repository.state.value.error)
    }

    @Test
    fun transient_http_statuses_retry_and_recover_with_no_error() = runTest {
        for (status in listOf(408, 429, 500, 503)) {
            val api = FakeApi { call ->
                if (call == 1) throw LibreWxrRadarApiException("temporary", status)
                metadata
            }
            val repository = repository(api)
            val observer = observe(repository)
            runCurrent()
            assertTrue(repository.state.value.isLoading)

            advanceTimeBy(100)
            runCurrent()

            assertEquals(2, api.calls)
            assertEquals(metadata, repository.state.value.metadata)
            assertNull(repository.state.value.error)
            assertFalse(repository.state.value.isStale)
            observer.cancel()
            runCurrent()
        }
    }

    @Test
    fun permanent_http_parsing_and_validation_failures_do_not_retry() = runTest {
        val failures = listOf(
            LibreWxrRadarApiException("bad request", 400),
            LibreWxrRadarApiException("unauthorized", 401),
            LibreWxrRadarApiException("forbidden", 403),
            LibreWxrRadarApiException("not found", 404),
            SerializationException("malformed JSON"),
            IllegalArgumentException("invalid tile host")
        )
        for (failure in failures) {
            val api = FakeApi { throw failure }
            val repository = repository(api)
            val observer = observe(repository)
            runCurrent()
            advanceTimeBy(1_000)
            runCurrent()

            assertEquals(1, api.calls)
            assertFailure(failure, repository.state.value.error)
            assertNull(repository.state.value.metadata)
            assertFalse(repository.state.value.isLoading)
            observer.cancel()
            runCurrent()
        }
    }

    @Test
    fun exhausted_failure_recovers_on_next_periodic_refresh_and_clears_error() = runTest {
        val failure = IOException("offline")
        val api = FakeApi { call -> if (call <= 2) throw failure else newMetadata }
        val repository = repository(api)
        observe(repository)
        runCurrent()
        advanceTimeBy(100)
        runCurrent()
        assertFailure(failure, repository.state.value.error)
        assertNull(repository.state.value.metadata)

        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(3, api.calls)
        assertEquals(newMetadata, repository.state.value.metadata)
        assertNull(repository.state.value.error)
        assertFalse(repository.state.value.isLoading)
        assertFalse(repository.state.value.isStale)
    }

    @Test
    fun fresh_persisted_cache_is_served_immediately_and_refresh_waits_for_its_remaining_lifetime() = runTest {
        val cached = CachedRadarMetadata(metadata, epoch - 9.seconds)
        val api = FakeApi { newMetadata }
        val repository = repository(api, MemoryCache(cached))
        observe(repository)
        runCurrent()

        assertEquals(metadata, repository.state.value.metadata)
        assertEquals(cached.fetchedAt, repository.state.value.fetchedAt)
        assertFalse(repository.state.value.isStale)
        assertFalse(repository.state.value.isLoading)
        assertEquals(0, api.calls)
        advanceTimeBy(999)
        runCurrent()
        assertEquals(0, api.calls)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, api.calls)
        assertEquals(newMetadata, repository.state.value.metadata)
    }

    @Test
    fun reobserving_uses_saved_cache_without_an_extra_request_and_still_refreshes_on_time() = runTest {
        val api = FakeApi { metadata }
        val repository = repository(api)
        val first = observe(repository)
        runCurrent()
        first.cancel()
        runCurrent()
        advanceTimeBy(2_000)
        runCurrent()

        observe(repository)
        runCurrent()
        assertEquals(metadata, repository.state.value.metadata)
        assertFalse(repository.state.value.isLoading)
        assertEquals(1, api.calls)

        advanceTimeBy(8_000)
        runCurrent()
        assertEquals(2, api.calls)
    }

    @Test
    fun old_or_future_dated_cache_is_stale_during_refresh_and_retained_unchanged_after_failure() = runTest {
        for (fetchedAt in listOf(epoch - 1.hours, epoch - 10.seconds, epoch + 1.hours)) {
            val cached = CachedRadarMetadata(metadata, fetchedAt)
            val cache = MemoryCache(cached)
            val response = CompletableDeferred<LibreWxrRadarMetadata>()
            val repository = repository(
                FakeApi { response.await() }, cache, policy.copy(maxRetries = 0), now = { epoch }
            )
            val observer = observe(repository)
            runCurrent()

            assertEquals(metadata, repository.state.value.metadata)
            assertEquals(fetchedAt, repository.state.value.fetchedAt)
            assertTrue(repository.state.value.isLoading)
            assertTrue(repository.state.value.isStale)
            assertNull(repository.state.value.error)

            val failure = IOException("offline")
            response.completeExceptionally(failure)
            runCurrent()

            assertEquals(metadata, repository.state.value.metadata)
            assertEquals(fetchedAt, repository.state.value.fetchedAt)
            assertFailure(failure, repository.state.value.error)
            assertTrue(repository.state.value.isStale)
            assertFalse(repository.state.value.isLoading)
            assertEquals(cached, cache.entry)
            assertEquals(0, cache.writes)
            observer.cancel()
            runCurrent()
        }
    }

    @Test
    fun empty_frames_are_unavailable_without_retry_or_replacing_usable_cache() = runTest {
        val cached = CachedRadarMetadata(metadata, epoch - 1.hours)
        val cache = MemoryCache(cached)
        val api = FakeApi { metadata.copy(frames = emptyList()) }
        val repository = repository(api, cache)
        observe(repository)
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(1, api.calls)
        assertTrue(repository.state.value.error is IllegalStateException)
        assertEquals(metadata, repository.state.value.metadata)
        assertTrue(repository.state.value.isStale)
        assertEquals(cached, cache.entry)
        assertEquals(0, cache.writes)
    }

    @Test
    fun persistence_failure_does_not_hide_live_success_or_its_later_in_memory_fallback() = runTest {
        val failure = IOException("offline")
        val api = FakeApi { call -> if (call == 1) metadata else throw failure }
        val cache = object : RadarMetadataCacheStore {
            override suspend fun get(provider: RadarProvider): CachedRadarMetadata? =
                throw IOException("read failed")
            override suspend fun put(entry: CachedRadarMetadata) { throw IOException("write failed") }
        }
        val repository = repository(api, cache, policy.copy(maxRetries = 0))
        observe(repository)
        runCurrent()

        assertEquals(metadata, repository.state.value.metadata)
        assertFalse(repository.state.value.isStale)
        assertNull(repository.state.value.error)

        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(metadata, repository.state.value.metadata)
        assertEquals(epoch, repository.state.value.fetchedAt)
        assertTrue(repository.state.value.isStale)
        assertFailure(failure, repository.state.value.error)
    }

    @Test
    fun in_memory_cache_survives_observer_restart_even_if_persistence_is_unavailable() = runTest {
        val api = FakeApi { metadata }
        val cache = object : RadarMetadataCacheStore {
            override suspend fun get(provider: RadarProvider): CachedRadarMetadata? =
                throw IOException("read failed")
            override suspend fun put(entry: CachedRadarMetadata) { throw IOException("write failed") }
        }
        val repository = repository(api, cache)
        val first = observe(repository)
        runCurrent()
        first.cancel()
        runCurrent()
        advanceTimeBy(2_000)
        runCurrent()

        observe(repository)
        runCurrent()

        assertEquals(metadata, repository.state.value.metadata)
        assertEquals(epoch, repository.state.value.fetchedAt)
        assertFalse(repository.state.value.isLoading)
        assertEquals(1, api.calls)
    }

    @Test
    fun source_cancellation_is_never_turned_into_failure_retry_or_cache_write() = runTest {
        val cancellation = CancellationException("request cancelled")
        val api = FakeApi { throw cancellation }
        val cache = MemoryCache()
        val repository = repository(api, cache)
        observe(repository)
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()

        assertEquals(1, api.calls)
        assertEquals(0, cache.writes)
        assertNull(repository.state.value.error)
    }

    @Test
    fun cache_cancellation_is_not_swallowed_for_reads_or_writes() = runTest {
        for (cancelRead in listOf(true, false)) {
            val cancellation = CancellationException("cache cancelled")
            var writes = 0
            val cache = object : RadarMetadataCacheStore {
                override suspend fun get(provider: RadarProvider): CachedRadarMetadata? {
                    if (cancelRead) throw cancellation
                    return null
                }
                override suspend fun put(entry: CachedRadarMetadata) {
                    writes++
                    throw cancellation
                }
            }
            val api = FakeApi { metadata }
            val repository = repository(api, cache)
            val observer = observe(repository)
            runCurrent()
            advanceTimeBy(60_000)
            runCurrent()

            assertEquals(if (cancelRead) 0 else 1, api.calls)
            assertEquals(if (cancelRead) 0 else 1, writes)
            assertNull(repository.state.value.error)
            observer.cancel()
            runCurrent()
        }
    }

    @Test
    fun request_timeout_cancels_each_hanging_attempt_and_exhausts_bounded_retries() = runTest {
        var cancelledAttempts = 0
        val api = FakeApi {
            try {
                awaitCancellation()
            } finally {
                cancelledAttempts++
            }
        }
        val repository = repository(api, policy = policy.copy(requestTimeout = 200.milliseconds))
        observe(repository)
        runCurrent()

        advanceTimeBy(500)
        runCurrent()

        assertEquals(2, api.calls)
        assertEquals(2, cancelledAttempts)
        assertTrue(repository.state.value.error is IOException)
        assertFalse(repository.state.value.isLoading)
        advanceTimeBy(9_999)
        runCurrent()
        assertEquals(2, api.calls)
    }

    @Test
    fun real_http_failures_remain_on_librewxr_endpoint_without_provider_substitution() = runTest {
        val urls = mutableListOf<String>()
        val client = HttpClient(MockEngine) {
            engine {
                dispatcher = StandardTestDispatcher(testScheduler)
                addHandler { request ->
                    urls += request.url.toString()
                    respond("unavailable", HttpStatusCode.ServiceUnavailable)
                }
            }
        }
        try {
            val repository = repository(KtorLibreWxrRadarApi(client))
            observe(repository)
            runCurrent()
            advanceTimeBy(100)
            runCurrent()

            assertEquals(
                List(2) { "https://api.librewxr.net/public/weather-maps.json" }, urls
            )
            val error = repository.state.value.error as LibreWxrRadarApiException
            assertEquals(503, error.statusCode)
            assertNull(repository.state.value.metadata)
            assertFalse(repository.state.value.isLoading)
        } finally {
            client.close()
        }
    }

    private fun TestScope.repository(
        api: LibreWxrRadarApi,
        cache: RadarMetadataCacheStore = MemoryCache(),
        policy: RadarRefreshPolicy = RadarRepositoryTest.policy,
        now: () -> Instant = { epoch + testScheduler.currentTime.milliseconds }
    ) = LibreWxrRadarRepository(
        api, cache, backgroundScope, policy, now, StandardTestDispatcher(testScheduler)
    )

    private fun TestScope.providerRepository(
        provider: RadarProvider,
        api: RadarMetadataApi,
        cache: RadarMetadataCacheStore = MemoryCache(),
        policy: RadarRefreshPolicy = RadarRepositoryTest.policy,
        now: () -> Instant = { epoch + testScheduler.currentTime.milliseconds }
    ) = RadarMetadataRepository(
        provider = provider,
        api = api,
        cache = cache,
        scope = backgroundScope,
        policy = policy,
        now = now,
        dispatcher = StandardTestDispatcher(testScheduler)
    )

    private fun assertFailure(expected: Exception, actual: Exception?) {
        // Coroutine stacktrace recovery can copy exceptions across suspension boundaries.
        assertEquals(expected.javaClass, actual?.javaClass)
        assertEquals(expected.message, actual?.message)
    }

    private fun TestScope.observe(repository: RadarRepository): Job = backgroundScope.launch(
        UnconfinedTestDispatcher(testScheduler)
    ) { repository.state.collect() }

    private class FakeApi(
        private val response: suspend (Int) -> LibreWxrRadarMetadata
    ) : LibreWxrRadarApi {
        var calls = 0
        override suspend fun metadata(): LibreWxrRadarMetadata = response(++calls)
    }

    private class FakeMetadataApi(
        private val response: suspend (Int) -> RadarMetadata
    ) : RadarMetadataApi {
        var calls = 0
        override suspend fun metadata(): RadarMetadata = response(++calls)
    }

    private class MemoryCache(var entry: CachedRadarMetadata? = null) : RadarMetadataCacheStore {
        var reads = 0
        var writes = 0
        val providersRead = mutableListOf<RadarProvider>()
        override suspend fun get(provider: RadarProvider): CachedRadarMetadata? {
            reads++
            providersRead += provider
            return entry?.takeIf { it.provider == provider }
        }
        override suspend fun put(entry: CachedRadarMetadata) {
            writes++
            this.entry = entry
        }
    }

    private companion object {
        val epoch = Instant.parse("2026-11-01T20:00:00Z")
        val metadata = LibreWxrRadarMetadata(
            host = "https://api.librewxr.net",
            frames = listOf(
                LibreWxrRadarFrame(1_793_563_200L, "/v2/radar/1793563200", LibreWxrRadarFrameType.OBSERVED),
                LibreWxrRadarFrame(1_793_563_800L, "/v2/radar/1793563800", LibreWxrRadarFrameType.FORECAST)
            )
        )
        val rainViewerMetadata = RadarMetadata(
            host = "https://tilecache.rainviewer.com",
            frames = listOf(
                LibreWxrRadarFrame(
                    1_793_563_200L,
                    "/v2/radar/1793563200",
                    LibreWxrRadarFrameType.OBSERVED,
                    RadarProvider.RAINVIEWER
                )
            ),
            provider = RadarProvider.RAINVIEWER
        )
        val newMetadata = metadata.copy(host = "https://tiles.librewxr.example.test")
        val policy = RadarRefreshPolicy(
            refreshInterval = 10.seconds,
            requestTimeout = 1.seconds,
            maxRetries = 1,
            initialRetryDelay = 100.milliseconds,
            maxRetryDelay = 400.milliseconds
        )
    }
}
