# Radar native readiness gate verification

## Decision — 2026-11-03

**NO-GO / BLOCKED.** Task 1.1 is unchecked. The audit did not establish a public, live-map operation that certifies current-viewport raster drawable readiness and a rendered swap for the exact source/style/surface lifetime and native update. Nor did it establish atomic consumption of the two opacity writes or a complete presented-frame capture. These are separate missing parts of design decision 1; a successful command, byte delivery, a Full event, or the unit test below cannot substitute for any of them.

No production code or dependency was changed for this gate, and the existing planning artifacts were preserved unchanged. No new probe was built: without a sound readiness/acknowledgment candidate or a proven complete live-output assertion, a sampled-color probe could not pass the specified contract. All controlled native cases below are **NOT RUN**, not failures of a demonstrated mechanism and not passes. Downstream tasks remain blocked. This conclusion is about what this audit demonstrated, not a mathematical proof that no possible public-API solution exists.

Before expanding scope, ask whether to investigate a dependency/API change, a native fork, or compositor-level capture; none is approved by this result. A suitable alternative must still demonstrate the whole existing contract and complete output verification, not weaken it to a timeout or map-wide event.

## Versions and evidence inputs

- Project baseline: `73fcfe8879e960aa02ae5f07baecc5f58b01bfcb`. Task 1.1 is `[high]`; the active session reported `openai/gpt-6.1-terra#max`.
- `gradle/libs.versions.toml` pins MapLibre Compose and its Android OpenGL runtime to `0.16.0`. Google debug dependency resolution selected `org.maplibre.nativeffi:maplibre-native-ffi-android:0.202609.1` and `maplibre-native-ffi-runtime-opengl-android:0.202609.1`.
- Audited source archives (SHA-256):
  - `org.maplibre.compose:maplibre-compose-android:0.16.0:sources`: `ec00c8b1207a1cca7b0e86d0ae0cce8d09b0be6fe3715016db119f00a16818f2`.
  - `org.maplibre.compose:maplibre-compose-runtime-opengl-android:0.16.0:sources`: `c6deada2fac53b8ea6523dbda77597b128006674616f140f04df23264c6d1aa3`.
  - [`org.maplibre.nativeffi:maplibre-native-ffi-android:0.202609.1:sources`](https://repo.maven.apache.org/maven2/org/maplibre/nativeffi/maplibre-native-ffi-android/0.202609.1/maplibre-native-ffi-android-0.202609.1-sources.jar): `025a01f2242a6dacafaa8157bc436bf78a874971a9c1c171d38cc566df1ad6f1`.
- Native implementation references below are from the matching upstream FFI release tag `core/v0.202609.1`, commit `c29b61a0c45a0471d1b1d2c0bce23a458b6a6440`, whose [`third_party/maplibre-native` submodule](https://api.github.com/repos/maplibre/maplibre-native-ffi/contents/third_party/maplibre-native?ref=c29b61a0c45a0471d1b1d2c0bce23a458b6a6440) is `d695deef12fc64ee075a892c2d913c45fe8c7b42`. These source references establish the inspected ordering; no independent binary-to-source rebuild was performed.
- The extracted sources were inspected under `/tmp/opencode/radar-gate-api-BEjeeh/{compose,nativeffi,nativeffi-opengl}`. `cmp` confirmed that the extracted `MlnFfiMapSession.kt` and `MapHandle.kt` matched the corresponding source-archive entries. These temporary paths are not durable project artifacts.

## Public API and ordering audit

| Candidate | What the inspected API/source actually provides | Gate consequence |
| --- | --- | --- |
| `MapState.withPlatformMap`, `PlatformMapScope.map` | Callback-scoped `MapHandle` access on the map owner context; queued cancellation can prevent entry, but a started callback is not interrupted. The map and the rendering session have different owner threads (`MlnFfiMapRuntimeLoop` and `MlnFfiMapSession`). | A guarded map callback can fence mutations and check current identities, but does not by itself freeze the renderer or attest to a render update. |
| `MapHandle.styleSourceExists`, `styleSourceInfo`, `styleLayerExists`, `layerSourceId`, `layerProperty`, `isFullyLoaded`, `requestRepaint`, `setLayerProperty` | Existence, style/source metadata, current owner-side properties, a map-wide loaded flag, and separate mutation/repaint calls. The inspected `MapHandle` surface exposes no current-viewport raster drawable certificate or native update token. | App-side source IDs, viewport generations, and property checks can invalidate stale work; they do not establish native raster decode/upload/draw or correlate an acknowledgment to a swap. `isFullyLoaded` is not a source-specific revision barrier. |
| `MapState.events`, `MapEvent.FrameRendered`, `RenderStats` | Finished frame reports mode, repaint/placement flags, timings, frame and draw counts. `Full` describes a map frame, not the frame/source/viewport/update identity. `Idle` means map-wide work is idle. Compose's `MlnFfiMapSession` selects `MAP_RENDER_FRAME_FINISHED`, but not `MAP_TILE_ACTION`; `MlnFfiMapEvents.toMapEvent()` forwards no tile action. | A Full event arriving after a setter can describe an older in-flight update; a counter increase does not identify the swap. Waiting for `Idle` also depends on unrelated map work. Surface recreation can reset native frame counters. |
| `RuntimeEventPayload.TileAction`, `TileOperation` | Lower-level FFI types include tile ID, source ID in the event message, and cache/load/parse/error operations. Compose owns and drains the live runtime, and does not select/forward those events to the app. | These types are not a usable live Compose readiness contract here. Even accessible parse/cache events alone have not been shown to certify every drawable viewport tile or a rendered swap, particularly across reused source IDs and lifetimes. |
| `RenderSessionHandle.renderUpdate()`, `RenderUpdate`, `readPremultipliedRgba8()` | The FFI handle can render/read a session; `RenderUpdate` reports `result` and `needsRepaint`, not the native update identity. The live Compose session is private and owned by the renderer thread. `MapSnapshotter.capture()` uses an independent private map and offscreen session. | No public handle to the live session or correlated output is established. A separate snapshot is not proof of what the live surface presented. Feature queries address features and do not establish raster tile coverage. |
| `RadarMapResourceProvider.observeNativeTiles`, `RadarTileLoader.awaitCoverage` | Existing app observations track native demand failures and app byte coverage; successful delivery is not a native rendering acknowledgment. | Cached app bytes can precede native delivery/decode; a native cache hit need not generate a new app-provider callback. Neither is a valid readiness certificate. |

### Two-opacity ordering

The existing `MapLibreRadarBackend.promote()` calls `setLayerProperty` for the replacement and then the predecessor inside one `withPlatformMap` callback. The source-level chain for a **changed** opacity is:

1. FFI [`map_set_layer_property`](https://github.com/maplibre/maplibre-native-ffi/blob/c29b61a0c45a0471d1b1d2c0bce23a458b6a6440/src/map/map.cpp#L6829-L6871) calls `Layer::setProperty`; native [`RasterLayer::setPropertyInternal` and `setRasterOpacity`](https://github.com/maplibre/maplibre-native/blob/d695deef12fc64ee075a892c2d913c45fe8c7b42/src/mln/style/layers/raster_layer.cpp) replace the layer implementation and notify `onLayerChanged` for a changed value.
2. [`Style::Impl::onLayerChanged`](https://github.com/maplibre/maplibre-native/blob/d695deef12fc64ee075a892c2d913c45fe8c7b42/src/mln/style/style_impl.cpp) updates the layer list and calls `onUpdate`; [`Map::Impl::onUpdate`](https://github.com/maplibre/maplibre-native/blob/d695deef12fc64ee075a892c2d913c45fe8c7b42/src/mln/map/map_impl.cpp) constructs update parameters containing the current layers and passes them to the frontend in continuous mode.
3. FFI [`HeadlessFrontend::update` and `latest_update`](https://github.com/maplibre/maplibre-native-ffi/blob/c29b61a0c45a0471d1b1d2c0bce23a458b6a6440/src/map/map.cpp#L1968-L1995) publish/read that latest update under a mutex. The separate render thread's [`render_session_render_update`](https://github.com/maplibre/maplibre-native-ffi/blob/c29b61a0c45a0471d1b1d2c0bce23a458b6a6440/src/render/render_session_common.cpp#L1248-L1424) snapshots it and renders it. An already scheduled render can therefore take the first changed-opacity update before the second setter publishes its update; the owner callback is not a renderer transaction.
4. [`ForwardingRendererObserver`](https://github.com/maplibre/maplibre-native-ffi/blob/c29b61a0c45a0471d1b1d2c0bce23a458b6a6440/src/map/map.cpp#L1801-L1856) posts render callbacks through the map-owner mailbox. Compose's Android surface controller documents a separate render thread and OpenGL surface presentation. The exposed frame event contains no consumed-update or presentation ID linking it to either setter.

This is a **possible interleaving from source inspection**, not an observed intermediate frame in this session. It rules out claiming atomicity solely from the owner-thread block; it does not claim that the intermediate update was actually presented or that every two-setter sequence must fail.

The native [`RasterTileWorker::parse`](https://github.com/maplibre/maplibre-native/blob/d695deef12fc64ee075a892c2d913c45fe8c7b42/src/mln/tile/raster_tile_worker.cpp) reports a null-data tile as empty to `onParsed`, while nonempty data is decoded to a raster bucket. That source observation is not an end-to-end proof for transparent/204 imagery, current coverage, GPU readiness, or presentation.

## Controlled probe and capture status

The existing `app/src/androidTest/java/net/droopia/hluweather/data/network/RadarRenderReadinessProbeTest.kt` holds the second source's provider delivery while filling app byte coverage, then treats map-wide Full as a barrier and polls a center pixel using `takeScreenshot()` with 50 ms delays. It is useful as a fixture reference, but its uncorrelated barrier and sampled output cannot prove this gate. It was **not run** for this gate and its assertions are not adopted as evidence. `RadarBufferedRendererRuntimeTest` also samples screenshots; historical results in `openspec/changes/speed-up-radar-loading/verification.md` describe the old acceptance contract and are not this gate's results.

| Required case | Result in this session |
| --- | --- |
| App bytes cached while native delivery is held | **NOT RUN** — no sound readiness candidate established. |
| Warm native cache hit without a fresh provider callback | **NOT RUN**. |
| Delayed/stale render observations and source/style/surface recreation | **NOT RUN** — the source audit identifies the correlation problem; it does not verify rejection. |
| Valid transparent and no-content/empty tiles | **NOT RUN** — worker source observation above is not a runtime pass. |
| Tiny-positive staging (`STAGING_OPACITY = 0.001f`) receives native demand/preparation | **NOT RUN**. |
| Unrelated basemap work remains pending | **NOT RUN** — no source-specific completion demonstrated. |
| Two-opacity atomic consumption and correlated swap acknowledgment | **NOT RUN / NOT PROVEN** — source ordering permits the interleaving described above. |
| Complete presented-frame capture or equivalent native live-output assertion | **BLOCKED / NOT RUN** — no completeness method or dropped-frame accounting established. No controlled transitions or presented frames were captured; missing frames and gap count were **NOT MEASURED**. |

No `screenrecord` result is treated as complete merely because the command exists. Repeated screenshots or an unvalidated recording could miss one refresh; the 60 Hz display alone does not establish capture cadence or completeness. No APK was built or installed for this gate, so there is no gate APK hash or app-level GPU measurement.

## Commands and raw results

The Google debug variant was the only Gradle variant used. No release build or signing was run.

1. `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:dependencyInsight --configuration googleDebugRuntimeClasspath --dependency maplibre-native-ffi` — **PASS**, `BUILD SUCCESSFUL`; resolved Compose `0.16.0` to native FFI and OpenGL runtime `0.202609.1`.
2. `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testGoogleDebugUnitTest --tests 'net.droopia.hluweather.ui.map.RadarFullRenderEventsTest'` — **PASS**, `BUILD SUCCESSFUL`. The XML at `app/build/test-results/testGoogleDebugUnitTest/TEST-net.droopia.hluweather.ui.map.RadarFullRenderEventsTest.xml` reports `tests="1" skipped="0" failures="0" errors="0"`, test `onlyLaterFullEventsAdvanceAcrossNativeFrameCounterReset`. It tests local event ordinal handling only; the latest invocation was up-to-date and this is not native readiness or continuity evidence.
3. `openspec validate reduce-radar-frame-flicker --strict` — **PASS**, `Change 'reduce-radar-frame-flicker' is valid`.
4. API-35 AVD availability only. Exact launch command:

   ```sh
   ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ANDROID_AVD_HOME=/home/homoludens/.config/.android/avd /home/homoludens/Android/Sdk/emulator/emulator -avd Pixel_3a_API_35_extension_level_13_x86_64 -port 5580 -read-only -no-snapshot -no-window -no-audio -gpu swiftshader_indirect -no-boot-anim
   ```

   Emulator `37.1.11.0` reported boot completed and logged GPU renderer `Android Emulator OpenGL ES Translator (Google SwiftShader)` and version `OpenGL ES 3.0 (OpenGL ES 3.0 SwiftShader 4.0.0.1)`. Initial ADB startup logged client/server version conflicts and overlay warnings; the subsequent SDK ADB availability checks succeeded. `adb devices -l` returned `emulator-5580 device`; `adb -s emulator-5580 emu avd name` returned `Pixel_3a_API_35_extension_level_13_x86_64`. The `adb` commands used `/home/homoludens/Android/Sdk/platform-tools/adb`.
5. `adb -s emulator-5580 shell getprop ro.build.fingerprint` returned `google/sdk_gphone64_x86_64/emu64xa:15/AE3A.240806.043/12960925:userdebug/dev-keys`; SDK was `35`, model `sdk_gphone64_x86_64`, and `wm size` was `1080x2220`. `dumpsys display` reported `renderFrameRate 60.000004` and density `440`. The renderer identification is from the emulator log, not an app-level graphics query.
6. `adb -s emulator-5580 emu kill` — `OK: killing emulator, bye bye`; the read-only AVD was stopped after the availability check. No native instrumentation or capture was performed.

Final radar-wide suites, `:app:assembleGoogleDebug`, live-provider checks, paired speed comparisons, and all later tasks are **NOT RUN**: this is the blocked first-task gate, not final integration verification.
