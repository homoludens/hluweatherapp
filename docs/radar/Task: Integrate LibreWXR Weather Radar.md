# Task: Integrate LibreWXR Weather Radar into HluWeatherApp Map Tab

## Objective

Upgrade the existing **Map tab** in HluWeatherApp to display animated precipitation radar using the LibreWXR public API.

The app already uses **Kotlin, Jetpack Compose, MapLibre, and OpenFreeMap**.

**Important constraints:**
- Do NOT create or host a backend server.
- Use the public LibreWXR API directly from Android.
- Reuse the existing MapLibre map and map infrastructure.
- Remove the large weather information card currently displayed above the map.
- Preserve existing map functionality, especially current location and "Weather on route".
- Follow the app's existing Material 3 design, typography, colors, and light/dark themes.
- Do not redesign the Hourly or Daily tabs.

## 1. Remove the weather card

The current Map tab contains:
- A large weather card showing location, temperature, conditions, humidity, dew point, feels-like temperature, and precipitation.
- A MapLibre map below it.
- A "Weather on route" button.

Remove the weather card **only from the Map tab**.

The MapLibre map should expand to occupy all available space below the existing app header and navigation tabs, down to the bottom system navigation area.

Keep the existing header, navigation tabs, location functionality, and "Weather on route" button.

Do not remove the weather card or weather information from other screens.

## 2. Integrate LibreWXR radar

Use the public API:

`https://api.librewxr.net/public/weather-maps.json`

Fetch available radar frames, including historical observations and forecast/nowcast frames where supported.

The API is intended to be compatible with RainViewer's weather-map API.

Implementation requirements:

1. Use the existing networking stack (Retrofit, OkHttp, or whatever the project currently uses).
2. Parse radar metadata dynamically.
3. Read the tile host and frame paths from the API response.
4. Add transparent radar raster tiles to the existing MapLibre map.
5. Use the provider's documented tile URL format and supported options.
6. Support historical and future radar frames when available.
7. Display the latest observed frame by default.
8. Cache metadata and radar tiles using the existing infrastructure where possible.
9. Handle missing radar coverage, failed requests, and unavailable frames gracefully.
10. Display the radar data's actual timestamp.

Do not hardcode radar frame timestamps or assume that future frames are always available.

Do not scrape the LibreWXR website or use undocumented internal website endpoints.

### MapLibre implementation

Use MapLibre raster sources and raster layers.

- Preserve the existing OpenFreeMap basemap.
- Place the radar layer above the basemap but below location markers, labels where practical, and interactive controls.
- Make radar tiles semi-transparent.
- Default radar opacity: approximately 75%.
- Ensure radar tiles remain aligned when zooming, panning, or rotating the map.
- Reattach the radar layer when the MapLibre style reloads.
- Avoid creating duplicate sources or layers.
- Keep map movement smooth during radar animation.

Prefer updating the existing raster source rather than rebuilding the entire map.

## 3. Radar controls

Add compact floating controls over the map.

### Radar toggle

Place a floating radar button near the top-right corner of the map.

Behavior:
- Tap to enable or disable the radar overlay.
- Visually distinguish enabled and disabled states.
- Remember the user's selection using the app's existing preferences system.

Radar should be enabled by default on the Map tab for first-time users.

### Layer options

Provide a small layer menu with:

- Radar visibility toggle.
- Radar opacity slider (0–100%).
- Basemap style selection, if already supported.
- Optional radar color scheme selector, if the API supports it.

Use Material 3 components and existing app icons where possible.

Avoid adding unnecessary UI elements.

## 4. Radar animation and timeline

Add a compact radar timeline at the bottom of the map, above the existing "Weather on route" button.

The timeline should include:

**Play/Pause button**
- Starts or pauses radar animation.
- Automatically advances through available frames.
- Loops from the final frame back to the first.
- Stops animation when the Map tab is no longer visible.

**Time slider**
- Shows available historical and forecast frames.
- Allows manually selecting a frame.
- Updates the radar layer when the slider changes.
- Highlights the latest observed frame.
- Clearly distinguishes observed frames from forecast frames.

**Time label**
- Shows the selected frame's local time.
- Displays "Observed" or "Forecast".
- Uses the device's timezone.
- Never labels a forecast frame as an observation.

For example:

`14:30  |  Observed`

`15:15  |  Forecast +30 min`

If forecast frames are unavailable, show only historical frames and hide the forecast section.

### Animation performance

- Use approximately 500–800 ms per frame.
- Preload nearby frames when practical.
- Avoid unnecessary network requests during playback.
- Pause animation during major map/style changes if needed.
- Handle slow or missing tile responses without freezing the UI.
- Avoid excessive memory consumption.

## 5. Layout and visual design

The Map tab should look like a natural extension of HluWeatherApp.

### Top area

Keep the existing:
- App title and subtitle.
- Settings button.
- Hourly / Daily / Map navigation.

Remove the large weather card entirely.

### Map area

The map should fill the remaining screen.

Floating controls:

**Top-right**
- Radar visibility button.
- Layer settings button.
- Existing current-location button, repositioned if necessary.

**Bottom**
- Compact translucent radar timeline.
- Play/pause button.
- Observed/forecast indicator.
- Existing "Weather on route" button.

Use rounded corners, subtle shadows, and the app's existing blue/purple accent palette.

Ensure controls remain legible over both light and dark map backgrounds.

Avoid covering important map areas with large panels.

Respect system bars, safe areas, and display cutouts.

### When radar is disabled

Hide the radar timeline and show the normal map.

The "Weather on route" button should remain available.

## 6. Preserve existing functionality

Do not change the behavior of:

- Current location and GPS tracking.
- Map gestures and camera movement.
- Existing map markers.
- Saved locations.
- Weather on route.
- Route navigation.
- Existing forecast providers.
- Hourly and Daily screens.
- App settings unrelated to radar.

The radar feature must be independent of existing forecast functionality.

Do not modify unrelated business logic.

## 7. Architecture

Follow the project's existing architecture and conventions.

Suggested separation of responsibilities:

- `RadarApi`: Fetch radar metadata.
- `RadarRepository`: Cache and expose available frames.
- `RadarViewModel` or existing Map ViewModel: Manage radar state, selected frame, animation, and loading/error states.
- `RadarMapLayer`: Manage MapLibre raster source/layer lifecycle.
- `RadarControls`: Compose controls for radar toggle, opacity, and timeline.

These names are suggestions, not requirements. Inspect the existing project before deciding where to place the implementation.

Use Kotlin coroutines and StateFlow where appropriate.

Avoid introducing unnecessary dependencies or duplicating existing utilities.

## 8. Reliability and API limitations

LibreWXR's public instance is a best-effort service, not a guaranteed production backend.

Implement:

- Reasonable HTTP timeouts.
- Retry with exponential backoff for transient failures.
- Metadata refresh at a sensible interval.
- Cached metadata fallback.
- Graceful degradation when radar tiles cannot load.
- A small unobtrusive error message instead of a blocking dialog.
- Proper cancellation of requests when leaving the screen.

Never block the map while loading radar data.

Do not automatically switch to a different radar provider without an explicit configuration or user choice.

Check the provider's current documentation, API response format, attribution requirements, and permitted usage before finalizing integration.

## 9. Attribution

Keep existing OpenFreeMap/OpenStreetMap attribution.

Add appropriate LibreWXR and upstream radar-data attribution according to the provider's licensing requirements.

Attribution should be visible but unobtrusive.

## 10. Testing

Verify:

1. Map tab opens with no weather card.
2. Map fills available screen space.
3. Existing OpenFreeMap basemap still works.
4. Radar tiles display correctly over Serbia.
5. Radar remains aligned when zooming and panning.
6. Historical radar timeline works.
7. Forecast timeline works when future frames are available.
8. Play/pause animation works smoothly.
9. Radar visibility and opacity controls work.
10. Existing GPS and "Weather on route" functions still work.
11. Radar layers survive map style reloads.
12. App works when LibreWXR is unavailable.
13. No duplicate MapLibre layers or memory leaks.
14. Light and dark themes remain readable.
15. No backend server or API proxy is required.

## Implementation instructions

First inspect the existing project structure, Map tab composables, MapLibre setup, networking stack, and theme.

Then implement the radar integration using the existing architecture.

**Prioritize a working native radar overlay and timeline over adding extra features.**

Do not replace MapLibre with a WebView or another map library.

Do not modify unrelated functionality.

After implementation, provide:
- Summary of changed files.
- API endpoints and tile format used.
- Any provider limitations discovered.
- Testing results.
- Any remaining issues or required configuration.

The final result should be a clean, full-screen Map tab with animated precipitation radar and short-term forecast frames where available, without requiring any server infrastructure.
