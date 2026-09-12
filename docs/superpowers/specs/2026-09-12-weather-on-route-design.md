# Weather On Route Design

Date: 2026-09-12
Status: Approved design

## Goal

Add a native Android route planner that shows forecast weather at the expected
arrival time at points along a driving route. Users choose start and destination
places, a local departure time, and an average speed. The planner initially uses
OSRM for driving routes, Photon for place search, and Open-Meteo for batched
route-weather forecasts while preserving interfaces for future routing and search
providers.

## Scope

Version one includes:

- A dedicated `weather_route` navigation destination, entered from Map mode.
- Start and destination selection from Photon or Open-Meteo city search, saved
  locations, map selection, and current GPS location for the start.
- A planner-only place-search provider selector. Photon is the default and
  Open-Meteo is the alternative.
- A local departure date and time, defaulting to the next full hour.
- An average-speed control, defaulting to 80 km/h and limited to 50 through
  240 km/h.
- OSRM driving-route geometry, distance, and provider travel estimate.
- Open-Meteo-only batch weather samples at departure, every elapsed travel hour,
  and arrival.
- A route map with color-coded weather markers and a selectable arrival timeline.
- Correct error, retry, loading, cancellation, and partial-weather handling.

Version one excludes saved or shared routes, multi-stop routes, traffic-aware
arrival estimates, rerouting, historical weather, weather-based route
optimization, and automatic provider fallback.

## Navigation And UI

The existing Hourly, Daily, and Map selector remains unchanged. Map mode gets a
Weather on route action that opens the full-screen `weather_route` destination.
The screen has a back action and does not modify saved locations when the user
selects route endpoints.

Before calculation, the planner displays:

- Start and destination endpoint cards.
- Search results before text can be accepted as an endpoint.
- Saved-location, map-picker, and current-location actions where applicable.
- A Photon/Open-Meteo place-search selector that applies only to the planner.
- Departure date/time and average-speed controls.
- A calculate action enabled only for two resolved endpoints and a valid trip
  within the forecast horizon.

After calculation, the planner displays:

- Resolved endpoint labels, total distance, chosen-speed trip duration, OSRM
  duration, departure, and expected arrival.
- A MapLibre/OpenFreeMap map with the driving polyline, endpoint markers, and
  color-coded route-weather markers.
- A vertical timeline with expected local time, condition, temperature, wind,
  precipitation probability, and travelled distance. The final item identifies
  the destination.
- Two-way selection between a timeline item and its map marker. Selecting either
  centers and highlights the corresponding marker/item.

Temperature, wind, distance, and precipitation use the existing app unit
settings. Average speed remains an entered km/h value in version one.

## Architecture

`WeatherRouteViewModel` owns planner inputs, request lifecycle, validation, and
route results. It is independent from `WeatherViewModel`, preserving the current
saved-location forecast flow.

The data layer introduces three replaceable source interfaces:

- `RoutingSource` calculates a driving polyline, distance, and provider travel
  estimate. `OsrmRoutingSource` is the first implementation. OpenRouteService
  and Valhalla implementations can be added without changes to planner UI,
  weather sampling, or result models.
- `PlaceSearchSource` returns normalized place-search results. Photon is the
  default implementation and Open-Meteo is the selectable alternative.
- `RouteWeatherSource` returns normalized weather for ordered route samples.
  `OpenMeteoRouteWeatherSource` is the only version-one implementation.

`HluWeatherApplication` constructs these services using the existing shared Ktor
client. The route feature uses focused packages under `data/model`,
`data/network`, `data/repository`, and `ui/weatherroute`, rather than expanding
the existing weather screen.

## Route And Weather Calculation

OSRM returns the route geometry in coordinate order. The planner computes
cumulative distance for each polyline segment, then interpolates positions on
that real route geometry rather than using straight-line positions.

Weather planning duration is always:

```text
route distance / chosen average speed
```

This duration is separate from OSRM's travel estimate, which is displayed as a
reference only. Samples are generated at departure, every elapsed travel hour,
and arrival. Short routes always retain distinct start and destination samples.

The complete planned trip, including the expected arrival time, must be within
Open-Meteo's currently available forecast horizon. The UI validates this before
routing/weather requests. Open-Meteo receives all sampled coordinates in a
single batch request and returns weather values needed for driving conditions:
condition, temperature, wind, and precipitation probability. Route weather does
not follow the app-wide Open-Meteo/MET.no weather-provider selection.

Weather colors derive from the existing normalized `WeatherCondition`:

- Favorable: clear through partly cloudy.
- Caution: cloudy, fog, and drizzle.
- Adverse: rain and snow.
- Severe: thunderstorms.
- Neutral: unavailable weather.

## Requests, State, And Errors

The ViewModel debounces city-search input and cancels obsolete search, route,
and weather jobs when a relevant input changes. Photon and Open-Meteo place
search use an identifying app user agent and conservative request throttling.
Public OSRM is the initial routing source; its name is shown in the result and a
failure is reported without silently switching providers.

Inputs modified after a completed calculation mark the result outdated until the
user calculates again. Outdated results are not presented as matching the new
inputs.

- Empty search: preserve entered text and allow retry or source change.
- GPS denied/unavailable: use existing permission/status behavior while map and
  saved endpoints remain available.
- Unreachable endpoints or routing failure: preserve inputs, remove old results,
  and offer retry.
- Complete weather failure: retain route geometry and summary, render neutral
  unavailable timeline/marker states, and offer a weather retry.
- Partial weather failure: retain successful samples and render only failed
  samples as unavailable.
- HTTP, network, timeout, and malformed-data errors use concise user-facing
  messages, never stack traces or raw response bodies.

Route results are in memory only for version one.

## Testing And Verification

Unit tests cover cumulative-distance and interpolation logic, start/hourly/end
sample generation, short routes, non-hour arrival, and forecast-horizon
validation. Ktor mock tests cover OSRM, Photon, Open-Meteo geocoding, and
Open-Meteo batch-weather request/response behavior. Interface tests verify that
alternative routing implementations can be supplied without changing the UI.

ViewModel tests cover happy path, cancellation, no search results, routing
failure, complete weather failure, and partial weather failure. Compose tests
cover endpoint selection, provider selection, speed/departure validation,
calculate/retry actions, route summary, timeline selection, and map-selection
wiring.

Completion verification runs:

```text
./gradlew test
./gradlew lint
./gradlew assembleDebug
```

Manual verification includes a representative route such as Zajecar to Trieste
on an emulator or device.
