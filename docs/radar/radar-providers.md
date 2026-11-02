# Radar providers

The main Map destination can display precipitation radar from LibreWXR or RainViewer. Radar requests go directly from the app to the selected provider; the app does not proxy them through a developer-operated service.

## Choosing a provider

The Radar provider control in Settings and the provider choice in map layers use one shared, saved preference. Existing installs and missing or invalid values default to LibreWXR. Changing the choice while radar is disabled saves the preference without starting radar requests. The app does not silently switch providers when one is unavailable.

## RainViewer coverage and request limits

RainViewer currently supplies past observed frames only; this app does not show RainViewer nowcasts or forecast frames. It requests 256-pixel XYZ tiles using Universal Blue (color 2), and does not request RainViewer tiles above native zoom 7. At higher map zooms, the map overzooms the available zoom-7 tiles.

The app uses RainViewer's public metadata endpoint, [`https://api.rainviewer.com/public/weather-maps.json`](https://api.rainviewer.com/public/weather-maps.json), then builds tile requests from the HTTPS host and frame paths in that metadata. The app's tile URL shape is `{host}{frame-path}/256/{z}/{x}/{y}/2/1_1.png`; coordinates and frame path vary with the viewport and selected frame. LibreWXR metadata is requested directly from [`https://api.librewxr.net/public/weather-maps.json`](https://api.librewxr.net/public/weather-maps.json), and its tiles also use the provider-returned host and frame paths. The app-wide scheduler limits RainViewer metadata, visible tile, retry, and speculative requests together to at most 90 request attempts in any rolling 60 seconds, below RainViewer's documented limit of 100 requests per IP per minute. It reserves up to 20 requests in that window for foreground work; speculative work also has bounded concurrency and yields to requested imagery. A 429 response is backed off, respecting `Retry-After` when usable. This app-side budget cannot account for other apps or users sharing the same public IP, and does not guarantee service availability.

## Loading and prefetching

When requesting another frame, the app distinguishes the requested time from the time still displayed and attempts to retain the last good frame while current-viewport tiles load into a second raster slot. It waits for tile coverage, staging installation, and a later map-wide full-render event before attempting a handoff. That MapLibre event is not an acknowledgment that this radar source's tiles were drawn. A controlled trace reproduced a temporary interval where the basemap showed through while new radar tiles were still blocked. This race is accepted and deferred; there is no universal blink-free handoff guarantee. Existing failure and timeout paths retain the old frame where implemented. The map remains interactive while radar loads.

While Map and radar are visible, the app opportunistically prefetches current-viewport tiles for up to two neighboring timeline entries before and after the requested frame. These are adjacent available frames, not fixed time intervals. Prefetch is best effort: requested imagery has priority, and the shared rate budget, bounded queue, and memory limit may prevent some or all speculative requests. Prefetch stops when the map/radar is hidden or the app is stopped; it is not an offline download feature.

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
