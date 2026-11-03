## Why

The user confirms that the radar loading speedup is significant, but radar briefly disappears between frames. The current handoff can hide the old frame after byte coverage and command acceptance, before MapLibre has prepared drawable replacement tiles; this follow-up targets that gap without undoing the speedup.

## What Changes

- Establish and verify a source-, viewport-, and render-revision-specific native readiness contract before changing production handoff behavior. Cached bytes, command acceptance, fixed delays, and uncorrelated full-map events are not sufficient.
- Retain the last good same-provider frame until its replacement is drawable, apply one guarded hard swap without a crossfade, and release predecessor resources only after a correlated acknowledgment of the swap.
- Prepare at most one next playback frame in the spare native slot during the current frame's display interval. Reuse the existing adjacent-frame byte prefetch; native preparation of unrequested frames is cache-only and cannot introduce extra foreground HTTP work.
- Make playback readiness reflect the correlated completed handoff rather than mere submission. Preserve stable timeline text, latest-selection-wins behavior, opacity controls, and lifecycle cleanup.
- Preserve the successful coarse-only tile policy, eight-total/four-speculative request capacity, provider budgets, memory-only radar caching, and existing attribution.
- Verify continuity at presented-frame granularity with delayed native delivery and compare visible latency against the current speedup build. If no suitable native hook exists, stop for approval rather than silently substituting a delay, broad dependency migration, fork, or custom compositor.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `weather-radar`: Replace acceptance-only frame handoff with native-ready, revision-correlated swaps, align playback acknowledgment with that contract, and add bounded native preparation of the next playback frame alongside existing adjacent byte prefetch.

## Impact

- Main-map radar controller/backend and renderer wiring: `RadarRenderController.kt`, `MapLibreRadarBackend.kt`, `WeatherRadarRenderer.kt`, playback state/view-model, and their main-map callers.
- Radar resource bridge and minimal slot/lease context needed to distinguish cache-only native preparation from requested-frame demand; existing loader, prefetch, transport, cache, and budget policies remain authoritative.
- Focused controller/playback/resource tests, OpenGL instrumentation tests, radar documentation, and implementation-time verification evidence. No release build, persistent-data migration, new setting, or unrelated map change.
- Depends on the implemented `speed-up-radar-loading` change. Its delta is not yet synced to the main spec: sync/archive that predecessor before this follow-up during a separately requested finalization. This change supersedes its acceptance-only handoff/playback behavior, not its loading optimizations.
- MapLibre Compose 0.16.0 currently has no established per-source/per-revision acknowledgment. Hook feasibility is a blocking first implementation task; dependency replacement or native-library maintenance requires a separate approval if necessary.
