## Why

The radar loading speedup is significant, but radar briefly disappears between frames. The user now requests a practical double-buffered plan that reduces flickering without depending on a MapLibre readiness signal, replacing this change's earlier blocked readiness-gated approach.

## What Changes

- Use two native rendering slots: keep the current frame visible while preparing the next playback frame in the spare slot during the existing approximately 600 ms display interval. Reuse a matching prepared slot instead of recreating it when selected.
- For an unprepared replacement, retain the front while obtaining full coarse byte coverage and installing staging, then allow a short app-timed warm-up before submitting the hard swap. An already warmed frame pays no additional warm-up; initial loading has no artificial delay.
- Hide the predecessor only when submitting the replacement, retain its hidden resources briefly for cleanup/rollback, and never intentionally crossfade or stack two frames at the selected opacity. This is a best-effort improvement, not a guarantee that native drawing is complete or that every transition is gap-free.
- Reuse adjacent-frame byte prefetch; unrequested native preparation is cache-only, uses at most the spare slot, and cannot add HTTP requests or displace foreground demand.
- Keep command-acceptance acknowledgment and the 600 ms playback interval. Do not require `FrameRendered`, Full/Idle, a native completion fence, or internal callbacks to advance playback or clean up slots.
- Preserve the successful coarse-only tile policy, eight-total/four-speculative request capacity, provider budgets, memory-only radar caching, and existing attribution.
- Compare observed gaps and visible latency against the speedup build using controlled tests and available on-device recordings. Report capture limitations and residual flicker honestly; no dependency upgrade, fork, custom compositor, or perfect-presentation proof is required to start implementation.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `weather-radar`: Add ahead-of-selection native preparation and bounded app-timed double-buffered handoff while preserving acceptance-based playback and documenting best-effort continuity.

## Impact

- Main-map radar controller/backend and renderer wiring: `RadarRenderController.kt`, `MapLibreRadarBackend.kt`, `WeatherRadarRenderer.kt`, playback state/view-model, and their main-map callers.
- Radar resource bridge and slot/lease context distinguish cache-only native preparation from requested demand; scoped byte-prefetch progress offers preparation opportunities without polling. Existing loader, transport, cache, and budget policies remain authoritative.
- Focused controller/playback/resource tests, OpenGL instrumentation tests, radar documentation, and implementation-time verification evidence. No release build, persistent-data migration, new setting, or unrelated map change.
- Depends on the implemented `speed-up-radar-loading` change. Its delta is not yet synced to the main spec: sync/archive that predecessor before this follow-up during separately requested finalization. Preserve its loading optimizations and acceptance semantics while adding ahead-of-time preparation and bounded warm-up.
- Keep MapLibre Compose 0.16.0. Preserve the previous API/capture audits in `verification.md` as historical evidence; their inability to prove native readiness is no longer an implementation gate for the revised scope. All implementation tasks remain unchecked.
