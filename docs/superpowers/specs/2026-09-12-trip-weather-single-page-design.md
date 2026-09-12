# Trip Weather Single-Page Design

## Goal

Reorganize Trip Weather into one clear page without changing the working route
calculation and weather functionality. The page combines route inputs and
results in one vertical flow; visual polish is intentionally a later pass.

## Scope

The Trip Weather destination contains these sections, in this order:

1. Start location
2. Destination
3. `Show trip weather`
4. Start-time slider
5. Speed slider
6. Result map with weather icons along the route
7. Result weather table

The whole page may scroll vertically on a phone. The start/destination area
must not contain its own vertical scroll container. The result table may scroll
horizontally on narrow screens, but it must not own a nested vertical scroll.

The existing route-weather data behavior remains in place:

- OSRM supplies the actual driving route.
- Open-Meteo supplies batched hourly weather for route samples.
- Route weather uses the in-memory hourly snapshot and existing debounced
  refresh behavior.
- `HluWeatherIcon`, existing themes, cards, buttons, units, MapLibre, and
  OpenFreeMap styles remain the source of UI primitives.

This reorganization does not redesign colors, typography, spacing, card shapes,
map styling, or table styling. Those are the next design task.

## Navigation And Ownership

Keep one `weather_route` navigation destination entered from Weather Map mode.
Remove the nested `trip` graph and the `trip/setup`, `trip/overview`, and
`trip/details` destinations. Remove automatic navigation between page states.

`WeatherRouteScreen` owns the complete page and collects one
`WeatherRouteViewModel.state`. The existing `WeatherRouteViewModel` remains the
owner of endpoint selection, route calculation, timing, forecast snapshots,
slider updates, errors, retries, and selected route samples.

The current setup/overview/details wrapper composables are removed from the
runtime path. `WeatherRouteMap` and `WeatherRouteTable` remain reusable result
components. There is no runtime `WeatherRouteTimeline` composable after this
change. Its hourly filtering, warning rows, and unavailable formatting are
migrated into `WeatherRouteTable`, and the obsolete timeline file and
page-specific tests are removed after equivalent table coverage is in place.

Back from `weather_route` returns to the Weather screen. There are no other
Trip Weather pages, tabs, details routes, saved trips, recent trips, or Save
trip persistence.

## Endpoint Inputs

Use two compact endpoint fields/cards in the first section of the page:

- `Start location`
- `Destination`

Both fields support the existing search, saved-location, map-picker, and start
GPS behaviors. The existing endpoint models and callbacks remain authoritative.

When a field is edited, show an anchored dropdown with at most five search
results. Selecting a result resolves that endpoint and closes the dropdown.
There is no nested scroll in the endpoint block and no provider selector in the
dropdown or endpoint card.

Search uses the provider selected in Settings. The route page does not expose a
provider label, provider switch, or provider explanation.

Keep the existing stable endpoint test tags where they still describe the same
control. Add no duplicate endpoint-selection logic in the screen.

## Start Time

Replace separate date and time dialogs with one integer start-time slider:

- Minimum: `0` hours from now.
- Maximum: `72` hours from now.
- Step: one hour.
- Labels: `Now`, `+24h`, `+48h`, `+72h`.

The ViewModel captures a stable reference instant when it is created and derives
the selected departure from that instant plus the slider offset. The screen
displays the selected local date and time beside the control. The selected
departure is evaluated when `Show trip weather` is pressed and when an existing
result is recalculated.

If a selected departure plus the route duration exceeds the available forecast
horizon, do not publish valid-looking weather. Show the existing concise
forecast-range error and keep unavailable samples unavailable. Returning the
slider to a valid range clears that stale error after a successful in-range
recalculation or snapshot application.

## Speed

Replace the text speed field with one integer slider:

- Minimum: `40 km/h`.
- Maximum: `130 km/h`.
- Step: one km/h.
- Default: `80 km/h`.

Show the selected numeric speed and unit adjacent to the slider. Route sample
arrival times use this selected speed. Remove the route-estimate switch from
the page; the single-page flow uses the entered speed consistently.

Update validation and sampling tests from the former `50..240` input range to
`40..130`. The route data source and route geometry are unchanged.

## Calculation And Results

The `Show trip weather` button is placed before the two sliders as requested.
It is enabled only when both endpoints are resolved and no route calculation
is already running. Pressing it:

1. Validates endpoints, start time, and speed.
2. Requests the OSRM driving route.
3. Builds route samples using the selected speed and current departure.
4. Fetches or reuses the Open-Meteo hourly route snapshot.
5. Renders the map and table below the controls.

Changing the start-time or speed slider after a result updates the in-memory
result when the current snapshot covers the new arrivals. A missing snapshot
range uses the existing 300 ms cancellable refresh. Inputs that invalidate a
calculated result show the existing outdated state until the user calculates
again.

The route result keeps the actual route geometry, endpoint labels, selected
departure, travel distance, arrival duration, route samples, summaries,
warnings, and selected sample index in the shared ViewModel state.

## Result Map

Render the actual route map immediately below the sliders after calculation.
Reuse the existing `WeatherRouteMap` implementation:

- Actual OSRM route polyline.
- Start and destination markers.
- Weather markers along the route.
- `HluWeatherIcon` for weather markers, including day/night behavior.
- Existing severity colors and unavailable-weather semantics.
- Existing route fitting, selected-marker camera behavior, MapLibre style URL,
  OpenFreeMap tiles, and tile-error message.

Selecting a weather marker updates the shared selected sample index. The map
does not open another page or tab.

## Result Table

Render the route weather table below the map on the same page. Reuse the
existing Destination table visual language and keep these columns:

- Time
- Weather icon/condition
- Location or route-checkpoint fallback
- Temperature
- Precipitation
- Wind
- Distance

Use hourly route checkpoints plus the final destination sample. The table is
horizontally scrollable when its seven columns do not fit the viewport. Its
rows are part of the parent page's vertical content, so the table must not use
a nested vertical `LazyColumn` or vertical scroll container.

Keep destination weather and route warnings in the table result content using
the existing unavailable formatting. Selecting a table row updates the same
selected sample index used by the map. No separate Timeline/Map/Table tab
navigation remains.

## Settings

Add a separate persisted `Location search provider` setting with these values:

- Photon, default.
- Open-Meteo.

This setting is independent of the existing weather forecast provider setting.
The existing weather provider remains responsible for the main weather screen;
it does not select the route-weather source.

Persist the selected place-search provider in the existing Settings repository
using the existing enum/default parsing pattern. Invalid or missing stored
values fall back to Photon. Add a Settings row and tests for both selections,
persisted reload, default behavior, and stable selection semantics.

The Settings screen is still a separate app destination. "No other pages"
applies to the Trip Weather flow, not to the existing app Settings screen.

## Errors And Availability

- Incomplete endpoints: disable `Show trip weather`.
- Invalid speed: show the `40..130 km/h` validation message.
- Routing failure: keep the input page visible and show route retry.
- Weather failure after a valid route: keep the map/table result area visible,
  show unavailable weather values, and show weather retry inline.
- Missing or out-of-horizon forecast data: never substitute a distant hourly
  value as valid weather; clear those sample weather fields and show the
  forecast-range/unavailable state.
- Preserve `CancellationException`; map only non-cancellation failures to UI
  messages.

## Verification

Add or update tests for:

- One `weather_route` destination and Back behavior.
- Removal of nested trip routes and automatic page transitions.
- Persisted Photon/Open-Meteo location-search setting.
- Endpoint dropdown capped at five results with no nested vertical scroll.
- Start-time slider `0..72`, one-hour steps, labels, and now-relative display.
- Speed slider `40..130`, one-unit steps, default `80`, and validation.
- Calculation ordering and result visibility on the same page.
- Cached slider changes without unnecessary network requests.
- Forecast horizon and stale-error clearing after returning in range.
- Route map icon/marker selection and unavailable weather.
- Seven table columns, horizontal scrolling, parent-owned vertical scrolling,
  destination row, warnings, units, and map/table selection synchronization.
- Light/dark rendering against the existing theme without introducing visual
  redesign in this reorganization.

Run the complete Android test, lint, and debug-build commands before declaring
the reorganization complete. Manual device verification should confirm the
single-page order and that endpoint editing does not open a nested scrolling
panel.
