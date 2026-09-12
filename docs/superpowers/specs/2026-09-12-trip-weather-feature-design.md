# Trip Weather Feature Design

## Goal

Extend the existing Android weather-route feature into a native three-screen
trip flow without creating a second visual or data architecture. The flow is
optimized for selecting a departure time and seeing weather along the actual
road route.

## Scope Decisions

- Extend the existing `ui/weatherroute` feature in place.
- Use the existing OSRM routing source and MapLibre/OpenFreeMap integration.
- Use the existing batched Open-Meteo route-weather source for route samples.
- Do not expose MET Norway as a route-weather implementation until a route
  source for it exists. The setup screen may show the app provider setting as
  context, but must not imply that route data came from MET Norway.
- Do not implement saved trips, recent trips, or Save trip persistence in this
  change.
- Keep Share as an action callback where it is useful; persistence is deferred.
- Elevation is optional. Do not show an empty graph when routing data has no
  elevation values.

## Navigation

The existing Weather screen opens a nested `trip` navigation graph:

- `trip/setup`
- `trip/overview`
- `trip/details`

One `WeatherRouteViewModel` is scoped to the `trip` graph and is shared by all
three destinations. Setup navigates to overview after a successful calculation.
Overview opens details, and back navigation returns through the graph normally.
The app has no separate bottom-navigation component, so the feature must not
invent one. It uses the established app top-bar and tab treatments.

## Shared State And Models

Keep `WeatherRouteResult` as the primary result model and extend the existing
route models instead of introducing duplicate trip models.

`RouteTimingMode` has two values:

- `AVERAGE_SPEED`: arrival time is departure plus cumulative route distance
  divided by the entered speed.
- `ROUTE_ESTIMATE`: arrival time follows the routing provider duration,
  proportionally distributed over cumulative route distance.

`DrivingRoute` may contain optional elevation values aligned to its polyline.
`RouteWeatherSample` gains optional normalized values needed by the screens:

- `isDay`
- humidity percentage
- precipitation amount
- optional place label
- optional elevation

Add derived immutable values for `TripWeatherSummary` and
`TripWeatherWarning`. Summary values include departure temperature, maximum
temperature, rainy duration, and strongest wind. Warnings cover rain, snow,
thunderstorms, fog, and strong wind. Missing weather remains unavailable rather
than being converted into a false clear condition.

## Forecast Snapshot And Slider

The route-weather source will expose the full hourly response for every
sampled coordinate as a `RouteWeatherSnapshot`, while retaining the existing
normalized `RouteWeatherSample` output path where practical.

The ViewModel keeps the current route snapshot in memory. The snapshot is
associated with the resolved route geometry and sampled coordinates, not with a
departure time. It is populated after OSRM returns the route and the route is
sampled.

Departure state consists of a base departure instant and an integer
`departureOffsetHours` constrained to `0..72`. The setup slider uses 72 discrete
steps and changes the offset in one-hour increments. Each offset change:

1. Updates the displayed departure immediately.
2. Rebuilds sample arrival times using the selected timing mode.
3. Matches each arrival to the nearest cached hourly forecast.
4. Recomputes summaries, warnings, timeline values, and map marker content.

No request is made for ordinary slider movement when the snapshot covers the
required forecast range. If required data is missing, a 300 ms debounced,
cancellable refresh fetches it through the route-weather repository. Composables
only emit ViewModel events and collect immutable `StateFlow` state.

Route sampling retains approximately 30-minute internal samples, including
departure and final arrival. Timeline and table presentation filter these to
hourly checkpoints plus the destination; map markers and warning derivation may
use the complete internal sample set.

## Setup Screen

`TripWeatherSetupScreen` uses a `LazyColumn` and existing Material 3 theme
tokens. It contains:

- Existing endpoint search, saved-location, map-picker, and start-GPS actions.
- A swap-locations action owned by the ViewModel.
- Average speed input defaulting to 80 km/h.
- Date and time controls using the existing platform dialogs.
- A `Start time preview` card with a formatted departure and discrete
  `Now`, `+24h`, `+48h`, `+72h` labels.
- A route-estimate switch labelled `Use route estimates` with supporting text
  `Use actual driving time from routing`.
- A provider context row using existing provider settings. Route calculations
  continue to use Open-Meteo route weather as defined above.
- Existing validation, loading, route error, retry, and outdated-result states.
- A primary `Show trip weather` action styled like existing app buttons.

The endpoint picker is visually unified with existing app cards and uses the
existing `WeatherCondition` and location models. It must preserve existing
test tags and endpoint selection behavior.

## Overview Screen

`TripWeatherOverviewScreen` is a vertically scrollable result screen with:

- Existing app top bar treatment showing `From -> To`, departure, and speed.
- Rounded MapLibre/OpenFreeMap surface showing the actual route polyline,
  endpoint markers, and tappable weather markers.
- Weather markers rendered with the shared `HluWeatherIcon`, including its
  light/dark palette and day/night handling.
- A summary card with distance, estimated time, and average speed.
- A weather-along-route card with departure temperature, maximum temperature,
  rainy duration, and strongest wind.
- Derived warning cards when route samples contain actionable weather.
- An optional elevation card only when elevation data is available.
- A highlights list using compact rows consistent with existing forecast lists.
- Share and Details actions. Save trip is not included.

Selecting a marker updates the shared selected sample and keeps the existing
map/timeline selection behavior.

## Details Screen

`TripWeatherDetailsScreen` uses the existing app tab visual language with three
tabs:

- `Timeline`: hourly route-weather rows with shared weather icons, time, place
  label or route checkpoint fallback, condition, temperature, and distance;
  followed by destination weather and route warnings.
- `Map`: the same route map component and tappable marker behavior.
- `Table`: a horizontally scrollable compact table using the existing hourly
  forecast table colors, typography, header treatment, and row dividers. Its
  columns are time, weather, location, temperature, precipitation, wind, and
  distance.

Destination weather displays temperature, condition, precipitation, wind, and
humidity. A route warning displays its type, time range, and route location
when that information is available.

## Reuse Rules

- Use `HluWeatherIcon` for all route weather visuals. Do not add emoji glyphs or
  a second icon mapping.
- Use `MaterialTheme`, `LocalHluColors`, existing card shapes, spacing, buttons,
  tab patterns, and unit-formatting helpers.
- Reuse OSRM route geometry and the existing MapLibre style URLs.
- Route weather requests remain in repository/data classes.
- User-facing text should use string resources when the surrounding feature has
  resources available; otherwise keep the strings centralized in trip UI code.
- Add light and dark previews for the three screen surfaces using deterministic
  preview route data where practical.

## Verification

Add tests for:

- Average-speed and route-estimate timing.
- 30-minute sampling and hourly/final display filtering.
- Slider updates using the cached snapshot without extra source calls.
- Debounced refresh only when forecast coverage is missing.
- Summary and warning derivation, including unavailable weather.
- Setup, overview, and details rendering in light and dark themes.
- Details tab switching, map marker selection, and compact table content.
- Navigation graph state sharing and back-stack behavior.

Run the complete Android test, lint, and debug-build commands before completion.
