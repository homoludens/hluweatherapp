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
import androidx.work.WorkInfo
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Application-owned access used by Glance actions and widget lifecycle entry points. */
interface WeatherWidgetUpdaterOwner {
    val weatherWidgetUpdater: WeatherWidgetUpdater
}

/** Schedules the shared widget refresh independently from notification work. */
class WeatherWidgetUpdater(
    private val workManager: WorkManager,
    private val workerClass: Class<out ListenableWorker>,
    private val hasInstalledWidgets: () -> Boolean,
    private val refreshInstalledWidgetInventory: () -> Unit = {}
) {
    private val selectionLock = Mutex()

    /** Refresh the Android inventory before reconciling schedule state after receiver callbacks. */
    fun refreshInventory() {
        refreshInstalledWidgetInventory()
        reconcile()
    }

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

    /** Cache-due selection reconciliation; unlike a tap this never forces a fresh cache. */
    suspend fun enqueueSelectionRefresh() = withContext(NonCancellable + Dispatchers.IO) {
        selectionLock.withLock {
            if (!hasInstalledWidgets()) {
                cancelWidgetWork()
                return@withLock
            }
            val work = workManager.getWorkInfosForUniqueWork(SELECTION_REFRESH_WORK_NAME).get()
            // One pending request is enough: it resolves the latest identity when it runs.
            val policy = selectionWorkPolicy(work.map { it.state }) ?: return@withLock
            // KEEP alone loses a change arriving after a running worker's final identity check.
            if (!hasInstalledWidgets()) {
                cancelWidgetWork()
                return@withLock
            }
            workManager.enqueueUniqueWork(
                SELECTION_REFRESH_WORK_NAME,
                policy,
                OneTimeWorkRequest.Builder(workerClass)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
                    .build()
            ).result.get()
        }
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
        workManager.cancelUniqueWork(SELECTION_REFRESH_WORK_NAME)
    }

    companion object {
        const val PERIODIC_REFRESH_WORK_NAME = "widget.weather.periodic_refresh"
        const val MANUAL_REFRESH_WORK_NAME = "widget.weather.manual_refresh"
        const val SELECTION_REFRESH_WORK_NAME = "widget.weather.selection_refresh"
        const val REFRESH_INTERVAL_MINUTES = 60L
        const val BACKOFF_MINUTES = 10L

        private val periodicConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}

internal fun selectionWorkPolicy(states: List<WorkInfo.State>): ExistingWorkPolicy? = when {
    states.any { it == WorkInfo.State.ENQUEUED || it == WorkInfo.State.BLOCKED } -> null
    states.any { it == WorkInfo.State.RUNNING } -> ExistingWorkPolicy.APPEND_OR_REPLACE
    else -> ExistingWorkPolicy.KEEP
}
