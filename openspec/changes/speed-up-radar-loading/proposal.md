## Why

LibreWXR radar feels slow because a cold frame may require many viewport tiles and the app adds map-wide render barriers before and after the frame handoff. Try a speed-first configuration that trades resolution and strict render acknowledgment for less loading work, without claiming a measured improvement before device testing.

## What Changes

- Increase shared radar HTTP concurrency to eight requests, with at most four speculative requests included in that total; align the HTTP client's per-host limit with the scheduler.
- Render and prefetch tiles two zoom levels coarser for both providers, clamped to supported limits. Keep supported 256-pixel provider images and do not subsequently upgrade the same viewport to detailed tiles.
- Remove full-map render-event waits from the normal handoff path. Retain coverage, staging installation, native mutation guards, foreground priority, cancellation, and provider/style/visibility invalidation.
- Define speed-first frame acknowledgment as acceptance of the guarded native promotion, not proof that pixels have been drawn. Accept possible temporary gaps or increased blinking during this experiment.
- Preserve one-frame-before/one-frame-after prefetch, the existing memory-only radar cache, and RainViewer's current request budget and backoff.
- Update focused tests and provider documentation, and record any device/performance checks that cannot run as unverified.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `weather-radar`: Use a fixed coarse-only tile policy, eight/four request concurrency, and a coverage/install-gated handoff without full-render-event waits; align playback acknowledgment and prefetch with those behaviors.

## Impact

- Radar scheduler and HTTP transport, tile coverage planning, native raster-source configuration, buffered handoff controller, and playback integration.
- Radar unit/integration/native-runtime tests and `docs/radar/radar-providers.md`.
- No new provider, backend, dependency, public preference, or release build. Basemap resolution, unrelated maps, forecast features, and earlier UI/version/changelog work remain unchanged.
- Five source files already contain partial, unverified experimental edits. Implementation must inspect and finish those edits rather than assume they or their tests are complete; all tasks remain unchecked.
- RainViewer distribution eligibility remains unresolved and is not changed by this performance experiment.
