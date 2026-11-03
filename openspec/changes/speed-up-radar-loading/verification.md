# Task 4.1 — provider guide and performance verification procedure

## Status

The guide and this procedure were manually reviewed against the change's `specs/weather-radar/spec.md`, `design.md`, and `proposal.md`. No live-provider baseline-versus-experiment performance comparison has been run. Command-acceptance latency, first-visible-pixel latency, complete-coverage latency, request counts/bytes, and visual-quality results are **NOT RUN**; visible speedup is unverified. The integrated task 5.1 tests, Google debug build, and strict OpenSpec validation are also **NOT RUN** here.

Previously reported implementation results are carried forward, not rerun for this documentation task: task 1.1's focused HTTP/concurrency tests passed (an earlier broad network run had four stale prefetch failures, subsequently corrected by task 2); task 2.1's focused suite passed 61/61; task 3.1's focused unit suite passed 61/61 and its native runtime suite passed 3/3 on Pixel 3a, API 35, emulator-5584. These results do not measure live-provider visible performance.

## Comparison target

- **Baseline:** commit `40d1077`, the pre-experiment source state. Use a separate detached worktree; do not reset, check out, or discard changes in the active checkout.
- **Experiment:** source state through task 3.1, commit `c9029d1`. Task 4.1 changes documentation only.
- Build and compare the same Google debug variant on the same device, provider, frame, viewport, and network conditions. Record each source revision and the SHA-256 of its installed APK. Do not substitute an acknowledgment-time comparison for visible-performance evidence.

## Reproducible setup

1. From the repository, create a new, unused baseline worktree (choose a different unused path if this example already exists):

   ```sh
   git worktree add --detach /tmp/opencode/radar-baseline-40d1077 40d1077
   ```

   Keep the experiment in the active checkout at `c9029d1` plus the documentation-only task 4.1 edits. Do not move the active checkout to the baseline revision.

2. On each checkout, build only `GoogleDebug` with the same SDK configuration:

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

## Results and checks for this task

| Check | Result |
| --- | --- |
| Manual comparison of guide/procedure with delta spec and design | **PASS** — coarse-only policy and caps, 256-pixel requests/1024-point display tile size, 8/4 concurrency, guarded acceptance semantics, accepted gap/blinking trade-off, and one neighbor per side are represented. Cache/privacy/rate-limit and unresolved RainViewer distribution warnings are retained. |
| Live cold/warm baseline-versus-experiment runs | **NOT RUN** |
| Adjacent-frame network/reuse comparison | **NOT RUN** |
| Command acknowledgment, first pixels, complete coverage, request count/bytes, visual quality | **NOT RUN** — no measurements recorded; visible speedup remains unverified. |
| Task 5.1 integrated Google debug tests/build and strict OpenSpec validation | **NOT RUN** — deferred to task 5.1. |
