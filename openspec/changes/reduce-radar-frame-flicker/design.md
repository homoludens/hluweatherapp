## Context

See `proposal.md` for motivation and `specs/weather-radar/spec.md` for the behavior contract. Planning starts from clean revision `73fcfe8879e960aa02ae5f07baecc5f58b01bfcb`, which includes the radar speedup. The user reports brief disappearance, not a brightness pulse; no new device trace has been captured during planning.

The current implementation already owns two native raster slots. `RadarRenderController` waits for app-side byte coverage and installation, calls `MapLibreRadarBackend.promote`, immediately records the replacement as displayed, and removes the predecessor. The backend changes both opacities in one owner-thread block, but that is not proof that the renderer has consumed drawable replacement tiles. `RadarMapResourceProvider` observes failures, not decode/upload completion. Existing `awaitFullRender` comments and archived native evidence explicitly document that map-wide Full events can refer to an older update. Re-enabling the diagnostic flag is therefore not a fix.

Adjacent prefetch currently warms compressed bytes for i−1 and i+1; it does not prepare a native slot during the current frame's approximately 600 ms display interval. All native resource loads currently call the loader as demand. An unrequested native layer must not silently convert speculation into foreground HTTP work.

The implemented predecessor `speed-up-radar-loading` is still an active, completed change and its delta is not synced to the main radar spec. This design builds on its coarse-only policy and request limits. This follow-up replaces its acceptance-only playback/handoff contract; it does not restore the older detailed tile policy or full-map barriers. Spec finalization order is predecessor first, this change second, only when separately requested.

## Goals / Non-Goals

**Goals:**
- Eliminate premature removal of the old frame during same-provider, same-style, unchanged-tile-set handoffs, with actual native evidence rather than additional timing guesses.
- Reuse the existing two-slot architecture and overlap next-frame native preparation with the existing playback interval.
- Make readiness, promotion, acknowledgment, rollback, and resource ownership explicit across request and render-thread races.
- Preserve coarse source settings, transport/scheduler capacity, cache/privacy policies, provider budgets, and interactive map behavior.
- Prove the result at individual presented-frame granularity; slower polling screenshots cannot establish absence of a millisecond-scale gap.

**Non-Goals:**
- Crossfades, opacity animations, fixed grace periods, configurable quality/speed modes, progressive detail, larger byte caches, or extra speculative timeline neighbors.
- A custom radar compositor, a maintained MapLibre fork, private/reflection-based native access, or an automatic dependency upgrade. If needed, these require a new approval before expanding the change.
- Uninterrupted imagery during provider/style replacement, initial map loading, or newly exposed viewport regions. Normal empty weather data is not flicker.
- New persisted state, provider/API changes, attribution changes, unrelated maps, release signing, or release builds.

## Decisions

### 1. Make native capability proof a blocking gate, not an assumed API

Before production integration, audit the pinned MapLibre Compose 0.16.0 public platform APIs and the renderer ordering they expose. Establish the smallest usable contract for both:

1. **Drawable readiness:** the installed slot has successfully prepared its required current-viewport native tiles, including valid empty tiles, in the render update that will be used for promotion.
2. **Rendered swap acknowledgment:** a completion tied to the particular opacity/source update establishes that the renderer has consumed that swap, allowing predecessor retirement and playback acknowledgment.

The proof may use an appropriately ordered source-specific native query plus a correlated render completion; it need not require a newly invented event type. It must demonstrate why stale renderer snapshots, callbacks from a removed/recreated source, surface recreation, and unrelated Full events cannot satisfy it. Successful HTTP callbacks prove delivery only, not decoding or draw readiness. A native cache hit must satisfy the same contract without requiring a new provider callback.

Use a controlled OpenGL probe with a blue basemap and distinct opaque radar colors at the selected layer opacity. Independently hold native delivery while app bytes are cached, exercise any controllable preparation/render-update boundary, inject/replay stale observations, and keep unrelated basemap work pending. Verify that a ready certificate cannot appear early and that both opacity changes are consumed in one render update without an intermediate empty or double-opacity frame. Also check that tiny-positive staging actually receives native preparation on the installed runtime. Document API symbols, ordering argument, exact version/device, probe commands, and raw results in implementation-time `verification.md`.

**Go/no-go:** downstream tasks require a demonstrated contract and passing probe, not simply a source-loaded boolean or a plausible event sequence. If the pinned public API cannot express the contract, or a device/capture facility needed to prove it is unavailable, leave the production renderer unchanged, record the gate as blocked/not run, and ask for the smallest explicit scope or verification decision. Do not auto-upgrade dependencies, create a fork/compositor, weaken readiness to a timeout, or mark the change implemented. This is a deliberate implementation gate; the plan does not claim current API feasibility has already been established.

**Alternatives:** waiting for one/two Full events, sleeping for several milliseconds, or waiting only for native resource delivery are rejected because none excludes the documented race. Broad rendering replacement is deferred rather than implicitly approved.

### 2. Bind readiness and swap evidence to explicit ownership

Extend the radar backend boundary with the proven operations from decision 1. Keep the smallest internal readiness/swap records needed to bind evidence to:

- provider/host/frame identity;
- canonical coarse viewport tile set;
- map/surface/style lifetime and unique native slot/source lifetime;
- current selection generation, preparation generation, and native render update/swap revision as applicable.

Do not equate the existing app-side `styleEpoch` or a render event counter with a native update revision. Those are useful invalidation inputs, not evidence that a submitted raster update was consumed. Recheck identity, viewport, native failures, source existence/volatility, selected opacity, and selection generation on the native owner thread immediately before promotion. Consume only an acknowledgment for that same swap. Opacity-only changes must not accidentally validate previously invisible imagery.

Keep observation lifecycle under the backend: register before native demand, cancel on invalidation, and release source leases only after native removal or confirmed map destruction. Late callbacks are inert. Treat valid transparent PNGs and successful no-content tiles as terminal coverage, not as missing signal.

**Alternative:** a global ready flag or URL-keyed delivery counter is smaller but cannot distinguish reused frame resources, cache hits, stale viewport coverage, or source replacement.

### 3. Retain two slots and make the swap/retirement state machine explicit

The existing controller remains the sole owner of native radar slots and latest-selection arbitration. Use two physical slots at most, regardless of role:

```text
front A + spare
   -> front A + preparing B
   -> front A + drawable B
   -> guarded hard swap A -> B, retain A's resources
   -> correlated rendered-swap acknowledgment
   -> front B + spare (A removed)
```

Preparing B can represent either an actual request or optional next-playback preparation. Optional preparation never changes `requested`, `displayed`, `pending`, or current errors. A compatible prepared B can be adopted by the real request without rebuilding it; otherwise cancel/remove it before allocating the replacement. Do not recreate and throw away a matching prepared slot at the start of every reconciliation, as the current unconditional staging cleanup would do.

The front stays at the user's selected opacity until a current readiness certificate allows the guarded swap. Keep raster fade and opacity transitions disabled. Set replacement to the current opacity and predecessor to zero in the proven single native render update; do not intentionally stack two visible 75%-opacity frames. Record an in-progress swap and retain predecessor ownership until acknowledgment. Publish `displayed`/clear pending and recycle the predecessor only on that acknowledgment, not in the native mutation guard merely because setters returned.

Handle cancellation after native mutation but before coroutine resumption without losing ownership. If a replacement fails before acknowledgment, preserve or restore the previous same-provider front using the latest valid generation, then retire failed resources safely. A new request arriving during an unacknowledged swap must resolve or safely supersede that swap before reusing either slot. A failed removal retains the lease and prevents allocation of a third slot; it does not erase the remaining front. Never hold the short cross-thread ownership lock while suspending or waiting for readiness.

Hide/stop/provider/style invalidations still fence queued native work immediately. Opacity zero hides imagery without accepting an invisible render as readiness; restoring positive opacity re-establishes the visible handoff contract. A tile-set change invalidates readiness but does not intentionally clear a compatible front's existing coverage. Retain the existing bounded 45-second requested-handoff timeout unless the feasibility proof reveals a specific need to revisit it; optional preparation does not extend that deadline or surface its own timeout as a display error.

**Alternatives:** holding the predecessor visible underneath an unready replacement causes stale precipitation/brightness artifacts; a third slot increases lifetime complexity and memory. Neither is necessary for the chosen serial two-slot protocol.

### 4. Prepare only the next playback frame, using valid cached bytes

Expose playback activity and the next chronological non-wrapping frame identity as renderer inputs, derived from the same ordered metadata as the playback loop. After the current handoff is acknowledged and its old slot removed, try preparing i+1 while i's existing display interval runs. Existing byte prefetch remains i−1/i+1 and uses the unchanged shared scheduler. No native preparation when paused, hidden, stopped, at opacity zero, or when there is no valid i+1. Playback still loops to index 0 at the boundary, but that wrap request uses the normal safe foreground path instead of extending the speculative neighborhood.

Add the minimum per-live-source/lease context in the radar resource bridge to distinguish **unrequested preparation** from **requested demand**. For unrequested preparation, force `RadarTileLoader.load(..., cacheOnly = true)` regardless of a native cache/network loading hint; never call ordinary foreground coverage loading to warm that frame. Native coarse parent/fallback requests are subject to this rule too. If any required native resource is missing, stale, non-cacheable, or unusable, discard that optional slot without a user-facing error; let existing byte prefetch progress, and retry only after a relevant cache/input change or the eventual actual request, not a tight polling/recreation loop.

This avoids introducing a new speculative network scheduler or weakening rate accounting. A matching successful prepared slot may become requested demand under the controller's guarded ownership transition. A partially failed preparation is removed and recreated as ordinary requested work; do not assume a previously failed native source will retry by itself. Keep remote-key HTTP deduplication and source registration leases intact. Because registry aliases currently derive from frame identity and can be shared, preparation policy must not be stored as an ambiguous global flag on a remote URL; role ownership must be explicit and conflicting live roles resolved without letting warmup bypass cache-only access.

Pause/manual selection, metadata identity changes, tile-set changes, provider/style/surface changes, and lifecycle stop invalidate the preparation generation. Foreground staging always owns the spare slot first. Optional work must not hold a controller mutex across a wait that prevents a real request from cancelling/preempting it.

**Alternatives:** larger byte prefetch does not prepare GPU resources; letting native warmup issue demand HTTP can consume the foreground reserve and download unplanned fallback tiles. Cache-only preparation is smaller and preserves existing network policies, though its hit rate is deliberately best effort.

### 5. Keep the playback interval, but start it from completed handoff

Keep `RADAR_PLAYBACK_INTERVAL_MILLIS = 600` and requested/displayed separation. `onDisplayChanged` continues to accept only the current provider/request. Its displayed acknowledgment now means the native-ready swap was rendered according to the proven contract, not screenshot-based runtime color detection. Start the next display interval only then; preparing a neighbor is not a new selection and cannot restart that interval.

Thread playback/preparation inputs through the main map only. The timeline continues showing selected time/type with no loading flash or duplicate displayed row. Manual scrubbing pauses playback and converges to the newest selection. Startup has no retained front and can use normal loading until the same readiness/acknowledgment contract is satisfied. Preparation failure is silent; failure of the actual requested frame retains existing error/pause behavior.

**Alternative:** continuing to advance from command acceptance can supersede imagery before it is drawn; adding a second 600 ms preparation interval would undo the perceived speedup.

### 6. Verify continuity and visible speed, not just callbacks

Run narrow tests while implementing each unit; reserve radar-wide regression and Google debug assembly for final integration. Existing acceptance-before-native-delivery tests intentionally describe the old contract and must be replaced for the new default, while preserving their coverage of coarse requests and lifecycle ownership. Keep historical verification evidence intact.

The native fixture must capture or inspect every presented frame in the handoff window at the actual refresh rate, or use an equivalent proven native render-output assertion. Repeated `takeScreenshot` polling or a recording that drops frames is insufficient to exclude the reported gap. Record capture cadence and missing frames; if completeness cannot be established, continuity is not passed. Check a grid covering the viewport/tile edges, not just a center pixel. For nonempty red/green radar over blue at 75% opacity, every sampled handoff output must match one expected single-layer composite within a documented tolerance, never blue-only or the two-layer composite. Tiny staging tolerance must not hide a full-opacity overlap. Run at least 100 controlled transitions per provider identity, including delayed native delivery, warm cache, and prepared reuse; add separate empty/transparent and lifecycle/race cases.

Use the current speedup revision above as the baseline, not the pre-speedup implementation. On the same device/viewport/provider/frame fixture, alternate at least 30 paired warm/adjacent trials and five paired cold trials. Record selection-to-first-visible-replacement, readiness, rendered-swap acknowledgment, gap count/duration, HTTP attempts/bytes, requested zooms, source/lease counts, and bounded native memory behavior separately. For warm/prepared adjacent transitions, median and p95 first-visible-replacement latency must not exceed the baseline by more than one measured display refresh interval; there must be no added fixed delay or extra display interval. Cold paths can wait for genuine preparation, but must retain old imagery and unchanged tile/network policies. If the warm target fails, investigate and resolve or obtain an explicit user-approved trade-off rather than claiming speed preservation.

Repeat a live visual check for both providers with actual radar signal when available; if a provider has no signal, report that run unavailable instead of treating a blank map as a continuity pass. Synthetic tests establish renderer behavior, not live-provider speed. Record exact commands, revisions/APK hashes, device/GPU/capture details, result counts, and unavailable checks in `verification.md`. Do not fabricate results or mark required verification passed when it was not run.

**Alternative:** controller acknowledgment timings and low-frequency screenshots are easier but miss the very defect being fixed.

## Risks / Trade-offs

- **Pinned runtime cannot expose sufficient readiness or swap ordering** → Task 1.1 blocks integration; request approval for the minimum dependency/native change rather than silently widening scope.
- **A native callback precedes render consumption or a cached tile bypasses observations** → Prove ordering with controlled gates and stale evidence; use source/lifetime/revision correlation rather than delivery counts.
- **Warmup cache misses include native parent tiles not planned by byte prefetch** → Abandon optional preparation without new HTTP or an error; requested loading remains authoritative. Measure hit rate before considering any separately approved policy expansion.
- **Temporary extra native memory** → Strictly two live slots, no new decoded-image cache, cleanup tests across long loops, and no new preparation while retirement is pending.
- **A delayed swap acknowledgment holds both slots** → Bounded timeout and generation-safe restore/cleanup; new demand can invalidate optional work and no third source is created.
- **Transparent images hide a false success/failure** → Explicit no-content/transparent terminal semantics and separate nonempty synthetic continuity fixtures.
- **Sampling misses a one-refresh gap** → Require complete presented-frame evidence or equivalent native output assertions; label incomplete capture not passed.
- **Stronger readiness adds latency** → Move preparation into the existing display interval and enforce the warm/adjacent comparison target above. Do not promise identical cold latency or universal continuity beyond the defined scope.
- **Spec finalization order restores old requirements** → Sync/archive the predecessor before this change only when requested; review the composed radar contract and retain its coarse/budget requirements.

## Migration Plan

1. Complete the native capability/capture gate and record the demonstrated contract. If blocked, stop here with no production behavior change.
2. Implement the minimal readiness adapter and generation-safe two-slot protocol behind the radar backend boundary; verify focused tests before wiring it into the main map.
3. Add cache-only next-frame preparation and align playback acknowledgment, preserving the current network/source settings and controls.
4. Run controlled native continuity and speed comparisons, live visual checks when available, then final radar regressions and `:app:assembleGoogleDebug`. Update documentation/evidence without replacing historical failed or unavailable results.
5. No preference, cache schema, provider URL, or persistent-data migration is required. If rollback is needed, revert only this change's readiness/preparation/playback integration to the existing acceptance-only renderer while retaining the speedup's coarse tiles and eight/four request limits; explicitly restore the documented flicker limitation. Do not discard unrelated work.

The exact existing API mechanism and capture method are deliverables of the blocking first task, not established facts or silently deferred product decisions. Any result requiring a different rendering architecture or dependency ownership returns for approval before remaining tasks execute.
