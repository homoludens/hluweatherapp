package net.droopia.hluweather.ui.map

import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.droopia.hluweather.MainActivity
import net.droopia.hluweather.data.model.RadarFrame
import net.droopia.hluweather.data.model.RadarFrameType
import net.droopia.hluweather.data.model.RadarProvider
import net.droopia.hluweather.data.model.RadarProviderDescriptors
import net.droopia.hluweather.data.model.RadarTileCoverage
import net.droopia.hluweather.data.network.RadarMapResourceProvider
import net.droopia.hluweather.data.network.RadarNetworkScheduler
import net.droopia.hluweather.data.network.RadarTileHttpResult
import net.droopia.hluweather.data.network.RadarTileLoader
import net.droopia.hluweather.data.network.RadarTilePayload
import net.droopia.hluweather.data.network.RadarTileRegistry
import net.droopia.hluweather.data.network.RadarTileTransport
import net.droopia.hluweather.data.network.radarMapRuntimeOptions
import net.droopia.hluweather.data.network.radarViewportBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.BackgroundLayer
import org.maplibre.compose.map.DelicateMapApi
import org.maplibre.compose.map.MapState
import org.maplibre.compose.map.MapEvent
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
import org.maplibre.compose.style.BaseStyle

@OptIn(DelicateMapApi::class)
@RunWith(AndroidJUnit4::class)
class RadarBufferedRendererRuntimeTest {
    @Test
    fun nativeRollbackRestoresOldFrontAfterUnacknowledgedSwapFails() = runBlocking {
        val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val ui = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val registry = RadarTileRegistry()
        val scheduler = RadarNetworkScheduler(io)
        val red = png(Color.RED)
        val green = png(Color.GREEN)
        val loader = RadarTileLoader(io, scheduler, RadarTileTransport { key, _ ->
            RadarTileHttpResult(200, RadarTilePayload(
                if (key.url.contains("1700000001")) red else green,
                System.currentTimeMillis() + 60_000
            ))
        })
        val bridge = RadarMapResourceProvider(registry, loader, scheduler)
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
            .resolve("radar-rollback-${System.nanoTime()}").apply { mkdirs() }
        val runtime = createMapRuntime(radarMapRuntimeOptions(cache, bridge))
        val mapRef = AtomicReference<MapState?>()
        val controllerRef = AtomicReference<RadarRenderController?>()
        val backendRef = AtomicReference<MapLibreRadarBackend?>()
        val blockedSlot = AtomicReference<Long?>()
        val postSwapBarrier = CompletableDeferred<Unit>()
        val failBarrier = CompletableDeferred<Unit>()
        val rollbackApplied = CompletableDeferred<Unit>()
        val cleanupBarrier = CompletableDeferred<Unit>()
        val allowCleanup = CompletableDeferred<Unit>()
        val samples = ConcurrentLinkedQueue<Int>()
        val sampled = CompletableDeferred<Unit>()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.setContent {
                        val map = rememberMapState(
                            runtime = runtime,
                            baseStyle = BaseStyle.Json("""{"version":8,"sources":{
                                "empty":{"type":"geojson","data":{"type":"FeatureCollection","features":[]}}},
                                "layers":[{"id":"base","type":"background","paint":{"background-color":"#0000ff"}},
                                {"id":"labels","type":"symbol","source":"empty"}]}"""),
                            initialCameraPosition = CameraPosition(zoom = 0.0)
                        )
                        SideEffect { mapRef.set(map) }
                        MaplibreMap(Modifier.fillMaxSize(), state = map, overlay = {})
                    }
                }
                await { mapRef.get()?.style?.loadState == StyleLoadState.Ready && mapRef.get()?.viewport != null }
                val map = requireNotNull(mapRef.get())
                scenario.onActivity {
                    val native = MapLibreRadarBackend(map, registry, loader, bridge, ui)
                    backendRef.set(native)
                    val controlled = object : RadarRenderBackend by native {
                        override suspend fun promote(
                            next: RadarRenderSlot, previous: RadarRenderSlot?, opacity: Float, guard: RadarMutationGuard
                        ) {
                            native.promote(next, previous, opacity, guard)
                            if (previous != null) blockedSlot.compareAndSet(null, next.id)
                        }

                        override suspend fun awaitFullRender(
                            slot: RadarRenderSlot, viewport: RadarRenderRequest, guard: RadarMutationGuard
                        ) {
                            if (slot.id == blockedSlot.get() && !rollbackApplied.isCompleted) {
                                postSwapBarrier.complete(Unit)
                                failBarrier.await()
                                throw IllegalStateException("Injected post-swap render failure")
                            }
                            if (rollbackApplied.isCompleted && slot.id != blockedSlot.get() && !allowCleanup.isCompleted) {
                                cleanupBarrier.complete(Unit)
                                allowCleanup.await()
                            }
                            native.awaitFullRender(slot, viewport, guard)
                        }

                        override suspend fun rollback(
                            failed: RadarRenderSlot, previous: RadarRenderSlot?, opacity: Float, guard: RadarMutationGuard
                        ) {
                            native.rollback(failed, previous, opacity, guard)
                            rollbackApplied.complete(Unit)
                        }
                    }
                    controllerRef.set(RadarRenderController(ui, controlled))
                }
                val controller = requireNotNull(controllerRef.get())
                fun request(time: Long): RadarRenderRequest {
                    val config = WeatherMapRadarConfig("https://tilecache.rainviewer.com",
                        RadarFrame(time, "/v2/radar/$time", RadarFrameType.OBSERVED, RadarProvider.RAINVIEWER))
                    return RadarRenderRequest(config, RadarTileCoverage.tiles(
                        radarViewportBounds(requireNotNull(map.viewport), map.cameraPosition),
                        RadarProviderDescriptors[RadarProvider.RAINVIEWER]
                    ).sortedWith(MapLibreRadarBackend.tileOrder), 1)
                }
                val first = request(1700000001)
                val second = request(1700000002)
                scenario.onActivity { controller.update(first, RadarProvider.RAINVIEWER) }
                await { controller.state.value.displayed == first.identity }
                awaitColor(red = true)
                val oldLayer = map.withPlatformMap { this.map.styleLayerIds().single { it.startsWith("hlu-radar-layer-") } }
                scenario.onActivity { controller.update(second, RadarProvider.RAINVIEWER) }
                withTimeout(30_000) { postSwapBarrier.await() }
                awaitColor(red = false)
                assertEquals(first.identity, controller.state.value.displayed)
                assertTrue(controller.state.value.pending)
                assertEquals(2, registry.registeredCount)

                val sampler = io.launch {
                    while (isActive) {
                        samples.add(pixel())
                        sampled.complete(Unit)
                        delay(10)
                    }
                }
                withTimeout(30_000) { sampled.await() }
                failBarrier.complete(Unit)
                withTimeout(30_000) { rollbackApplied.await(); cleanupBarrier.await() }
                awaitColor(red = true)
                samples.add(pixel())
                sampler.cancelAndJoin()
                assertTrue("Rollback samples must remain red or green, never blue: $samples",
                    samples.isNotEmpty() && samples.all { isRed(it) || isGreen(it) })
                val layers = map.withPlatformMap { this.map.styleLayerIds().filter { it.startsWith("hlu-radar-layer-") } }
                assertEquals(2, layers.size)
                val failedLayer = layers.single { it != oldLayer }
                val oldOpacity = map.withPlatformMap { this.map.layerProperty(oldLayer, "raster-opacity")!!.decodeToString().toFloat() }
                val failedOpacity = map.withPlatformMap { this.map.layerProperty(failedLayer, "raster-opacity")!!.decodeToString().toFloat() }
                assertEquals(0.75f, oldOpacity, 0f)
                assertEquals(0f, failedOpacity, 0f)
                assertEquals(first.identity, controller.state.value.displayed)
                assertEquals(second.identity, controller.state.value.requested)
                assertEquals("Injected post-swap render failure", controller.state.value.error)
                assertTrue(!controller.state.value.pending)
                assertEquals("Both leases must survive until a restored Full render", 2, registry.registeredCount)
                println("RADAR_ROLLBACK_PIXELS=$samples old=$oldOpacity failed=$failedOpacity")

                allowCleanup.complete(Unit)
                await { registry.registeredCount == 1 }
                assertTrue(map.withPlatformMap { this.map.styleLayerExists(oldLayer) })
                scenario.onActivity { controller.retry() }
                await { controller.state.value.displayed == second.identity }
                awaitColor(red = false)
                await { registry.registeredCount == 1 }
                scenario.onActivity { controller.update(null, RadarProvider.RAINVIEWER) }
                await { registry.registeredCount == 0 }
            }
        } finally {
            failBarrier.complete(Unit)
            allowCleanup.complete(Unit)
            runtime.close()
            withTimeout(20_000) { runtime.awaitClosed() }
            backendRef.get()?.closeAfterMap()
            ui.cancel()
            io.cancel()
        }
    }

    @Test
    fun realControllerKeepsFrontUntilDelayedNativeTilesAndSwapRender() = runBlocking {
        val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val ui = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val registry = RadarTileRegistry()
        val red = png(Color.RED)
        val green = png(Color.GREEN)
        val scheduler = RadarNetworkScheduler(io)
        val transportCalls = ConcurrentLinkedQueue<String>()
        val downloadGate = CompletableDeferred<Unit>()
        val loader = RadarTileLoader(io, scheduler, RadarTileTransport { key, _ ->
            transportCalls.add(key.url)
            if (key.url.contains("1700000002")) downloadGate.await()
            RadarTileHttpResult(200, RadarTilePayload(
                if (key.url.contains("1700000001")) red else green,
                System.currentTimeMillis() + 60_000
            ))
        })
        val bridge = RadarMapResourceProvider(registry, loader, scheduler)
        val gate = CompletableDeferred<Unit>()
        val nativeCalls = ConcurrentLinkedQueue<MapResourceLoadRequest>()
        val guarded = object : MapResourceProvider {
            override fun accepts(request: MapResourceRequest) = bridge.accepts(request)
            override suspend fun load(request: MapResourceLoadRequest): MapResourceLoad {
                nativeCalls.add(request)
                if (registry.resolve(request.url)?.url?.contains("1700000002") == true) gate.await()
                return bridge.load(request)
            }
        }
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
            .resolve("radar-buffered-${System.nanoTime()}").apply { mkdirs() }
        val runtime = createMapRuntime(radarMapRuntimeOptions(cache, bridge).copy(resourceProvider = guarded))
        val mapRef = AtomicReference<MapState?>()
        val controllerRef = AtomicReference<RadarRenderController?>()
        val backendRef = AtomicReference<MapLibreRadarBackend?>()
        val renders = ConcurrentLinkedQueue<Pair<Long, RenderStats>>()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.setContent {
                        val map = rememberMapState(
                            runtime = runtime,
                            baseStyle = BaseStyle.Json("""{"version":8,"sources":{
                                "empty":{"type":"geojson","data":{"type":"FeatureCollection","features":[]}}},
                                "layers":[{"id":"base","type":"background","paint":{"background-color":"#0000ff"}},
                                {"id":"labels","type":"symbol","source":"empty"}]}"""),
                            initialCameraPosition = CameraPosition(zoom = 0.0)
                        )
                        SideEffect { mapRef.set(map) }
                        MaplibreMap(Modifier.fillMaxSize(), state = map, overlay = {})
                    }
                }
                await { mapRef.get()?.style?.loadState == StyleLoadState.Ready && mapRef.get()?.viewport != null }
                val map = requireNotNull(mapRef.get())
                ui.launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
                    map.events.collect { event ->
                        (event as? MapEvent.FrameRendered)?.stats?.let { renders.add(System.nanoTime() to it) }
                    }
                }
                scenario.onActivity {
                    val backend = MapLibreRadarBackend(map, registry, loader, bridge, ui)
                    backendRef.set(backend)
                    controllerRef.set(RadarRenderController(ui, backend))
                }
                val controller = requireNotNull(controllerRef.get())
                fun request(time: Long): RadarRenderRequest {
                    val config = WeatherMapRadarConfig("https://tilecache.rainviewer.com",
                        RadarFrame(time, "/v2/radar/$time", RadarFrameType.OBSERVED, RadarProvider.RAINVIEWER))
                    return RadarRenderRequest(config, RadarTileCoverage.tiles(
                        radarViewportBounds(requireNotNull(map.viewport), map.cameraPosition),
                        RadarProviderDescriptors[RadarProvider.RAINVIEWER]
                    ).sortedWith(MapLibreRadarBackend.tileOrder), 1)
                }
                val first = request(1700000001)
                val second = request(1700000002)
                scenario.onActivity { controller.update(first, RadarProvider.RAINVIEWER) }
                await { controller.state.value.displayed == first.identity }
                awaitColor(red = true)
                val frontLayer = map.withPlatformMap {
                    this.map.styleLayerIds().single { it.startsWith("hlu-radar-layer-") }
                }
                val nativeCallsBeforeMemoryRender = nativeCalls.size
                val transportCallsBeforeMemoryRender = transportCalls.size
                val memoryBarrierAt = System.nanoTime()
                withTimeout(30_000) {
                    requireNotNull(backendRef.get()).awaitFullRender(RadarRenderSlot(1, first), first) { mutation ->
                        mutation()
                        true
                    }
                }
                val memoryFulls = renders.count { (time, stats) ->
                    time > memoryBarrierAt && stats.mode == RenderStats.Mode.Full
                }
                assertTrue("An already loaded native source still requires new Full events", memoryFulls >= 2)
                assertEquals("A native in-memory render should not need resource callbacks", nativeCallsBeforeMemoryRender, nativeCalls.size)
                assertEquals("A native in-memory render should not redownload", transportCallsBeforeMemoryRender, transportCalls.size)
                println("RADAR_BUFFERED_NATIVE_MEMORY_FULLS=$memoryFulls callbacks=$nativeCallsBeforeMemoryRender")
                scenario.onActivity { controller.update(second, RadarProvider.RAINVIEWER) }
                await { nativeCalls.any { registry.resolve(it.url)?.url?.contains("1700000002") == true } }
                val installedAt = System.nanoTime()
                await { transportCalls.any { it.contains("1700000002") } }
                val ids = map.withPlatformMap { this.map.styleLayerIds() }
                val radarIds = ids.filter { it.startsWith("hlu-radar-layer-") }
                assertEquals("Exactly two bounded radar layers while pending", 2, radarIds.size)
                assertTrue("Radar must be below labels: $ids", radarIds.all { ids.indexOf(it) < ids.indexOf("labels") })
                val staging = radarIds.last()
                val stageOpacity = map.withPlatformMap { this.map.layerProperty(staging, "raster-opacity")!!.decodeToString() }
                assertTrue("Staging must be tiny and positive: $stageOpacity", stageOpacity.toFloat() in 0.000001f..0.001f)
                val pendingPixels = mutableListOf<Int>()
                repeat(8) { pendingPixels += pixel(); delay(100) }
                assertTrue("Old frame must remain visible while native tile responses are blocked: $pendingPixels",
                    pendingPixels.all(::isRed))
                assertEquals(first.identity, controller.state.value.displayed)
                downloadGate.complete(Unit)
                val coverageLease = registry.register(second.config.host, second.config.frame)
                try {
                    withTimeout(30_000) { loader.awaitCoverage(second.coordinates.map(coverageLease::tile)) }
                } finally {
                    registry.unregister(coverageLease)
                }
                val coverageAt = System.nanoTime()
                assertTrue("App byte coverage is not a substitute for a native render", isRed(pixel()))
                assertEquals(first.identity, controller.state.value.displayed)
                // Native responses remain blocked even though the app has all the bytes. Drive
                // actual native partial frames and check the front after each, rather than using
                // a sleep as evidence that the old frame was retained.
                repeat(2) {
                    val repaintAt = System.nanoTime()
                    map.withPlatformMap { this.map.requestRepaint() }
                    await { renders.any { (time, stats) -> time > repaintAt && stats.mode == RenderStats.Mode.Partial } }
                    val opacities = map.withPlatformMap {
                        radarIds.associateWith { this.map.layerProperty(it, "raster-opacity")!!.decodeToString().toFloat() }
                    }
                    assertEquals("Front must not be hidden while native responses are blocked", 0.75f, opacities.getValue(frontLayer), 0f)
                    assertEquals("Staging must not be promoted while native responses are blocked", 0.001f, opacities.getValue(staging), 0f)
                    assertEquals(first.identity, controller.state.value.displayed)
                    assertTrue("Blocked native delivery must keep the red front visible", isRed(pixel()))
                }
                val releaseAt = System.nanoTime()
                gate.complete(Unit)
                val transitionPixels = mutableListOf<Int>()
                withTimeout(30_000) {
                    while (controller.state.value.displayed != second.identity || transitionPixels.none(::isGreen)) {
                        transitionPixels += pixel()
                        delay(35)
                    }
                }
                repeat(3) { transitionPixels += pixel(); delay(35) }
                val acknowledgedAt = System.nanoTime()
                val eligibleRenders = renders.filter { (time, stats) ->
                    time > releaseAt && time < acknowledgedAt && stats.mode == RenderStats.Mode.Full
                }.map { it.second.frameCount }
                assertTrue("Native Full renders must follow installation, coverage and native delivery: $eligibleRenders",
                    installedAt < coverageAt && coverageAt < releaseAt && eligibleRenders.size >= 4 &&
                        eligibleRenders.zipWithNext().all { it.first < it.second })
                assertTrue("No sampled handoff image may be a blank blue map: $transitionPixels",
                    transitionPixels.all { isRed(it) || isGreen(it) })
                println("RADAR_BUFFERED_PENDING_PIXELS=$pendingPixels")
                println("RADAR_BUFFERED_TRANSITION_PIXELS=$transitionPixels")
                println("RADAR_BUFFERED_POST_DELIVERY_FULL_FRAMES=$eligibleRenders")
                await { runBlocking { map.withPlatformMap { this.map.styleLayerIds().count { it.startsWith("hlu-radar-layer-") } == 1 } } }
                assertTrue(nativeCalls.isNotEmpty() && nativeCalls.all { it.storagePolicy == MapResourceLoadRequest.StoragePolicy.Volatile })
                scenario.onActivity { controller.update(null, RadarProvider.RAINVIEWER) }
                await { runBlocking { map.withPlatformMap { this.map.styleLayerIds().none { it.startsWith("hlu-radar-layer-") } } } }
            }
        } finally {
            downloadGate.complete(Unit)
            gate.complete(Unit)
            runtime.close()
            withTimeout(20_000) { runtime.awaitClosed() }
            backendRef.get()?.closeAfterMap()
            ui.cancel()
            io.cancel()
        }
    }

    @Test
    fun composedRendererRejectsStaleFramesAndSurvivesStyleAndOpacityChanges() = runBlocking {
        val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val eventScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val frameEvents = ConcurrentLinkedQueue<Pair<Long, RenderStats>>()
        val registry = RadarTileRegistry()
        val scheduler = RadarNetworkScheduler(io)
        val calls = ConcurrentLinkedQueue<String>()
        val colors = mapOf("1700000001" to png(Color.RED), "1700000002" to png(Color.YELLOW),
            "1700000003" to png(Color.GREEN), "1700000004" to png(Color.MAGENTA))
        val loader = RadarTileLoader(io, scheduler, RadarTileTransport { key, _ ->
            calls.add(key.url)
            val color = colors.entries.first { key.url.contains(it.key) }.value
            RadarTileHttpResult(200, RadarTilePayload(color, System.currentTimeMillis() + 60_000))
        })
        val bridge = RadarMapResourceProvider(registry, loader, scheduler)
        val gateB = CompletableDeferred<Unit>()
        val gateD = CompletableDeferred<Unit>()
        val attempts = ConcurrentLinkedQueue<String>()
        val nativeProvider = object : MapResourceProvider {
            override fun accepts(request: MapResourceRequest) = bridge.accepts(request)
            override suspend fun load(request: MapResourceLoadRequest): MapResourceLoad {
                val remote = registry.resolve(request.url)?.url.orEmpty()
                attempts.add(remote)
                if (remote.contains("1700000002")) gateB.await()
                if (remote.contains("1700000004")) gateD.await()
                return bridge.load(request)
            }
        }
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
            .resolve("radar-composed-${System.nanoTime()}").apply { mkdirs() }
        val runtime = createMapRuntime(radarMapRuntimeOptions(cache, bridge).copy(resourceProvider = nativeProvider))
        val resources = WeatherRadarResources(registry, loader, bridge)
        val mapRef = AtomicReference<MapState?>()
        val display = AtomicReference(RadarDisplayState())
        val displayHistory = ConcurrentLinkedQueue<RadarDisplayState>()
        fun config(time: Long, opacity: Float = 0.75f) = WeatherMapRadarConfig(
            "https://tilecache.rainviewer.com",
            RadarFrame(time, "/v2/radar/$time", RadarFrameType.OBSERVED, RadarProvider.RAINVIEWER), opacity
        )
        val radar = mutableStateOf<WeatherMapRadarConfig?>(config(1700000001))
        val selected = mutableStateOf(RadarProvider.RAINVIEWER)
        val managed = mutableStateOf(false)
        fun style(epoch: Int) = BaseStyle.Json("""{"version":8,"sources":{
            "empty":{"type":"geojson","data":{"type":"FeatureCollection","features":[]}}},
            "layers":[{"id":"base-$epoch","type":"background","paint":{"background-color":"#0000ff"}},
            {"id":"labels","type":"symbol","source":"empty"}]}""")
        val style = mutableStateOf(style(1))
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.setContent {
                        val map = rememberMapState(runtime = runtime, baseStyle = style.value,
                            initialCameraPosition = CameraPosition(zoom = 0.0)) {
                            if (managed.value) BackgroundLayer("compose-owned", opacity = const(0f))
                        }
                        SideEffect { mapRef.set(map) }
                        WeatherRadarRenderer(map, resources, radar.value, selected.value) {
                            display.set(it)
                            displayHistory.add(it)
                        }
                        MaplibreMap(Modifier.fillMaxSize(), state = map, overlay = {})
                    }
                }
                var phase = "initial-frame"
                fun phase(name: String) {
                    phase = name
                    println("RADAR_COMPOSED_PHASE=$name")
                }
                try {
                val first = config(1700000001)
                val third = config(1700000003)
                await { display.get().displayed == first.frame.identity(first.host) }
                println("RADAR_COMPOSED_STEP=first-rendered")
                phase("first-red-pixel")
                awaitColor(red = true)
                val map = requireNotNull(mapRef.get())
                eventScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    map.events.collect { event ->
                        (event as? MapEvent.FrameRendered)?.stats?.let { frameEvents.add(System.nanoTime() to it) }
                    }
                }
                scenario.onActivity { managed.value = true }
                phase("compose-owned-layer")
                await { runBlocking { map.withPlatformMap { this.map.styleLayerExists("compose-owned") } } }
                val order = map.withPlatformMap { this.map.styleLayerIds() }
                assertTrue("Radar must coexist below labels and Compose layer: $order",
                    order.indexOfFirst { it.startsWith("hlu-radar-layer-") } < order.indexOf("labels") &&
                        order.indexOf("labels") < order.indexOf("compose-owned"))

                scenario.onActivity { radar.value = config(1700000002) }
                phase("frame-b-native-demand")
                await { attempts.any { it.contains("1700000002") } }
                scenario.onActivity { radar.value = third }
                phase("frame-c-display-ack")
                await { display.get().displayed == third.frame.identity(third.host) }
                println("RADAR_COMPOSED_STEP=rapid-selection-rendered")
                phase("frame-c-green-pixel")
                awaitColor(red = false)
                gateB.complete(Unit)
                repeat(5) { assertTrue(isGreen(pixel())); delay(80) }
                assertEquals(third.frame.identity(third.host), display.get().displayed)

                scenario.onActivity { radar.value = config(1700000004) }
                phase("frame-d-native-demand")
                await { attempts.any { it.contains("1700000004") } }
                scenario.onActivity { radar.value = null }
                phase("hide-cleared")
                await { runBlocking { map.withPlatformMap { this.map.styleLayerIds().none { it.startsWith("hlu-radar-layer-") } } } }
                gateD.complete(Unit)
                delay(300)
                assertTrue(map.withPlatformMap { this.map.styleLayerIds().none { it.startsWith("hlu-radar-layer-") } })
                assertEquals(null, display.get().displayed)
                println("RADAR_COMPOSED_STEP=hide-cleared")

                val beforeRestore = calls.count { it.contains("1700000003") }
                val beforeNativeRestore = attempts.count { it.contains("1700000003") }
                scenario.onActivity { radar.value = third }
                phase("cache-restore")
                await { display.get().displayed == third.frame.identity(third.host) }
                awaitColor(red = false)
                assertEquals("Restored cached imagery must not redownload", beforeRestore, calls.count { it.contains("1700000003") })
                println("RADAR_COMPOSED_STEP=cache-restored")
                scenario.onActivity { radar.value = third.copy(opacity = 0f) }
                phase("opacity-zero")
                await { pixel().let { Color.blue(it) > 200 && Color.green(it) < 50 } }
                scenario.onActivity { radar.value = third.copy(opacity = 0.75f) }
                phase("opacity-restored")
                awaitColor(red = false)
                assertEquals("Positive restoration must reuse app/native memory", beforeRestore, calls.count { it.contains("1700000003") })
                println("RADAR_COMPOSED_STEP=opacity-restored")
                val afterNativeRestore = attempts.count { it.contains("1700000003") }

                val oldSourceIds = map.withPlatformMap { this.map.styleLayerIds().filter { it.startsWith("hlu-radar-layer-") } }
                scenario.onActivity { style.value = style(2) }
                phase("style-reload")
                await { runBlocking { map.withPlatformMap { this.map.styleLayerExists("base-2") } } }
                await {
                    display.get().displayed == third.frame.identity(third.host) && runBlocking {
                        map.withPlatformMap { this.map.styleLayerIds().any { it.startsWith("hlu-radar-layer-") && it !in oldSourceIds } }
                    }
                }
                awaitColor(red = false)
                scenario.onActivity { managed.value = false }
                await { runBlocking { map.withPlatformMap { !this.map.styleLayerExists("compose-owned") } } }
                val reloaded = map.withPlatformMap { this.map.styleLayerIds() }
                assertEquals(1, reloaded.count { it.startsWith("hlu-radar-layer-") })
                assertTrue(reloaded.indexOfFirst { it.startsWith("hlu-radar-layer-") } < reloaded.indexOf("labels"))
                println("RADAR_COMPOSED_STEP=style-reloaded")
                val beforeStop = frameEvents.filter { it.second.mode == RenderStats.Mode.Full }.lastOrNull()?.second?.frameCount
                scenario.moveToState(Lifecycle.State.CREATED)
                phase("stopped-cleared")
                await { runBlocking { map.withPlatformMap { this.map.styleLayerIds().none { it.startsWith("hlu-radar-layer-") } } } }
                println("RADAR_COMPOSED_STEP=stopped-cleared")
                val resumedAt = System.nanoTime()
                scenario.moveToState(Lifecycle.State.RESUMED)
                phase("resume")
                try {
                    await { display.get().displayed == third.frame.identity(third.host) }
                    println("RADAR_COMPOSED_STEP=resumed-ack")
                    awaitColor(red = false)
                } catch (error: Exception) {
                    val afterResume = frameEvents.filter { it.first >= resumedAt && it.second.mode == RenderStats.Mode.Full }
                        .map { it.second.frameCount }
                    println("RADAR_COMPOSED_RESUME_FAILURE state=${display.get()} style=${map.style.loadState} " +
                        "closed=${map.isClosed} viewport=${map.viewport} layers=${map.withPlatformMap { this.map.styleLayerIds() }}")
                    println("RADAR_COMPOSED_RESUME_FULL_COUNTS before=$beforeStop after=$afterResume")
                    throw error
                }
                println("RADAR_COMPOSED_STEP=resumed-rendered")
                val afterResume = frameEvents.filter { it.first >= resumedAt && it.second.mode == RenderStats.Mode.Full }
                    .map { it.second.frameCount }
                assertTrue("Resume must be acknowledged after native Full render events", afterResume.isNotEmpty())
                println("RADAR_COMPOSED_RESUME_FULL_COUNTS before=$beforeStop after=$afterResume")
                scenario.onActivity { selected.value = RadarProvider.LIBREWXR }
                phase("provider-cleared")
                await { runBlocking { map.withPlatformMap { this.map.styleLayerIds().none { it.startsWith("hlu-radar-layer-") } } } }
                assertEquals(null, display.get().displayed)
                println("RADAR_COMPOSED_STYLE_RELOAD_ORDER=$reloaded")
                println("RADAR_COMPOSED_GREEN_NETWORK_CALLS=$beforeRestore")
                println("RADAR_COMPOSED_GREEN_NATIVE_CALLS_BEFORE_AFTER_RESTORE=$beforeNativeRestore,$afterNativeRestore")
                } catch (error: Exception) {
                    val map = mapRef.get()
                    val layers = runCatching {
                        withTimeout(5_000) {
                            map?.withPlatformMap {
                                this.map.styleLayerIds().map { id ->
                                    if (id.startsWith("hlu-radar-layer-")) {
                                        "$id:${this.map.layerProperty(id, "raster-opacity")?.decodeToString()}"
                                    } else id
                                }
                            }
                        }
                    }.getOrNull()
                    val lastPixel = runCatching { pixel() }.getOrNull()
                    println("RADAR_COMPOSED_FAILURE phase=$phase state=${display.get()} " +
                        "style=${map?.style?.loadState} layers=$layers pixel=$lastPixel rateLimitedUntil=${scheduler.rateLimitedUntil.value}")
                    val callsByFrame = calls.groupingBy { it.substringAfter("/radar/").substringBefore('/') }.eachCount()
                    val attemptsByFrame = attempts.groupingBy { it.substringAfter("/radar/").substringBefore('/') }.eachCount()
                    println("RADAR_COMPOSED_FAILURE_CALLS=$callsByFrame")
                    println("RADAR_COMPOSED_FAILURE_ATTEMPTS=$attemptsByFrame")
                    println("RADAR_COMPOSED_FAILURE_STATES=${displayHistory.toList().takeLast(15)}")
                    println("RADAR_COMPOSED_FAILURE_FRAMES=${frameEvents.toList().takeLast(15)}")
                    throw error
                }
            }
        } finally {
            gateB.complete(Unit)
            gateD.complete(Unit)
            runtime.close()
            withTimeout(20_000) { runtime.awaitClosed() }
            eventScope.cancel()
            io.cancel()
        }
    }

    private suspend fun await(condition: () -> Boolean) = withTimeout(30_000) {
        while (!condition()) delay(30)
    }

    private suspend fun awaitColor(red: Boolean) {
        var lastPixel = 0
        try {
            withTimeout(30_000) {
                while (true) {
                    lastPixel = pixel()
                    if (if (red) isRed(lastPixel) else isGreen(lastPixel)) return@withTimeout
                    delay(50)
                }
            }
        } catch (error: TimeoutCancellationException) {
            println("RADAR_COLOR_TIMEOUT red=$red pixel=$lastPixel")
            throw error
        }
    }

    private fun pixel(): Int = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { image ->
        image.getPixel(image.width / 2, image.height / 2).also { image.recycle() }
    }

    private fun isRed(pixel: Int) = Color.red(pixel) > 130 && Color.green(pixel) < 60 && Color.blue(pixel) < 120
    private fun isGreen(pixel: Int) = Color.green(pixel) > 130 && Color.red(pixel) < 60 && Color.blue(pixel) < 120

    private fun png(color: Int): ByteArray = ByteArrayOutputStream().use { out ->
        Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).apply {
            eraseColor(color)
            compress(Bitmap.CompressFormat.PNG, 100, out)
            recycle()
        }
        out.toByteArray()
    }
}
