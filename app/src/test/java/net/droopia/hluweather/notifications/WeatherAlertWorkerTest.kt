package net.droopia.hluweather.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import net.droopia.hluweather.data.model.ActiveLocation
import net.droopia.hluweather.data.model.CurrentWeather
import net.droopia.hluweather.data.model.DayForecast
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.HourForecast
import net.droopia.hluweather.data.model.LocationMode
import net.droopia.hluweather.data.model.WeatherCondition
import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherProvider
import net.droopia.hluweather.data.repository.ForecastLoad
import net.droopia.hluweather.data.repository.LocationRepository
import net.droopia.hluweather.data.repository.WeatherRepository
import net.droopia.hluweather.ui.settings.PersistedSettings
import net.droopia.hluweather.ui.settings.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import kotlin.time.Duration.Companion.hours
import kotlin.time.Clock

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeatherAlertWorkerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val location = WeatherLocation("belgrade", "Belgrade", 44.8176, 20.4633)
    private val now = Instant.parse("2026-09-10T10:00:00Z")

    @Test
    fun disabled_alerts_skip_without_loading_a_forecast() = runBlocking {
        val weather = RecordingWeatherRepository()

        val result = worker(
            settings = PersistedSettings(selectedLocationId = location.id),
            weatherRepository = weather
        ).doWork()

        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
        assertEquals(0, weather.calls)
    }

    @Test
    fun track_me_and_missing_saved_location_skip_successfully() = runBlocking {
        val weather = RecordingWeatherRepository()

        val trackMeResult = worker(
            settings = PersistedSettings(
                selectedLocationId = location.id,
                trackMeEnabled = true,
                weatherAlerts = true
            ),
            locationMode = LocationMode.TRACK_ME,
            weatherRepository = weather
        ).doWork()
        val missingResult = worker(
            settings = PersistedSettings(selectedLocationId = "missing", weatherAlerts = true),
            weatherRepository = weather
        ).doWork()

        assertEquals(androidx.work.ListenableWorker.Result.success(), trackMeResult)
        assertEquals(androidx.work.ListenableWorker.Result.success(), missingResult)
        assertEquals(0, weather.calls)
    }

    @Test
    fun missing_live_or_matching_cached_forecast_retries() = runBlocking {
        val weather = RecordingWeatherRepository(error = IOException("offline"))

        val result = worker(
            settings = PersistedSettings(selectedLocationId = location.id, weatherAlerts = true),
            weatherRepository = weather
        ).doWork()

        assertEquals(androidx.work.ListenableWorker.Result.retry(), result)
    }

    @Test
    fun only_thunderstorms_in_the_next_24_hours_are_posted_once_per_event() = runBlocking {
        val forecast = forecast(
            hours = listOf(
                hour(now + 1.hours, WeatherCondition.THUNDERSTORM),
                hour(now + 2.hours, WeatherCondition.THUNDERSTORM),
                hour(now + 25.hours, WeatherCondition.THUNDERSTORM),
                hour(now + 3.hours, WeatherCondition.RAIN)
            )
        )
        val publisher = RecordingPublisher()
        val state = RecordingStateRepository()
        val dependencies = dependencies(
            settings = PersistedSettings(selectedLocationId = location.id, weatherAlerts = true),
            weatherRepository = RecordingWeatherRepository(ForecastLoad(forecast)),
            publisher = publisher,
            state = state
        )

        assertEquals(androidx.work.ListenableWorker.Result.success(), build(dependencies).doWork())
        assertEquals(androidx.work.ListenableWorker.Result.success(), build(dependencies).doWork())

        assertEquals(2, publisher.alerts.size)
        assertEquals(2, state.marked.size)
        assertTrue(publisher.alerts.all { it.provider == WeatherProvider.OPEN_METEO })
        assertFalse(publisher.alerts.any { it.periodStart == now + 25.hours })
    }

    @Test
    fun matching_cached_forecast_can_deliver_an_alert() = runBlocking {
        val publisher = RecordingPublisher()
        val forecast = forecast(hours = listOf(hour(now + 1.hours, WeatherCondition.THUNDERSTORM)))
        val result = build(
            dependencies(
                settings = PersistedSettings(selectedLocationId = location.id, weatherAlerts = true),
                weatherRepository = RecordingWeatherRepository(ForecastLoad(forecast, isStale = true)),
                publisher = publisher
            )
        ).doWork()

        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
        assertEquals(1, publisher.alerts.size)
    }

    @Test
    fun denied_notification_permission_skips_without_fetching_or_posting() = runBlocking {
        val weather = RecordingWeatherRepository()
        val publisher = RecordingPublisher()

        val result = worker(
            settings = PersistedSettings(selectedLocationId = location.id, weatherAlerts = true),
            weatherRepository = weather,
            publisher = publisher,
            permissionGranted = false
        ).doWork()

        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
        assertEquals(0, weather.calls)
        assertEquals(0, publisher.alerts.size)
    }

    @Test
    fun failed_post_fails_without_marking_the_alert_delivered() = runBlocking {
        val publisher = RecordingPublisher(failures = 1)
        val state = RecordingStateRepository()
        val dependencies = dependencies(
            settings = PersistedSettings(selectedLocationId = location.id, weatherAlerts = true),
            weatherRepository = RecordingWeatherRepository(
                ForecastLoad(forecast(hours = listOf(hour(now + 1.hours, WeatherCondition.THUNDERSTORM))))
            ),
            publisher = publisher,
            state = state
        )

        assertEquals(androidx.work.ListenableWorker.Result.failure(), build(dependencies).doWork())
        assertTrue(state.marked.isEmpty())
    }

    private fun worker(
        settings: PersistedSettings,
        locationMode: LocationMode = LocationMode.SAVED_LOCATION,
        weatherRepository: RecordingWeatherRepository,
        publisher: RecordingPublisher = RecordingPublisher(),
        permissionGranted: Boolean = true
    ): WeatherAlertWorker = build(
        dependencies(
            settings = settings,
            locationMode = locationMode,
            weatherRepository = weatherRepository,
            publisher = publisher,
            permissionGranted = permissionGranted
        )
    )

    private fun build(dependencies: NotificationWorkerDependencies): WeatherAlertWorker =
        TestListenableWorkerBuilder.from(context, WeatherAlertWorker::class.java)
            .setWorkerFactory(AppWorkerFactory(dependencies))
            .build()

    private fun dependencies(
        settings: PersistedSettings,
        locationMode: LocationMode = LocationMode.SAVED_LOCATION,
        weatherRepository: RecordingWeatherRepository,
        publisher: RecordingPublisher,
        state: RecordingStateRepository = RecordingStateRepository(),
        permissionGranted: Boolean = true
    ) = NotificationWorkerDependencies(
        settingsRepository = TestSettingsRepository(settings),
        locationRepository = TestLocationRepository(listOf(location), locationMode),
        weatherRepository = weatherRepository,
        publisher = publisher,
        stateRepository = state,
        scheduler = RecordingScheduler(),
        permissionChecker = NotificationPermissionChecker { permissionGranted },
        clock = FixedClock(now)
    )

    private fun forecast(hours: List<HourForecast>) = WeatherForecast(
        location = location,
        provider = WeatherProvider.OPEN_METEO,
        fetchedAt = now,
        current = CurrentWeather(20.0, null, 50, null, 0.0, WeatherCondition.CLEAR, true),
        hourly = hours,
        daily = emptyList(),
        moonPhase = 0.0
    )

    private fun hour(time: Instant, condition: WeatherCondition) = HourForecast(
        time = time,
        temperature = 20.0,
        apparentTemperature = null,
        humidity = 50,
        dewPoint = null,
        precipitation = 0.0,
        precipitationProbability = null,
        condition = condition,
        isDay = true
    )
}

private class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}

private class TestSettingsRepository(
    settings: PersistedSettings
) : SettingsRepository {
    override val settings: Flow<PersistedSettings> = flowOf(settings)
    override suspend fun save(settings: PersistedSettings) = Unit
}

private class TestLocationRepository(
    saved: List<WeatherLocation>,
    mode: LocationMode
) : LocationRepository {
    override val locations: Flow<List<WeatherLocation>> = flowOf(saved)
    override val activeLocation: Flow<ActiveLocation?> = flowOf(null)
    override val locationMode: Flow<LocationMode> = flowOf(mode)
    override suspend fun add(location: WeatherLocation) = Unit
    override suspend fun update(location: WeatherLocation) = Unit
    override suspend fun delete(id: String) = Unit
    override suspend fun selectSaved(id: String) = Unit
    override suspend fun setTrackMe(enabled: Boolean) = Unit
    override suspend fun setCurrentLocation(point: GeoPoint, altitude: Int?) = Unit
}

private class RecordingWeatherRepository(
    private val result: ForecastLoad? = null,
    private val error: Throwable? = null
) : WeatherRepository {
    var calls = 0
    override suspend fun getForecast(provider: WeatherProvider, location: ActiveLocation): ForecastLoad {
        calls++
        error?.let { throw it }
        return result ?: error("forecast result not configured")
    }
    override suspend fun clearCache() = Unit
}

private class RecordingPublisher(
    private var failures: Int = 0
) : WeatherNotificationPublisher {
    val alerts = mutableListOf<WeatherAlertEvent>()
    override fun publishAlert(event: WeatherAlertEvent, title: String, body: String) {
        if (failures > 0) {
            failures--
            throw IOException("notification post failed")
        }
        alerts += event
    }
    override fun publishDailySummary(title: String, body: String) = Unit
}

private class RecordingStateRepository : NotificationStateRepository {
    private val delivered = mutableSetOf<String>()
    val marked = mutableListOf<String>()
    override suspend fun wasDelivered(eventKey: String) = eventKey in delivered
    override suspend fun markDelivered(eventKey: String) {
        delivered += eventKey
        marked += eventKey
    }
}

private class RecordingScheduler : NotificationScheduler {
    override fun reconcile(settings: PersistedSettings) = Unit
    override fun enqueueNextDailySummary(settings: PersistedSettings) = Unit
}
