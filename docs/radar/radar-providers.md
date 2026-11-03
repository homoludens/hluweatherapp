# Radar providers

The main Map destination can display precipitation radar from LibreWXR or RainViewer. Radar requests go directly from the app to the selected provider; the app does not proxy them through a developer-operated service.

## Choosing a provider

The Radar provider control in Settings and the provider choice in map layers use one shared, saved preference. Existing installs and missing or invalid values default to LibreWXR. Changing the choice while radar is disabled saves the preference without starting radar requests. The app does not silently switch providers when one is unavailable.

## Tile resolution and request limits

Both providers supply supported 256-pixel radar images. The native raster source displays them with a 1024-point tile size; this is display scaling, not a request for 1024-pixel provider images. For each viewport, the app requests tiles two zoom levels below the normal source zoom, clamped to the provider's native minimum and two levels below its native maximum. The resulting coarse caps are zoom 10 for LibreWXR and zoom 5 for RainViewer (whose native maximum is zoom 7). At higher map zooms, the map overzooms the available coarse tiles. The same coarse policy is used for visible and prefetched imagery, and a stationary viewport does not later fetch a more detailed replacement.

RainViewer currently supplies past observed frames only; this app does not show RainViewer nowcasts or forecast frames. RainViewer requests use Universal Blue (color 2). The app uses RainViewer's public metadata endpoint, [`https://api.rainviewer.com/public/weather-maps.json`](https://api.rainviewer.com/public/weather-maps.json), then builds tile requests from the HTTPS host and frame paths in that metadata. The app's tile URL shape is `{host}{frame-path}/256/{z}/{x}/{y}/2/1_1.png`; coordinates and frame path vary with the viewport and selected frame. LibreWXR metadata is requested directly from [`https://api.librewxr.net/public/weather-maps.json`](https://api.librewxr.net/public/weather-maps.json), and its tiles also use the provider-returned host and frame paths. The shared scheduler permits up to eight simultaneous radar HTTP attempts in total across metadata, visible tiles, retries, and prefetch; at most four may be speculative. Foreground work takes priority, and the HTTP transport permits eight eligible requests to the same host. RainViewer metadata, visible tiles, retries, and speculation still share a limit of 90 attempts in any rolling 60 seconds, below its documented limit of 100 requests per IP per minute, with up to 20 attempts reserved for foreground work. A 429 response is backed off, respecting `Retry-After` when usable. This app-side budget cannot account for other apps or users sharing the same public IP, and does not guarantee service availability.

## Loading, preparation, and prefetching

When requesting another frame, the app attempts to retain the last good frame while the current viewport's complete coarse tile coverage loads and the staging source is installed. Once the current request's native promotion is accepted under its mutation guard, the app acknowledges the frame. This acceptance means the promotion was submitted, not that native tile delivery, decoding, or drawing has completed. The normal path does not wait for map-wide full-render events before or after promotion or during cleanup. If a replacement fails before accepted promotion, the last good frame is retained. Temporary basemap-only gaps, opacity discontinuities, or increased blinking are accepted trade-offs; there is no universal blink-free handoff guarantee. The map remains interactive while radar loads.

### Ahead-of-time native preparation

On the main Map during active playback, the app can prepare only the immediately next, non-wrapping timeline frame in the spare native radar slot while the accepted front frame is displayed. At most two native radar source/layer slots are live, counting a hidden predecessor awaiting removal. Preparation requires the exact next-frame coarse viewport tiles to be fresh in the app cache and a spare slot; an optional slot is staged at tiny-positive opacity so MapLibre can request its tiles. If the selected frame, coarse tile set, provider, style, or source lifetime still matches, selection adopts and reuses that installed slot instead of creating another one. Preparation does not change the requested or displayed frame, playback timing, or error state.

Every request made by an unrequested native source is cache-only, including requests for optional coarse parent/fallback tiles, regardless of MapLibre's request hint. This native preparation adds no HTTP requests: an absent or unusable required planned tile abandons the optional attempt, while an uncached optional parent is returned as a cache miss without fabricated weather data. Actual frame demand continues through the ordinary foreground loading path. The adjacent-frame byte prefetch described below remains separate and may use HTTP under the existing shared scheduler and provider budgets.

The app uses a **50 ms warm-up** after both successful complete required-tile coverage and staging installation for an unprepared replacement while a visible front exists. A matching prepared slot reuses its elapsed warm-up, so a fully warmed slot incurs no second delay. Initial loading without a visible front has no artificial warm-up. After accepted promotion, the hidden predecessor is retained for up to **50 ms** before cleanup, or removed earlier when new demand needs the slot. Both values are bounded app heuristics—not provider or native requirements, settings, or evidence that pixels are drawable. The normal playback display interval remains **600 ms**, measured from accepted submission; preparation and cleanup do not add an interval. No MapLibre render/readiness event is required for promotion, acknowledgment, or cleanup. Delivery or decoding slower than the warm-up can still produce temporary basemap-only gaps or opacity discontinuities; these timings do not guarantee flicker-free output.

While Map and radar are visible, the app opportunistically prefetches current-viewport tiles for the immediately previous and next available timeline entries—one neighbor on each side of the requested frame. These are adjacent indices in the provider's timeline, not fixed time intervals; at timeline endpoints, neighbors do not wrap around. This byte prefetch is best effort: requested imagery has priority, and the shared rate budget, bounded queue, concurrency limit, and memory limit may prevent some or all speculative requests. Prefetch stops when the map/radar is hidden or the app is stopped; it is not an offline download feature.

## Source links and credits

- RainViewer radar is credited with a link to [RainViewer](https://www.rainviewer.com/).
- LibreWXR radar is credited with a link to [LibreWXR](https://librewxr.net/). The app also displays applicable upstream credits: EUMETNET OPERA, NOAA Enterprise Rain Rate (RRQPE), and Radar-DPC (Italy; CC BY-SA 4.0 where included).
- Basemap attribution is separate from radar-source attribution.

## Local storage and network privacy

The selected provider and provider-scoped radar metadata are saved in local app storage. The metadata cache supports refresh and fallback; Android's app-data clearing or uninstall removes this local data. Compressed radar tile responses use a bounded cache of up to 16 MiB in memory only. Radar map sources are volatile and radar tiles are not persisted in the native disk cache. The basemap retains its separate disk-backed map cache.

When radar is used, the service receiving a request can see the device's IP address and ordinary request information. A radar tile request includes the frame and tile coordinates/zoom, identifying the map area requested; that area may correspond to the user's location if the map is centered there. RainViewer's metadata request itself uses the endpoint linked above. Each service's own logging, retention, and privacy practices are governed by its policies.

## RainViewer API terms and distribution status

RainViewer's current public API documentation limits use to personal and educational purposes and asks for linked source attribution. Eligibility for distributing this app or using the API outside that scope has not been established. No public-distribution permission is granted or implied by this guide or by the app integration. Confirm eligibility and obtain any necessary permission from RainViewer before distribution beyond the stated personal/educational scope.

Official RainViewer references reviewed **November 2, 2026**:

- [API and terms](https://www.rainviewer.com/api.html)
- [Weather Maps API](https://www.rainviewer.com/api/weather-maps-api.html)
- [API transition summary](https://www.rainviewer.com/api/transition-faq.html)
- [Color schemes](https://www.rainviewer.com/api/color-schemes.html)
