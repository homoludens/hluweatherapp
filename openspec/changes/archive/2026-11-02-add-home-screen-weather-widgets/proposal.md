## Why

HluWeather currently requires opening the app to check the weather. Small and medium home-screen widgets will make current conditions and the next few hours available at a glance while retaining the app's appearance, settings, and existing weather data pipeline.

## What Changes

- Add two resizable Android home-screen widgets: a 2x2 current-weather card and a 4x2 current-weather card with three to five upcoming hourly forecasts.
- Use the supplied images in `docs/widgets/` as visual references, with the written requirements taking precedence: the small widget has no hourly strip in v1.
- Reuse `WeatherRepository`, normalized forecasts, provider selection, temperature formatting, condition labels, theme colors, and weather icon selection. Add only the cache-read and passive location access needed by widgets, preserving existing callers' behavior.
- Render cached forecasts without waiting for the network, retain valid weather after failed refreshes, and clearly distinguish stale or unavailable data.
- Share one approximately hourly periodic refresh across installed widgets, support manual refresh, and react to relevant settings, app forecast-cache, and location changes without feedback loops or frequent polling.
- Resolve the app's selected location and passive last-known Track Me location without requesting GPS or adding permissions. Use the last selected saved location when Track Me has no valid fix.
- Open the main weather screen from the widget or its location label; include accessibility descriptions, light/dark presentation, and examples and tests for edge cases.

## Capabilities

### New Capabilities

- `home-screen-weather-widgets`: Launcher registration, responsive small and medium layouts, shared visual language, accessible weather presentation, theme and unit support, and app-opening interactions.
- `widget-weather-updates`: Repository-backed cache-first rendering, passive location resolution, settings synchronization, periodic and manual refresh lifecycle, and offline/failure behavior.

### Modified Capabilities

None. There are no existing OpenSpec capability specs, and this feature must preserve existing in-app and notification behavior.

## Impact

- Add a focused `app/src/main/java/net/droopia/hluweather/widget/` package with presentation adapters, Glance layouts and receivers, a refresh callback, and WorkManager scheduling.
- Add Jetpack Glance app-widget support and launcher metadata/preview resources; reuse the already-installed WorkManager and DataStore dependencies.
- Extend shared cache-only access in `WeatherRepository`/`CachingWeatherRepository` without relaxing the current one-hour policy for existing callers. Expose or persist passive last-known current-location data without changing `activeLocation` emissions on process restart.
- Make the existing Compose weather-icon specification and theme tokens reusable by Glance without changing their in-app output; do not introduce another condition mapping or provider client.
- Add minimal application lifecycle hooks for installed-widget reconciliation and any worker integration needed alongside existing notification workers.
- Add targeted repository, widget adapter, scheduling, rendering, and interaction tests, example states, and a launcher verification checklist. Only the Google debug variant is built for final verification; no release signing, new configuration UI, app redesign, or unrelated refactoring is included.
