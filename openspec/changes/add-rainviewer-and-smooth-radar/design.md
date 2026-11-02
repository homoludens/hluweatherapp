## Context

See `proposal.md` for motivation and `specs/` for the behavior contract.

- Radar currently has LibreWXR-named frame/metadata types, one `LibreWxrRadarRepository`, one DataStore metadata key, and hard-coded LibreWXR attribution. `RadarPlaybackViewModel` collects metadata only while visible and advances selection every 600 ms regardless of rendering readiness.
- `WeatherMapRadar.kt` recreates its single `RasterTileSource` when the tile template changes. MapLibre Compose 0.16.0 reconciles this by removing the old layer/source and adding the replacement. Its raster fade duration is zero; introducing a delay or increasing fade duration alone cannot preserve the removed frame.
- Preferences already flow through `PersistedSettings`, `SettingsRepository`, `SettingsViewModel`, `SettingsUiState`, and `HluNavHost`. Radar-provider choice is independent of the existing forecast provider.
- The installed MapLibre Compose 0.16.0 source exposes `MapRuntimeOptions.resourceProvider`, `MapResourceProvider`, `MapState.events`, `MapEvent.FrameRendered` with `RenderStats.Mode.Full`, viewport bounds, mutable source handles, and delicate native-map borrowing. It does not expose a per-raster-source readiness event or a per-response no-store flag. These hooks support a shared tile loader and a guarded render-readiness bridge without a dependency upgrade or private native API.
- Official RainViewer documentation checked on 2026-11-02 documents past-only frames, color 2 (Universal Blue), maximum native zoom 7, and 100 requests/IP/minute. It limits the public API to personal/educational use and asks for linked attribution. References: [API and terms](https://www.rainviewer.com/api.html), [metadata and tile API](https://www.rainviewer.com/api/weather-maps-api.html), [2026 transition limits](https://www.rainviewer.com/api/transition-faq.html), [colors](https://www.rainviewer.com/api/color-schemes.html).

## Goals / Non-Goals

**Goals:**

- Extend the existing radar pipeline with two small provider adapters and common domain types, rather than duplicate the entire map/playback stack.
- Separate user-requested time from actually displayed time, with explicit readiness, cancellation, and provider/style/viewport ownership.
- Make prefetching useful to the renderer: both demand and speculative requests must consume the same tile bytes/cache and request budget.
- Bound network, cache, and map-layer resources independently of timeline length and slider speed.

**Non-Goals:**

- Automatic provider fallback, simultaneous mixed-provider overlays, a separate temporary map-only provider preference, or changes to forecast providers.
- Adding RainViewer nowcast, satellite, coverage-mask UI, alternate color palettes, offline downloads, a backend, or API keys.
- Recreating the map/camera for each frame, upgrading MapLibre, intercepting unrelated map resources, or implementing a general-purpose map cache framework.
- Guaranteeing that a brand-new style or viewport has instant imagery, or that other devices sharing the same public IP cannot exhaust RainViewer's service-side allowance.

## Decisions

### 1. Provider-neutral domain model with explicit capabilities

Introduce a small `RadarProvider` enum (`LIBREWXR`, `RAINVIEWER`) and neutral frame/type/metadata models. Metadata carries its provider identity, validated host, chronological frame list, and timestamps; frame identity includes provider, host, path, timestamp, and type rather than timestamp alone. A provider descriptor owns display name, tile options/native maximum zoom, source link, credits, and metadata adapter. Keep this to two adapters and a simple lookup, not a plugin system.

RainViewer reads `https://api.rainviewer.com/public/weather-maps.json`, normalizes `radar.past` as observations, and does not require a `nowcast` property or promise future frames. LibreWXR retains its existing observed/forecast behavior and zoom 12. Both use provider-reported hosts/paths and validated HTTPS URLs; RainViewer uses 256-pixel XYZ tiles, color 2, and supported smoothing/snow options. Reject invalid hosts, credentials, traversal paths, malformed timestamps, and invalid frame entries consistently; normalize ordering and duplicate frame identities deterministically.

**Alternative:** Reuse LibreWXR-named models for both providers. Rejected because it obscures cache isolation, attribution, and differing provider capabilities. Do not build a broad generic provider abstraction for only two integrations.

### 2. Keep metadata refresh semantics and scope caches by provider

Parameterize the existing repository's metadata-fetch/cache logic with the selected adapter, preserving its five-minute freshness, request timeout, bounded transient retries, cached fallback, and cancellation when unobserved. Application wiring exposes the appropriate provider repository without collecting both. Cache envelopes identify provider and schema version. Preserve the existing `radar.librewxr_metadata_cache` data by reading its old schema as LibreWXR only, then writing the neutral schema to a new provider-scoped key after successful validation. RainViewer cannot read that legacy entry.

Switch collection using latest-selection cancellation. Reset provider-specific timeline, status, displayed imagery, and playback at a provider change, even when timestamps coincide. Ignore late metadata from an old provider. Selecting a provider while radar is hidden changes preferences only; it does not start collection or tile fetching. No service error triggers automatic substitution.

**Alternative:** One unqualified metadata cache or parallel background collection for both providers. Rejected due to data/credit mixing and unnecessary requests.

### 3. One saved preference with two synchronized controls

Add `radarProvider` with a LibreWXR default to persisted settings and UI state and thread a single setter through navigation to Settings and map layers. Use existing enum decoding and settings-write merging so missing/unknown/wrongly typed values fall back safely and unrelated edits survive load/save races. Existing radar visibility remains independent.

Settings gets a compact Radar provider section; the layer menu gets the same mutually exclusive choices, current selection semantics, and stable test tags. Both controls remain available when radar is disabled. Labels must distinguish radar imagery from the forecast weather provider; selecting a row saves the default immediately and affects the active map.

**Alternative:** Settings-only default plus an unsaved map override. Rejected because the request asks for choosing the default in both places and two states would make the controls surprising.

### 4. Shared tile loader through the public resource-provider hook

Create one radar tile loader with cache keys containing provider identity, validated remote URL/tile options, and canonical XYZ coordinate. A stable resource provider accepts only registered radar tile URLs; style, glyph, basemap, sprite, and unrelated resources continue through MapLibre unchanged. Metadata-supplied hosts are registered through validated provider descriptors, not a permissive URL proxy.

Attach this provider through a main-map runtime with its own bounded native cache file, created/disposed with map ownership, not with frame or provider selection. Do not reuse the default cache file with incompatible runtime options. Non-radar picker/route maps retain their current default runtime. All radar sources MUST be made volatile through their public mutable source handles before any layer can request them; reject nonvolatile radar callbacks before returning bytes. Use a new, disjoint volatile-only alias namespace and cache filename so old experimental native entries cannot bypass the bridge; do not delete or migrate existing cache files. The native ambient cache remains enabled for basemap and other unrelated resources, but radar tiles do not persist there across app restarts. The app-level compressed-byte LRU stores only cacheable radar responses, honors `no-store`, and is shared with prefetching so staging/visible requests can join in-flight prefetches or reuse valid bytes without a second HTTP fetch. No-store bytes are also advertised to native as expired and requiring revalidation; the public API cannot express a per-response no-store flag or guarantee immediate eviction of already rendered/in-memory tiles. Readiness cannot depend solely on a resource callback firing: the app cache and in-memory native tiles may satisfy a request. Only requested/native sources are registered; prefetch neighbors use validated tile keys without registrations. Each plan or native slot owns an independent, idempotently releasable registration lease; acquire a new owner's lease before releasing an old one, and release obsolete plans/slots only when they can no longer request tiles.

Initial resource limits are policy constants with tests: 16 MiB compressed-byte LRU, 32 MiB native ambient cache, four concurrent radar HTTP requests total with at most two speculative requests, finite tile-response size and request timeouts, and no full-timeline downloads. Do not cache failures as valid blank radar. Honor cache validity/response validators where provided. In-flight requests have consumer ownership: cancelling obsolete speculative demand must not cancel a matching request still needed for display.

RainViewer metadata, tiles, and retries share one conservative rolling budget of at most 90 network attempts in any 60 seconds, below the documented 100/IP/minute. Cache hits do not spend tokens. Speculative work leaves at least 20 tokens available for demand/metadata, and queued visible work always precedes queued speculation. On 429, honor `Retry-After` when valid, otherwise apply bounded exponential cooldown; do not retry around the limiter. This cannot control other clients behind the same IP, so preserve cache and surface rate-limit status. LibreWXR retains bounded concurrency/retries without assuming RainViewer's limit applies to it.

**Alternative:** Prefetch through unrelated Ktor requests while the native renderer downloads the same URLs independently. Rejected because cache reuse, deduplication, and a common rate budget would not be guaranteed. Hidden layers alone are also insufficient: zero-opacity layers may not request tiles.

### 5. Viewport-scoped, index-based prefetch policy

Use a pure planner to select sorted timeline neighbors `i−2`, `i−1`, `i+1`, `i+2`, clamped to existing indices with no wrap. Prefetch is centered on the requested frame, not the possibly older displayed one. The requested frame's tiles always have demand priority.

Compute canonical Web Mercator tile coverage from the map's actual visible bounds and camera zoom at the provider's source tile size, clamped to its native max zoom. Handle the antimeridian, world wrap, latitude limits, rotation, and viewport size conservatively; use the map's emitted radar tile coordinates to verify coverage calculations against the installed runtime. Deduplicate wrapped/canonical tiles. Replan only when the relevant tile set, selection, metadata snapshot, provider, or visibility changes; coalesce camera changes rather than restarting on every subpixel movement.

Bound the speculative queue (initially 64 unique tiles) and schedule nearest steps first (`i−1`, `i+1`, then distance two), with center-nearest tiles first if the viewport is large. This is an opportunistic queue, not a promise to bypass rate limits or download the world. Preserve matching current demand and cached tiles when replanning; cancel obsolete speculative consumers. At Map/radar/lifecycle stop, cancel all speculative consumers and stop queue producers. A late successful byte-cache fill never changes selection or display.

**Alternative:** Prefetch timestamps at fixed minute offsets, all tiles at all zooms, or all timeline frames. Rejected because frame intervals differ and bandwidth/memory would be unbounded.

### 6. Two raster slots and a guarded render-ready handoff

Keep two bounded radar source/layer slots under the existing label/marker anchor. The front slot holds the last good frame; only the back slot is replaced while loading a new request. The number of sources/layers is bounded at two, independent of the prefetch cache. Do not remove the front slot on a same-provider time selection. Own radar sources and layers imperatively, separately from Compose-owned layers: add an unreferenced raster source, set and verify `isVolatile` on its public mutable handle, then borrow the public native map only as needed to insert its layer after rechecking the expected source/template and current generation. Use generation-specific IDs and guard all asynchronous additions, updates, removals, and style reloads so stale work cannot attach to a replacement source. Explicitly manage layer order, paint, teardown, and reconciliation with Compose-owned layers; do not declare those radar resources in Compose style content. Other maps and Compose-owned layers remain unchanged.

Own a small transition controller with `displayed`, `requested`, `staging`, and load/error state. Every request has a monotonically increasing token incorporating provider, frame identity, viewport tile-set revision, visibility epoch, and style epoch. A completion is eligible only if all ownership fields still match. Manual selection pauses playback, invalidates the old staging token, and requests the latest slider value. At most one pending display request exists.

Readiness is a combination of validated/cached coverage for the current required tile set, staging-style installation, and a subsequent full native render for that same epoch. A `FrameRendered(Full)` or `Idle` event by itself is not per-source proof. Stage with a very small positive opacity (at most 0.001 and never above the chosen opacity) so native actually demands/decodes the layer while the front image remains visually unchanged. Do not rely on opacity zero to load tiles. Track failed tile requests explicitly and reject readiness if any required tile failed; successful transparent imagery or a genuine no-content response is valid and must not be mistaken for failure. Recheck the token and viewport before promotion.

Use the public style load state/events to establish that the staging revision is installed before accepting a subsequent full-render event. App-cache and native in-memory hits still require the post-installation render barrier. Demonstrate this bridge against the installed renderer, including tiny-opacity loading and stale/full events; tests must not simulate all downloaded bytes as proof that native has drawn them. If the supported hooks cannot establish the barrier, stop and revise the adapter/design rather than silently using a fixed delay or map-wide idle heuristic.

Promote by making the ready back slot the front at the chosen opacity and hiding the old front in the same style revision; retain the old slot until the promoted revision has rendered before recycling it. Default to this atomic render-ready handoff, without an extra crossfade. It avoids a blank interval and avoids alpha stacking/brightness flashes from overlapping two 75%-opacity rasters. The user's animation suggestion is optional; do not add a fade that weakens continuity or accessibility. When opacity is zero, no visible-image readiness is needed, but returning to positive opacity must re-establish normal staging readiness.

On request timeout or error, keep the front slot and displayed-time label and expose pending/error state; explicit reselection or resumed playback can retry under the same bounded policies. A camera change invalidates old-viewport readiness but does not clear the front layer. A style reload invalidates renderer handles/tokens and reattaches the current request once; a new basemap/style's initial loading is not a same-style handoff. A provider switch deliberately clears old imagery immediately rather than displaying it under the new provider's controls/credits. Radar disable/Map stop removes both slots and cancels work; late events cannot resurrect either.

**Alternative:** Replacing the single source, adding a fixed delay, or fading out before fetching. Rejected because each creates a clear-map interval under slow loading. An unconditional crossfade is rejected because ordinary translucent raster alpha stacking can visibly change intensity.

### 7. Honest timeline state, readiness-aware playback, and attribution

Keep `requestedFrame` separate from `displayedFrame`. The slider and target time follow the request immediately; while loading, show a compact "Loading <requested time> · Showing <displayed time>" equivalent with observed/forecast information. Error text must not relabel the retained imagery. Rendering acknowledgments update displayed time only for the current token.

When frames are ready, retain the existing approximately 600 ms playback interval and loop. If the next requested frame is still pending, wait for its display acknowledgment instead of repeatedly superseding it; on failure pause playback and retain the last good display. Leaving Map, disabling radar, or changing provider pauses playback. Provider changes start with the new provider's latest observation rather than transferring timestamps between providers.

Render provider-specific attribution from the descriptor, tied to displayed imagery. While an initial/new-provider request has no displayed image, show the selected provider's source/loading status. LibreWXR keeps its existing upstream credits, and RainViewer has a clickable, accessibility-labelled `https://www.rainviewer.com/` source link. Preserve basemap credit expansion/collapse and avoid reintroducing the removed upstream disclaimer.

**Alternative:** A single selected timestamp driving both UI and rendering. Rejected because it labels retained old imagery as a future request and cannot prevent cold-frame playback starvation.

## Risks / Trade-offs

- [Public API terms may not cover intended distribution] → Record the terms and intended use; obtain owner/provider clarification before distribution outside personal/educational use. Do not treat open-source licensing as API permission or silently substitute another service. This is a distribution gate, not authorization for release work.
- [RainViewer rate limits can delay neighborhood loads or be exhausted by shared-IP users] → Conservative shared budget, foreground reserve, cache reuse, cancellation, `Retry-After`, and nonblocking status. Two-step prefetch remains best effort.
- [Map-wide full-render events are weaker than per-source readiness] → Combine current coverage success, staging installation, and post-installation full-render evidence. Native integration checks are mandatory; a timeout is a failure path, not a readiness signal.
- [Very-low-opacity staging may be optimized away or still introduce a small visual difference] → Verify tile demand and screenshots on the actual OpenGL runtime; cap staging opacity and use a render-ready swap instead of alpha-stacking animation. Do not declare flicker fixed on Robolectric evidence alone.
- [Dual layers and cached tiles increase memory] → Two slots, byte-bounded LRU/native cache, bounded queue/concurrency, no retained whole-timeline sources, and explicit lifecycle teardown.
- [Request churn or style reload can promote stale imagery] → Tokenized provider/frame/viewport/style ownership, latest-only staging, and deterministic out-of-order/cancellation tests.
- [At low zoom or during rapid map movement a viewport may exceed prefetch limits] → Current-frame demand wins; speculative coverage is capped/deferred rather than making the map unresponsive.

## Migration Plan

1. Add neutral domain types, descriptors, and tested adapters while retaining LibreWXR behavior and legacy-cache reads.
2. Add provider-scoped repository/cache wiring and the new preference with a LibreWXR fallback; no existing setting is removed.
3. Add the shared tile loader/resource adapter and pure neighborhood/viewport planner with deterministic clock/network tests.
4. Add the buffered renderer/readiness controller and integrate requested/displayed state and readiness-aware playback. Prove native load/render/cache behavior before claiming continuity.
5. Wire both provider selectors, attribution, loading/error UI, and lifecycle teardown. Run focused tests as each area changes.
6. Finish with Google debug regression/build verification and device/emulator visual checks for both providers, including cold-cache slow loading, rapid scrubbing, unavailable tiles, high-zoom overzoom, style reload, provider switch, and navigation/background cancellation. Record unavailable verification as not run, never passed.

Rollback is a normal application-code rollback with LibreWXR still the default; the new provider-specific preference/cache keys can remain inert. Keep legacy LibreWXR cache data during this change so reverting does not require a destructive migration. No backend, database migration, or release signing is involved.

## Verification and Task Routing

Each implementation task includes its associated focused verification. Use the current `AGENTS.md` routing: `[low]` → `openai/gpt-6-luna#max`; `[high]` → `openai/gpt-6.1-terra#max`. Three of fifteen tasks (20%) are high complexity: the shared priority/rate/cancellation tile scheduler, the renderer-readiness state machine, and provider/playback coordination with asynchronous display acknowledgments. Ordinary adapters, preferences, UI, documentation, and prescribed broader integration checks remain low. Tags do not authorize implementation or delegation.

Run targeted tests with `./gradlew :app:testGoogleDebugUnitTest --tests '<test-class>'` using `ANDROID_HOME` and `ANDROID_SDK_ROOT` from `AGENTS.md`. Extend the existing API/cache/repository/settings/playback/overlay/style-reconciler harnesses and add focused scheduler/planner/transition tests. Replace the existing assertion that a frame change removes the only radar source with assertions preserving the front until promotion. Renderer-specific visual and cache-reuse evidence requires the real installed runtime, not only the recording style engine. Final build is `./gradlew :app:assembleGoogleDebug`; do not build other distribution/release variants.
