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
import kotlinx.coroutines.launch
import net.droopia.hluweather.data.model.RadarProvider
import net.droopia.hluweather.data.model.RadarProviderDescriptors
import net.droopia.hluweather.data.model.RadarTileCoverage
import net.droopia.hluweather.data.network.RadarMapResourceProvider
import net.droopia.hluweather.data.network.RadarTileLoader
import net.droopia.hluweather.data.network.RadarTileRegistry
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
    onDisplayChanged: (RadarDisplayState) -> Unit
) {
    val scope = remember(mapState, resources) { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    val backend = remember(mapState, resources) {
        MapLibreRadarBackend(mapState, resources.registry, resources.loader, resources.provider, scope)
    }
    val controller = remember(backend) { RadarRenderController(scope, backend) }
    val currentRadar by rememberUpdatedState(radar)
    val currentProvider by rememberUpdatedState(selectedProvider)
    val currentCallback by rememberUpdatedState(onDisplayChanged)
    val lifecycleOwner = LocalLifecycleOwner.current
    var lifecycleStarted by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    var styleEpoch by remember(mapState) { mutableLongStateOf(0) }

    DisposableEffect(controller, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            lifecycleStarted = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            // Do not wait for recomposition: a stopped Compose host may pause its recomposer.
            if (!lifecycleStarted) controller.update(null, currentProvider)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(mapState) {
        mapState.events.collect { if (it == MapEvent.StyleLoaded) styleEpoch++ }
    }
    LaunchedEffect(controller) { controller.state.collect { currentCallback(it) } }
    LaunchedEffect(controller, mapState) {
        snapshotFlow {
            RendererInputs(currentRadar, currentProvider, styleEpoch, mapState.style.loadState, lifecycleStarted,
                mapState.viewport, mapState.cameraPosition)
        }.collect { input ->
            val config = input.radar
            if (!input.started || !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) ||
                config == null || input.provider != config.frame.provider || input.style != StyleLoadState.Ready) {
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
                    onSuccess = { controller.update(it, input.provider) },
                    onFailure = { controller.reject(config.frame.identity(config.host), input.provider, input.styleEpoch,
                        it.message ?: "Radar viewport unavailable") }
                )
            }
        }
    }
    DisposableEffect(controller) {
        onDispose {
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
    val styleEpoch: Long,
    val style: StyleLoadState,
    val started: Boolean,
    val viewport: org.maplibre.compose.camera.Viewport?,
    val camera: org.maplibre.compose.camera.CameraPosition
)
