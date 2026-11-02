package net.droopia.hluweather.data.network

import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import net.droopia.hluweather.MainActivity
import net.droopia.hluweather.data.model.RadarFrame
import net.droopia.hluweather.data.model.RadarFrameType
import net.droopia.hluweather.data.model.RadarProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.BackgroundLayer
import org.maplibre.compose.map.DelicateMapApi
import org.maplibre.compose.map.MapState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.StyleLoadState
import org.maplibre.compose.map.createMapRuntime
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.map.withPlatformMap
import org.maplibre.compose.resource.MapResourceLoad
import org.maplibre.compose.resource.MapResourceLoadRequest
import org.maplibre.compose.resource.MapResourceProvider
import org.maplibre.compose.resource.MapResourceRequest
import org.maplibre.compose.sources.MutableSourceHandle
import org.maplibre.compose.sources.RasterTileSource
import org.maplibre.compose.sources.TileSetOptions
import org.maplibre.compose.sources.isVolatile
import org.maplibre.compose.style.BaseStyle

/** Public-API ownership proof for task 4.1; this is not the production radar renderer. */
@OptIn(DelicateMapApi::class)
@RunWith(AndroidJUnit4::class)
class RadarDynamicVolatileRuntimeTest {
    @Test
    fun imperativeVolatileSourcesAndNativeLayersSupportFrameChangesAndStyleReload() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val registry = RadarTileRegistry()
        val first = registry.register("https://tilecache.rainviewer.com",
            RadarFrame(1_700_000_001, "/v2/radar/1700000001", RadarFrameType.OBSERVED, RadarProvider.RAINVIEWER))
        val second = registry.register("https://tilecache.rainviewer.com",
            RadarFrame(1_700_000_002, "/v2/radar/1700000002", RadarFrameType.OBSERVED, RadarProvider.RAINVIEWER))
        val red = png(Color.RED)
        val green = png(Color.GREEN)
        val scheduler = RadarNetworkScheduler(scope)
        val loader = RadarTileLoader(scope, scheduler, RadarTileTransport { key, _ ->
            val image = if (key.url.contains("1700000001")) red else green
            val cacheable = key.url.contains("1700000002")
            RadarTileHttpResult(200, RadarTilePayload(image, System.currentTimeMillis() + 60_000,
                cacheable = cacheable))
        })
        val bridge = RadarMapResourceProvider(registry, loader, scheduler)
        val calls = ConcurrentLinkedQueue<Pair<String, MapResourceLoadRequest.StoragePolicy>>()
        val guarded = object : MapResourceProvider {
            override fun accepts(request: MapResourceRequest): Boolean = bridge.accepts(request)

            override suspend fun load(request: MapResourceLoadRequest): MapResourceLoad {
                calls.add(request.url to request.storagePolicy)
                return bridge.load(request)
            }
        }
        CacheableBasemapServer(png(Color.BLUE)).use { server ->
            fun style(epoch: Int) = BaseStyle.Json("""{"version":8,"sources":{
                "base":{"type":"raster","tiles":["http://127.0.0.1:${server.port}/base/{z}/{x}/{y}.png"],"tileSize":256,"maxzoom":7}
              },"layers":[
                {"id":"base","type":"raster","source":"base","paint":{"raster-fade-duration":0}},
                {"id":"label-anchor","type":"background","paint":{"background-opacity":0}},
                {"id":"epoch-$epoch","type":"background","paint":{"background-opacity":0}}
              ]}""".trimIndent())

            val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
                .resolve("radar-dynamic-volatile-${System.nanoTime()}").apply { mkdirs() }
            val runtime = createMapRuntime(radarMapRuntimeOptions(cache, bridge).copy(resourceProvider = guarded))
            val mapRef = AtomicReference<MapState?>()
            val baseStyle = mutableStateOf(style(1))
            val composePhase = mutableIntStateOf(0)
            try {
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    scenario.onActivity { activity ->
                        activity.setContent {
                            val mapState = rememberMapState(
                                runtime = runtime,
                                baseStyle = baseStyle.value,
                                initialCameraPosition = CameraPosition(zoom = 0.0)
                            ) {
                                if (composePhase.intValue != 2) {
                                    BackgroundLayer("managed-first", opacity = const(0f))
                                }
                                if (composePhase.intValue != 0) {
                                    BackgroundLayer("managed-second", opacity = const(0f))
                                }
                            }
                            SideEffect { mapRef.set(mapState) }
                            MaplibreMap(modifier = Modifier.fillMaxSize(), state = mapState, overlay = {})
                        }
                    }
                    await("initial live style and basemap") {
                        mapRef.get()?.style?.loadState == StyleLoadState.Ready && server.requests.get() > 0
                    }
                    val map = requireNotNull(mapRef.get())
                    await("initial Compose-owned layer") { map.nativeLayerExists("managed-first") }
                    assertTrue("No radar layer must mean no radar attempts", calls.isEmpty())

                    fun addVolatile(id: String, source: RegisteredRadarSource): MutableSourceHandle {
                        val result = AtomicReference<MutableSourceHandle?>()
                        scenario.onActivity {
                            val handle = map.style.sources.add(RasterTileSource(id, listOf(source.template),
                                options = TileSetOptions(maxZoom = 7), tileSize = 256))
                            handle.isVolatile = true
                            check(handle.isVolatile)
                            result.set(handle)
                        }
                        return requireNotNull(result.get())
                    }

                    val firstHandle = addVolatile("radar-source-a", first)
                    delay(250)
                    assertTrue("Adding an unreferenced source must not demand radar tiles", calls.isEmpty())
                    map.addRadarLayer("radar-layer-a", "radar-source-a", first.template)
                    await("first volatile frame requests") { calls.any { it.first.startsWith(first.template.substringBefore("{z}")) } }
                    awaitScreenColor(Color.RED)

                    scenario.onActivity { composePhase.intValue = 1 }
                    await("second Compose-owned layer") { map.nativeLayerExists("managed-second") }
                    map.assertLayerOrder("radar-layer-a", "label-anchor", "managed-second")

                    val secondHandle = addVolatile("radar-source-b", second)
                    assertFalse("Second source has no layer yet", calls.any { it.first.startsWith(second.template.substringBefore("{z}")) })
                    map.addRadarLayer("radar-layer-b", "radar-source-b", second.template)
                    await("second volatile frame requests") { calls.any { it.first.startsWith(second.template.substringBefore("{z}")) } }
                    awaitScreenColor(Color.GREEN)
                    map.withPlatformMap {
                        assertTrue(this.map.styleSourceExists("radar-source-a"))
                        assertTrue(this.map.styleSourceExists("radar-source-b"))
                        assertTrue(this.map.removeStyleLayer("radar-layer-a"))
                    }
                    scenario.onActivity { assertTrue(firstHandle.remove()) }
                    scenario.onActivity { composePhase.intValue = 2 }
                    await("Compose removal alongside surviving radar") {
                        !map.nativeLayerExists("managed-first") && map.nativeLayerExists("managed-second")
                    }
                    map.assertLayerOrder("radar-layer-b", "label-anchor", "managed-second")
                    awaitScreenColor(Color.GREEN)

                    val secondCallsBeforeReload = calls.count { it.first.startsWith(second.template.substringBefore("{z}")) }
                    scenario.onActivity { baseStyle.value = style(2) }
                    await("replacement style generation") {
                        map.style.loadState == StyleLoadState.Ready && map.nativeLayerExists("epoch-2")
                    }
                    map.withPlatformMap {
                        assertFalse("Old radar layer must not survive a style reload", this.map.styleLayerExists("radar-layer-b"))
                        assertFalse("Old radar source must not survive a style reload", this.map.styleSourceExists("radar-source-b"))
                    }
                    scenario.onActivity {
                        assertTrue("Old source handle must expire with the style", runCatching { secondHandle.isVolatile }.isFailure)
                    }
                    assertTrue("An old-generation layer command must fail closed",
                        runCatching { map.addRadarLayer("stale-layer", "radar-source-b", second.template) }.isFailure)
                    // IDs are unique to each source/style generation, so a stale command cannot
                    // attach a layer to an unrelated newly created source with the same slot name.
                    val reloadedHandle = addVolatile("radar-source-b-reloaded", second)
                    map.addRadarLayer("radar-layer-b-reloaded", "radar-source-b-reloaded", second.template)
                    await("same frame requested after style reload") {
                        calls.count { it.first.startsWith(second.template.substringBefore("{z}")) } > secondCallsBeforeReload
                    }
                    awaitScreenColor(Color.GREEN)
                    scenario.onActivity { composePhase.intValue = 0 }
                    await("Compose reconciliation in replacement style") {
                        map.nativeLayerExists("managed-first") && !map.nativeLayerExists("managed-second")
                    }
                    map.assertLayerOrder("radar-layer-b-reloaded", "label-anchor", "managed-first")
                    awaitScreenColor(Color.GREEN)
                    map.withPlatformMap { assertTrue(this.map.removeStyleLayer("radar-layer-b-reloaded")) }
                    scenario.onActivity { assertTrue(reloadedHandle.remove()) }
                }
            } finally {
                runtime.close()
                withTimeout(20_000) { runtime.awaitClosed() }
                scope.cancel()
            }

            assertTrue("All radar requests through shutdown must be volatile: $calls", calls.isNotEmpty() &&
                calls.all { it.second == MapResourceLoadRequest.StoragePolicy.Volatile })
            SQLiteDatabase.openDatabase(cache.resolve(RADAR_MAP_CACHE_FILENAME).path,
                null, SQLiteDatabase.OPEN_READONLY).use { db ->
                fun count(prefix: String): Int = db.rawQuery("SELECT count(*) FROM tiles WHERE url_template LIKE ?",
                    arrayOf("$prefix%")).use { cursor ->
                    check(cursor.moveToFirst())
                    cursor.getInt(0)
                }
                assertTrue("Cacheable basemap must retain native persistence", count("http://127.0.0.1:${server.port}/base/") > 0)
                assertEquals("First no-store radar frame must not persist", 0, count(first.template.substringBefore("{z}")))
                assertEquals("Replaced/reloaded cacheable radar frame must not persist", 0, count(second.template.substringBefore("{z}")))
            }
        }
    }

    private suspend fun MapState.nativeLayerExists(id: String): Boolean = withPlatformMap { map.styleLayerExists(id) }

    private suspend fun MapState.addRadarLayer(layer: String, source: String, expectedTemplate: String) {
        withPlatformMap {
            val info = map.styleSourceInfo(source)
            check(info?.volatileSource == true && info.tileJson?.tileUrls == listOf(expectedTemplate)) {
                "Source is missing, changed or not volatile"
            }
            check(!map.styleLayerExists(layer))
            val json = """{"id":"$layer","type":"raster","source":"$source","paint":{"raster-fade-duration":0}}"""
            map.addStyleLayerJson(json.toByteArray(), "label-anchor")
            map.requestRepaint()
        }
    }

    private suspend fun MapState.assertLayerOrder(vararg expected: String) {
        val ids = withPlatformMap { map.styleLayerIds() }
        val indices = expected.map(ids::indexOf)
        assertTrue("Incorrect layer order $expected in $ids", indices.all { it >= 0 } && indices.zipWithNext().all { it.first < it.second })
        assertEquals(StyleLoadState.Ready, style.loadState)
    }

    private suspend fun await(description: String, condition: suspend () -> Boolean) {
        val completed = withTimeoutOrNull(30_000) {
            while (!condition()) delay(50)
            true
        } ?: false
        assertTrue("Timed out waiting for $description", completed)
    }

    private suspend fun awaitScreenColor(expected: Int) {
        var actual = 0
        val found = withTimeoutOrNull(30_000) {
            while (true) {
                val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                if (screenshot != null) {
                    try {
                        actual = screenshot.getPixel(screenshot.width / 2, screenshot.height / 2)
                        val channel = if (expected == Color.RED) Color.red(actual) else Color.green(actual)
                        val other = if (expected == Color.RED) Color.green(actual) else Color.red(actual)
                        if (channel > 200 && other < 50 && Color.blue(actual) < 50) return@withTimeoutOrNull true
                    } finally {
                        screenshot.recycle()
                    }
                }
                delay(100)
            }
            @Suppress("UNREACHABLE_CODE")
            false
        } ?: false
        assertTrue("Timed out waiting for map color $expected, last center pixel was $actual", found)
    }

    private fun png(color: Int): ByteArray = ByteArrayOutputStream().use { output ->
        Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).apply {
            eraseColor(color)
            compress(Bitmap.CompressFormat.PNG, 100, output)
            recycle()
        }
        output.toByteArray()
    }

    private class CacheableBasemapServer(private val tile: ByteArray) : AutoCloseable {
        private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val port: Int = server.localPort
        val requests = AtomicInteger()
        private val worker = thread(name = "dynamic-volatile-basemap", isDaemon = true) {
            while (!server.isClosed) {
                try {
                    server.accept().use { socket ->
                        socket.soTimeout = 10_000
                        val reader = socket.getInputStream().bufferedReader()
                        reader.readLine()
                        while (!reader.readLine().isNullOrEmpty()) Unit
                        requests.incrementAndGet()
                        val output = socket.getOutputStream()
                        output.write("HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nCache-Control: public, max-age=3600\r\nContent-Length: ${tile.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        output.write(tile)
                        output.flush()
                    }
                } catch (error: Exception) {
                    if (!server.isClosed) throw error
                }
            }
        }

        override fun close() {
            server.close()
            worker.join(5_000)
        }
    }
}
