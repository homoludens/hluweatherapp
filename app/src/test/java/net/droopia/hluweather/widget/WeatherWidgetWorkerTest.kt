package net.droopia.hluweather.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import java.io.IOException
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
import net.droopia.hluweather.data.model.*
import net.droopia.hluweather.data.repository.*
import net.droopia.hluweather.notifications.AppWorkerFactory
import net.droopia.hluweather.ui.settings.PersistedSettings
import net.droopia.hluweather.ui.settings.SettingsRepository
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeatherWidgetWorkerTest {
    private val now = Instant.parse("2026-10-28T12:00:00Z")
    private val first = WeatherLocation("first", "First", 43.0, 20.0, 10)
    private val second = WeatherLocation("second", "Second", 44.0, 21.0, 20)

    @Test fun application_worker_factory_routes_widget_workers_to_injected_dependencies() = runTest {
        val fixture = Fixture()
        val factory = AppWorkerFactory(
            dependenciesProvider = { error("Widget worker creation must not resolve notification dependencies") },
            widgetDependenciesProvider = { fixture }
        )

        val worker = TestListenableWorkerBuilder.from(
            ApplicationProvider.getApplicationContext<Context>(),
            WeatherWidgetWorker::class.java
        ).setWorkerFactory(factory).build()

        assertTrue(worker is WeatherWidgetWorker)
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertEquals(listOf("first"), fixture.calls)
        assertTrue(fixture.deliveries.isNotEmpty())
    }

    @Test fun manual_preserves_cache_during_loading_and_updates_every_instance_on_success() = runTest {
        val f = Fixture()
        val old = f.forecast(first, now - 2.hours)
        f.cache[first.id] = ForecastLoad(old, true)
        val gate = CompletableDeferred<Unit>()
        f.fetch = { location -> gate.await(); ForecastLoad(f.forecast(location)) }
        val work = async { f.worker(manual = true).doWork() }
        runCurrent()
        val loading = f.snapshot() as WeatherWidgetSnapshot.Available
        assertTrue(loading.refresh.isRefreshing)
        assertEquals(old.fetchedAt, loading.weather.lastWeatherUpdate)
        assertEquals(listOf(1, 2, 3), f.deliveries)
        gate.complete(Unit)
        assertEquals(ListenableWorker.Result.success(), work.await())
        val done = f.snapshot() as WeatherWidgetSnapshot.Available
        assertFalse(done.refresh.isRefreshing)
        assertEquals(now, done.weather.lastWeatherUpdate)
        assertTrue(f.deliveries.count { it == 1 } >= 2)
        assertEquals(f.deliveries.count { it == 1 }, f.deliveries.count { it == 3 })
    }

    @Test fun periodic_reuses_fresh_cache_but_manual_fetches() = runTest {
        val f = Fixture()
        f.cache[first.id] = ForecastLoad(f.forecast(first))
        assertEquals(ListenableWorker.Result.success(), f.worker().doWork())
        assertEquals(0, f.calls.size)
        assertEquals(ListenableWorker.Result.success(), f.worker(manual = true).doWork())
        assertEquals(listOf("first"), f.calls)
    }

    @Test fun manual_retry_reconciles_current_fresh_cache_without_forcing_another_fetch() = runTest {
        val f = Fixture()
        f.cache[first.id] = ForecastLoad(f.forecast(first))
        assertEquals(ListenableWorker.Result.success(), f.worker(manual = true, attempts = 1).doWork())
        assertTrue(f.calls.isEmpty())
    }

    @Test fun stale_fallback_and_io_failure_retain_cache_and_retry_only_twice() = runTest {
        val f = Fixture()
        val old = f.forecast(first, now - 2.hours)
        f.cache[first.id] = ForecastLoad(old, true)
        f.fetch = { ForecastLoad(old, true) }
        assertEquals(ListenableWorker.Result.retry(), f.worker(manual = true).doWork())
        var snapshot = f.snapshot() as WeatherWidgetSnapshot.Available
        assertTrue(snapshot.refresh.lastAttemptFailed)
        assertFalse(snapshot.refresh.isRefreshing)
        assertEquals(old.fetchedAt, snapshot.weather.lastWeatherUpdate)
        f.fetch = { throw IOException("offline") }
        assertEquals(ListenableWorker.Result.retry(), f.worker(manual = true, attempts = 1).doWork())
        assertEquals(ListenableWorker.Result.success(), f.worker(manual = true, attempts = 2).doWork())
        snapshot = f.snapshot() as WeatherWidgetSnapshot.Available
        assertEquals(old.fetchedAt, snapshot.weather.lastWeatherUpdate)
        assertTrue(snapshot.weather.isStale)
        assertFalse(snapshot.refresh.isRefreshing)
    }

    @Test fun permanent_failure_and_no_location_finish_without_retry() = runTest {
        val f = Fixture()
        f.fetch = { throw IllegalArgumentException("invalid request") }
        assertEquals(ListenableWorker.Result.success(), f.worker(manual = true).doWork())
        val unavailable = f.snapshot() as WeatherWidgetSnapshot.NoWeather
        assertTrue(unavailable.refresh.lastAttemptFailed)
        assertFalse(unavailable.refresh.isRefreshing)
        f.active.value = null
        f.locationsValue.value = emptyList()
        assertEquals(ListenableWorker.Result.success(), f.worker(manual = true).doWork())
        assertEquals(1, f.calls.size)
    }

    @Test fun transient_exception_without_cache_delivers_unavailable_state() = runTest {
        val f = Fixture()
        f.fetch = { throw IOException("offline") }
        assertEquals(ListenableWorker.Result.retry(), f.worker(manual = true).doWork())
        val snapshot = f.snapshot() as WeatherWidgetSnapshot.NoWeather
        assertTrue(snapshot.refresh.lastAttemptFailed)
        assertFalse(snapshot.refresh.isRefreshing)
        assertEquals(3, f.deliveries.count { it == 1 })
        assertEquals(3, f.deliveries.count { it == 2 })
        assertEquals(3, f.deliveries.count { it == 3 })
    }

    @Test fun cancellation_cleans_loading_delivers_cache_and_rethrows() = runTest {
        val f = Fixture()
        val old = f.forecast(first, now - 2.hours)
        f.cache[first.id] = ForecastLoad(old, true)
        val gate = CompletableDeferred<Unit>()
        f.fetch = { gate.await(); ForecastLoad(f.forecast(it)) }
        var cancellationPropagated = false
        val job = launch {
            try {
                f.worker(manual = true).doWork()
            } catch (cancelled: CancellationException) {
                cancellationPropagated = true
                throw cancelled
            }
        }
        runCurrent()
        assertTrue((f.snapshot() as WeatherWidgetSnapshot.Available).refresh.isRefreshing)
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertTrue(cancellationPropagated)
        val snapshot = f.snapshot() as WeatherWidgetSnapshot.Available
        assertFalse(snapshot.refresh.isRefreshing)
        assertEquals(old.fetchedAt, snapshot.weather.lastWeatherUpdate)
        assertEquals(2, f.deliveries.count { it == 1 })
    }

    @Test fun obsolete_completion_reconciles_latest_without_wrong_weather() = runTest {
        val f = Fixture()
        val gate = CompletableDeferred<Unit>()
        f.fetch = { location -> if (location.id == first.id) gate.await(); ForecastLoad(f.forecast(location)) }
        val work = async { f.worker(manual = true).doWork() }
        runCurrent()
        f.active.value = ActiveLocation.Saved(second)
        gate.complete(Unit)
        assertEquals(ListenableWorker.Result.success(), work.await())
        assertEquals(listOf("first", "second"), f.calls)
        val snapshot = f.snapshot() as WeatherWidgetSnapshot.Available
        assertEquals("Second", snapshot.weather.locationName)
        assertEquals(second.id, snapshot.request.let { (it.location as ActiveLocation.Saved).location.id })
        assertTrue(f.cache.containsKey("first"))
    }

    @Test fun repeatedly_changing_selection_defers_latest_after_bounded_retries() = runTest {
        val f = Fixture()
        f.fetch = { location ->
            f.active.value = ActiveLocation.Saved(if (location.id == first.id) second else first)
            ForecastLoad(f.forecast(location))
        }
        assertEquals(ListenableWorker.Result.retry(), f.worker(manual = true).doWork())
        f.cache.clear()
        assertEquals(ListenableWorker.Result.success(), f.worker(manual = true, attempts = 2).doWork())
        assertEquals(1, f.deferred)
    }

    private inner class Fixture : WeatherWidgetWorkerDependencies {
        val active = MutableStateFlow<ActiveLocation?>(ActiveLocation.Saved(first))
        val locationsValue = MutableStateFlow(listOf(first, second))
        val cache = mutableMapOf<String, ForecastLoad>()
        val calls = mutableListOf<String>()
        val deliveries = mutableListOf<Int>()
        var deferred = 0
        var fetch: suspend (WeatherLocation) -> ForecastLoad = { ForecastLoad(forecast(it)) }
        private val settings = object : SettingsRepository {
            override val settings = flowOf(PersistedSettings())
            override suspend fun save(settings: PersistedSettings) = Unit
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
                calls += saved.id
                return fetch(saved).also { if (!it.isStale) cache[saved.id] = it }
            }
            override suspend fun getLastCachedForecast(provider: WeatherProvider, location: ActiveLocation) =
                cache[(location as ActiveLocation.Saved).location.id]
            override suspend fun clearCache() { cache.clear() }
        }
        private val metadata = object : WidgetRefreshMetadataStore {
            val statuses = MutableStateFlow<Map<WeatherWidgetRequestIdentity, WidgetRefreshStatus>>(emptyMap())
            override fun observe(identity: WeatherWidgetRequestIdentity, now: () -> Instant) = statuses.map { it[identity] ?: WidgetRefreshStatus() }
            override suspend fun start(identity: WeatherWidgetRequestIdentity, now: Instant): WidgetRefreshAttempt {
                statuses.value = statuses.value + (identity to WidgetRefreshStatus(startedAt = now, isRefreshing = true))
                return WidgetRefreshAttempt("attempt")
            }
            override suspend fun finish(identity: WeatherWidgetRequestIdentity, attempt: WidgetRefreshAttempt, now: Instant, failed: Boolean) {
                statuses.value = statuses.value + (identity to statuses.value.getValue(identity).copy(endedAt = now, isRefreshing = false, lastAttemptFailed = failed))
            }
        }
        private val widgets = WeatherWidgetRepository(settings, locations, weather, object : Clock { override fun now() = now }, metadata)
        override val coordinator = WeatherWidgetRefreshCoordinator(widgets, weather) { updateAllWidgets() }
        override suspend fun updateAllWidgets() { deliveries += listOf(1, 2, 3) }
        override suspend fun deferSelectionReconciliation() { deferred++ }
        suspend fun snapshot() = widgets.loadCachedState()
        fun forecast(location: WeatherLocation, fetchedAt: Instant = now) = WeatherForecast(
            location, WeatherProvider.OPEN_METEO, fetchedAt,
            CurrentWeather(23.0, 24.0, null, null, null, WeatherCondition.CLEAR, true),
            emptyList(), emptyList(), 0.0, "UTC"
        )
        fun worker(manual: Boolean = false, attempts: Int = 0): WeatherWidgetWorker {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val factory = object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                    WeatherWidgetWorker(appContext, workerParameters, this@Fixture)
            }
            return TestListenableWorkerBuilder.from(context, WeatherWidgetWorker::class.java)
                .setWorkerFactory(factory)
                .setInputData(Data.Builder().putBoolean(WeatherWidgetWorker.INPUT_MANUAL, manual).build())
                .setRunAttemptCount(attempts).build()
        }
    }
}
