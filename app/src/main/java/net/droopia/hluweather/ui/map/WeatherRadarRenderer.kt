package net.droopia.hluweather.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import net.droopia.hluweather.data.model.RadarFrame
import net.droopia.hluweather.data.model.RadarFrameIdentity
import net.droopia.hluweather.data.model.RadarMetadata
import net.droopia.hluweather.data.model.RadarProvider
import net.droopia.hluweather.data.model.RadarProviderDescriptors
import net.droopia.hluweather.data.model.RadarTimelineNeighborhoodPlanner
import net.droopia.hluweather.data.model.RadarTileCoverage
import net.droopia.hluweather.data.model.RadarViewportBounds
import net.droopia.hluweather.data.network.RadarMapResourceProvider
import net.droopia.hluweather.data.network.RadarNextFrameCoverageOpportunity
import net.droopia.hluweather.data.network.RadarPrefetchController
import net.droopia.hluweather.data.network.RadarTileLoader
import net.droopia.hluweather.data.network.RadarTileRegistry
import net.droopia.hluweather.data.network.planRadarTiles
import net.droopia.hluweather.data.network.radarViewportBounds
import org.maplibre.compose.map.MapEvent
import org.maplibre.compose.map.MapState
import org.maplibre.compose.map.StyleLoadState

/** Shared app loader/registry and resource bridge; only the main map opts in. */
data class WeatherRadarResources(
    val registry: RadarTileRegistry,
    val loader: RadarTileLoader,
    val provider: RadarMapResourceProvider
)

@Composable
internal fun WeatherRadarRenderer(
    mapState: MapState,
    resources: WeatherRadarResources,
    radar: WeatherMapRadarConfig?,
    selectedProvider: RadarProvider?,
    metadata: RadarMetadata?,
    playbackPreparationEnabled: Boolean = false,
    playbackIsPlaying: Boolean = false,
    nextPlaybackFrame: RadarFrame? = null,
    retryToken: Long,
    onDisplayChanged: (RadarDisplayState) -> Unit
) {
    val scope = remember(mapState, resources) { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    val prefetch = remember(mapState, resources) { RadarPrefetchController(scope, resources.loader) }
    val backend = remember(mapState, resources) {
        MapLibreRadarBackend(mapState, resources.registry, resources.loader, resources.provider, scope)
    }
    val controller = remember(backend, playbackPreparationEnabled) {
        RadarRenderController(
            scope,
            backend,
            timing = if (playbackPreparationEnabled) RadarHandoffTiming() else null
        )
    }
    val currentRadar by rememberUpdatedState(radar)
    val currentProvider by rememberUpdatedState(selectedProvider)
    val currentMetadata by rememberUpdatedState(metadata)
    val currentPlaybackIsPlaying by rememberUpdatedState(playbackIsPlaying)
    val currentNextPlaybackFrame by rememberUpdatedState(nextPlaybackFrame)
    val currentCallback by rememberUpdatedState(onDisplayChanged)
    var lastRetryToken by remember(controller) { mutableLongStateOf(retryToken) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var lifecycleStarted by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    var styleEpoch by remember(mapState) { mutableLongStateOf(0) }

    DisposableEffect(controller, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            lifecycleStarted = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            // Do not wait for recomposition: a stopped Compose host may pause its recomposer.
            if (!lifecycleStarted) {
                prefetch.stop()
                controller.update(null, currentProvider)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(mapState) {
        mapState.events.collect { if (it == MapEvent.StyleLoaded) styleEpoch++ }
    }
    LaunchedEffect(controller) { controller.state.collect { currentCallback(it) } }
    LaunchedEffect(controller, retryToken) {
        if (retryToken > lastRetryToken) controller.retry()
        lastRetryToken = retryToken
    }
    LaunchedEffect(controller, mapState) {
        val inputs = snapshotFlow {
            RendererInputs(
                currentRadar,
                currentProvider,
                currentMetadata,
                styleEpoch,
                mapState.style.loadState,
                lifecycleStarted,
                mapState.viewport,
                mapState.cameraPosition,
                currentPlaybackIsPlaying,
                currentNextPlaybackFrame
            )
        }
        var activePlanGeneration: Long? = null
        combine(inputs, prefetch.nextFrameCoverageOpportunity) { input, _ -> input }.collect { input ->
            val config = input.radar
            if (!input.started || !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) ||
                config == null || input.provider != config.frame.provider || input.style != StyleLoadState.Ready) {
                prefetch.stop()
                activePlanGeneration = null
                controller.prepare(null)
                controller.update(null, input.provider)
            } else {
                val result = runCatching {
                    require(config.opacity.isFinite()) { "Radar opacity is invalid" }
                    val viewport = requireNotNull(input.viewport) { "Radar viewport unavailable" }
                    RadarRenderRequest(
                        config,
                        RadarTileCoverage.tiles(radarViewportBounds(viewport, input.camera),
                            RadarProviderDescriptors[config.frame.provider]).sortedWith(MapLibreRadarBackend.tileOrder),
                        input.styleEpoch
                    )
                }
                result.fold(
                    onSuccess = { request ->
                        controller.update(request, input.provider)
                        val metadata = input.metadata
                        val viewport = input.viewport
                        if (shouldPlanRadarPrefetch(
                                radar = config,
                                selectedProvider = input.provider,
                                metadata = metadata,
                                lifecycleStarted = input.started,
                                styleReady = input.style == StyleLoadState.Ready,
                                hasViewport = viewport != null
                            ) && config.opacity > 0f
                        ) {
                            val plan = runCatching {
                                planRadarTiles(
                                    registry = resources.registry,
                                    metadata = requireNotNull(metadata),
                                    requested = config.frame.identity(config.host),
                                    viewport = radarViewportBounds(requireNotNull(viewport), input.camera)
                                )
                            }.getOrNull()
                            if (plan == null) {
                                prefetch.stop()
                                activePlanGeneration = null
                            } else {
                                try {
                                    activePlanGeneration = prefetch.replan(plan)
                                } finally {
                                    resources.registry.unregister(plan.requestedSource)
                                }
                            }
                        } else {
                            prefetch.stop()
                            activePlanGeneration = null
                        }
                        val bounds = viewport?.let { radarViewportBounds(it, input.camera) }
                        val opportunity = prefetch.nextFrameCoverageOpportunity.value
                        val preparation = if (playbackPreparationEnabled) {
                            nextRadarPreparationRequest(
                                currentRequest = request,
                                selectedProvider = input.provider,
                                metadata = metadata,
                                lifecycleStarted = input.started,
                                styleReady = input.style == StyleLoadState.Ready,
                                playbackIsPlaying = input.playbackIsPlaying,
                                nextPlaybackFrame = input.nextPlaybackFrame,
                                viewport = bounds,
                                opportunity = opportunity,
                                currentPlanGeneration = activePlanGeneration
                            )
                        } else {
                            null
                        }
                        controller.prepare(preparation, revision = opportunity?.generation ?: 0L)
                    },
                    onFailure = {
                        activePlanGeneration = null
                        controller.prepare(null)
                        controller.reject(config.frame.identity(config.host), input.provider, input.styleEpoch,
                            it.message ?: "Radar viewport unavailable").also { prefetch.stop() }
                    }
                )
            }
        }
    }
    DisposableEffect(controller) {
        onDispose {
            prefetch.stop()
            // The map's own disposal closes the native engine. Keep slot leases until each source
            // is removed or that close completes, even if the composable's regular scope is gone.
            scope.launch {
                try {
                    runCatching { controller.close() }
                } finally {
                    backend.closeAfterMap()
                    scope.cancel()
                }
            }
        }
    }
}

private data class RendererInputs(
    val radar: WeatherMapRadarConfig?,
    val provider: RadarProvider?,
    val metadata: RadarMetadata?,
    val styleEpoch: Long,
    val style: StyleLoadState,
    val started: Boolean,
    val viewport: org.maplibre.compose.camera.Viewport?,
    val camera: org.maplibre.compose.camera.CameraPosition,
    val playbackIsPlaying: Boolean,
    val nextPlaybackFrame: RadarFrame?
)

/** Translate only the current plan's fully covered, exact non-wrapping next frame to preparation. */
internal fun nextRadarPreparationRequest(
    currentRequest: RadarRenderRequest,
    selectedProvider: RadarProvider?,
    metadata: RadarMetadata?,
    lifecycleStarted: Boolean,
    styleReady: Boolean,
    playbackIsPlaying: Boolean,
    nextPlaybackFrame: RadarFrame?,
    viewport: RadarViewportBounds?,
    opportunity: RadarNextFrameCoverageOpportunity?,
    currentPlanGeneration: Long?
): RadarRenderRequest? {
    if (!lifecycleStarted || !styleReady || !playbackIsPlaying ||
        currentRequest.config.opacity <= 0f || selectedProvider != currentRequest.identity.provider
    ) return null
    val currentMetadata = metadata ?: return null
    val bounds = viewport ?: return null
    if (currentMetadata.provider != selectedProvider || currentMetadata.host != currentRequest.config.host) return null
    val expectedNext = RadarTimelineNeighborhoodPlanner.nextFrame(currentMetadata, currentRequest.identity)
        ?: return null
    if (nextPlaybackFrame != expectedNext) return null
    val cachedCoverage = opportunity ?: return null
    if (currentPlanGeneration == null || cachedCoverage.generation != currentPlanGeneration ||
        cachedCoverage.requestedFrame != currentRequest.identity ||
        cachedCoverage.nextFrame != expectedNext.identity(currentMetadata.host) ||
        cachedCoverage.viewport != bounds
    ) return null
    return currentRequest.copy(config = currentRequest.config.copy(frame = expectedNext))
}

internal fun shouldPlanRadarPrefetch(
    radar: WeatherMapRadarConfig?,
    selectedProvider: RadarProvider?,
    metadata: RadarMetadata?,
    lifecycleStarted: Boolean,
    styleReady: Boolean,
    hasViewport: Boolean
): Boolean {
    if (radar == null || !lifecycleStarted || !styleReady || !hasViewport ||
        selectedProvider != radar.frame.provider
    ) return false
    if (metadata == null || metadata.provider != selectedProvider || metadata.host != radar.host) return false
    val identity: RadarFrameIdentity = radar.frame.identity(radar.host)
    return metadata.frames.any { it.identity(metadata.host) == identity }
}
