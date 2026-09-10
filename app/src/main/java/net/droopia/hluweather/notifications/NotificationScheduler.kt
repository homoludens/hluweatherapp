package net.droopia.hluweather.notifications

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import net.droopia.hluweather.data.model.ActiveLocation
import net.droopia.hluweather.ui.settings.PersistedSettings
import kotlin.time.Clock
import kotlin.time.Duration

interface NotificationScheduler {
    fun reconcile(settings: PersistedSettings, activeLocation: ActiveLocation?)

    fun enqueueNextDailySummary(settings: PersistedSettings, activeLocation: ActiveLocation.Saved)

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

    override fun reconcile(settings: PersistedSettings, activeLocation: ActiveLocation?) {
        val canonicalSettings = settings.forSavedLocation(activeLocation) ?: run {
            cancelWeatherAlerts()
            cancelDailySummary()
            return
        }

        if (canonicalSettings.weatherAlerts) {
            enqueueWeatherAlerts(canonicalSettings)
        } else {
            cancelWeatherAlerts()
        }

        if (canonicalSettings.dailySummary) {
            enqueueDailySummary(canonicalSettings, ExistingWorkPolicy.REPLACE, preserveMatchingIdentity = true)
        } else {
            cancelDailySummary()
        }
    }

    fun delayUntil(summaryTime: LocalTime, now: LocalDateTime): Duration =
        delayUntil(summaryTime, now, timeZone)

    override fun enqueueNextDailySummary(settings: PersistedSettings, activeLocation: ActiveLocation.Saved) {
        val canonicalSettings = settings.forSavedLocation(activeLocation) ?: return
        if (canonicalSettings.dailySummary) {
            enqueueDailySummary(canonicalSettings, ExistingWorkPolicy.APPEND, preserveMatchingIdentity = false)
        }
    }

    private fun enqueueWeatherAlerts(settings: net.droopia.hluweather.ui.settings.PersistedSettings) {
        if (hasPendingWork(
                NotificationScheduler.WEATHER_ALERT_WORK_NAME,
                alertScheduleIdentity(settings)
            )
        ) return

        val request = PeriodicWorkRequest.Builder(
            alertWorkerClass,
            ALERT_INTERVAL_MINUTES,
            java.util.concurrent.TimeUnit.MINUTES
        )
            .setConstraints(alertConstraints)
            .setInitialDelay(ALERT_INITIAL_DELAY_MINUTES, java.util.concurrent.TimeUnit.MINUTES)
            .setInputData(settingsInput(settings))
            .addTag(alertScheduleIdentity(settings))
            .build()
        workManager.enqueueUniquePeriodicWork(
            NotificationScheduler.WEATHER_ALERT_WORK_NAME,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            request
        )
    }

    private fun enqueueDailySummary(
        settings: PersistedSettings,
        existingWorkPolicy: ExistingWorkPolicy,
        preserveMatchingIdentity: Boolean
    ) {
        if (preserveMatchingIdentity && hasPendingWork(
                NotificationScheduler.DAILY_SUMMARY_WORK_NAME,
                summaryScheduleIdentity(settings)
            )
        ) return

        val delay = delayUntil(
            settings.dailySummaryTime,
            clock.now().toLocalDateTime(timeZone)
        )
        val request = OneTimeWorkRequest.Builder(summaryWorkerClass)
            .setInitialDelay(delay.inWholeMilliseconds, java.util.concurrent.TimeUnit.MILLISECONDS)
            .setInputData(settingsInput(settings))
            .addTag(summaryScheduleIdentity(settings))
            .build()
        workManager.enqueueUniqueWork(
            NotificationScheduler.DAILY_SUMMARY_WORK_NAME,
            existingWorkPolicy,
            request
        )
    }

    private fun settingsInput(settings: PersistedSettings) = workDataOf(
        LOCATION_ID_INPUT to settings.selectedLocationId,
        PROVIDER_INPUT to settings.provider.name,
        SUMMARY_TIME_INPUT to settings.dailySummaryTime.toString()
    )

    private fun hasPendingWork(name: String, identity: String): Boolean =
        workManager.getWorkInfosForUniqueWork(name).get().any { workInfo ->
            !workInfo.state.isFinished && identity in workInfo.tags
        }

    private fun alertScheduleIdentity(settings: PersistedSettings): String =
        "notification.schedule.alert.${settings.provider.name}.${settings.selectedLocationId}"

    private fun summaryScheduleIdentity(settings: PersistedSettings): String =
        "notification.schedule.summary.${settings.provider.name}.${settings.selectedLocationId}.${settings.dailySummaryTime}"

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

private fun PersistedSettings.forSavedLocation(activeLocation: ActiveLocation?): PersistedSettings? =
    (activeLocation as? ActiveLocation.Saved)?.let { active ->
        copy(
            selectedLocationId = active.location.id,
            trackMeEnabled = false
        )
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
const val SUMMARY_TIME_INPUT = "notification.summary_time"
