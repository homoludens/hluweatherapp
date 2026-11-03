# Radar speed experiment verification

## Status

The guide and this procedure were manually reviewed against the change's `specs/weather-radar/spec.md`, `design.md`, and `proposal.md`. Controlled native runtime checks are recorded under task 5.1 below. A later visual feasibility probe is also recorded below: both builds showed no radar pixels for the sampled RainViewer location/frame, so no cold/warm or adjacent performance trial was run. Command-acceptance and complete-coverage latency, live request counts/bytes, and visual gap/quality comparison remain **NOT MEASURED / NOT RUN**; first-visible-pixel timing is unavailable for the no-signal sample. Visible speedup remains unverified. Unit tests and synthetic-transport native tests are not live performance evidence.

Previously reported implementation results are carried forward, not rerun for this documentation task: task 1.1's focused HTTP/concurrency tests passed (an earlier broad network run had four stale prefetch failures, subsequently corrected by task 2); task 2.1's focused suite passed 61/61; task 3.1's focused unit suite passed 61/61 and its native runtime suite passed 3/3 on Pixel 3a, API 35, emulator-5584. These results do not measure live-provider visible performance.

## Comparison target

- **Baseline:** commit `40d1077`, the pre-experiment source state, built in a separate detached worktree.
- **Experiment:** code through task 3.1 at `c9029d1`; `a8d83fb` adds only task 4.1 documentation. Task 5.1 used a clean detached worktree at `a8d83fb` because the active checkout contains unrelated dirty timeline, attribution, version, changelog, and main-spec work.
- The active checkout was not reset, checked out, or discarded. Both comparison worktrees were created separately and removed after use.
- Build and compare the same Google debug variant on the same device, provider, frame, viewport, and network conditions. Record each source revision and the SHA-256 of its installed APK. Do not substitute an acknowledgment-time comparison for visible-performance evidence.

## Reproducible setup

1. From the repository, create new, unused baseline and experiment worktrees (choose different unused paths if these examples already exist):

   ```sh
   git worktree add --detach /tmp/opencode/radar-baseline-40d1077 40d1077
   git worktree add --detach /tmp/opencode/radar-experiment-a8d83fb a8d83fb
   ```

2. From each worktree, build only `GoogleDebug` with the same SDK configuration:

   ```sh
   ANDROID_HOME=/home/homoludens/Android/Sdk \
   ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk \
   ./gradlew :app:assembleGoogleDebug
   ```

   Install one build at a time on a dedicated test device or AVD; record revision, APK hash, device model/API, screen size/density, and build result. Use the same device and Google debug variant for both. Do not clear data from a personal installation.

3. Fix and record the selected provider, exact frame timestamp/path, map center, camera zoom, bearing and pitch, orientation, and network type for each paired trial. Use a frame available in both builds. Keep network and device load stable, disable unrelated map interaction, and alternate baseline/experiment order across repeated pairs. If the same frame or conditions cannot be reproduced, mark that pair invalid rather than comparing different inputs.

4. Use a dedicated test install/profile and clear that app's data before each **cold** trial, then launch a fresh process and restore the recorded provider and viewport. This deliberately clears persisted metadata and basemap state too, so apply the same procedure to both builds and report metadata requests separately. The radar tile byte cache is memory-only; process/data clearing makes the radar tile cache cold. For a **warm same-frame** trial, keep the process alive, load target frame `i`, load a frame at least three timeline indices away, then return to `i`; the intervening frame avoids making `i` an immediate prefetch neighbor. Record actual cache/network behavior rather than assuming the frame was warm.

## Cold, warm, and adjacent-frame runs

Run at least five paired trials per case, alternating which build runs first. Capture each case from the timeline selection command through completion of coarse coverage. Keep the provider, frame path, camera, device, and network fixed within a pair.

1. **Cold frame:** with app data cleared and a fresh process, select a middle timeline frame `i` at the fixed viewport. Record the selection command and all subsequent work until complete coarse coverage or a terminal failure. Keep the viewport stationary afterward long enough to check that no higher-detail upgrade is requested.
2. **Warm same frame:** in the same process, load a frame at least three indices from `i`, then reselect `i`. Record cache hits and any new HTTP requests/bytes separately from the cold pass.
3. **Adjacent-frame prefetch/reuse:** in a separate cold setup, select middle frame `i`, allow its current-viewport requests and eligible prefetch to settle, then select `i-1`. Repeat from a fresh equivalent setup for `i+1`. Record which requested-frame tiles were already available and which caused HTTP attempts. Also check the first and last timeline entries for valid neighbors only and no wraparound.

Use the same local test trace/recorder on both builds to record timeline-command time, guarded native-promotion acceptance time, the planned canonical coarse tile keys and each tile's completion/status, requested tile zooms, and cache hits. If the trace cannot expose a field without changing scheduling or caching, mark that field **NOT MEASURED**; do not infer it from timeline text or a faster command acknowledgment. Capture the display continuously using the same screen-recording method in both builds. For first-pixel timing, select a frame/viewport with visible radar signal and record the first captured frame containing radar pixels; if no radar signal is visible, report this measure unavailable, not zero.

For each run, report these outcomes independently:

| Outcome | What to record |
| --- | --- |
| Command acknowledgment | Selection-command timestamp to acceptance of that request's guarded native promotion. Acceptance is submitted/accepted work, **not proof of drawing**. |
| First visible pixels | Selection-command timestamp to first captured radar pixels in the map viewport; include capture method and note if the chosen frame has no visible signal. |
| Complete coarse coverage | Time until every planned canonical coarse viewport tile has completed successfully (including successful transparent/empty tiles); record expected, loaded, missing/failed keys and their zooms. This is separate from native acceptance and pixels. |
| Requests and bytes | HTTP attempt count, success/failure count, and response-body bytes by metadata, visible, retry, and speculative work; distinguish network requests from app-cache hits. Record requested `z/x/y`, status, and any later detail request while the viewport is stationary. Include wire bytes only if the capture tool reports them separately. |
| Visual quality | Save same-scale screenshots/video and note coarse detail/blur, alignment, uncovered/basemap-only gaps, opacity discontinuities, and blinking during handoff. Do not treat an accepted promotion as a visual pass. |

Use Android Studio Network Inspector or an equivalent recorder only if it captures this app's Ktor/OkHttp requests and response sizes on both builds. If it does not, do not estimate counts or bytes from elapsed time; mark them **NOT MEASURED** until an equivalent recorder is available. Preserve each run's raw trace and screenshots with its build/device/provider/frame identifiers.

## Task 5.1 integrated verification — 2026-11-03

### Device and provider probe

- AVD command: `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ANDROID_AVD_HOME=/home/homoludens/.config/.android/avd /home/homoludens/Android/Sdk/emulator/emulator -avd Pixel_3a_API_35_extension_level_13_x86_64 -no-window -no-audio -gpu swiftshader_indirect -no-snapshot-load -no-boot-anim` — booted as `emulator-5554`.
- Device: AVD `Pixel_3a_API_35_extension_level_13_x86_64`; Android API 35; reported model `sdk_gphone64_x86_64`; 1080×2220 at 440 dpi; Google APIs x86_64 image; SwiftShader rendering.
- Host provider probe: `curl --connect-timeout 10 --max-time 20 --silent --show-error --output /dev/null --write-out 'RainViewer metadata: HTTP %{http_code}, response bytes %{size_download}\n' https://api.rainviewer.com/public/weather-maps.json` — HTTP 200, 818 response bytes. This was a host metadata reachability check only, not an app tile request or performance measurement.
- A qualifying app-level Ktor/OkHttp recorder was not established: `mitmproxy` was unavailable on the host and `tcpdump` was unavailable in the AVD. Android Studio Network Inspector was not configured/exercised to verify request URLs and response-body sizes for both APKs. Therefore no qualifying paired live cold/warm/adjacent performance trial was run. A limited visual feasibility probe was run and is documented below; actual radar signal was absent in the sampled frames. Pixel timing and gap-quality outcomes were unavailable, request counts and response bytes were **NOT MEASURED**, and visible speedup remains unverified.

### Exact commands and results

All Gradle commands below used the Google debug variant only; no release build or signing was run.

1. From the dirty active checkout, `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testGoogleDebugUnitTest` — **FAIL / NOT PASS**: 894 tests, one failure. `HluWeatherApplicationTest.application_uses_branded_launcher_metadata` expects version code 42, but the pre-existing dirty `app/build.gradle.kts` sets version code 43 (and version name 3.7.0 rather than the test's 3.6.3). That unrelated version work and its test were preserved unchanged.
2. From clean experiment worktree `/tmp/opencode/radar-experiment-a8d83fb` at `a8d83fbd4ca7f96dfe15eaba529191d98d2adb12`, `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testGoogleDebugUnitTest` — **PASS**, 894 tests, 0 failures/errors/skips.
3. From the active checkout, `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:assembleGoogleDebug` — **PASS**. The build reported its existing debug native libraries were packaged unstripped.
4. From baseline worktree `/tmp/opencode/radar-baseline-40d1077` at `40d1077d8fa1a750d43a01e590cd9722ee9351b4`, `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:assembleGoogleDebug` — **PASS**. APK SHA-256: `e5a555a89927027e63f96ba1fb4ef1907a506089e572a57996b355f3bbaa65bc`.
5. From experiment worktree `/tmp/opencode/radar-experiment-a8d83fb`, `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:assembleGoogleDebug` — **PASS**. APK SHA-256: `7e84ba5bb8d373aab21e415be2957b8948e9a75e36d726f7467c30b7a5d0a59d`.
6. Controlled backend native check, from the active checkout:

   ```sh
   ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ANDROID_SERIAL=emulator-5554 PATH=/home/homoludens/Android/Sdk/platform-tools:$PATH ./gradlew :app:connectedGoogleDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=net.droopia.hluweather.ui.map.RadarBufferedRendererRuntimeTest#defaultControllerAcceptsCoverageBeforeNativeDeliveryWithoutRenderBarriers
   ```

   Final run **PASS**, 1/1 test. With a stationary high-zoom RainViewer viewport, real MapLibre callbacks emitted zooms `{1, 5}`: four zoom-1 coarse parent fallbacks and four planned zoom-5 tiles. The controlled fake transport saw 8 initial attempts and 0 additional attempts during the following 5 seconds; no request exceeded the zoom-5 render cap. An initial assertion requiring only zoom 5 was corrected to allow the observed coarser parent fallback while still rejecting any zoom above the cap.

7. Controlled native source/coverage check, from the active checkout:

   ```sh
   ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ANDROID_SERIAL=emulator-5554 PATH=/home/homoludens/Android/Sdk/platform-tools:$PATH ./gradlew :app:connectedGoogleDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=net.droopia.hluweather.data.network.RadarResourceRuntimeTest#volatileHookRendersAndCoversNativeTilesAcrossCameraCases
   ```

   Final run **PASS**, 1/1 test covering seven native cases. The emitted zooms matched the coarse plan: RainViewer `{3, 2, 3, 5, 5}` and LibreWXR `{0, 10}`. The test fixture now uses the coarse native max zoom and 1024-point display tile size. Its synthetic transport and local map tiles do not measure provider performance.

8. `openspec validate speed-up-radar-loading --strict` — **PASS** (`Change 'speed-up-radar-loading' is valid`). This validation was rerun after the final task checkbox update.

### Results and checks

| Check | Result |
| --- | --- |
| Manual comparison of guide/procedure with delta spec and design | **PASS** — coarse-only policy and caps, 256-pixel requests/1024-point display tile size, 8/4 concurrency, guarded acceptance semantics, accepted gap/blinking trade-off, and one neighbor per side are represented. Cache/privacy/rate-limit and unresolved RainViewer distribution warnings are retained. |
| Full unit suite on clean experiment code | **PASS** — `:app:testGoogleDebugUnitTest`, 894/894. |
| Full unit suite on dirty active checkout | **FAIL / NOT PASS** — 893 passed, 1 unrelated version assertion failure described above. |
| Active Google debug assembly and clean baseline/experiment Google debug assemblies | **PASS** — hashes recorded above; no release variant built. |
| Native actual coarse request zooms and no delayed detail fetch | **PASS** — controlled native checks above; 5-second stationary check found no request above the RainViewer coarse cap and no additional fake-transport attempt. |
| Live cold/warm baseline-versus-experiment visible comparison | **NOT RUN / NOT PASSED** — visual feasibility probe found no radar pixels at the sampled RainViewer location/frame; no qualifying app-level request/byte trace. |
| Adjacent-frame live network/reuse comparison | **NOT RUN / NOT PASSED**. |
| Command acknowledgment, first visible pixels, complete live coverage, live request counts/bytes, visual gaps/quality | **NOT MEASURED** — first-pixel timing unavailable for the no-signal sample; visible speedup remains unverified. |

### Task 5.1 visual-only feasibility follow-up — 2026-11-03

- Assigned model: `openai/gpt-6-luna#max`.
- Created clean detached worktrees at baseline `40d1077d8fa1a750d43a01e590cd9722ee9351b4` and experiment `a8d83fbd4ca7f96dfe15eaba529191d98d2adb12`. Exact setup/build commands:

  ```sh
  git worktree add --detach /tmp/opencode/radar-baseline-40d1077 40d1077
  git worktree add --detach /tmp/opencode/radar-experiment-a8d83fb a8d83fb
  ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:assembleGoogleDebug
  ```

  The build command was run from each worktree and passed in both. Baseline APK SHA-256: `e5a555a89927027e63f96ba1fb4ef1907a506089e572a57996b355f3bbaa65bc`; experiment APK SHA-256: `7e84ba5bb8d373aab21e415be2957b8948e9a75e36d726f7467c30b7a5d0a59d`. No release build or signing was run.
- Restarted the dedicated AVD with:

  ```sh
  ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ANDROID_AVD_HOME=/home/homoludens/.config/.android/avd /home/homoludens/Android/Sdk/emulator/emulator -avd Pixel_3a_API_35_extension_level_13_x86_64 -no-window -no-audio -gpu swiftshader_indirect -no-snapshot-load -no-boot-anim
  ```

  `adb devices -l` reported `emulator-5554`; device properties were model `sdk_gphone64_x86_64`, API 35, 1080×2220 at 440 dpi. Boot initially displayed “System UI isn't responding”; tapping **Wait** recovered the launcher. The first ADB invocation also had a client/server version mismatch; using the SDK's `platform-tools` in `PATH` resolved it.
- Installed baseline and created an isolated app profile with:

  ```sh
  ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk PATH=/home/homoludens/Android/Sdk/platform-tools:$PATH adb -s emulator-5554 install -r /tmp/opencode/radar-baseline-40d1077/app/build/outputs/apk/google/debug/app-google-debug.apk
  ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk PATH=/home/homoludens/Android/Sdk/platform-tools:$PATH adb -s emulator-5554 shell pm clear net.droopia.hluweather
  ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk PATH=/home/homoludens/Android/Sdk/platform-tools:$PATH adb -s emulator-5554 shell am start -n net.droopia.hluweather/.MainActivity
  ```

  The first-location flow used the AVD location and resolved to **Mendocino County**. All ADB commands below used the same SDK-prefixed invocation; the successful UI controls were:

  ```sh
  ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk PATH=/home/homoludens/Android/Sdk/platform-tools:$PATH adb -s emulator-5554 shell input tap 676 2044
  ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk PATH=/home/homoludens/Android/Sdk/platform-tools:$PATH adb -s emulator-5554 shell input tap 992 319
  ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk PATH=/home/homoludens/Android/Sdk/platform-tools:$PATH adb -s emulator-5554 shell input tap 700 925
  ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk PATH=/home/homoludens/Android/Sdk/platform-tools:$PATH adb -s emulator-5554 shell uiautomator dump /sdcard/window.xml
  ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk PATH=/home/homoludens/Android/Sdk/platform-tools:$PATH adb -s emulator-5554 shell input tap 500 1604
  ```

  An initial selection tap/swipe at `y=1465` did not select a RainViewer frame because that provider's shorter attribution moved the slider lower; the hierarchy dump showed slider bounds `[27,1549][1053,1659]`, after which `tap 500 1604` selected `Tue, Nov 3, 2026 • 00:50`. The current/initial frame label was `Tue, Nov 3, 2026 • 02:00`.
- Baseline screenshots showed no visible radar-colored pixels outside the location marker at either `02:00` or `00:50`. The app was left with the test location/provider state; experiment was installed over baseline, without clearing app data, solely to check the same saved location/UI frame, not as a cold trial:

  ```sh
  ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk PATH=/home/homoludens/Android/Sdk/platform-tools:$PATH adb -s emulator-5554 install -r /tmp/opencode/radar-experiment-a8d83fb/app/build/outputs/apk/google/debug/app-google-debug.apk
  ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk PATH=/home/homoludens/Android/Sdk/platform-tools:$PATH adb -s emulator-5554 shell am start -n net.droopia.hluweather/.MainActivity
  ```

  The same UI frame label `00:50` and Mendocino County map view were visible in experiment, but it also showed no radar pixels. The exact screenshot commands were:

  ```sh
  ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk PATH=/home/homoludens/Android/Sdk/platform-tools:$PATH adb -s emulator-5554 exec-out screencap -p > /tmp/opencode/radar-visual-attempt-20261103/baseline-rainviewer-actual.png
  ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk PATH=/home/homoludens/Android/Sdk/platform-tools:$PATH adb -s emulator-5554 exec-out screencap -p > /tmp/opencode/radar-visual-attempt-20261103/baseline-middle-frame.png
  ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk PATH=/home/homoludens/Android/Sdk/platform-tools:$PATH adb -s emulator-5554 exec-out screencap -p > /tmp/opencode/radar-visual-attempt-20261103/experiment-map-default.png
  ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk PATH=/home/homoludens/Android/Sdk/platform-tools:$PATH adb -s emulator-5554 exec-out screencap -p > /tmp/opencode/radar-visual-attempt-20261103/experiment-middle-frame.png
  stat -c '%n | %y | %s bytes' /tmp/opencode/radar-visual-attempt-20261103/baseline-rainviewer-actual.png /tmp/opencode/radar-visual-attempt-20261103/baseline-middle-frame.png /tmp/opencode/radar-visual-attempt-20261103/experiment-map-default.png /tmp/opencode/radar-visual-attempt-20261103/experiment-middle-frame.png
  ```

  Screenshot capture times and files:

  | Build | UI frame | Capture (local, +0100) | Evidence |
  | --- | --- | --- | --- |
  | Baseline | `02:00` | `2026-11-03 02:10:10.846746809` | `/tmp/opencode/radar-visual-attempt-20261103/baseline-rainviewer-actual.png` |
  | Baseline | `00:50` | `2026-11-03 02:13:01.110093025` | `/tmp/opencode/radar-visual-attempt-20261103/baseline-middle-frame.png` |
  | Experiment | `02:00` | `2026-11-03 02:14:34.916766790` | `/tmp/opencode/radar-visual-attempt-20261103/experiment-map-default.png` |
  | Experiment | `00:50` | `2026-11-03 02:14:55.293434998` | `/tmp/opencode/radar-visual-attempt-20261103/experiment-middle-frame.png` |

  Times are screenshot file modification times, not selection-to-pixel timings. Numeric camera center/zoom and the RainViewer frame URL/path were not exposed by the UI and were not independently captured. No screen recording or timing trial was made because no actual radar signal was visible in either build at the sampled frame/location; the screenshots cannot establish a pixel latency or a gap/blink result. The probe therefore does not satisfy a paired cold, warm, or adjacent-frame case, let alone five pairs per case.
- The host check `command -v ffmpeg; command -v ffprobe; command -v mitmproxy; command -v tcpdump; command -v scrcpy` found only `/usr/bin/ffmpeg` and `/usr/bin/ffprobe`. Android Studio Network Inspector was not configured or exercised. App-level request attempts, response sizes/bytes, tile status/cache behavior, and complete coverage are **NOT MEASURED**; no test transport or timing estimate was substituted for live-provider evidence.
- Cleanup commands:

  ```sh
  ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk PATH=/home/homoludens/Android/Sdk/platform-tools:$PATH adb -s emulator-5554 uninstall net.droopia.hluweather
  ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk PATH=/home/homoludens/Android/Sdk/platform-tools:$PATH adb -s emulator-5554 emu kill
  git worktree remove /tmp/opencode/radar-baseline-40d1077
  git worktree remove /tmp/opencode/radar-experiment-a8d83fb
  ```

  Both worktrees were removed; `git worktree list --porcelain` shows only the active checkout. The test APK was uninstalled and the emulator process exited; the existing AVD definition/data were not wiped. Screenshots above were retained under `/tmp/opencode/radar-visual-attempt-20261103`. The active dirty checkout was not reset, checked out, or discarded. `openspec validate speed-up-radar-loading --strict` passed again after this follow-up. Task 5.1's checkbox remains unchanged; the visual speedup remains unverified.
