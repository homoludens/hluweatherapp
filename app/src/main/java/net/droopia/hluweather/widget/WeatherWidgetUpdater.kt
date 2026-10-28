package net.droopia.hluweather.widget

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

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
            OneTimeWorkRequest.Builder(workerClass).build()
        )
    }

    private fun periodicRequest(): PeriodicWorkRequest = PeriodicWorkRequest.Builder(
        workerClass,
        REFRESH_INTERVAL_MINUTES,
        TimeUnit.MINUTES
    )
        .setConstraints(periodicConstraints)
        .build()

    private fun cancelWidgetWork() {
        workManager.cancelUniqueWork(PERIODIC_REFRESH_WORK_NAME)
        workManager.cancelUniqueWork(MANUAL_REFRESH_WORK_NAME)
    }

    companion object {
        const val PERIODIC_REFRESH_WORK_NAME = "widget.weather.periodic_refresh"
        const val MANUAL_REFRESH_WORK_NAME = "widget.weather.manual_refresh"
        const val REFRESH_INTERVAL_MINUTES = 60L

        private val periodicConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}
