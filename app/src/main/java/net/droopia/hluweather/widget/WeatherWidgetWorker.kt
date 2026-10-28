package net.droopia.hluweather.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Supplied by the application worker factory. The coordinator must be shared by all widget
 * workers and constructed with a publish callback that invalidates all installed instances.
 * Invalidations resolve the current snapshot, never an old request's result.
 */
interface WeatherWidgetWorkerDependencies {
    val coordinator: WeatherWidgetRefreshCoordinator
    suspend fun updateAllWidgets()

    /**
     * Persist or enqueue one coalesced reconciliation of the latest selection. It must survive
     * this worker's completion (KEEP on this still-running work alone is not sufficient).
     */
    suspend fun deferSelectionReconciliation()
}

class WeatherWidgetWorker(
    context: Context,
    parameters: WorkerParameters,
    private val dependencies: WeatherWidgetWorkerDependencies
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        try {
            // A manual tap forces the first attempt only. Retries reconcile the current selection
            // normally, so an obsolete request cannot force a fetch for already-fresh new data.
            val manual = runAttemptCount == 0 && inputData.getBoolean(INPUT_MANUAL, false)
            val result = dependencies.coordinator.refresh(manual)
            dependencies.updateAllWidgets()
            return when (result) {
                WidgetRefreshResult.SUCCESS, WidgetRefreshResult.NO_LOCATION,
                WidgetRefreshResult.PERMANENT_FAILURE -> Result.success()
                // The shared repository hides the cause of stale fallback and wraps some provider
                // failures as IO errors. Treat those ambiguous failures as transient, but bounded.
                WidgetRefreshResult.FAILED -> retryIfAllowed()
                WidgetRefreshResult.SELECTION_CHANGED -> {
                    if (runAttemptCount < MAX_RETRIES) Result.retry() else {
                        dependencies.deferSelectionReconciliation()
                        Result.success()
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            // The coordinator finishes metadata before rethrowing; deliver the cleared state.
            withContext(NonCancellable) {
                try { dependencies.updateAllWidgets() } catch (_: Exception) { }
            }
            throw cancelled
        } catch (_: IOException) {
            return retryIfAllowed()
        }
    }

    private fun retryIfAllowed(): Result =
        if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()

    companion object {
        const val INPUT_MANUAL = "widget.weather.manual"
        const val MAX_RETRIES = 2
    }
}
