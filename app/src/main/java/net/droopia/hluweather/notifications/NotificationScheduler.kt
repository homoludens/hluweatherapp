package net.droopia.hluweather.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration

interface NotificationScheduler {
    fun reconcile(settings: net.droopia.hluweather.ui.settings.PersistedSettings)

    companion object {
        const val WEATHER_ALERT_WORK_NAME = "weather_alerts"
        const val DAILY_SUMMARY_WORK_NAME = "daily_summary"
    }
}

class WorkManagerNotificationScheduler(
    private val workManager: WorkManager,
    private val alertWorkerClass: Class<out ListenableWorker>,
    private val summaryWorkerClass: Class<out ListenableWorker>,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault()
) : NotificationScheduler {

    override fun reconcile(settings: net.droopia.hluweather.ui.settings.PersistedSettings) {
        if (settings.trackMeEnabled || settings.selectedLocationId == null) {
            cancelWeatherAlerts()
            cancelDailySummary()
            return
        }

        if (settings.weatherAlerts) {
            enqueueWeatherAlerts(settings)
        } else {
            cancelWeatherAlerts()
        }

        if (settings.dailySummary) {
            enqueueDailySummary(settings)
        } else {
            cancelDailySummary()
        }
    }

    fun delayUntil(summaryTime: LocalTime, now: LocalDateTime): Duration =
        delayUntil(summaryTime, now, timeZone)

    fun enqueueNextDailySummary(settings: net.droopia.hluweather.ui.settings.PersistedSettings) {
        if (!settings.trackMeEnabled && settings.selectedLocationId != null && settings.dailySummary) {
            enqueueDailySummary(settings)
        }
    }

    private fun enqueueWeatherAlerts(settings: net.droopia.hluweather.ui.settings.PersistedSettings) {
        val request = PeriodicWorkRequest.Builder(
            alertWorkerClass,
            ALERT_INTERVAL_MINUTES,
            java.util.concurrent.TimeUnit.MINUTES
        )
            .setConstraints(alertConstraints)
            .setInitialDelay(ALERT_INITIAL_DELAY_MINUTES, java.util.concurrent.TimeUnit.MINUTES)
            .setInputData(settingsInput(settings))
            .build()
        workManager.enqueueUniquePeriodicWork(
            NotificationScheduler.WEATHER_ALERT_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun enqueueDailySummary(settings: net.droopia.hluweather.ui.settings.PersistedSettings) {
        val delay = delayUntil(
            settings.dailySummaryTime,
            clock.now().toLocalDateTime(timeZone)
        )
        val request = OneTimeWorkRequest.Builder(summaryWorkerClass)
            .setInitialDelay(delay.inWholeMilliseconds, java.util.concurrent.TimeUnit.MILLISECONDS)
            .setInputData(settingsInput(settings))
            .build()
        workManager.enqueueUniqueWork(
            NotificationScheduler.DAILY_SUMMARY_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun settingsInput(settings: net.droopia.hluweather.ui.settings.PersistedSettings) = workDataOf(
        LOCATION_ID_INPUT to settings.selectedLocationId,
        PROVIDER_INPUT to settings.provider.name
    )

    private fun cancelWeatherAlerts() {
        workManager.cancelUniqueWork(NotificationScheduler.WEATHER_ALERT_WORK_NAME)
    }

    private fun cancelDailySummary() {
        workManager.cancelUniqueWork(NotificationScheduler.DAILY_SUMMARY_WORK_NAME)
    }

    private companion object {
        const val ALERT_INTERVAL_MINUTES = 15L
        const val ALERT_INITIAL_DELAY_MINUTES = 15L
        val alertConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}

fun delayUntil(summaryTime: LocalTime, now: LocalDateTime, timeZone: TimeZone): Duration {
    if (now.time == summaryTime) return Duration.ZERO

    val targetDate = if (now.time < summaryTime) {
        now.date
    } else {
        now.date.plus(1, DateTimeUnit.DAY)
    }
    val target = LocalDateTime(targetDate, summaryTime)
    return target.toInstant(timeZone) - now.toInstant(timeZone)
}

const val LOCATION_ID_INPUT = "notification.location_id"
const val PROVIDER_INPUT = "notification.provider"

/** Scheduling boundary used until Task 3 supplies the real delivery workers. */
class NotificationBoundaryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = Result.success()
}
