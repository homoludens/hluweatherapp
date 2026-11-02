## Why

Users need a choice of radar coverage without losing the existing LibreWXR integration. Moving the radar timeline currently tears down the visible raster source before the next frame loads, causing distracting blinking; buffered transitions and nearby-frame prefetching should make scrubbing and playback continuous.

## What Changes

- Add RainViewer as a second, explicitly selected radar provider alongside LibreWXR, using its public metadata and tile service directly.
- Add a shared, persisted default radar-provider selection in Settings and the map layer menu. Changing either control updates the same preference and the active map; existing installations continue to default to LibreWXR.
- Keep provider metadata, cached data, tile configuration, availability messages, and attribution separate. Never silently switch providers on failure.
- Respect current RainViewer capabilities: past observations only, Universal Blue tiles, native zoom up to 7 with map overzoom, rate-aware requests, and linked RainViewer attribution.
- Keep the last rendered frame visible while the requested frame loads, then perform a render-ready handoff or brief, opacity-preserving transition without an intentional blank interval. Handle rapid slider movement, playback, failed loads, and style reloads without promoting obsolete frames.
- Prefetch viewport radar tiles for up to two available timeline steps before and two after the requested frame for either provider, with bounded caching, deduplication, cancellation, and lower priority than the requested frame.

## Capabilities

### New Capabilities

None. Provider selection and rendering continuity extend the existing radar capability.

### Modified Capabilities

- `weather-radar`: Support two selectable providers and shared preferences, provider-specific capabilities and availability, blink-free frame transitions, and bounded two-step neighborhood prefetching.
- `map-information-presentation`: Show concise credits for the provider whose radar is displayed, including RainViewer's source link, while preserving existing basemap attribution and LibreWXR upstream credits when applicable.

## Impact

- Radar network models/adapters, tile URL construction, metadata repository/cache, application wiring, and provider-scoped cache compatibility.
- Settings state/repository/view model/screen and navigation wiring; radar-provider choice remains independent of the forecast weather-provider preference.
- Radar playback state, map raster rendering, viewport/load integration, layer controls, and attribution UI. Other maps, forecasts, routes, and widgets remain unchanged.
- Existing Ktor, DataStore, coroutines, and MapLibre Compose 0.16.0 should be reused; no backend, new API key, or broad map-library migration is proposed. Rendering readiness and tile-cache reuse must be demonstrated against the installed runtime rather than assumed.
- Focused API/cache/preferences/playback/rendering tests, followed by Google debug integration and device/emulator visual verification during implementation.
- RainViewer's current terms limit the public API to personal/educational use. Record eligibility and obtain clarification if intended distribution falls outside those terms before distribution; this plan does not assume a public-distribution license.
