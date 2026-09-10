package net.droopia.hluweather.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import net.droopia.hluweather.data.model.WeatherProvider
import net.droopia.hluweather.ui.settings.PersistedSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationSchedulerTest {

    private lateinit var context: Context
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: WorkManagerNotificationScheduler

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().build()
        )
        workManager = WorkManager.getInstance(context)
        scheduler = WorkManagerNotificationScheduler(
            workManager = workManager,
            alertWorkerClass = TestAlertWorker::class.java,
            summaryWorkerClass = TestSummaryWorker::class.java,
            timeZone = TimeZone.of("Europe/Belgrade")
        )
    }

    @After
    fun tearDown() {
        workManager.cancelAllWork()
    }

    @Test
    fun weather_alert_event_key_contains_provider_location_and_period() {
        val event = WeatherAlertEvent(
            provider = WeatherProvider.OPEN_METEO,
            locationId = "belgrade",
            periodStart = kotlinx.datetime.Instant.fromEpochSeconds(1_780_000_000)
        )

        assertEquals("weather-alert:OPEN_METEO:belgrade:1780000000", event.key)
    }

    @Test
    fun delay_until_summary_time_is_zero_at_the_selected_local_time() {
        val localDateTime = LocalDateTime(2026, 6, 1, 8, 0)

        assertEquals(Duration.ZERO, scheduler.delayUntil(LocalTime(8, 0), localDateTime))
    }

    @Test
    fun delay_until_summary_time_uses_the_following_day_after_delivery_time() {
        val localDateTime = LocalDateTime(2026, 6, 1, 9, 0)

        assertEquals(23.hours, scheduler.delayUntil(LocalTime(8, 0), localDateTime))
    }

    @Test
    fun delay_until_summary_time_accounts_for_a_dst_transition() {
        val localDateTime = LocalDateTime(2026, 3, 28, 9, 0)

        assertEquals(22.hours, scheduler.delayUntil(LocalTime(8, 0), localDateTime))
    }

    @Test
    fun enabled_alerts_and_summary_use_independent_unique_work() = runBlocking {
        scheduler.reconcile(
            PersistedSettings(
                selectedLocationId = "belgrade",
                weatherAlerts = true,
                dailySummary = true
            )
        )

        val alert = workManager.getWorkInfosForUniqueWork(NotificationScheduler.WEATHER_ALERT_WORK_NAME).get()
        val summary = workManager.getWorkInfosForUniqueWork(NotificationScheduler.DAILY_SUMMARY_WORK_NAME).get()

        assertEquals(1, alert.size)
        assertEquals(1, summary.size)
        assertEquals(NetworkType.CONNECTED, alert.single().constraints.requiredNetworkType)
        assertEquals(androidx.work.WorkInfo.State.ENQUEUED, alert.single().state)
        assertEquals(androidx.work.WorkInfo.State.ENQUEUED, summary.single().state)
    }

    @Test
    fun disabled_setting_cancels_only_its_work() = runBlocking {
        scheduler.reconcile(
            PersistedSettings(
                selectedLocationId = "belgrade",
                weatherAlerts = true,
                dailySummary = true
            )
        )
        scheduler.reconcile(
            PersistedSettings(
                selectedLocationId = "belgrade",
                weatherAlerts = false,
                dailySummary = true
            )
        )

        val alert = workManager.getWorkInfosForUniqueWork(NotificationScheduler.WEATHER_ALERT_WORK_NAME).get()
        val summary = workManager.getWorkInfosForUniqueWork(NotificationScheduler.DAILY_SUMMARY_WORK_NAME).get()

        assertTrue(alert.single().state.isFinished)
        assertFalse(summary.single().state.isFinished)
    }

    @Test
    fun track_me_or_missing_selected_location_cancels_both_work_sequences() = runBlocking {
        scheduler.reconcile(
            PersistedSettings(
                selectedLocationId = "belgrade",
                weatherAlerts = true,
                dailySummary = true
            )
        )
        scheduler.reconcile(
            PersistedSettings(
                selectedLocationId = "belgrade",
                trackMeEnabled = true,
                weatherAlerts = true,
                dailySummary = true
            )
        )

        val alert = workManager.getWorkInfosForUniqueWork(NotificationScheduler.WEATHER_ALERT_WORK_NAME).get()
        val summary = workManager.getWorkInfosForUniqueWork(NotificationScheduler.DAILY_SUMMARY_WORK_NAME).get()

        assertTrue(alert.single().state.isFinished)
        assertTrue(summary.single().state.isFinished)
    }

    @Test
    fun notification_channels_are_low_importance_and_independent() {
        NotificationChannels.create(context)

        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        val alert = manager.getNotificationChannel(NotificationChannels.WEATHER_ALERTS_CHANNEL_ID)
        val summary = manager.getNotificationChannel(NotificationChannels.DAILY_SUMMARY_CHANNEL_ID)

        assertEquals(android.app.NotificationManager.IMPORTANCE_LOW, alert.importance)
        assertEquals(android.app.NotificationManager.IMPORTANCE_LOW, summary.importance)
        assertFalse(alert.id == summary.id)
    }

    class TestAlertWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
        override fun doWork(): Result = Result.success()
    }

    class TestSummaryWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
        override fun doWork(): Result = Result.success()
    }

}
