@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package net.droopia.hluweather.ui.map

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import java.io.File
import java.lang.reflect.Proxy
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.droopia.hluweather.data.network.LibreWxrRadarFrame
import net.droopia.hluweather.data.network.LibreWxrRadarFrameType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.compose.map.MapState
import org.maplibre.compose.map.RuntimeImplementation
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.style.DesiredStyleRevision
import org.maplibre.compose.style.LayerDefinition
import org.maplibre.compose.style.LayerPropertyKind
import org.maplibre.compose.style.LayerPropertyWrite
import org.maplibre.compose.style.LayerSummary
import org.maplibre.compose.style.MapNodeApplier
import org.maplibre.compose.style.SourceDefinition
import org.maplibre.compose.style.StyleBinding
import org.maplibre.compose.style.StyleContent
import org.maplibre.compose.style.StyleIdentity
import org.maplibre.compose.style.StyleNode
import org.maplibre.compose.style.StyleReconciler
import org.maplibre.compose.style.rememberStyleComposition
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercise the installed 0.16.0 style composer/reconciler with a recording engine, not a second
 * app-owned lifecycle implementation. Test-only internal access avoids loading Android's native
 * renderer in Robolectric. Pixel alignment/real tile rendering still need a device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeatherMapRadarTest {
    @Test
    fun raster_uses_stable_ids_xyz_tile_size_and_provider_zoom_not_screen_coordinates() = runTest {
        val engine = RecordingStyle()
        val content = RadarComposition(this, engine, radar())
        try {
            val revision: DesiredStyleRevision = content.flush()
            val source = revision.sources.single() as SourceDefinition.Json
            val layer: LayerDefinition = revision.layers.single().definition

            assertEquals(WEATHER_RADAR_SOURCE_ID, source.id)
            assertEquals(WEATHER_RADAR_LAYER_ID, layer.id)
            assertEquals(WEATHER_RADAR_SOURCE_ID, layer.sourceId)
            assertEquals("raster", source.value["type"]?.jsonPrimitive?.content)
            assertEquals("xyz", source.value["scheme"]?.jsonPrimitive?.content)
            assertEquals(256, source.value["tileSize"]?.jsonPrimitive?.int)
            assertEquals(0, source.value["minzoom"]?.jsonPrimitive?.int)
            assertEquals(12, source.value["maxzoom"]?.jsonPrimitive?.int)
            assertEquals(
                "https://tiles.example.test/v2/radar/100/256/{z}/{x}/{y}/2/1_1.png",
                source.value["tiles"]?.jsonArray?.single()?.jsonPrimitive?.content
            )
            // No layer maxZoom=12: let MapLibre overzoom native XYZ tiles while panning/zooming.
            assertEquals(24f, layer.value["maxzoom"]?.jsonPrimitive?.float)
            assertEquals(0.75f, layer.value["paint"]?.jsonObject?.get("raster-opacity")?.jsonPrimitive?.float)
        } finally {
            content.close()
        }
    }

    @Test
    fun radar_is_above_basemap_below_first_label_and_duplicate_revisions_are_noops() = runTest {
        val engine = RecordingStyle()
        val content = RadarComposition(this, engine, radar())
        val reconciler = StyleReconciler()
        try {
            val revision: DesiredStyleRevision = content.flush()
            repeat(3) { reconciler.apply(engine.binding, revision) }

            assertEquals(listOf("background", "roads", WEATHER_RADAR_LAYER_ID, "labels", "places"), engine.layerIds)
            assertEquals(listOf("add-source", "add-layer"), engine.operations)
            assertEquals(setOf(WEATHER_RADAR_SOURCE_ID), engine.sources.keys)
        } finally {
            content.close()
        }
    }

    @Test
    fun style_without_labels_places_radar_above_basemap() = runTest {
        val engine = RecordingStyle(withLabels = false)
        val content = RadarComposition(this, engine, radar())
        try {
            StyleReconciler().apply(engine.binding, content.flush())
            assertEquals(listOf("background", "roads", WEATHER_RADAR_LAYER_ID), engine.layerIds)
        } finally {
            content.close()
        }
    }

    @Test
    fun interleaved_basemap_layers_keep_all_symbols_above_radar() = runTest {
        // OpenFreeMap's dark style has a water label before some buildings and roads. A single
        // raster layer cannot be simultaneously above every basemap layer and below every label.
        val engine = RecordingStyle(baseLayers = listOf(
            "background" to "background", "water" to "fill", "water_name" to "symbol",
            "buildings" to "fill", "roads" to "line", "places" to "symbol"
        ))
        val content = RadarComposition(this, engine, radar())
        try {
            StyleReconciler().apply(engine.binding, content.flush())
            assertEquals(
                listOf("background", "water", WEATHER_RADAR_LAYER_ID, "water_name", "buildings", "roads", "places"),
                engine.layerIds
            )
        } finally {
            content.close()
        }
    }

    @Test
    fun frame_changes_replace_only_radar_source_and_layer_under_the_same_ids() = runTest {
        val engine = RecordingStyle()
        val content = RadarComposition(this, engine, radar())
        val reconciler = StyleReconciler()
        try {
            reconciler.apply(engine.binding, content.flush())
            engine.operations.clear()
            content.config.value = radar(200).copy(host = "https://new-cdn.example.test")
            val next: DesiredStyleRevision = content.flush()
            reconciler.apply(engine.binding, next)

            assertEquals(listOf("remove-layer", "remove-source", "add-source", "add-layer"), engine.operations)
            assertEquals(listOf(WEATHER_RADAR_SOURCE_ID), next.sources.map { it.id })
            assertEquals(listOf(WEATHER_RADAR_LAYER_ID), next.layers.map { it.definition.id })
            assertTrue(engine.tileTemplate().startsWith("https://new-cdn.example.test/v2/radar/200/"))
            assertEquals(listOf("background", "roads", WEATHER_RADAR_LAYER_ID, "labels", "places"), engine.layerIds)

            // Timestamp/type changes alone do not alter the selected tile URL.
            engine.operations.clear()
            content.config.value = content.config.value!!.let {
                it.copy(frame = it.frame.copy(timestampSeconds = 999, type = LibreWxrRadarFrameType.FORECAST))
            }
            reconciler.apply(engine.binding, content.flush())
            assertTrue(engine.operations.isEmpty())

            engine.operations.clear()
            content.config.value = content.config.value!!.copy(opacity = 0.4f)
            reconciler.apply(engine.binding, content.flush())
            assertEquals(listOf("paint:raster-opacity"), engine.operations)

            listOf(-1f to 0f, 2f to 1f).forEach { (requested, expected) ->
                content.config.value = content.config.value!!.copy(opacity = requested)
                val clamped: DesiredStyleRevision = content.flush()
                assertEquals(
                    expected,
                    clamped.layers.single().definition.value.getValue("paint")
                        .jsonObject.getValue("raster-opacity").jsonPrimitive.float
                )
            }
        } finally {
            content.close()
        }
    }

    @Test
    fun latest_frame_during_reload_is_attached_once_and_outgoing_style_is_rejected() = runTest {
        val outgoing = RecordingStyle()
        val reconciler = StyleReconciler()
        val oldContent = RadarComposition(this, outgoing, radar())
        reconciler.apply(outgoing.binding, oldContent.flush())
        // A frame changes while the old style is being replaced; its pending revision must never
        // mutate the new style. No app callback retains a handle from the outgoing generation.
        oldContent.config.value = radar(200)
        val pendingOldRevision: DesiredStyleRevision = oldContent.flush()
        outgoing.loaded = false
        val staleFailure = runCatching<Unit> {
            reconciler.apply(outgoing.binding, pendingOldRevision)
        }.exceptionOrNull()
        assertTrue(staleFailure is IllegalStateException)
        oldContent.close()

        val incoming = RecordingStyle()
        val newContent = RadarComposition(this, incoming, radar(300))
        try {
            // Rapid changes before a draw are coalesced to the latest frame.
            newContent.config.value = radar(400)
            newContent.config.value = radar(500)
            val latest: DesiredStyleRevision = newContent.flush()
            repeat(3) { reconciler.apply(incoming.binding, latest) }
            assertTrue(incoming.tileTemplate().contains("/v2/radar/500/"))
            assertEquals(listOf("add-source", "add-layer"), incoming.operations)
            assertEquals(1, incoming.layerIds.count { it == WEATHER_RADAR_LAYER_ID })
            assertEquals(1, incoming.sources.size)

            newContent.config.value = null
            reconciler.apply(incoming.binding, newContent.flush())
            assertTrue(incoming.sources.isEmpty())
            assertFalse(incoming.layerIds.contains(WEATHER_RADAR_LAYER_ID))

            newContent.config.value = radar(600)
            val reenabled: DesiredStyleRevision = newContent.flush()
            repeat(2) { reconciler.apply(incoming.binding, reenabled) }
            assertEquals(1, incoming.layerIds.count { it == WEATHER_RADAR_LAYER_ID })
            assertTrue(incoming.tileTemplate().contains("/v2/radar/600/"))
        } finally {
            newContent.close()
        }
    }

    @Test
    fun remembered_map_receives_latest_frame_across_style_reload_and_retained_revision_replay() = runTest {
        val outgoing = RecordingStyle()
        val incoming = RecordingStyle()
        val reconciler = StyleReconciler()
        val content = RememberedMapComposition(this, outgoing, radar())
        try {
            val retained: DesiredStyleRevision = content.flush()
            val originalMap = content.mapState
            val originalCamera = originalMap.cameraPosition
            reconciler.apply(outgoing.binding, retained)

            // Frame selection changes while no style is loaded, and the old style is invalidated.
            outgoing.loaded = false
            content.style.value = null
            content.config.value = radar(200)
            assertNull(content.flushNullable())

            // MapLibre may replay the last applied revision before publishing the newly composed
            // one. Replaying it must not make the next composition treat radar as a base layer.
            reconciler.apply(incoming.binding, retained)
            content.style.value = incoming.binding
            content.config.value = radar(300)
            val latest: DesiredStyleRevision = content.flush()
            reconciler.apply(incoming.binding, latest)
            repeat(2) { reconciler.apply(incoming.binding, latest) }

            assertSame(originalMap, content.mapState)
            assertEquals(originalCamera, content.mapState.cameraPosition)
            assertTrue(incoming.tileTemplate().contains("/v2/radar/300/"))
            assertEquals(1, incoming.sources.size)
            assertEquals(1, incoming.layerIds.count { it == WEATHER_RADAR_LAYER_ID })
            assertEquals(
                listOf("add-source", "add-layer", "remove-layer", "remove-source", "add-source", "add-layer"),
                incoming.operations
            )

            // Invalid metadata after a valid frame removes only radar; valid metadata can recover.
            content.config.value = radar().copy(host = "not a URI")
            reconciler.apply(incoming.binding, content.flush())
            assertTrue(incoming.sources.isEmpty())
            assertFalse(incoming.layerIds.contains(WEATHER_RADAR_LAYER_ID))
            content.config.value = radar(400)
            reconciler.apply(incoming.binding, content.flush())
            assertTrue(incoming.tileTemplate().contains("/v2/radar/400/"))
            assertSame(originalMap, content.mapState)
        } finally {
            content.close()
        }
    }

    @Test
    fun absent_or_unusable_config_omits_radar_and_picker_callers_never_opt_in() = runTest {
        val engine = RecordingStyle()
        val content = RadarComposition(this, engine)
        try {
            val invalidConfigs = listOf(
                null,
                radar().copy(host = "http://tiles.example.test"),
                radar().copy(host = "not a URI"),
                radar().copy(frame = radar().frame.copy(path = "//other.test/frame")),
                radar().copy(opacity = Float.NaN)
            )
            invalidConfigs.forEach {
                assertNull(weatherRadarTileTemplate(it))
                content.config.value = it
                val revision: DesiredStyleRevision = content.flush()
                assertTrue(revision.sources.isEmpty())
                assertTrue(revision.layers.isEmpty())
            }
            listOf("ui/locationpicker/LocationPickerScreen.kt", "ui/weatherroute/RouteEndpointPicker.kt", "ui/weatherroute/WeatherRouteMap.kt")
                .forEach { path -> assertFalse(mainSource(path).contains("radar =")) }
            assertTrue(mainSource("ui/map/WeatherMap.kt").contains("radar: WeatherMapRadarConfig? = null"))
            assertTrue(mainSource("ui/weather/WeatherScreen.kt").contains("radar = selectedRadar"))
        } finally {
            content.close()
        }
    }

    private fun radar(time: Long = 100) = WeatherMapRadarConfig(
        host = "https://tiles.example.test",
        frame = LibreWxrRadarFrame(time, "/v2/radar/$time", LibreWxrRadarFrameType.OBSERVED)
    )

    private fun mainSource(path: String): String =
        File("src/main/java/net/droopia/hluweather/$path").takeIf { it.isFile }
            ?.readText() ?: File("app/src/main/java/net/droopia/hluweather/$path").readText()

    private class RadarComposition(
        private val scope: TestScope,
        engine: RecordingStyle,
        initial: WeatherMapRadarConfig? = null
    ) {
        val config = mutableStateOf(initial)
        private val clock = BroadcastFrameClock()
        private val recomposer = Recomposer(scope.coroutineContext + clock)
        private val job = scope.backgroundScope.launch(clock) { recomposer.runRecomposeAndApplyChanges() }
        private val node = StyleNode(engine.binding)
        private val composition = Composition(MapNodeApplier(node), recomposer)
        private var revision: DesiredStyleRevision? = null
        private var frameTimeNanos = 0L

        init {
            // The same style-only subcomposition/applier used by rememberMapState, with no GPU.
            composition.setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(1f),
                    LocalLayoutDirection provides LayoutDirection.Ltr
                ) {
                    StyleContent(node, publish = { revision = it }) { WeatherMapRadar(config.value) }
                }
            }
        }

        fun flush(): DesiredStyleRevision {
            repeat(4) {
                Snapshot.sendApplyNotifications()
                scope.runCurrent()
                frameTimeNanos += 16_000_000L
                clock.sendFrame(frameTimeNanos)
                scope.runCurrent()
            }
            return checkNotNull(revision)
        }

        fun close() {
            composition.dispose()
            recomposer.cancel()
            job.cancel()
        }
    }

    /** Exercises rememberMapState's updated-content bridge and its real style subcomposition. */
    private class RememberedMapComposition(
        private val scope: TestScope,
        engine: RecordingStyle,
        initial: WeatherMapRadarConfig
    ) {
        val config = mutableStateOf<WeatherMapRadarConfig?>(initial)
        val style = mutableStateOf<StyleBinding?>(engine.binding)
        private val runtime = RuntimeImplementation(
            platformContext = null,
            closeResources = {},
            logger = null,
            physicalScope = scope.backgroundScope
        )
        private val clock = BroadcastFrameClock()
        private val recomposer = Recomposer(scope.coroutineContext + clock)
        private val job = scope.backgroundScope.launch(clock) { recomposer.runRecomposeAndApplyChanges() }
        private val composition = Composition(EmptyApplier(), recomposer)
        lateinit var mapState: MapState
            private set
        private var revision: DesiredStyleRevision? = null
        private var frameTimeNanos = 0L

        init {
            composition.setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(1f),
                    LocalLayoutDirection provides LayoutDirection.Ltr
                ) {
                    // Like WeatherMap, capture the current caller value rather than the state itself.
                    val selected = config.value
                    val map = rememberMapState(runtime = runtime, baseStyle = BaseStyle.Uri("https://style.test")) {
                        WeatherMapRadar(selected)
                    }
                    val desired = rememberStyleComposition(
                        content = map.styleContent,
                        maybeStyle = style.value,
                        replaceableSourceIds = setOf(WEATHER_RADAR_SOURCE_ID),
                        replaceableLayerIds = setOf(WEATHER_RADAR_LAYER_ID)
                    ).value
                    SideEffect {
                        mapState = map
                        revision = desired
                    }
                }
            }
        }

        fun flush(): DesiredStyleRevision = checkNotNull(flushNullable())

        fun flushNullable(): DesiredStyleRevision? {
            repeat(6) {
                Snapshot.sendApplyNotifications()
                scope.runCurrent()
                frameTimeNanos += 16_000_000L
                clock.sendFrame(frameTimeNanos)
                scope.runCurrent()
            }
            return revision
        }

        fun close() {
            composition.dispose()
            recomposer.cancel()
            job.cancel()
            runtime.close()
        }
    }

    private class EmptyApplier : AbstractApplier<Unit>(Unit) {
        override fun insertTopDown(index: Int, instance: Unit) = Unit
        override fun insertBottomUp(index: Int, instance: Unit) = Unit
        override fun remove(index: Int, count: Int) = Unit
        override fun move(from: Int, to: Int, count: Int) = Unit
        override fun onClear() = Unit
    }

    /** Reject duplicates and referenced-source removal just as the engine does. */
    private class RecordingStyle(
        withLabels: Boolean = true,
        baseLayers: List<Pair<String, String>> = listOf("background" to "fill", "roads" to "fill") +
            if (withLabels) listOf("labels" to "symbol", "places" to "symbol") else emptyList()
    ) {
        var loaded = true
        val sources = linkedMapOf<String, JsonObject>()
        val layerIds = baseLayers.mapTo(mutableListOf()) { it.first }
        private val baseLayerTypes = baseLayers.toMap()
        val operations = mutableListOf<String>()
        private val layers = mutableMapOf<String, JsonObject>()
        private val identity = StyleIdentity.create()

        val binding = Proxy.newProxyInstance(
            StyleBinding::class.java.classLoader, arrayOf(StyleBinding::class.java)
        ) { proxy, method, arguments ->
            val args = arguments.orEmpty()
            when (method.name) {
                "getIdentity" -> identity
                "isLoaded" -> loaded
                "getAnimatorDurationScale" -> 1f
                "getLogger", "unsupportedLayerPropertyReason" -> null
                "getSources" -> emptyList<Any>()
                "layerIds" -> layerIds.toList()
                "layerSummaries" -> layerIds.associateWith {
                    LayerSummary(baseLayerTypes[it] ?: "raster", null, null)
                }
                "sourceExists" -> sources.containsKey(args[0])
                "layerExists" -> layerIds.contains(args[0])
                "requireCurrent" -> { check(loaded); Unit }
                "addSource" -> {
                    check(loaded)
                    val definition = args.single() as SourceDefinition.Json
                    check(sources.putIfAbsent(definition.id, definition.value) == null)
                    operations += "add-source"
                    true
                }
                "removeSource" -> {
                    check(layers.values.none { it["source"]?.jsonPrimitive?.content == args[0] })
                    check(sources.remove(args[0]) != null)
                    operations += "remove-source"
                    Unit
                }
                "addLayer" -> {
                    val definition = args[0] as LayerDefinition
                    check(definition.id !in layerIds)
                    check(definition.sourceId in sources)
                    layers[definition.id] = definition.value
                    val before = args[1] as String
                    layerIds.add(if (before.isEmpty()) layerIds.size else layerIds.indexOf(before), definition.id)
                    operations += "add-layer"
                    true
                }
                "removeLayer" -> {
                    check(layers.remove(args[0]) != null)
                    check(layerIds.remove(args[0]))
                    operations += "remove-layer"
                    Unit
                }
                "setLayerProperties" -> {
                    @Suppress("UNCHECKED_CAST")
                    (args[0] as List<LayerPropertyWrite>).forEach {
                        check(it.layerId == WEATHER_RADAR_LAYER_ID && it.kind == LayerPropertyKind.PAINT)
                        operations += "paint:${it.name}"
                    }
                    Unit
                }
                "equals" -> proxy === args[0]
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "RecordingStyle"
                else -> error("Unexpected MapLibre engine operation: ${method.name}")
            }
        } as StyleBinding

        fun tileTemplate() = sources.getValue(WEATHER_RADAR_SOURCE_ID)
            .getValue("tiles").jsonArray.single().jsonPrimitive.content
    }
}
