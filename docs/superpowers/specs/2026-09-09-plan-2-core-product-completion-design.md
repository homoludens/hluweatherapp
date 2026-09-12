# Plan 2: Core Product Completion Design

## Status

Approved in conversation on 2026-09-09. This plan finishes the approved v1
weather, location, map, provider, cache, and unit behavior. Notifications and
release hardening are Plan 3.

## Goals

1. Make provider, selected location, Track Me, and unit settings affect the
   weather screen.
2. Replace the fixed sample-location behavior with persisted saved locations
   and a transient Track Me location.
3. Add the OpenFreeMap/MapLibre location map and full-screen picker.
4. Support GPS-assisted selection, foreground Track Me, and reverse geocoded
   editable names.
5. Add MET Norway and a provider-aware cached-forecast fallback.

## Non-Goals

- Weather notifications, daily summaries, or trip alerts.
- Background location tracking.
- City-name search, offline map downloads, map weather overlays, accounts, or
  cloud sync.
- Unit conversion in storage, network requests, or forecast cache entries.

## Architecture

The project remains a single Android module. State moves out of UI-owned
settings into focused repository boundaries:

```text
SettingsRepository -> provider, theme, units
LocationRepository -> saved locations, selected location, Track Me
        |
        v
WeatherViewModel -> combines active location and provider; cancels stale work
        |
        v
WeatherRepository -> provider source selection and provider-aware cache
```

`SettingsRepository` retains appearance and unit preferences, plus the
selected saved-location identifier and location mode. `LocationRepository`
owns persisted saved-location records and derives an `ActiveLocation` flow.
Track Me produces a transient current location and never rewrites a saved
location.

The application container creates singleton repositories, HTTP clients, map
and device-location dependencies, and cache storage. View models receive these
dependencies through explicit factories. Screens depend only on view-model
state and callbacks.

`WeatherViewModel` combines the current provider and active location. A change
to either cancels an obsolete request and starts a new one. A request result is
applied only when its provider and active-location key still match current
state. Pull to refresh always forces a live request.

## Locations

Saved locations persist an ID, editable name, latitude, longitude, and optional
altitude. Add, edit, delete, and select actions update `LocationRepository`.
Deleting the active saved location selects the first remaining saved location;
if no saved location remains, the weather screen shows the existing empty state.

The weather screen location title opens a quick-switcher bottom sheet with:

- Track Me.
- Saved locations.
- Add location.
- Manage locations.

Selecting a saved location immediately disables Track Me. Selecting Track Me
keeps the last selected saved-location ID for a later switch back.

## Map, Picker, And Geocoding

The Map tab uses MapLibre Compose with OpenFreeMap Liberty in light theme and
the OpenFreeMap dark style in dark theme. It renders saved-location markers,
distinguishes the active saved location, activates a marker when tapped, and
has a recenter action. In Track Me mode it follows the latest foreground device
position. Map tile failures show a non-blocking message; location and forecast
functions remain available.

Add and edit location use a full-screen map picker with a fixed centered
selection marker. Camera-idle coordinates update the bottom panel. The panel
contains an editable name, latitude, longitude, optional altitude, a GPS
recenter action, and Save. Name edits always win over generated reverse-geocode
names.

GPS permission is requested only after the picker GPS action or enabling Track
Me. Permission denial and disabled or unavailable location services explain
the condition and preserve manual map selection. Reverse geocoding uses
Nominatim after camera movement settles or a successful GPS fix, is debounced
to at most one request per second, and never blocks saving. Failure supplies
the editable fallback name `New location`.

Track Me receives foreground, low-frequency updates. It fetches weather on
initial activation and only refreshes after the device moves at least 5 km from
the last successful weather fetch or 30 minutes have elapsed. It does not track
when the app is closed.

## Weather Providers, Cache, And Units

Open-Meteo and MET Norway implement a shared provider source contract and map
to the existing forecast model. MET Norway sends the required non-generic
User-Agent and maps its hourly response into the normalized current, hourly,
and daily data. MET-specific unavailable values remain null and render as an
em dash.

Successful live forecasts are cached. Cache entries are keyed by provider plus
the stable saved-location ID or rounded Track Me coordinates, so data from a
different provider cannot be shown after a provider switch. Keep the 20 most
recent entries. A live failure uses only its matching cache entry, labels it
stale with its fetched time, and exposes Retry. There is no automatic provider
fallback. Clear cache removes all forecast entries.

Network, cache, and domain values stay metric: Celsius, km/h, kilometres, and
millimetres. Unit preferences transform values only in presentation helpers.
Every displayed temperature, precipitation, wind, and distance uses those
helpers consistently.

## Error Handling

- A live failure with a matching cache entry retains data and displays stale
  status plus Retry.
- A live failure without cache displays a concise error plus Retry.
- An empty saved-location list displays setup actions without fabricating a
  forecast.
- Missing optional provider values render as an em dash.
- Permission, GPS, geocoding, and map errors do not prevent saved locations
  from working.

## Delivery Slices

1. State integration and location persistence: introduce the location
   repository, persist location records, connect provider/location state to
   `WeatherViewModel`, and implement the weather quick-switcher.
2. Map and foreground location: add MapLibre/OpenFreeMap, picker navigation,
   GPS permission handling, device location source, Track Me, and Nominatim.
3. Provider, cache, and units: add MET Norway, cache persistence/fallback,
   clear cache, and consistent presentation conversion.

## Testing

- Unit tests: location CRUD and persistence, active-location selection,
  Track Me refresh thresholds, coordinate cache keys and eviction, provider
  mapping, MET Norway symbols/daily aggregation, and unit conversion.
- Repository and view-model tests: provider/location changes cancel obsolete
  requests, cache success and fallback behavior, no-cache errors, and
  foreground Track Me updates.
- UI tests: location quick-switcher, empty/stale/error states, map-state
  callbacks, picker save/edit/delete flow, unit display, and theme-specific map
  style selection through injected seams.
- Device verification: GPS permission granted/denied, disabled services,
  emulator location updates, OpenFreeMap rendering, Nominatim failure, offline
  cached fallback, and switching between both providers.

## Acceptance Criteria

- No production weather request uses a fixed location or ignores the selected
  provider.
- Saved locations, Track Me, map picking, and geocoded editable names work
  without requiring startup location permission.
- Map mode is a functioning location map rather than a placeholder.
- Both providers load into the existing weather UI and the chosen unit settings
  affect all displayed values.
- Matching cached forecasts are used only after a live failure and are visibly
  stale.
- Unit tests remain offline and deterministic; debug unit tests, debug
  assembly, lint, and the listed device flows pass.
