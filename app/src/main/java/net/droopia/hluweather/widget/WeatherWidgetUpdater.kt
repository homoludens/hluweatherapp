package net.droopia.hluweather.widget

import androidx.work.Constraints
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Application-owned access used by Glance actions and widget lifecycle entry points. */
interface WeatherWidgetUpdaterOwner {
    val weatherWidgetUpdater: WeatherWidgetUpdater
}

/** Schedules the shared widget refresh independently from notification work. */
class WeatherWidgetUpdater(
    private val workManager: WorkManager,
    private val workerClass: Class<out ListenableWorker>,
    private val hasInstalledWidgets: () -> Boolean
) {

    /** Reconcile after widget inventory changes; both widget sizes share one periodic request. */
    fun reconcile() {
        if (hasInstalledWidgets()) {
            workManager.enqueueUniquePeriodicWork(
                PERIODIC_REFRESH_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest()
            )
        } else {
            cancelWidgetWork()
        }
    }

    /** Manual refresh is intentionally unconstrained so it can run while offline. */
    fun enqueueManualRefresh() {
        if (!hasInstalledWidgets()) {
            cancelWidgetWork()
            return
        }

        workManager.enqueueUniqueWork(
            MANUAL_REFRESH_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequest.Builder(workerClass)
                .setInputData(Data.Builder().putBoolean(WeatherWidgetWorker.INPUT_MANUAL, true).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
                .build()
        )
    }

    private fun periodicRequest(): PeriodicWorkRequest = PeriodicWorkRequest.Builder(
        workerClass,
        REFRESH_INTERVAL_MINUTES,
        TimeUnit.MINUTES
    )
        .setConstraints(periodicConstraints)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
        .build()

    private fun cancelWidgetWork() {
        workManager.cancelUniqueWork(PERIODIC_REFRESH_WORK_NAME)
        workManager.cancelUniqueWork(MANUAL_REFRESH_WORK_NAME)
    }

    companion object {
        const val PERIODIC_REFRESH_WORK_NAME = "widget.weather.periodic_refresh"
        const val MANUAL_REFRESH_WORK_NAME = "widget.weather.manual_refresh"
        const val REFRESH_INTERVAL_MINUTES = 60L
        const val BACKOFF_MINUTES = 10L

        private val periodicConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}
