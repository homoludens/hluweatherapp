package net.droopia.hluweather.widget

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import net.droopia.hluweather.data.repository.ForecastLoad
import net.droopia.hluweather.data.repository.WeatherRepository

/** Result of one bounded reconciliation, suitable for a worker's retry decision. */
enum class WidgetRefreshResult { SUCCESS, FAILED, NO_LOCATION, SELECTION_CHANGED }

/**
 * Share one instance between all widget workers. Concurrent callers join the same run rather
 * than queueing more fetches. [publish] is an invalidation: it must load the current snapshot
 * at rendering time, not retain a request or forecast from this coordinator.
 *
 * At most two fetches are performed per run. If the selection changes again, the caller can
 * schedule another reconciliation; this prevents a moving location from creating an endless run.
 */
class WeatherWidgetRefreshCoordinator(
    private val widgets: WeatherWidgetRepository,
    private val weather: WeatherRepository,
    private val publish: suspend () -> Unit = {}
) {
    private val lock = Any()
    private class Run {
        val result = CompletableDeferred<WidgetRefreshResult>()
        var manualRequested = false
        var fetched = false
    }

    private var active: Run? = null

    /** Manual refresh bypasses a fresh cache; periodic/reconciliation work reuses it. */
    suspend fun refresh(manual: Boolean): WidgetRefreshResult {
        val (run, owner) = synchronized(lock) {
            active?.let {
                if (manual) it.manualRequested = true
                it to false
            } ?: Run().let {
                active = it
                it to true
            }
        }
        if (!owner) return run.result.await()
        try {
            var result = reconcile(manual, run)
            val upgrade = synchronized(lock) {
                if (run.manualRequested && !run.fetched && result == WidgetRefreshResult.SUCCESS) {
                    true
                } else {
                    active = null
                    run.result.complete(result)
                    false
                }
            }
            if (upgrade) {
                // One upgrade at most: a stream of taps must not create an unbounded run.
                result = reconcile(true, run)
                synchronized(lock) {
                    active = null
                    run.result.complete(result)
                }
            }
            return result
        } catch (error: Throwable) {
            synchronized(lock) {
                active = null
                run.result.completeExceptionally(error)
            }
            throw error
        }
    }

    private suspend fun reconcile(manual: Boolean, run: Run): WidgetRefreshResult {
        var force = manual
        repeat(2) {
            val request = widgets.resolveRequest() ?: run {
                publish()
                return WidgetRefreshResult.NO_LOCATION
            }
            if (!force) {
                val snapshot = widgets.loadCachedState()
                val latest = widgets.resolveRequest()
                if (latest?.identity != request.identity) return@repeat
                if (snapshot is WeatherWidgetSnapshot.Available &&
                    snapshot.request.identity == request.identity && !snapshot.weather.isStale
                ) {
                    publish()
                    return WidgetRefreshResult.SUCCESS
                }
            }
            force = false
            // Cache and settings reads can suspend; resolve once more before starting a request.
            if (widgets.resolveRequest()?.identity != request.identity) return@repeat
            val result = attempt(request, run)
            val latest = widgets.resolveRequest()
            if (latest?.identity == request.identity) {
                publish()
                return result
            }
            // Obsolete results and failure metadata stay keyed to their original request.
        }
        publish()
        return WidgetRefreshResult.SELECTION_CHANGED
    }

    private suspend fun attempt(request: WeatherWidgetRequest, run: Run): WidgetRefreshResult {
        val attempt = widgets.beginRefresh(request)
        var result: ForecastLoad? = null
        try {
            // Never publish a loading state for an obsolete selection.
            if (widgets.resolveRequest()?.identity != request.identity) return WidgetRefreshResult.SELECTION_CHANGED
            publish()
            if (widgets.resolveRequest()?.identity != request.identity) return WidgetRefreshResult.SELECTION_CHANGED
            synchronized(lock) { run.fetched = true }
            result = weather.getForecast(request.provider, request.location)
            return if (!result.isStale && request.matchesIdentity(result.forecast)) {
                WidgetRefreshResult.SUCCESS
            } else WidgetRefreshResult.FAILED
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return WidgetRefreshResult.FAILED
        } finally {
            // Only metadata cleanup is non-cancellable; publication and networking remain cancellable.
            withContext(NonCancellable) { widgets.finishRefresh(request, attempt, result) }
        }
    }
}
