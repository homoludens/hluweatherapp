## Context

See `proposal.md` for motivation and `specs/weather-radar/spec.md` for the behavior contract. The current stack shares a Ktor/OkHttp radar client, an app scheduler, a 16 MiB compressed-tile memory cache, and a two-slot MapLibre renderer. Coverage and native tile requests share the tile loader. The previous normal handoff waited for two full-map events before promotion and two afterward; those events never proved that this specific radar source was drawn.

The working tree already contains **unverified partial implementation** in these files:

- `app/src/main/java/net/droopia/hluweather/data/network/RadarNetworkScheduler.kt`: eight total/four speculative limits.
- `app/src/main/java/net/droopia/hluweather/data/network/RadarHttpClient.kt`: matching OkHttp per-host dispatcher limit.
- `app/src/main/java/net/droopia/hluweather/data/model/RadarTileCoverage.kt`: shared two-level reduction and coarse render-source settings.
- `app/src/main/java/net/droopia/hluweather/ui/map/MapLibreRadarBackend.kt`: a raster-source factory using the coarse settings.
- `app/src/main/java/net/droopia/hluweather/ui/map/RadarRenderController.kt`: an internal `waitForFullRender = false` default and guarded fast acknowledgment.

These are starting points, not completed tasks. No tests or build were run after those experimental edits. Earlier focused test results predate them. Existing network/prefetch tests still contain assumptions about the previous concurrency and the older two-neighbors-per-side policy. Other pending edits include radar timeline text/layout, attribution placement, version 3.7.0/code 43, and a changelog; preserve them without expanding this change's scope. The local main radar spec already reflects the earlier one-neighbor-per-side/text changes, so deltas are based on that working-tree contract.

## Goals / Non-Goals

**Goals:**
- Reduce cold-frame tile work and eliminate application waits for full-map render events, with a small change to the existing pipeline.
- Keep foreground loading, native source selection, coverage checks, and prefetch on the same resolution policy.
- Make accepted native promotion the explicit playback readiness signal while preserving request-generation guards and cleanup.
- Provide reproducible verification and distinguish command acceptance, first visible pixels, and complete visual coverage when comparing speed.

**Non-Goals:**
- Guaranteed blink-free transitions, progressive detail upgrades, a single-tile-for-every-viewport scheme, new persistent caching, or new settings.
- Extra viewport debouncing, larger prefetch neighborhoods, asymmetric playback prefetch, or progressive partial-coverage display in this first experiment.
- Fixing or investigating the previously accepted renderer race beyond the tests needed to protect current-request ownership, lifecycle behavior, and resource cleanup.
- Provider/API licensing changes, unrelated maps or UI changes, release signing, or release builds.

## Decisions

### 1. Increase scheduler and transport capacity together

Use `MAX_CONCURRENT = 8` and `MAX_SPECULATIVE_CONCURRENT = 4`. The speculative count is a subset of total HTTP attempts, not a count of frames. Keep the 64-request speculative queue, foreground-first scheduling, retry bounds, and request cancellation. Configure OkHttp's per-host dispatcher limit from the same total constant so its default limit of five cannot negate the experiment.

RainViewer retains 90 attempts per rolling minute and a 20-attempt foreground reserve; metadata, tiles, retries, and prefetch still share it. Do not increase retry attempts or follow redirects automatically. Verify eight simultaneous held requests using a local HTTP server, not just a constant assertion.

Alternative: changing scheduler constants alone is smaller but can remain bottlenecked by the transport. Unbounded concurrency is rejected because it harms foreground fairness and provider load.

### 2. One shared coarse policy; no deferred refinement

Choose a fixed reduction of two source zoom levels for both providers. For image size `s`, use:

```text
normalUnclampedZoom = floor(cameraZoom + log2(512 / s) + 0.5)
coarseMaxZoom = max(nativeMinZoom, nativeMaxZoom - 2)
coarseZoom = clamp(normalUnclampedZoom - 2, nativeMinZoom, coarseMaxZoom)
```

Both providers continue to download 256-pixel PNG tiles. Declare their display tile size as 1024 points in the native raster source and cap its zoom at the same coarse maximum, making native selection match the app's coverage formula. This is display scale, not a request for unsupported 1024-pixel provider images. LibreWXR caps at zoom 10 and RainViewer at zoom 5. At very low zoom, clamp rather than request negative levels.

Use the same policy in `RadarTileCoverage`, native source construction, and `planRadarTiles`. Keep conservative viewport coverage, wrapped-coordinate deduplication, provider-scoped identity, and nearest-first prefetch. Preserve one neighboring timeline index in each direction. Never run a second high-resolution pass when the viewport is stationary; deliberate camera changes simply recompute the same coarse policy.

Tests must verify requested URL image size, source JSON/display settings, calculated coordinates, and actual native request zooms when a device is available. A planner-only change is insufficient: the native source would otherwise independently fetch detailed tiles. Two levels can reduce tile count substantially, but boundary crossings and small viewports prevent promising a universal sixteenfold reduction.

Alternative: a low-resolution preview followed by detail is rejected because the user explicitly wants coarse imagery only. One level lower is a smaller quality trade-off but does less to expose the possible speed improvement in this trial.

### 3. Coverage/install gate with immediate guarded acknowledgment

The normal controller path still establishes staging and awaits successful complete coarse viewport coverage; metadata or a timer cannot substitute for tiles. Once installation and coverage succeed, invoke the existing native promotion with the current generation's mutation guard. In that guard, atomically update front/staging/retired ownership and acknowledge the accepted frame. Do not wait for `awaitFullRender` before or after promotion or to recycle an accepted predecessor.

An interruption after native mutation but before the coroutine resumes must not leave bookkeeping claiming the old frame is displayed. Conversely, an obsolete guard must not publish a frame or restore imagery after hiding, provider switching, or style replacement. Retry/failure paths must retain the previous front when replacement coverage or installation fails. Review cancellation around promotion and removal, zero opacity, invalid viewport rejection, and retained source leases; test normal fast-path behavior directly.

Keep the existing conservative internal option only for diagnostics and legacy barrier-specific tests. Such tests must explicitly opt in, while the main map remains barrier-free by default. Do not introduce a user-facing mode selector or use conservative tests as a substitute for fast-path coverage. Update playback terminology/tests so `displayed` in this path means accepted/submitted, not screenshot-confirmed. Playback still waits for this acknowledgment before its next approximately 600 ms step.

Alternative: replace two-event waits with one-event waits is rejected because it still depends on an unscoped event and is not the requested speed-first experiment. Removing coverage checks as well would show partial frames and broadens this change unnecessarily.

### 4. Evidence separates readiness from visible performance

During iteration run the smallest relevant Google debug tests. Then run the radar regression tests and `:app:assembleGoogleDebug`; include existing tests affected by earlier pending neighborhood changes rather than silently ignoring their stale expectations. Add/adapt controlled native runtime tests for coarse requests, no later detailed requests, and fast-path lifecycle behavior; barrier diagnostics must explicitly use the conservative option.

When a device/AVD is available, compare baseline and experiment using the same provider, viewport, device, and network conditions. Use an isolated baseline worktree/artifact rather than resetting the dirty checkout. Compare cold and warm loads, first actual radar pixels, coarse coverage, adjacent-frame reuse, HTTP count/bytes, and visible gaps. A faster controller acknowledgment alone is not evidence of faster visible radar. Do not add a general telemetry system; use bounded test/local measurements. Record exact commands, environment, results, and missing checks in `verification.md` during implementation. Unavailable native/live checks are not run, not passed; a lack of device does not justify inventing performance numbers.

## Risks / Trade-offs

- **Blurrier radar and fewer local details** → Keep basemap resolution intact, document fixed coarse-only behavior, and assess quality on device before release decisions.
- **More blinking or promotion before native drawing** → Explicitly accepted for the experiment; preserve coverage and guarded ownership, and do not present command acceptance as pixel proof.
- **Coarse planner/native zoom mismatch** → Use one policy for source settings and coverage, assert source configuration in unit tests, and inspect real native tile requests when possible.
- **Higher concurrency causes provider/network contention** → Keep foreground priority, bounded work, RainViewer budget/backoff, and the existing cache limits; compare observed behavior rather than assuming throughput doubles.
- **Old tests accidentally exercise only the diagnostic path** → Add default-mode no-event tests and lifecycle/cancellation coverage; retain barrier-specific cases only with explicit opt-in.
- **Partial edits or unrelated work are overwritten** → Inspect and preserve the dirty working tree; complete no checkbox solely because code is already present.
- **RainViewer public distribution remains unresolved** → This experiment grants no distribution permission and must not change that documentation warning.

## Migration Plan

No persisted preferences, metadata format, cache schema, or provider URL options change. Complete and verify the scheduler/transport and coarse-source changes, then the guarded fast controller/playback path, then documentation and regression evidence. Main specs should receive this delta through the normal sync/archive workflow, not by replacing existing contracts during planning.

Rollback is source-local: restore the prior four/two scheduler and matching transport policy, normal raster tile size/native cap and coverage calculation, and conservative render waits as one coordinated change. Do not revert unrelated timeline/layout/version work or mix a coarse planner with a detailed native source. Neither migration nor rollback requires a signed build or persistent-data reset.
