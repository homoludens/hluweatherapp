## Context

See `proposal.md` and the delta spec. The user explicitly replaced the previous readiness-gated approach with best-effort double buffering that does not depend on a MapLibre readiness signal. Keep the previous audit in `verification.md` as historical evidence, not an active gate. No implementation has passed yet.

The existing `RadarRenderController` already uses two slots, but creates staging only after the requested selection changes. `RadarPrefetchController` warms compressed bytes, not a native layer. The improvement is to install the next frame into the spare native slot while the current frame is still displayed, and reuse it when selected. Existing unconditional staging removal must not destroy that matching prepared slot.

Keep MapLibre Compose 0.16.0, the two-level coarse tile policy, 256-pixel requests/1024-point display tiles, caps 5/10, eight total/four speculative requests, RainViewer's existing 90/minute budget and 20-attempt reserve, and cache/privacy policies. Baseline for comparison is speedup revision `73fcfe8879e960aa02ae5f07baecc5f58b01bfcb`.

The predecessor `speed-up-radar-loading` is implemented but its delta is not synced into the main spec. Separately requested finalization must apply that predecessor first, then this follow-up; do not revert its loading optimizations or acceptance semantics.

## Goals / Non-Goals

**Goals:**
- Give native delivery/decoding a head start during the current 600 ms interval, reducing the observed gap without new rendering architecture.
- Bound app-controlled warm-up, retirement, memory, and cancellation; keep foreground work and latest selection authoritative.
- Measure observed flicker and visible latency honestly rather than claiming timing proves native readiness.

**Non-Goals:**
- Source-specific readiness certificates, render-generation fences, proof of every physical presentation, or guaranteed gap-free handoff.
- Dependency upgrades, internal API access, native forks, custom compositors, crossfades, deliberate full-opacity overlap, new settings/caches, or additional speculative timeline neighbors.
- Uninterrupted provider/style/surface transitions, newly exposed viewport coverage, unrelated UI/maps, persistent-data migration, or release builds/signing.

## Decisions

### 1. Use two slots ahead of selection, not two readiness events

The controller owns at most two live source/layer pairs, with distinct current-front and back/retired roles:

```text
Current display interval (~600 ms):
  A: visible  ------------------------------------+
  B: spare -> cached bytes -> install -> warm -----+-> selected

Switch:
  submit B at selected opacity; submit A at zero
  acknowledge B as accepted (not proven drawn)
  retain hidden A briefly -> remove -> prepare C
```

Keep staging at `min(selectedOpacity, 0.001f)` so native tile demand is not disabled by zero opacity. Preparing an unrequested frame never changes requested/displayed identity, pending status, error, or the playback timer. Reuse B when frame identity, canonical coarse tile set, style/surface lifetime, and source registration still match. A different request cancels B and takes the spare slot. Physical native sources can be removed/recreated as slots recycle; no raster-source retargeting API is required.

**Alternative:** only downloading more bytes leaves native preparation until selection. A third slot or a full timeline's native layers is unnecessary and increases memory/cleanup complexity.

### 2. Use small bounded timers as heuristics, explicitly not readiness

Initial proposed internal defaults are **50 ms warm-up** and **50 ms hidden-predecessor retention**, injected with a monotonic clock/scheduler for deterministic tests. They are tunable implementation constants, not settings or claims about GPU latency.

- Warm-up starts at the later of accepted native installation and successful complete app-side coarse byte coverage for that exact slot/viewport. Coverage and installation remain mandatory; elapsed time never substitutes for them. For optional preparation coverage is checked cache-only.
- Once an actual request adopts matching B, revalidate byte freshness/cache-control and ownership. Wait only `max(0, 50 ms - elapsedCompatibleWarmup)`. A fully warmed B adds **zero** warm-up at selection. Reset age if coverage or viewport/lifetime became incompatible. Non-cacheable or failed warmup is not reused.
- If there is no visible front, skip this artificial warm-up: initial loading keeps the current speed-first behavior.
- Submit B at the latest chosen opacity before submitting A at zero in the existing guarded owner-thread block; keep raster fade/opacity transitions disabled. Do not insert a deliberately visible overlap. This callback is **not an atomic render transaction**; an intermediate native state or late decode can still cause a pulse/gap.
- Record accepted promotion under the same mutation guard as ownership, as in the existing fast path. Retain A hidden for up to 50 ms from accepted promotion, then remove it without a frame event. This retention protects ownership/cleanup but **does not hide a blank**, since A is already transparent. New demand may remove A early to obtain the spare slot.
- Keep the existing 45-second requested-operation timeout; the optional preparation and cleanup waits must never serialize a new request behind an uninterruptible timer. Do not repeat/extend a warm-up for the same eligible request.

Warm-up is chosen because a few tens of milliseconds can give native decode/upload time when ahead-of-time preparation missed. Its effectiveness is empirical. No chosen delay can prove that replacement pixels exist, and an intentionally held native delivery longer than the grace may still blank after promotion.

**Alternatives:** a 600 ms extra delay undoes the speedup. Full/Idle or a fixed count of render callbacks reintroduces event dependence without source readiness. Crossfading/retaining A visibly under B can ghost precipitation and brighten transparent rasters; exclude it from this plan.

### 3. Keep ownership guards; replace the readiness audit with implementable invariants

The controller remains the single owner of front, optional/demand back, and hidden-retired roles, counting all roles toward the two-slot cap. Use separate selection/preparation/cleanup generations and immutable slot IDs so obsolete timers, prefetch completions, and removals cannot affect reused ownership. The main-map backend remains responsible for native source/layer validation, volatile configuration, and actual removal.

Cancellation after partially applied promotion must retain enough state for the current generation to restore the compatible old front before removing a failed back. After a promotion was accepted, do not let cancellation of its coroutine leave displayed bookkeeping claiming A. Later failures cannot be called pre-promotion failures or guaranteed detectable from byte coverage. A failed removal retains its lease and blocks allocation of a third slot, but must not deliberately clear the current front. Never hold the short cross-thread lock across suspension.

Pause cancels only unrequested preparation. Manual selection pauses but may adopt a matching installed back rather than needlessly destroying it; withdraw its optional owner without removing a slot now owned by demand. Disabling radar, lifecycle stop, provider/style/surface change, or incompatible visible tile set invalidate outgoing timers/preparation. At zero opacity stop optional preparation and hide live slots; positive restoration uses current compatible state or a new normal demand request, without any invisible-render handshake. Empty/transparent/204 tiles remain successful coverage.

**Alternative:** a global ready boolean, shared cleanup timer, or role-only lookup can mutate the wrong slot after rapid B→C requests. Existing request fencing plus explicit slot generations is sufficient; no native render revision is introduced.

### 4. Warm the next frame from the existing byte cache, with scoped retry opportunities

While playing, use the same metadata order as playback to target only i+1 when it exists without wrapping. Byte prefetch stays i−1/i+1. At the final entry, playback still loops to index 0 via ordinary requested loading; do not add speculative wraparound. Foreground demand gets the spare slot before optional work. Begin optional native preparation when the front is accepted, hidden predecessor cleanup has yielded a spare slot, and all next-frame coarse bytes are valid in cache.

Expose a small plan-scoped byte-prefetch progress notification from `RadarPrefetchController` for completion of next-frame coverage. Check once on a new target/viewport and reconsider when that matching coverage completes; otherwise bytes arriving during the display interval would never trigger native preparation. This is app-cache progress, **not renderer readiness**. Do not add periodic polling or recreate the same failed optional source on every recomposition. Retry a failed optional attempt only for a new matching coverage opportunity/input, or actual demand.

All native requests for an unrequested back, including coarse parent/fallback URLs, must force `RadarTileLoader.load(cacheOnly = true)` regardless of native request hints. Missing or unusable **required planned-viewport** bytes abandon optional preparation silently. Native may also request optional coarse parents that byte prefetch never downloaded: return a cache miss for those without HTTP, do not invent an empty weather tile, and do not discard an otherwise covered back solely for an optional-parent miss. This distinction matters because the earlier native trace requested zoom-1 fallbacks as well as planned zoom-5 tiles. Verify that the pinned runtime still demands/decodes the cached planned tiles in this case; if it cannot, abandon that optional attempt and report the hit-rate limitation. Do not call ordinary network-capable `awaitCoverage` to warm an unrequested frame. Existing speculative HTTP work remains in the existing prefetch scheduler, with unchanged limits, deduplication, retries, and budgets.

Use an unambiguous per-native-registration alias/lease identity to carry cache-only versus actual-demand ownership and the required planned tile set; keep remote `RadarTileKey` identity unchanged for shared HTTP/cache deduplication. A native alias must not select an arbitrary same-frame lease or inherit another map/source's demand privilege. Adoption may switch that exact live registration to demand only after the latest real selection owns it. Obsolete registrations fail closed; a warmup with failed required tiles is removed before normal demand recreates the source rather than assuming native will retry a prior failure. Preserve no-store behavior and release leases only after native removal/map destruction.

**Alternative:** letting native warmup issue HTTP as demand consumes foreground capacity and unplanned parent tiles. Conversely, rejecting every source for a missing optional parent would defeat much of high-zoom preparation despite all planned tiles being cached. Keep cache-only fallback misses separate from required coverage failures, with no policy expansion or general telemetry.

### 5. Preserve acceptance-based playback and keep render callbacks nonessential

Keep `RADAR_PLAYBACK_INTERVAL_MILLIS = 600`, stable selected-time/type text, and the existing exact requested/provider filtering in `onDisplayChanged`. `displayed` still means accepted/submitted, not visually confirmed. Start the display interval on accepted promotion; optional preparation and hidden cleanup do not restart or postpone it. Do not add a second interval for preparation. Preparation failures are silent; actual requested-load failures keep current error/pause/retry semantics.

`MapEvent.FrameRendered(stats)` in pinned 0.16.0 reports that an engine frame finished, not which radar update it consumed. Its Full mode can belong to older map state. It can be logged for performance diagnostics but is not an eligibility, acknowledgment, or retirement gate. The older [`MapAdapter.Callbacks.onFrame(fps)` in 0.15.0](https://github.com/maplibre/maplibre-compose/blob/v0.15.0/lib/maplibre-compose/src/commonMain/kotlin/org/maplibre/compose/map/MapAdapter.kt) is internal frame-rate reporting; the inspected 0.16.0 adapter uses `onEvent`, not `onFrame`. Neither internal adapter access nor an upgrade is needed. Tests with no events, only Partial events, and delayed/out-of-order Full events must have the same controller timer behavior.

**Alternative:** changing acknowledgment to a rendered-swap proof is precisely the blocked former scope. Preserve the fast path rather than renaming timers or callbacks as proof.

### 6. Measure reduction, not universal absence, of flicker

Use narrow controller tests with virtual time to prove the two-slot cap, no double warm-up, no artificial initial delay, completion with zero render events, bounded hidden retirement, and latest-generation cancellation. Resource tests prove unrequested native warmup starts zero HTTP attempts, including fallback/cache-miss paths. Integration tests prove byte-prefetch completion starts optional native preparation without selection changes and without a polling loop.

On Google debug OpenGL instrumentation, use distinct nonempty red/green radar over a blue basemap at 75% opacity, checking center and tile-edge locations. Exercise normal/warm/next-frame reuse, valid empty/transparent/204 results, rapid scrubbing, lifecycle/style/provider/viewport/opacity changes, cache misses, and native delivery intentionally delayed beyond 50 ms. The latter demonstrates the accepted limitation; it must not be relabeled a continuity guarantee or used to block implementation pending a new native API. Still require functional convergence, error handling, two-slot/lease bounds, and no stale resurrection.

Compare the speedup baseline with the revision using the same device, provider identities, viewport, capture method, and controlled tile fixture. Run at least 20 alternating paired warm/adjacent transitions and five paired cold replacements per provider identity. Record accepted-submission time and first observed replacement pixels separately, observed basemap-only gaps and durations, brightness/ghosting, capture cadence/limitations, requested zooms, HTTP attempts, warmup reuse rate, and source/lease counts. Record median/p95 as descriptive statistics, not a guarantee from a small sample.

The intended outcome is fewer/shorter **observed** gaps on reproducible nonempty fixtures, no added full playback interval, no new HTTP work from native preparation, zero extra warm-up for already warmed frames, and no coarse/network policy regressions. Actual native latency can exceed the app timer. If there is no observed reduction, investigate the timing/preparation hit rate and ask before declaring this change effective or expanding to overlap/forks/compositors. Make a live visual check where radar signal is available; no-signal or unavailable device/capture checks are not passed. Available recordings/screenshot samples are permitted, but cannot establish zero missed refreshes or universally blink-free output. Preserve raw evidence and missing checks honestly.

**Alternative:** the former gate demanded complete physical-presentation proof before any implementation. The revised user-approved goal is measurable best-effort reduction, so that proof is no longer a prerequisite.

## Risks / Trade-offs

- **50 ms is shorter than actual native preparation** → Residual flicker is explicitly possible; prepare early, measure, and tune the bounded constant rather than claim readiness.
- **Cold manual selection gains up to 50 ms app delay** → Keep the front through that delay, overlap preparation with playback, skip warm-up for initial loading, and reuse elapsed warm-up for prepared frames.
- **Retaining hidden A does not cover missing B pixels** → Do not call hidden retirement a visual fallback; the main mitigation is warming B ahead of selection.
- **Two opacity setters can publish intermediate state** → Submit replacement first with no deliberate overlap, measure pulses, and keep the documented best-effort limitation; no atomic-render claim.
- **Cache-only warmup lacks native fallback bytes** → Never fetch or fabricate them for warmup; distinguish optional-parent misses from missing required tiles, verify native planned-tile demand, and fall back to ordinary demand if the optional attempt cannot proceed.
- **Delayed timer/cache completion mutates a newer slot** → Exact registration/slot/generation guards and deterministic cancellation tests.
- **Native memory/leasing grows during rapid playback** → At most two live pairs, no decoded-image cache, early hidden-retired removal for demand, and no third allocation after failed cleanup.
- **Sampling misses a millisecond gap** → Report observed results and capture limits, never zero-flicker proof. Missing required empirical verification remains not run.

## Migration Plan

1. Implement the timer-driven two-slot controller against a recording backend; no API-feasibility gate is needed. Verify virtual-time cancellation, ownership, and event independence.
2. Add cache-only coverage/freshness checks for registered planned tiles, with optional source creation/adoption still failing closed.
3. Implement per-registration cache-only/demand ownership and alias safety before enabling optional native source creation; preserve remote cache-key deduplication and existing budgets.
4. Complete the MapLibre backend's optional create/adopt path on that exact role/lease contract, then wire next-frame planning and scoped prefetch into the main map while keeping acceptance-based playback.
5. Run focused native tests and empirical comparisons, then relevant final regressions and `:app:assembleGoogleDebug`. Record results under the revised scope while retaining historical audits. No persistent-data migration is required; rollback must preserve the speedup's coarse tiles, transport capacity, provider budgets, and acceptance semantics. Do not discard unrelated work or sync/archive specs without a separate request.

The 50 ms defaults are proposed initial trial values, not measured results. Adjusting those internal constants after empirical comparison stays within this design; introducing deliberate visible overlap, a new rendering API, or a different architecture requires approval.
