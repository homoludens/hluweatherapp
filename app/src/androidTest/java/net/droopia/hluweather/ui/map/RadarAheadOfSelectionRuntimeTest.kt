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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.droopia.hluweather.MainActivity
import net.droopia.hluweather.data.model.RadarFrame
import net.droopia.hluweather.data.model.RadarFrameType
import net.droopia.hluweather.data.model.RadarProvider
import net.droopia.hluweather.data.model.RadarProviderDescriptors
import net.droopia.hluweather.data.model.RadarTileCoordinate
import net.droopia.hluweather.data.model.RadarTileCoverage
import net.droopia.hluweather.data.network.RadarMapResourceProvider
import net.droopia.hluweather.data.network.RadarNetworkScheduler
import net.droopia.hluweather.data.network.RadarTileHttpResult
import net.droopia.hluweather.data.network.RadarTileKey
import net.droopia.hluweather.data.network.RadarTileLoader
import net.droopia.hluweather.data.network.RadarTilePayload
import net.droopia.hluweather.data.network.RadarTileRegistry
import net.droopia.hluweather.data.network.RadarTileTransport
import net.droopia.hluweather.data.network.radarMapRuntimeOptions
import net.droopia.hluweather.data.network.radarViewportBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.map.DelicateMapApi
import org.maplibre.compose.map.MapState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.StyleLoadState
import org.maplibre.compose.map.createMapRuntime
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.map.withPlatformMap
import org.maplibre.compose.resource.MapResourceKind
import org.maplibre.compose.resource.MapResourceLoad
import org.maplibre.compose.resource.MapResourceLoadRequest
import org.maplibre.compose.resource.MapResourceProvider
import org.maplibre.compose.resource.MapResourceRequest
import org.maplibre.compose.style.BaseStyle

/** Live OpenGL fixtures for the bounded, best-effort ahead-of-selection path. */
@OptIn(DelicateMapApi::class)
@RunWith(AndroidJUnit4::class)
class RadarAheadOfSelectionRuntimeTest {
    @Test
    fun cachedPreparationIsReusedForBothProvidersAndConvergesAfterHeldNativeDelivery() = runBlocking {
        for (provider in RadarProvider.values()) {
            val firstTime = 1_700_000_100L + provider.ordinal * 10L
            val secondTime = firstTime + 1
            val transparentTime = firstTime + 2
            val noContentTime = firstTime + 3
            val fixture = OpenGlRadarFixture(
                colors = mapOf(
                    firstTime to Color.RED,
                    secondTime to Color.GREEN,
                    transparentTime to Color.TRANSPARENT,
                    noContentTime to Color.MAGENTA
                ),
                noContent = setOf(noContentTime)
            )
            fixture.start()
            try {
                val first = fixture.request(fixture.frame(firstTime, provider))
                val prepared = fixture.request(fixture.frame(secondTime, provider))
                val transparent = fixture.request(fixture.frame(transparentTime, provider))
                val noContent = fixture.request(fixture.frame(noContentTime, provider))
                val coarseZoom = RadarTileCoverage.renderMaxZoom(RadarProviderDescriptors[provider])
                assertTrue("High-zoom planned demand must use coarse z=$coarseZoom: ${prepared.coordinates}",
                    prepared.coordinates.isNotEmpty() && prepared.coordinates.all { it.zoom == coarseZoom })

                listOf(first, prepared, transparent, noContent).forEach { fixture.seed(it) }
                fixture.scenario.onActivity {
                    fixture.controller.update(first, provider)
                }
                fixture.await { fixture.controller.state.value.displayed == first.identity }
                fixture.awaitColor(Color.RED)
                val firstLayer = fixture.radarLayers().single().id
                val preparationHttpCount = fixture.networkCalls.size

                fixture.holdNative(secondTime)
                fixture.scenario.onActivity { fixture.controller.prepare(prepared, revision = 1) }
                fixture.await {
                    fixture.registry.registeredCount == 2 && fixture.radarLayers().size == 2 &&
                        fixture.nativeAttempts.any { it.frameTime == secondTime }
                }
                fixture.assertSlotBounds()
                assertEquals(first.identity, fixture.controller.state.value.displayed)
                assertEquals(first.identity, fixture.controller.state.value.requested)
                assertFalse(fixture.controller.state.value.pending)
                val preparedLayer = fixture.radarLayers().single { it.opacity in 0.000001f..0.001f }
                assertTrue("Unselected native demand must remain tiny-positive: ${preparedLayer.opacity}",
                    preparedLayer.opacity > 0f && preparedLayer.opacity <= RadarRenderController.STAGING_OPACITY)
                val stagedSourceAlias = preparedLayer.sourceAlias
                assertTrue("Optional native callbacks must target the planned coarse zoom",
                    fixture.nativeAttempts.filter { it.frameTime == secondTime }.any { it.coordinate?.zoom == coarseZoom })
                assertTrue("Native requests must not refine past the coarse cap",
                    fixture.nativeAttempts.filter { it.frameTime == secondTime }.all {
                        (it.coordinate?.zoom ?: coarseZoom) <= coarseZoom
                    })
                assertEquals("Native preparation and cache-only optional-parent misses must not issue HTTP",
                    preparationHttpCount, fixture.networkCalls.size)

                // Deliberately ask the exact optional alias for an uncached coarse parent while
                // MapLibre delivery is held. The optional registration must fail cache-only and
                // remain eligible because only the planned viewport set is required coverage.
                val planned = fixture.nativeAttempts.first { it.frameTime == secondTime }.coordinate!!
                val parent = RadarTileCoordinate(planned.zoom - 1, planned.x / 2, planned.y / 2)
                val sourceTemplate = preparedLayer.sourceTemplate
                val parentUrl = sourceTemplate
                    .replace("{z}", parent.zoom.toString())
                    .replace("{x}", parent.x.toString())
                    .replace("{y}", parent.y.toString())
                assertNotNull(fixture.registry.resolve(parentUrl))
                val parentResult = fixture.bridge.load(MapResourceLoadRequest(
                    parentUrl,
                    MapResourceKind.Tile,
                    loadingMethod = MapResourceLoadRequest.LoadingMethod.NetworkOnly,
                    storagePolicy = MapResourceLoadRequest.StoragePolicy.Volatile
                ))
                assertTrue("Missing optional coarse parent must not be fabricated or fetched: $parentResult",
                    parentResult is MapResourceLoad.Failed)
                assertEquals(first.identity, fixture.controller.state.value.displayed)
                assertNull(fixture.controller.state.value.error)
                assertEquals(2, fixture.registry.registeredCount)
                assertEquals(preparationHttpCount, fixture.networkCalls.size)

                val preparedAlias = stagedSourceAlias
                val preparedLayerId = preparedLayer.id
                val acceptedAt = System.nanoTime()
                fixture.scenario.onActivity { fixture.controller.update(prepared, provider) }
                fixture.await { fixture.controller.state.value.displayed == prepared.identity }
                assertFalse("Accepted submission must not wait for held MapLibre callbacks",
                    fixture.controller.state.value.pending)
                assertEquals("The prepared registration/layer must be adopted, not recreated", preparedLayerId,
                    fixture.radarLayers().single { it.sourceAlias == preparedAlias }.id)
                assertTrue("No render-readiness barrier is part of this fixture",
                    fixture.nativeAttempts.any { it.frameTime == secondTime })
                assertEquals(preparationHttpCount, fixture.networkCalls.size)

                // Samples describe only what this screenshot cadence observed; do not require a
                // universal no-gap result. A held native delivery longer than warm-up can expose
                // the basemap until the replacement converges.
                val heldSamples = fixture.samplePixels(durationMillis = 250, intervalMillis = 25)
                println("RADAR_AHEAD_HELD provider=$provider accepted=true elapsedMs=" +
                    "${(System.nanoTime() - acceptedAt) / 1_000_000} samples=$heldSamples " +
                    "observedBlue=${heldSamples.count(::isBlue)} residualGapPossible=true readinessWait=false")
                fixture.releaseNative(secondTime)
                fixture.awaitColor(Color.GREEN)
                fixture.await { fixture.registry.registeredCount == 1 && fixture.radarLayers().size == 1 }
                fixture.assertSlotBounds()

                val frameNetworkAfterConvergence = fixture.networkCount(secondTime)
                delay(1_000)
                val settledCoordinates = fixture.nativeAttempts.filter { it.frameTime == secondTime }
                    .mapNotNull { it.coordinate }.distinct()
                assertTrue("Settled native coordinates must retain the coarse zoom cap: $settledCoordinates",
                    settledCoordinates.isNotEmpty() && settledCoordinates.all { it.zoom <= coarseZoom })
                assertEquals("A stationary map must not trigger delayed detail fetches",
                    frameNetworkAfterConvergence, fixture.networkCount(secondTime))

                fixture.scenario.onActivity { fixture.controller.update(transparent, provider) }
                fixture.await { fixture.controller.state.value.displayed == transparent.identity }
                fixture.awaitBlue()
                assertNull("Transparent raster coverage is successful", fixture.controller.state.value.error)
                fixture.assertSlotBounds()

                fixture.scenario.onActivity { fixture.controller.update(noContent, provider) }
                fixture.await { fixture.controller.state.value.displayed == noContent.identity }
                fixture.awaitBlue()
                assertNull("HTTP 204/no-content coverage is successful", fixture.controller.state.value.error)
                fixture.assertSlotBounds()

                fixture.scenario.onActivity { fixture.controller.update(null, provider) }
                fixture.await { fixture.registry.registeredCount == 0 && fixture.radarLayers().isEmpty() }
                assertFalse("Removed front must not resurrect", fixture.radarLayers().any { it.id == firstLayer })
                println("RADAR_AHEAD_PROVIDER_RESULT provider=$provider plannedZoom=$coarseZoom " +
                    "nativeAttempts=${fixture.nativeAttempts.count { it.frameTime == secondTime }} " +
                    "networkDuringPreparation=0 acceptedBeforeDelivery=true")
            } finally {
                fixture.close()
            }
        }
    }

    @Test
    fun preparedSourcesDoNotResurrectAcrossViewportSelectionStyleProviderOpacityOrLifecycleRaces() = runBlocking {
        val firstTime = 1_700_000_200L
        val times = (firstTime..firstTime + 8).toList()
        val fixture = OpenGlRadarFixture(times.associateWith { Color.RED })
        fixture.start()
        try {
            val rain = RadarProvider.RAINVIEWER
            val libre = RadarProvider.LIBREWXR
            val first = fixture.request(fixture.frame(times[0], rain))
            val viewportCandidate = fixture.request(fixture.frame(times[1], rain))
            val selectionCandidate = fixture.request(fixture.frame(times[2], rain))
            val selectedAfterRace = fixture.request(fixture.frame(times[3], rain))
            val styleCandidate = fixture.request(fixture.frame(times[4], rain))
            // The style replacement below advances the live style epoch. Keep all requests used
            // after that transition in the new lifetime; the styleCandidate above intentionally
            // remains in epoch 1 so the replacement invalidates it.
            val providerCandidate = fixture.request(fixture.frame(times[5], rain), styleEpoch = 2)
            val newProviderSelection = fixture.request(fixture.frame(times[6], libre), styleEpoch = 2)
            val opacityCandidate = fixture.request(fixture.frame(times[7], libre), styleEpoch = 2)
            val lifecycleCandidate = fixture.request(fixture.frame(times[8], libre), styleEpoch = 2)
            listOf(first, viewportCandidate, selectionCandidate, selectedAfterRace, styleCandidate,
                providerCandidate, newProviderSelection, opacityCandidate, lifecycleCandidate).forEach { fixture.seed(it) }
            val seededNetworkCounts = times.associateWith(fixture::networkCount)

            fixture.scenario.onActivity { fixture.controller.update(first, rain) }
            fixture.await("initial front selected") { fixture.controller.state.value.displayed == first.identity }
            fixture.assertSlotBounds()

            // A newer tile-set/viewport opportunity cancels an installed but unselected source.
            fixture.holdNative(times[1])
            fixture.scenario.onActivity { fixture.controller.prepare(viewportCandidate, revision = 1) }
            fixture.await("viewport candidate installed") {
                fixture.radarLayers().size == 2 && fixture.nativeAttempts.any { it.frameTime == times[1] }
            }
            val viewportAlias = fixture.nativeAttempts.last { it.frameTime == times[1] }.url
            val changedTiles = viewportCandidate.coordinates.map { tile ->
                val count = 1 shl tile.zoom
                tile.copy(x = (tile.x + 1) % count)
            }.distinct()
            fixture.scenario.onActivity {
                fixture.controller.prepare(viewportCandidate.copy(coordinates = changedTiles), revision = 2)
            }
            fixture.await("changed viewport candidate released") {
                fixture.registry.registeredCount == 1 && fixture.radarLayers().size == 1
            }
            assertNull(fixture.registry.resolve(viewportAlias))
            fixture.releaseNative(times[1])
            fixture.awaitNoStaleLayer(times[1], first.identity)
            assertEquals(seededNetworkCounts.getValue(times[1]), fixture.networkCount(times[1]))

            // Selecting a different frame while an optional callback is delayed removes that exact
            // lease before ordinary demand takes the spare slot.
            fixture.holdNative(times[2])
            fixture.scenario.onActivity { fixture.controller.prepare(selectionCandidate, revision = 3) }
            fixture.await("selection candidate installed") {
                fixture.radarLayers().size == 2 && fixture.nativeAttempts.any { it.frameTime == times[2] }
            }
            val selectionAlias = fixture.nativeAttempts.last { it.frameTime == times[2] }.url
            fixture.scenario.onActivity { fixture.controller.update(selectedAfterRace, rain) }
            fixture.await("manual selection accepted") {
                fixture.controller.state.value.displayed == selectedAfterRace.identity
            }
            fixture.await("old selection candidate released") {
                fixture.registry.registeredCount == 1 && fixture.radarLayers().size == 1
            }
            assertNull(fixture.registry.resolve(selectionAlias))
            fixture.releaseNative(times[2])
            fixture.awaitNoStaleLayer(times[2], selectedAfterRace.identity)
            fixture.assertSlotBounds()
            assertEquals(seededNetworkCounts.getValue(times[2]), fixture.networkCount(times[2]))

            // A real style replacement plus its new style epoch invalidates every old registration.
            fixture.holdNative(times[4])
            fixture.scenario.onActivity { fixture.controller.prepare(styleCandidate, revision = 4) }
            fixture.await("style candidate installed") {
                fixture.radarLayers().size == 2 && fixture.nativeAttempts.any { it.frameTime == times[4] }
            }
            val styleAlias = fixture.nativeAttempts.last { it.frameTime == times[4] }.url
            fixture.reloadStyle(2)
            fixture.await("replacement style loaded") {
                fixture.map.style.loadState == StyleLoadState.Ready && fixture.hasLayer("base-2")
            }
            val afterStyle = selectedAfterRace.copy(styleEpoch = 2)
            fixture.scenario.onActivity { fixture.controller.update(afterStyle, rain) }
            fixture.await("selection accepted in new style epoch") {
                fixture.controller.state.value.displayed == afterStyle.identity
            }
            fixture.await("old style candidate released") {
                fixture.registry.registeredCount == 1 && fixture.radarLayers().size == 1
            }
            assertNull(fixture.registry.resolve(styleAlias))
            fixture.releaseNative(times[4])
            fixture.awaitNoStaleLayer(times[4], afterStyle.identity)
            fixture.assertSlotBounds()
            assertEquals(seededNetworkCounts.getValue(times[4]), fixture.networkCount(times[4]))

            fixture.holdNative(times[5])
            fixture.scenario.onActivity { fixture.controller.prepare(providerCandidate, revision = 5) }
            fixture.await("provider candidate installed") {
                fixture.radarLayers().size == 2 && fixture.nativeAttempts.any { it.frameTime == times[5] }
            }
            val providerAlias = fixture.nativeAttempts.last { it.frameTime == times[5] }.url
            fixture.scenario.onActivity { fixture.controller.update(newProviderSelection, libre) }
            fixture.await("new provider selection accepted") {
                fixture.controller.state.value.displayed == newProviderSelection.identity
            }
            fixture.await("old provider candidate released") {
                fixture.registry.registeredCount == 1 && fixture.radarLayers().size == 1
            }
            assertNull(fixture.registry.resolve(providerAlias))
            fixture.releaseNative(times[5])
            fixture.awaitNoStaleLayer(times[5], newProviderSelection.identity)
            fixture.assertSlotBounds()
            assertEquals(seededNetworkCounts.getValue(times[5]), fixture.networkCount(times[5]))

            fixture.holdNative(times[7])
            fixture.scenario.onActivity { fixture.controller.prepare(opacityCandidate, revision = 6) }
            fixture.await("opacity candidate installed") {
                fixture.radarLayers().size == 2 && fixture.nativeAttempts.any { it.frameTime == times[7] }
            }
            val opacityAlias = fixture.nativeAttempts.last { it.frameTime == times[7] }.url
            fixture.scenario.onActivity {
                fixture.controller.update(newProviderSelection.copy(config = newProviderSelection.config.copy(opacity = 0f)), libre)
            }
            fixture.await("zero-opacity candidate released") {
                fixture.registry.registeredCount == 1 && fixture.radarLayers().size == 1
            }
            assertEquals(0f, fixture.radarLayers().single().opacity, 0f)
            assertNull(fixture.registry.resolve(opacityAlias))
            fixture.releaseNative(times[7])
            fixture.awaitNoStaleLayer(times[7], newProviderSelection.identity)
            fixture.scenario.onActivity { fixture.controller.update(newProviderSelection, libre) }
            fixture.await("selection restored after zero opacity") {
                fixture.controller.state.value.displayed == newProviderSelection.identity
            }
            assertTrue(fixture.radarLayers().single().opacity > 0f)
            fixture.assertSlotBounds()
            assertEquals(seededNetworkCounts.getValue(times[7]), fixture.networkCount(times[7]))

            // Lifecycle-stop race withdraws optional work and releases every live registration.
            fixture.holdNative(times[8])
            fixture.scenario.onActivity { fixture.controller.prepare(lifecycleCandidate, revision = 7) }
            fixture.await("lifecycle candidate installed") {
                fixture.radarLayers().size == 2 && fixture.nativeAttempts.any { it.frameTime == times[8] }
            }
            val lifecycleAlias = fixture.nativeAttempts.last { it.frameTime == times[8] }.url
            fixture.scenario.moveToState(Lifecycle.State.CREATED)
            fixture.controller.update(null, libre)
            fixture.await("lifecycle stop released registrations") {
                fixture.registry.registeredCount == 0 && fixture.radarLayers().isEmpty()
            }
            assertNull(fixture.registry.resolve(lifecycleAlias))
            fixture.releaseNative(times[8])
            fixture.delayAndAssertNoStale(times[8], null)
            assertEquals(seededNetworkCounts.getValue(times[8]), fixture.networkCount(times[8]))

            // Resume uses current demand only; no late optional response may replace it.
            fixture.scenario.moveToState(Lifecycle.State.RESUMED)
            fixture.controller.update(newProviderSelection, libre)
            fixture.await("current selection accepted after resume") {
                fixture.controller.state.value.displayed == newProviderSelection.identity
            }
            fixture.await("current source installed after resume") {
                fixture.registry.registeredCount == 1 && fixture.radarLayers().size == 1
            }
            fixture.assertSlotBounds()
            println("RADAR_AHEAD_RACES=viewport,selection,style,provider,zero-opacity,lifecycle " +
                "maxLiveSources=2 staleResurrection=false")
        } finally {
            fixture.close()
        }
    }

    private data class RadarLayerInfo(
        val id: String,
        val opacity: Float,
        val sourceTemplate: String,
        val sourceAlias: String
    )

    private data class NativeAttempt(
        val url: String,
        val frameTime: Long?,
        val coordinate: RadarTileCoordinate?
    )

    private inner class OpenGlRadarFixture(
        private val colors: Map<Long, Int>,
        private val noContent: Set<Long> = emptySet()
    ) {
        private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val ui = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val registry = RadarTileRegistry()
        private val scheduler = RadarNetworkScheduler(io)
        val networkCalls = ConcurrentLinkedQueue<RadarTileKey>()
        val nativeAttempts = ConcurrentLinkedQueue<NativeAttempt>()
        private val heldNative = ConcurrentHashMap<Long, CompletableDeferred<Unit>>()
        private val images = colors.mapValues { png(it.value) }
        val loader = RadarTileLoader(io, scheduler, RadarTileTransport { key, _ ->
            networkCalls.add(key)
            val time = frameTime(key.url) ?: error("Fixture received an unrecognized radar URL: ${key.url}")
            if (time in noContent) {
                RadarTileHttpResult(204, RadarTilePayload(null, System.currentTimeMillis() + 60_000))
            } else {
                RadarTileHttpResult(200, RadarTilePayload(
                    requireNotNull(images[time]), System.currentTimeMillis() + 60_000
                ))
            }
        })
        val bridge = RadarMapResourceProvider(registry, loader, scheduler)
        private val recordingProvider = object : MapResourceProvider {
            override fun accepts(request: MapResourceRequest) = bridge.accepts(request)

            override suspend fun load(request: MapResourceLoadRequest): MapResourceLoad {
                val key = registry.resolve(request.url)
                val time = key?.let { frameTime(it.url) }
                nativeAttempts.add(NativeAttempt(request.url, time, key?.coordinate))
                time?.let { heldNative[it]?.await() }
                return bridge.load(request)
            }
        }
        private val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
            .resolve("radar-ahead-${System.nanoTime()}").apply { mkdirs() }
        private val runtime = createMapRuntime(
            radarMapRuntimeOptions(cache, bridge).copy(resourceProvider = recordingProvider)
        )
        private val mapRef = AtomicReference<MapState?>()
        private val baseStyle = mutableStateOf(style(1))
        lateinit var scenario: ActivityScenario<MainActivity>
            private set
        lateinit var map: MapState
            private set
        lateinit var backend: MapLibreRadarBackend
            private set
        lateinit var controller: RadarRenderController
            private set

        suspend fun start() {
            scenario = ActivityScenario.launch(MainActivity::class.java)
            scenario.onActivity { activity ->
                activity.setContent {
                    val state = rememberMapState(
                        runtime = runtime,
                        baseStyle = baseStyle.value,
                        initialCameraPosition = CameraPosition(zoom = 12.0)
                    )
                    SideEffect { mapRef.set(state) }
                    MaplibreMap(Modifier.fillMaxSize(), state = state, overlay = {})
                }
            }
            await { mapRef.get()?.style?.loadState == StyleLoadState.Ready && mapRef.get()?.viewport != null }
            map = requireNotNull(mapRef.get())
            scenario.onActivity {
                backend = MapLibreRadarBackend(map, registry, loader, bridge, ui)
                val noReadinessBarrier = object : RadarRenderBackend by backend {
                    override suspend fun awaitFullRender(
                        slot: RadarRenderSlot,
                        viewport: RadarRenderRequest,
                        guard: RadarMutationGuard
                    ) = error("Ahead-of-selection handoff must not wait for MapLibre render readiness")
                }
                controller = RadarRenderController(ui, noReadinessBarrier, timing = RadarHandoffTiming())
            }
        }

        fun frame(time: Long, provider: RadarProvider) = RadarFrame(
            timestampSeconds = time,
            path = "/v2/radar/$time",
            type = RadarFrameType.OBSERVED,
            provider = provider
        )

        fun request(
            frame: RadarFrame,
            opacity: Float = 0.75f,
            styleEpoch: Long = 1,
            coordinates: List<RadarTileCoordinate>? = null
        ): RadarRenderRequest {
            val planned = coordinates ?: RadarTileCoverage.tiles(
                radarViewportBounds(requireNotNull(map.viewport), map.cameraPosition),
                RadarProviderDescriptors[frame.provider]
            ).sortedWith(MapLibreRadarBackend.tileOrder)
            return RadarRenderRequest(
                WeatherMapRadarConfig("https://tilecache.rainviewer.com", frame, opacity), planned, styleEpoch
            )
        }

        suspend fun seed(request: RadarRenderRequest) {
            val source = registry.register(request.config.host, request.config.frame)
            try {
                loader.awaitCoverage(request.coordinates.map(source::tile))
            } finally {
                registry.unregister(source)
            }
        }

        fun holdNative(time: Long) { heldNative[time] = CompletableDeferred() }
        fun releaseNative(time: Long) { heldNative.remove(time)?.complete(Unit) }

        fun networkCount(time: Long) = networkCalls.count { frameTime(it.url) == time }

        suspend fun radarLayers(): List<RadarLayerInfo> = map.withPlatformMap {
            this.map.styleLayerIds().filter { it.startsWith("hlu-radar-layer-") }.mapNotNull { id ->
                val opacity = this.map.layerProperty(id, "raster-opacity")?.decodeToString()?.toFloatOrNull()
                    ?: return@mapNotNull null
                val source = this.map.layerSourceId(id) ?: return@mapNotNull null
                val template = this.map.styleSourceInfo(source)?.tileJson?.tileUrls?.singleOrNull()
                    ?: return@mapNotNull null
                RadarLayerInfo(id, opacity, template, sourceAlias(template))
            }
        }

        suspend fun assertSlotBounds() {
            assertTrue("Native registrations exceed the strict two-slot cap: ${registry.registeredCount}",
                registry.registeredCount <= 2)
            assertTrue("Native layers exceed the strict two-slot cap: ${radarLayers()}", radarLayers().size <= 2)
        }

        suspend fun hasLayer(id: String) = map.withPlatformMap { this.map.styleLayerExists(id) }

        fun reloadStyle(epoch: Int) {
            scenario.onActivity { baseStyle.value = style(epoch) }
        }

        suspend fun awaitNoStaleLayer(time: Long, expected: net.droopia.hluweather.data.model.RadarFrameIdentity) {
            delayAndAssertNoStale(time, expected)
        }

        suspend fun delayAndAssertNoStale(
            time: Long,
            expected: net.droopia.hluweather.data.model.RadarFrameIdentity?
        ) {
            delay(200)
            val staleIds = nativeAttempts.filter { it.frameTime == time }.map { sourceAlias(it.url) }.toSet()
            val layers = radarLayers()
            assertTrue("Late native delivery resurrected an obsolete source: $layers", layers.none { it.sourceAlias in staleIds })
            assertEquals(expected, controller.state.value.displayed)
            assertSlotBounds()
        }

        suspend fun awaitColor(color: Int) = withTimeout(30_000) {
            while (true) {
                if (pixelMatches(pixel(), color)) return@withTimeout
                delay(50)
            }
        }

        suspend fun awaitBlue() = withTimeout(30_000) {
            while (true) {
                if (isBlue(pixel())) return@withTimeout
                delay(50)
            }
        }

        suspend fun samplePixels(durationMillis: Long, intervalMillis: Long): List<Int> {
            val end = System.nanoTime() + durationMillis * 1_000_000
            val samples = mutableListOf<Int>()
            while (System.nanoTime() < end) {
                samples += pixel()
                delay(intervalMillis)
            }
            return samples
        }

        suspend fun await(phase: String = "unspecified", condition: suspend () -> Boolean) {
            try {
                withTimeout(30_000) {
                    while (!condition()) delay(25)
                }
            } catch (timeout: TimeoutCancellationException) {
                throw AssertionError("Timed out waiting for radar fixture phase: $phase", timeout)
            }
        }

        suspend fun close() {
            heldNative.values.forEach { it.complete(Unit) }
            heldNative.clear()
            runCatching { withTimeout(20_000) { controller.close() } }
            runtime.close()
            withTimeout(20_000) { runtime.awaitClosed() }
            runCatching { backend.closeAfterMap() }
            scenario.close()
            ui.cancel()
            io.cancel()
        }

        private fun pixel(): Int {
            val image = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            return image.getPixel(image.width / 2, image.height / 2).also { image.recycle() }
        }

        private fun style(epoch: Int) = BaseStyle.Json("""{"version":8,"sources":{
            "empty":{"type":"geojson","data":{"type":"FeatureCollection","features":[]}}},
            "layers":[{"id":"base-$epoch","type":"background","paint":{"background-color":"#0000ff"}},
            {"id":"labels","type":"symbol","source":"empty"}]}""")
    }

    private fun frameTime(url: String): Long? = Regex("/radar/(\\d+)/").find(url)?.groupValues?.get(1)?.toLongOrNull()

    private fun sourceAlias(url: String): String = url.substringAfter("://tile/").substringBefore('/')

    private fun pixelMatches(pixel: Int, color: Int): Boolean = when (color) {
        Color.RED -> Color.red(pixel) > 130 && Color.green(pixel) < 60 && Color.blue(pixel) < 120
        Color.GREEN -> Color.green(pixel) > 130 && Color.red(pixel) < 60 && Color.blue(pixel) < 120
        Color.TRANSPARENT -> isBlue(pixel)
        else -> true
    }

    private fun isBlue(pixel: Int) = Color.blue(pixel) > 170 && Color.red(pixel) < 90 && Color.green(pixel) < 90

    private fun png(color: Int): ByteArray = ByteArrayOutputStream().use { output ->
        Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).apply {
            eraseColor(color)
            compress(Bitmap.CompressFormat.PNG, 100, output)
            recycle()
        }
        output.toByteArray()
    }
}
