package net.droopia.hluweather.data.network

import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.droopia.hluweather.MainActivity
import net.droopia.hluweather.data.model.RadarFrame
import net.droopia.hluweather.data.model.RadarFrameType
import net.droopia.hluweather.data.model.RadarProvider
import net.droopia.hluweather.data.model.RadarProviderDescriptors
import net.droopia.hluweather.data.model.RadarTileCoverage
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.map.DelicateMapApi
import org.maplibre.compose.map.MapEvent
import org.maplibre.compose.map.MapState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.RenderStats
import org.maplibre.compose.map.StyleLoadState
import org.maplibre.compose.map.createMapRuntime
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.map.withPlatformMap
import org.maplibre.compose.resource.MapResourceLoad
import org.maplibre.compose.resource.MapResourceLoadRequest
import org.maplibre.compose.resource.MapResourceProvider
import org.maplibre.compose.resource.MapResourceRequest
import org.maplibre.compose.sources.RasterTileSource
import org.maplibre.compose.sources.TileSetOptions
import org.maplibre.compose.sources.isVolatile
import org.maplibre.compose.style.BaseStyle

/** Tests the proposed readiness barrier on a real, presented native map with delayed tile delivery. */
@OptIn(DelicateMapApi::class)
@RunWith(AndroidJUnit4::class)
class RadarRenderReadinessProbeTest {
    @Test
    fun stagedNativeDemandCannotBePromotedByAnEarlierFullRender() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val eventsScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val registry = RadarTileRegistry()
        fun source(time: Long) = registry.register(
            "https://tilecache.rainviewer.com",
            RadarFrame(time, "/v2/radar/$time", RadarFrameType.OBSERVED, RadarProvider.RAINVIEWER)
        )
        val first = source(1700000001)
        val second = source(1700000002)
        val red = png(Color.RED)
        val green = png(Color.GREEN)
        val scheduler = RadarNetworkScheduler(scope)
        val loader = RadarTileLoader(scope, scheduler, RadarTileTransport { key, _ ->
            RadarTileHttpResult(200, RadarTilePayload(
                if (key.url.contains("1700000001")) red else green,
                System.currentTimeMillis() + 60_000
            ))
        })
        val bridge = RadarMapResourceProvider(registry, loader, scheduler)
        val gate = CompletableDeferred<Unit>()
        val nativeAttempts = ConcurrentLinkedQueue<String>()
        val guarded = object : MapResourceProvider {
            override fun accepts(request: MapResourceRequest) = bridge.accepts(request)
            override suspend fun load(request: MapResourceLoadRequest): MapResourceLoad {
                nativeAttempts.add(request.url)
                if (request.url.startsWith(second.template.substringBefore("{z}"))) gate.await()
                return bridge.load(request)
            }
        }
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
            .resolve("radar-readiness-probe-${System.nanoTime()}").apply { mkdirs() }
        val runtime = createMapRuntime(radarMapRuntimeOptions(cache, bridge).copy(resourceProvider = guarded))
        val mapRef = AtomicReference<MapState?>()
        val renders = ConcurrentLinkedQueue<RenderStats>()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.setContent {
                        val map = rememberMapState(
                            runtime = runtime,
                            baseStyle = BaseStyle.Json("""{"version":8,"sources":{},"layers":[
                                {"id":"base","type":"background","paint":{"background-color":"#0000ff"}},
                                {"id":"anchor","type":"background","paint":{"background-opacity":0}}
                            ]}"""),
                            initialCameraPosition = CameraPosition(zoom = 0.0)
                        )
                        SideEffect { mapRef.set(map) }
                        MaplibreMap(Modifier.fillMaxSize(), state = map, overlay = {})
                    }
                }
                await { mapRef.get()?.style?.loadState == StyleLoadState.Ready && mapRef.get()?.viewport != null }
                val map = requireNotNull(mapRef.get())
                eventsScope.launch {
                    map.events.collect { event ->
                        if (event is MapEvent.FrameRendered) event.stats?.let(renders::add)
                    }
                }
                fun addSource(id: String, source: RegisteredRadarSource) {
                    scenario.onActivity {
                        val handle = map.style.sources.add(RasterTileSource(
                            id, listOf(source.template), options = TileSetOptions(maxZoom = 7), tileSize = 256
                        ))
                        handle.isVolatile = true
                        check(handle.isVolatile)
                    }
                }
                suspend fun addLayer(id: String, sourceId: String, source: RegisteredRadarSource, opacity: Double) {
                    map.withPlatformMap {
                        check(this.map.styleSourceInfo(sourceId)?.volatileSource == true)
                        check(this.map.styleSourceInfo(sourceId)?.tileJson?.tileUrls == listOf(source.template))
                        this.map.addStyleLayerJson("""{"id":"$id","type":"raster","source":"$sourceId",
                            "paint":{"raster-opacity":$opacity,"raster-fade-duration":0,
                            "raster-opacity-transition":{"duration":0,"delay":0}}}""".toByteArray(), "anchor")
                        this.map.requestRepaint()
                    }
                }

                addSource("probe-a", first)
                addLayer("probe-layer-a", "probe-a", first, 1.0)
                awaitColor(Color.RED)
                addSource("probe-b", second)
                addLayer("probe-layer-b", "probe-b", second, 0.001)
                await { nativeAttempts.any { it.startsWith(second.template.substringBefore("{z}")) } }
                val coordinates = RadarTileCoverage.tiles(
                    radarViewportBounds(requireNotNull(map.viewport), map.cameraPosition),
                    RadarProviderDescriptors[RadarProvider.RAINVIEWER]
                )
                loader.awaitCoverage(coordinates.map(second::tile))
                // The coverage is available in the app cache, while the native callbacks are held.
                // A repaint now distinguishes actual source readiness from metadata/byte readiness.
                renders.clear()
                map.withPlatformMap { this.map.requestRepaint() }
                await { renders.isNotEmpty() }
                val blockedRenders = renders.toList()
                println("RADAR_PROBE_BLOCKED_RENDER_MODES=${blockedRenders.map { it.mode to it.frameCount }}")
                assertTrue("A delayed native tile must not yield a Full readiness event: $blockedRenders",
                    blockedRenders.none { it.mode == RenderStats.Mode.Full })
                awaitColor(Color.RED)
                gate.complete(Unit)
                renders.clear()
                map.withPlatformMap { this.map.requestRepaint() }
                await { renders.any { it.mode == RenderStats.Mode.Full } }
                awaitColor(Color.RED)
                map.withPlatformMap {
                    this.map.setLayerProperty("probe-layer-b", "raster-opacity", "1.0".toByteArray())
                    this.map.setLayerProperty("probe-layer-a", "raster-opacity", "0.0".toByteArray())
                    this.map.requestRepaint()
                }
                awaitColor(Color.GREEN)
            }
        } finally {
            gate.complete(Unit)
            eventsScope.cancel()
            runtime.close()
            withTimeout(20_000) { runtime.awaitClosed() }
            scope.cancel()
        }
    }

    private suspend fun await(condition: () -> Boolean) = withTimeout(30_000) {
        while (!condition()) delay(25)
    }

    private suspend fun awaitColor(color: Int) = withTimeout(30_000) {
        while (true) {
            val image = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            val pixel = image.getPixel(image.width / 2, image.height / 2)
            image.recycle()
            val match = if (color == Color.RED) Color.red(pixel) > 200 && Color.green(pixel) < 50
                else Color.green(pixel) > 200 && Color.red(pixel) < 50
            if (match && Color.blue(pixel) < 50) return@withTimeout
            delay(50)
        }
    }

    private fun png(color: Int): ByteArray = ByteArrayOutputStream().use { out ->
        Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).apply {
            eraseColor(color)
            compress(Bitmap.CompressFormat.PNG, 100, out)
            recycle()
        }
        out.toByteArray()
    }
}
