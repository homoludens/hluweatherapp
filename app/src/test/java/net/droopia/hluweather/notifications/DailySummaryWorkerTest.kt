package net.droopia.hluweather.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import kotlin.time.Clock

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DailySummaryWorkerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val location = WeatherLocation("belgrade", "Belgrade", 44.8176, 20.4633)

    @Test
    fun summary_contains_current_conditions_and_daily_extremes() = runBlocking {
        val publisher = SummaryPublisher()
        val summary = WeatherForecast(
            location = location,
            provider = WeatherProvider.OPEN_METEO,
            fetchedAt = Instant.parse("2026-09-10T07:00:00Z"),
            current = CurrentWeather(21.5, null, 60, null, 0.2, WeatherCondition.RAIN, true),
            hourly = emptyList(),
            daily = listOf(
                DayForecast(
                    date = LocalDate(2026, 9, 10),
                    condition = WeatherCondition.RAIN,
                    temperatureMin = 12.0,
                    temperatureMax = 25.0,
                    precipitation = 4.5,
                    sunrise = null,
                    sunset = null
                )
            ),
            moonPhase = 0.0,
            timezone = "Europe/Belgrade"
        )

        val result = build(
            settings = PersistedSettings(selectedLocationId = location.id, dailySummary = true),
            weather = SummaryWeatherRepository(ForecastLoad(summary)),
            publisher = publisher
        ).doWork()

        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
        assertEquals(1, publisher.summaries.size)
        assertTrue(publisher.summaries.single().contains("21.5"))
        assertTrue(publisher.summaries.single().contains("25.0"))
        assertTrue(publisher.summaries.single().contains("12.0"))
        assertTrue(publisher.summaries.single().contains("4.5"))
    }

    @Test
    fun summary_includes_the_daily_rain_window_and_amount() = runBlocking {
        val publisher = SummaryPublisher()
        val result = build(
            settings = PersistedSettings(selectedLocationId = location.id, dailySummary = true),
            weather = SummaryWeatherRepository(
                ForecastLoad(
                    summaryForecast().copy(
                        hourly = listOf(
                            hour("2026-09-10T12:00:00Z", 0.0),
                            hour("2026-09-10T13:00:00Z", 1.2),
                            hour("2026-09-10T14:00:00Z", 3.3),
                            hour("2026-09-10T15:00:00Z", 0.0)
                        )
                    )
                )
            ),
            publisher = publisher
        ).doWork()

        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
        assertEquals(1, publisher.summaries.size)
        assertTrue(publisher.summaries.single().contains("Rain 15:00-16:00, 4.5 mm."))
        assertTrue(!publisher.summaries.single().contains("probability"))
    }

    @Test
    fun successful_summary_schedules_the_following_delivery() = runBlocking {
        val scheduler = SummaryScheduler()
        val result = build(
            settings = PersistedSettings(selectedLocationId = location.id, dailySummary = true),
            weather = SummaryWeatherRepository(ForecastLoad(summaryForecast())),
            scheduler = scheduler
        ).doWork()

        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
        assertEquals(1, scheduler.successors.size)
        assertEquals(location.id, scheduler.successors.single().selectedLocationId)
    }

    @Test
    fun disabled_summary_skips_without_fetching() = runBlocking {
        val weather = SummaryWeatherRepository()
        val result = build(
            settings = PersistedSettings(selectedLocationId = location.id),
            weather = weather
        ).doWork()

        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
        assertEquals(0, weather.calls)
    }

    @Test
    fun missing_forecast_retries_and_does_not_schedule_a_successor() = runBlocking {
        val scheduler = SummaryScheduler()
        val result = build(
            settings = PersistedSettings(selectedLocationId = location.id, dailySummary = true),
            weather = SummaryWeatherRepository(error = IOException("offline")),
            scheduler = scheduler
        ).doWork()

        assertEquals(androidx.work.ListenableWorker.Result.retry(), result)
        assertTrue(scheduler.successors.isEmpty())
    }

    @Test
    fun summary_uses_delivery_clock_date_in_forecast_timezone() = runBlocking {
        val publisher = SummaryPublisher()
        val forecast = summaryForecast().copy(
            fetchedAt = Instant.parse("2026-09-09T23:30:00Z"),
            daily = listOf(day(LocalDate(2026, 9, 10)))
        )

        val result = build(
            settings = PersistedSettings(selectedLocationId = location.id, dailySummary = true),
            weather = SummaryWeatherRepository(ForecastLoad(forecast)),
            publisher = publisher,
            clock = FixedSummaryClock(Instant.parse("2026-09-10T00:30:00Z"))
        ).doWork()

        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
        assertEquals(1, publisher.summaries.size)
        assertTrue(publisher.summaries.single().contains("25.0"))
    }

    @Test
    fun summary_with_no_delivery_day_succeeds_without_posting() = runBlocking {
        val publisher = SummaryPublisher()
        val scheduler = SummaryScheduler()
        val result = build(
            settings = PersistedSettings(selectedLocationId = location.id, dailySummary = true),
            weather = SummaryWeatherRepository(
                ForecastLoad(summaryForecast().copy(daily = listOf(day(LocalDate(2026, 9, 11)))))
            ),
            publisher = publisher,
            scheduler = scheduler,
            clock = FixedSummaryClock(Instant.parse("2026-09-10T07:00:00Z"))
        ).doWork()

        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
        assertTrue(publisher.summaries.isEmpty())
        assertEquals(1, scheduler.successors.size)
    }

    @Test
    fun summary_uses_configured_units_and_preserves_unknown_precipitation() = runBlocking {
        val publisher = SummaryPublisher()
        val result = build(
            settings = PersistedSettings(
                selectedLocationId = location.id,
                dailySummary = true,
                temperatureUnit = net.droopia.hluweather.ui.settings.TemperatureUnit.FAHRENHEIT,
                precipitationUnit = net.droopia.hluweather.ui.settings.PrecipitationUnit.INCH
            ),
            weather = SummaryWeatherRepository(
                ForecastLoad(summaryForecast().copy(daily = listOf(day(LocalDate(2026, 9, 10), null))))
            ),
            publisher = publisher,
            clock = FixedSummaryClock(Instant.parse("2026-09-10T07:00:00Z"))
        ).doWork()

        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
        assertTrue(publisher.summaries.single().contains("77.0°F"))
        assertTrue(publisher.summaries.single().contains("precipitation unavailable"))
        assertTrue(!publisher.summaries.single().contains("0.0"))
    }

    private fun build(
        settings: PersistedSettings,
        weather: SummaryWeatherRepository,
        publisher: SummaryPublisher = SummaryPublisher(),
        scheduler: SummaryScheduler = SummaryScheduler(),
        clock: Clock = FixedSummaryClock(Instant.parse("2026-09-10T07:00:00Z"))
    ): DailySummaryWorker = TestListenableWorkerBuilder.from(context, DailySummaryWorker::class.java)
        .setWorkerFactory(
            AppWorkerFactory(
                NotificationWorkerDependencies(
                    settingsRepository = SummarySettingsRepository(settings),
                    locationRepository = SummaryLocationRepository(listOf(location)),
                    weatherRepository = weather,
                    publisher = publisher,
                    stateRepository = SummaryStateRepository(),
                    scheduler = scheduler,
                    permissionChecker = NotificationPermissionChecker { true },
                    clock = clock
                )
            )
        )
        .build()

    private fun summaryForecast() = WeatherForecast(
        location = location,
        provider = WeatherProvider.OPEN_METEO,
        fetchedAt = Instant.parse("2026-09-10T07:00:00Z"),
        current = CurrentWeather(21.5, null, 60, null, 0.2, WeatherCondition.RAIN, true),
        hourly = emptyList(),
        daily = listOf(
            DayForecast(
                LocalDate(2026, 9, 10),
                WeatherCondition.RAIN,
                12.0,
                25.0,
                4.5,
                null,
                null
            )
        ),
        moonPhase = 0.0,
        timezone = "Europe/Belgrade"
    )

    private fun day(date: LocalDate, precipitation: Double? = 4.5) = DayForecast(
        date,
        WeatherCondition.RAIN,
        12.0,
        25.0,
        precipitation,
        null,
        null
    )

    private fun hour(time: String, precipitation: Double) = HourForecast(
        time = Instant.parse(time),
        temperature = 20.0,
        apparentTemperature = null,
        humidity = null,
        dewPoint = null,
        precipitation = precipitation,
        precipitationProbability = null,
        condition = WeatherCondition.RAIN,
        isDay = true
    )
}

private class FixedSummaryClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}

private class SummarySettingsRepository(
    settings: PersistedSettings
) : SettingsRepository {
    override val settings: Flow<PersistedSettings> = flowOf(settings)
    override suspend fun save(settings: PersistedSettings) = Unit
}

private class SummaryLocationRepository(
    saved: List<WeatherLocation>
) : LocationRepository {
    override val locations: Flow<List<WeatherLocation>> = flowOf(saved)
    override val activeLocation: Flow<ActiveLocation?> = flowOf(ActiveLocation.Saved(saved.first()))
    override val locationMode: Flow<LocationMode> = flowOf(LocationMode.SAVED_LOCATION)
    override suspend fun add(location: WeatherLocation) = Unit
    override suspend fun update(location: WeatherLocation) = Unit
    override suspend fun delete(id: String) = Unit
    override suspend fun selectSaved(id: String) = Unit
    override suspend fun setTrackMe(enabled: Boolean) = Unit
    override suspend fun setCurrentLocation(point: GeoPoint, altitude: Int?) = Unit
}

private class SummaryWeatherRepository(
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

private class SummaryPublisher : WeatherNotificationPublisher {
    val summaries = mutableListOf<String>()
    override fun publishAlert(event: WeatherAlertEvent, title: String, body: String) = Unit
    override fun publishDailySummary(title: String, body: String) {
        summaries += body
    }
}

private class SummaryScheduler : NotificationScheduler {
    val successors = mutableListOf<PersistedSettings>()
    override fun reconcile(settings: PersistedSettings, activeLocation: ActiveLocation?) = Unit
    override fun enqueueNextDailySummary(settings: PersistedSettings, activeLocation: ActiveLocation.Saved) {
        successors += settings
    }
}

private class SummaryStateRepository : NotificationStateRepository {
    override suspend fun wasDelivered(eventKey: String) = false
    override suspend fun markDelivered(eventKey: String) = Unit
}
