package net.droopia.hluweather.widget

import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.model.ThemeMode
import net.droopia.hluweather.ui.settings.TemperatureUnit

/**
 * Start once in application scope. Inventory must emit again on add/remove/restore; false cancels
 * all selection, cache, and metadata collectors. The callbacks are invalidation and asynchronous
 * coalesced non-manual work respectively, not rendering or networking in this collector.
 * Renderers must load the current snapshot, never a captured request.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
fun startWidgetReconciliation(
    scope: CoroutineScope,
    installed: Flow<Boolean>,
    widgets: WeatherWidgetRepository,
    enqueueSelectionRefresh: suspend () -> Unit,
    updateAllWidgets: suspend () -> Unit,
    clock: Clock = Clock.System
): Job = scope.launch {
    installed.distinctUntilChanged().collectLatest { hasWidgets ->
        if (!hasWidgets) return@collectLatest
        coroutineScope {
            val selection = widgets.observeSelection().map { (settings, request) ->
                Selection(request, settings.temperatureUnit, settings.themeMode)
            }.distinctUntilChanged { a, b ->
                a.request?.identity == b.request?.identity &&
                    a.request?.locationName == b.request?.locationName &&
                    a.unit == b.unit && a.theme == b.theme
            }.shareIn(this, SharingStarted.Eagerly, replay = 1)

            val requests = selection.map { it.request }
                .distinctUntilChanged { a, b -> a?.identity == b?.identity }
                .shareIn(this, SharingStarted.Eagerly, replay = 1)

            // Only a new request identity may schedule work. A cache or status emission cannot.
            launch {
                requests.collectLatest { request ->
                    if (request == null) return@collectLatest
                    val cached = widgets.observeCachedForecast(request).first()
                    if ((cached == null || clock.now() - cached.fetchedAt > 1.hours) &&
                        widgets.resolveRequest()?.identity == request.identity
                    ) enqueueSelectionRefresh()
                }
            }

            // Emit an immediate invalidation on identity switch, then observe only that identity.
            // The tag prevents an old cache/status emission being combined with a new label.
            val content = requests.flatMapLatest { request ->
                if (request == null) flowOf(Content(null, null, WidgetRefreshStatus()))
                else combine(
                    widgets.observeCachedForecast(request),
                    widgets.observeRefreshStatus(request).expireLoading(clock)
                ) { forecast, status -> Content(request.identity, forecast, status) }
                    .onStart { emit(Content(request.identity, null, WidgetRefreshStatus())) }
            }
            combine(selection, content) { current, observed -> current to observed }
                .distinctUntilChanged { a, b ->
                    a.first.samePresentation(b.first) && a.second == b.second
                }
                .collectLatest { (current, observed) ->
                    if (current.request?.identity == observed.identity) updateAllWidgets()
                }
        }
    }
}

private data class Selection(
    val request: WeatherWidgetRequest?,
    val unit: TemperatureUnit,
    val theme: ThemeMode
) {
    fun samePresentation(other: Selection) = request?.identity == other.request?.identity &&
        request?.locationName == other.request?.locationName && unit == other.unit && theme == other.theme
}

private data class Content(
    val identity: WeatherWidgetRequestIdentity?,
    val forecast: WeatherForecast?,
    val status: WidgetRefreshStatus
)

/** One timer per active attempt, cancelled by completion or selection/inventory changes. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
private fun Flow<WidgetRefreshStatus>.expireLoading(clock: Clock): Flow<WidgetRefreshStatus> =
    transformLatest { status ->
        emit(status)
        val started = status.startedAt
        if (status.isRefreshing && started != null) {
            val remaining = (started + REFRESH_LOADING_TTL - clock.now()).coerceAtLeast(kotlin.time.Duration.ZERO)
            delay(remaining.inWholeMilliseconds.coerceAtLeast(1))
            emit(status.copy(isRefreshing = false, lastAttemptFailed = true))
        }
    }.distinctUntilChanged()
