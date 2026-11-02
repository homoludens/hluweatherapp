package net.droopia.hluweather.data.network

import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toPixelMap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.droopia.hluweather.data.model.RadarFrame
import net.droopia.hluweather.data.model.RadarFrameType
import net.droopia.hluweather.data.model.RadarProvider
import net.droopia.hluweather.data.model.RadarProviderDescriptors
import net.droopia.hluweather.data.model.RadarTileCoverage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.Viewport
import org.maplibre.compose.layers.RasterLayer
import org.maplibre.compose.map.LocalViewport
import org.maplibre.compose.map.MapSnapshotRequest
import org.maplibre.compose.map.createMapRuntime
import org.maplibre.compose.resource.MapResourceKind
import org.maplibre.compose.resource.MapResourceLoad
import org.maplibre.compose.resource.MapResourceLoadRequest
import org.maplibre.compose.resource.MapResourceProvider
import org.maplibre.compose.resource.MapResourceRequest
import org.maplibre.compose.sources.RasterTileSource
import org.maplibre.compose.sources.getBaseSource
import org.maplibre.compose.sources.isVolatile
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position

/** Installed OpenGL renderer, controlled radar transport and local unrelated HTTP basemap. */
@RunWith(AndroidJUnit4::class)
class RadarResourceRuntimeTest {
    @Test
    fun volatileHookRendersAndCoversNativeTilesAcrossCameraCases() = runBlocking {
        val cameras = listOf(
            CameraCase(RadarProvider.RAINVIEWER, CameraPosition(bearing = 32.0, target = Position(179.5, 20.0), zoom = 3.6), 5),
            CameraCase(RadarProvider.RAINVIEWER, CameraPosition(bearing = 0.0, target = Position(-179.5, -20.0), zoom = 3.3), 4),
            CameraCase(RadarProvider.RAINVIEWER, CameraPosition(bearing = 35.0, tilt = 45.0, target = Position(179.5, 50.0), zoom = 3.6), 5),
            CameraCase(RadarProvider.RAINVIEWER, CameraPosition(bearing = 12.0, tilt = 60.0, target = Position(24.9, 60.2), zoom = 9.0), 7),
            CameraCase(RadarProvider.RAINVIEWER, CameraPosition(bearing = 45.0, target = Position(25.0, 60.0), zoom = 9.0), 7),
            CameraCase(RadarProvider.LIBREWXR, CameraPosition(bearing = 18.0, target = Position(540.0, 0.0), zoom = 0.0), 1),
            CameraCase(RadarProvider.LIBREWXR, CameraPosition(bearing = 27.0, target = Position(24.9, 60.2), zoom = 14.0), 12)
        )
        LocalTileServer(png(Color.BLUE)).use { server ->
            for ((index, case) in cameras.withIndex()) {
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                val registry = RadarTileRegistry()
                val source = registry.register("https://tilecache.rainviewer.com",
                    RadarFrame(index + 1L, "/v2/radar/${index + 1}", RadarFrameType.OBSERVED, case.provider))
                val scheduler = RadarNetworkScheduler(scope)
                val network = ConcurrentLinkedQueue<RadarTileKey>()
                val loader = RadarTileLoader(scope, scheduler, RadarTileTransport { key, _ ->
                    network.add(key)
                    RadarTileHttpResult(200, RadarTilePayload(png(Color.RED), System.currentTimeMillis() + 60_000))
                })
                val bridge = RadarMapResourceProvider(registry, loader, scheduler)
                val accepted = ConcurrentLinkedQueue<MapResourceLoadRequest>()
                val declined = ConcurrentLinkedQueue<MapResourceRequest>()
                val results = ConcurrentLinkedQueue<MapResourceLoad>()
                val recording = object : MapResourceProvider {
                    override fun accepts(request: MapResourceRequest): Boolean = bridge.accepts(request).also {
                        if (!it) declined.add(request)
                    }
                    override suspend fun load(request: MapResourceLoadRequest): MapResourceLoad {
                        accepted.add(request)
                        return bridge.load(request).also(results::add)
                    }
                }
                server.style = style(server.port, source.template, maxZoom = RadarProviderDescriptors[case.provider].nativeMaxZoom)
                val cache = testCache("coverage-$index")
                val runtime = createMapRuntime(radarMapRuntimeOptions(cache, bridge).copy(resourceProvider = recording))
                val viewport = AtomicReference<Viewport?>()
                val showRadar = AtomicBoolean(false)
                val snapshotter = runtime.createSnapshotter(BaseStyle.Uri("http://127.0.0.1:${server.port}/style.json")) {
                    val current = LocalViewport.current
                    SideEffect { viewport.set(current) }
                    if (showRadar.get()) getBaseSource<RasterTileSource>("radar")?.let { RasterLayer("radar-layer", it) }
                }
                try {
                    withTimeout(60_000) { snapshotter.capture(MapSnapshotRequest(512, 512, cameraPosition = case.camera)) }
                    assertTrue("No layer means no radar demand", accepted.isEmpty())
                    val bounds = radarViewportBounds(requireNotNull(viewport.get()), case.camera)
                    val coverage = RadarTileCoverage.tiles(bounds, RadarProviderDescriptors[case.provider])
                    assertEquals(setOf(case.nativeZoom), coverage.map { it.zoom }.toSet())
                    // Real native display must reuse the exact bytes prefetched by the shared loader.
                    coverage.forEach { loader.load(source.tile(it), speculative = true) }
                    val prefetched = network.size
                    val handle = requireNotNull(snapshotter.style.sources["radar"]?.asMutable)
                    handle.isVolatile = true
                    assertTrue(handle.isVolatile)
                    showRadar.set(true)
                    val image = withTimeout(60_000) {
                        snapshotter.capture(MapSnapshotRequest(512, 512, cameraPosition = case.camera))
                    }
                    val center = image.toPixelMap()[256, 256]
                    assertTrue("Radar must actually render over the basemap: $center", center.red > 0.9f && center.blue < 0.1f)
                    assertTrue("Native style and basemap must be declined", declined.any { it.kind == MapResourceKind.Style } && declined.any { it.url.contains("/base/") })
                    assertTrue("Radar callbacks must be volatile", accepted.isNotEmpty() && accepted.all { it.storagePolicy == MapResourceLoadRequest.StoragePolicy.Volatile })
                    assertTrue("The real hook must return bytes", results.any { it is MapResourceLoad.Bytes })
                    val emitted = accepted.mapNotNull { registry.resolve(it.url)?.coordinate }.toSet()
                    assertTrue("No native radar tiles emitted", emitted.isNotEmpty())
                    assertEquals(setOf(case.nativeZoom), emitted.map { it.zoom }.toSet())
                    assertTrue("Viewport coverage misses native tiles ${emitted - coverage.toSet()} for $bounds", coverage.containsAll(emitted))
                    assertEquals("Native display must reuse prefetched bytes: ${accepted.map { it.loadingMethod }}", prefetched, network.size)
                } finally {
                    snapshotter.close()
                    withTimeout(20_000) { snapshotter.awaitClosed() }
                    runtime.close()
                    withTimeout(20_000) { runtime.awaitClosed() }
                    scope.cancel()
                }
                assertTrue("Native basemap must persist", rows(cache, "http://127.0.0.1:${server.port}/base/") > 0)
                assertEquals("Cacheable radar must not be persisted natively", 0, rows(cache, source.template.substringBefore("{z}")))
            }
        }
    }

    @Test
    fun noStoreAndCacheableRadarStayOffDiskWhileBasemapSurvivesRuntimeRestart() = runBlocking {
        val registry = RadarTileRegistry()
        val noStore = registry.register("https://tilecache.rainviewer.com",
            RadarFrame(1, "/v2/radar/no-store", RadarFrameType.OBSERVED, RadarProvider.RAINVIEWER))
        val cacheable = registry.register("https://tilecache.rainviewer.com",
            RadarFrame(2, "/v2/radar/cacheable", RadarFrameType.OBSERVED, RadarProvider.RAINVIEWER))
        LocalTileServer(png(Color.BLUE)).use { server ->
            server.style = style(server.port, noStore.template, cacheable.template)
            val cache = testCache("restart")
            var firstBaseRequests = 0
            repeat(2) { run ->
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                val scheduler = RadarNetworkScheduler(scope)
                val requests = ConcurrentLinkedQueue<RadarTileKey>()
                val loader = RadarTileLoader(scope, scheduler, RadarTileTransport { key, _ ->
                    requests.add(key)
                    val keep = key.url.contains("/cacheable/")
                    RadarTileHttpResult(200, RadarTilePayload(png(Color.RED), System.currentTimeMillis() + 60_000,
                        cacheable = keep))
                })
                val bridge = RadarMapResourceProvider(registry, loader, scheduler)
                val runtime = createMapRuntime(radarMapRuntimeOptions(cache, bridge))
                val show = AtomicBoolean(false)
                val snapshotter = runtime.createSnapshotter(BaseStyle.Uri("http://127.0.0.1:${server.port}/style.json")) {
                    if (show.get()) {
                        getBaseSource<RasterTileSource>("radar")?.let { RasterLayer("radar-layer", it) }
                        getBaseSource<RasterTileSource>("cacheable")?.let { RasterLayer("cacheable-layer", it) }
                    }
                }
                try {
                    withTimeout(60_000) { snapshotter.capture(MapSnapshotRequest(256, 256)) }
                    assertTrue(requests.isEmpty())
                    listOf("radar", "cacheable").forEach { id ->
                        val handle = requireNotNull(snapshotter.style.sources[id]?.asMutable)
                        handle.isVolatile = true
                        assertTrue(handle.isVolatile)
                    }
                    show.set(true)
                    val image = withTimeout(60_000) { snapshotter.capture(MapSnapshotRequest(256, 256)) }
                    assertTrue("Radar did not render", image.toPixelMap()[128, 128].red > 0.9f)
                    val noStoreKey = requests.first { it.url.contains("/no-store/") }
                    val cacheableKey = requests.first { it.url.contains("/cacheable/") }
                    val before = requests.size
                    loader.load(cacheableKey)
                    assertEquals("Cacheable radar should be reused from app memory", before, requests.size)
                    loader.load(noStoreKey)
                    assertEquals("No-store radar must not be reused from app memory", before + 1, requests.size)
                } finally {
                    snapshotter.close()
                    withTimeout(20_000) { snapshotter.awaitClosed() }
                    runtime.close()
                    withTimeout(20_000) { runtime.awaitClosed() }
                    scope.cancel()
                }
                if (run == 0) firstBaseRequests = server.baseRequests.get()
                else assertEquals("A new runtime should reuse the basemap disk cache", firstBaseRequests, server.baseRequests.get())
            }
            assertTrue("The native basemap control must have rows", rows(cache, "http://127.0.0.1:${server.port}/base/") > 0)
            assertEquals("No-store radar must never reach disk", 0, rows(cache, noStore.template.substringBefore("{z}")))
            assertEquals("Cacheable radar must also remain memory-only", 0, rows(cache, cacheable.template.substringBefore("{z}")))
        }
    }

    private data class CameraCase(val provider: RadarProvider, val camera: CameraPosition, val nativeZoom: Int)

    private fun testCache(name: String) = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        .resolve("radar-$name-${System.nanoTime()}").apply { mkdirs() }

    private fun rows(cache: java.io.File, prefix: String): Int =
        SQLiteDatabase.openDatabase(cache.resolve(RADAR_MAP_CACHE_FILENAME).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT count(*) FROM tiles WHERE url_template LIKE ?", arrayOf("$prefix%")).use { cursor ->
                check(cursor.moveToFirst())
                cursor.getInt(0)
            }
        }

    private fun style(port: Int, radar: String, other: String? = null, maxZoom: Int = 7): String {
        val extra = other?.let { ",\"cacheable\":{\"type\":\"raster\",\"tiles\":[\"$it\"],\"tileSize\":256,\"maxzoom\":7}" }.orEmpty()
        return """{"version":8,"sources":{
          "base":{"type":"raster","tiles":["http://127.0.0.1:$port/base/{z}/{x}/{y}.png"],"tileSize":256,"maxzoom":7},
          "radar":{"type":"raster","tiles":["$radar"],"tileSize":256,"maxzoom":$maxZoom}$extra
        },"layers":[{"id":"base","type":"raster","source":"base","paint":{"raster-fade-duration":0}}]}""".trimIndent()
    }

    private fun png(color: Int): ByteArray = ByteArrayOutputStream().use { output ->
        Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).apply {
            eraseColor(color)
            compress(Bitmap.CompressFormat.PNG, 100, output)
            recycle()
        }
        output.toByteArray()
    }

    private class LocalTileServer(private val tile: ByteArray) : AutoCloseable {
        private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val port: Int = server.localPort
        val baseRequests = AtomicInteger()
        @Volatile var style: String = ""
        private val worker = thread(name = "radar-native-basemap-test", isDaemon = true) {
            while (!server.isClosed) {
                try {
                    server.accept().use { socket ->
                        socket.soTimeout = 10_000
                        val reader = socket.getInputStream().bufferedReader()
                        val first = reader.readLine().orEmpty()
                        while (!reader.readLine().isNullOrEmpty()) Unit
                        val isStyle = first.contains("/style.json")
                        val body = if (isStyle) style.toByteArray() else tile.also { baseRequests.incrementAndGet() }
                        val type = if (isStyle) "application/json" else "image/png"
                        val output = socket.getOutputStream()
                        output.write("HTTP/1.1 200 OK\r\nContent-Type: $type\r\nCache-Control: public, max-age=3600\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        output.write(body)
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
