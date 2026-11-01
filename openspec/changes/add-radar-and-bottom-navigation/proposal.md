## Why

The map lacks precipitation context, and route planning is hidden behind a shortcut rather than being a peer of the forecast and map screens. Add an animated native radar overlay first, then make the four primary destinations consistently accessible without sacrificing screen space.

## What Changes

- Integrate LibreWXR radar metadata and raster tiles directly into the existing MapLibre map, with timeline playback, visibility and opacity controls, attribution, and graceful offline behavior.
- Remove the Map tab's weather card while preserving map, location, and route behavior; initially retain its route shortcut.
- After radar is working, replace the large header and top tabs with a compact gradient header and four-destination bottom navigation, reusing the existing route screen and removing redundant route shortcuts.
- Preserve forecast, location, settings, route functionality, accessibility, theme support, and system insets.

## Capabilities

### New Capabilities
- `weather-radar`: Native radar map, metadata, timeline, settings, resilience, and attribution.
- `main-bottom-navigation`: Compact shared header and four top-level destinations, including existing route planning.

### Modified Capabilities

None.

## Impact

WeatherScreen, WeatherHero, WeatherMap, navigation and route presentation, settings preferences, networking, and focused UI/unit tests are affected. LibreWXR is a best-effort external service; no backend or implicit provider substitution is introduced. Existing MapLibre/OpenFreeMap infrastructure and app dependencies should be reused.
