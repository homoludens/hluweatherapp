# Weather On Route

## Overview

Weather On Route is the native Android route planner in HluWeather. It calculates
weather conditions at expected arrival points along a driving route. The planner
uses the route geometry returned by the routing service, a user-selected average
speed, and the selected local departure time. Route results are kept in memory
only.

The approved architecture is summarized below because the design-spec file is
not present in this branch.

## Approved Architecture Summary

The planner uses a dedicated `weather_route` navigation destination and
`WeatherRouteViewModel`. The ViewModel owns planner inputs, validation, request
cancellation, retries, and in-memory results without changing the existing
weather forecast flow.

The data layer uses replaceable source interfaces:

- `RoutingSource` supplies a driving polyline, distance, and provider estimate;
  `OsrmRoutingSource` is the v1 implementation.
- `PlaceSearchSource` supplies normalized place results; Photon is the default
  and Open-Meteo geocoding is selectable.
- `RouteWeatherSource` enriches ordered route samples; Open-Meteo is the only
  v1 implementation.

The shared route models flow from these sources through the ViewModel to the
Compose route screen, MapLibre map, and timeline. This keeps routing and search
providers replaceable without changing the planner UI or sampling logic.

## Opening The Planner

1. Open the main weather screen with a loaded location forecast.
2. Select **Map** in the Hourly, Daily, and Map navigation tabs.
3. Tap **Weather on route**.

The planner opens as the full-screen `weather_route` navigation destination.
Back returns to the weather screen. Selecting route endpoints does not create,
edit, or change saved locations.

## Inputs And Endpoint Selection

| Input | Behavior | Default or limit |
| --- | --- | --- |
| Start | Search, saved location, current GPS location, or map picker | Empty |
| Destination | Search, saved location, or map picker | Empty |
| Search provider | Planner-only selector between Photon and Open-Meteo geocoding | Photon |
| Departure | Android local date and time controls | Next full local hour |
| Average speed | Entered driving speed in km/h | 80 km/h; 50-240 km/h |

Search text does not become an endpoint until a result is selected. Search
results are normalized to a label and coordinate, and invalid or duplicate
coordinates are discarded. The current-location action is available only for
the start endpoint. The destination has no GPS shortcut.

The Calculate action requires both resolved endpoints. On calculation, the
speed must be an integer from 50 through 240 km/h, the departure cannot be in
the past, and the expected arrival must be within the available forecast
horizon. The planner's speed value is always km/h even when other app units are
changed in Settings.

## Providers

### Place Search

Photon is the default route-planner search provider. Open-Meteo geocoding can
be selected for either endpoint from the planner's provider selector. This
selector does not change the app-wide weather provider setting.

The Android clients use these public services:

| Purpose | Provider | Request |
| --- | --- | --- |
| Place search | Photon | `https://photon.komoot.io/api/` |
| Alternative place search | Open-Meteo geocoding | `https://geocoding-api.open-meteo.com/v1/search` |

Both searches request up to eight English results. Blank queries produce no
request and no results. Network or HTTP failures leave the current endpoint
unchanged and expose a concise error that can be retried or resolved by
switching provider.

### Routing

OSRM is the initial routing provider. The Android client requests a full GeoJSON
driving route from the public OSRM service and adapts its longitude/latitude
coordinates into the shared `GeoPoint` model. The route result contains the
OSRM provider name, driving polyline, distance, and provider travel estimate.

The planner's expected travel duration is calculated independently as:

```text
route distance / entered average speed
```

The OSRM travel estimate is not used to place weather samples. A routing
failure does not silently switch to another routing provider; the inputs remain
available and **Retry** is offered.

### Route Weather

Open-Meteo is the only route-weather provider in v1. It receives all sampled
coordinates in one batched forecast request and returns hourly temperature,
weather code, wind speed, and precipitation probability. Route weather does not
follow the app-wide Open-Meteo/MET.no provider selection.

The request uses a 16-day forecast, GMT timestamps, Celsius temperature, km/h
wind, and millimetres for precipitation. Each route sample is matched with the
nearest returned hourly forecast time. If an individual response is missing or
malformed, that sample is shown as unavailable while other samples remain
usable.

## Route Sampling

Samples are generated from the actual OSRM polyline, not from a straight line
between endpoints. The planner uses constant average speed and interpolates
each sample over cumulative route geometry distance. It creates samples at:

- Departure.
- Every elapsed travel hour before arrival.
- Expected arrival at the destination.

The complete trip, including expected arrival, must be no later than the
planner's current-time-plus-16-days forecast boundary. A departure in the past
and an arrival outside that boundary are rejected. A short route still has
distinct departure and destination samples.

## Results

After routing, the screen contains:

- A route summary with the resolved endpoints, route distance, entered average
  speed, routing provider label, OSRM duration, local departure, and expected
  arrival.
- A MapLibre map using the app's OpenFreeMap light or dark map style.
- The OSRM route line and start/destination markers.
- A weather marker for every route sample.
- A scrollable timeline containing local sample time, travelled distance,
  condition, temperature, wind, and precipitation probability.
- A visible `Destination` label on the final timeline item.

Weather markers use these normalized severity groups:

| Severity | Conditions |
| --- | --- |
| Favorable | Clear through partly cloudy |
| Caution | Cloudy, fog, and drizzle |
| Adverse | Rain and snow |
| Severe | Thunderstorms |
| Unavailable | Missing or unusable weather |

Selecting a timeline item selects the corresponding map marker and centers the
map on that sample. Selecting a map marker selects the corresponding timeline
item. Temperature, wind, distance, and precipitation display using the existing
app unit settings, while the average-speed input remains in km/h.

## Error And Retry States

- **Incomplete endpoints:** Calculate remains disabled until both endpoints are
  selected.
- **Invalid speed:** The planner reports that speed must be from 50 to 240
  km/h, without calling the routing service.
- **Past departure:** The planner reports that departure cannot be in the past.
- **Arrival beyond forecast:** The planner reports that arrival is outside the
  forecast range.
- **No search results:** No selectable result is shown and the existing
  endpoint is not replaced.
- **GPS denied or unavailable:** A concise location status is shown; saved and
  map endpoint selection remain available.
- **Routing failure:** The route result is cleared, inputs are preserved, and
  **Retry** is offered.
- **Complete route-weather failure:** The route geometry and summary remain;
  weather values render as unavailable and **Retry weather** is offered.
- **Partial route-weather failure:** Successful samples remain populated and
  failed samples render as unavailable.
- **Map style or tile failure:** The map displays **Map tiles unavailable**;
  route and timeline data are separate from map tile loading.

Changing an endpoint, search input/provider, speed, or departure invalidates
in-flight work. After a completed calculation, changing relevant inputs marks
the displayed result outdated until Calculate is run again.

## Explicit V1 Exclusions

The Android v1 planner does not provide:

- Persisted or shared route results.
- Multi-stop routes.
- Traffic-aware arrival estimates.
- Rerouting or route alternatives.
- Historical weather.
- Weather-based route optimization.
- Automatic routing or weather-provider fallback.
