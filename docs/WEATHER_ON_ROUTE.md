# Weather On Route

Weather on route is the single native Android `weather_route` page. It shows
weather at expected arrival points along the OSRM driving route. Route results
are kept in memory only.

## Open The Page

1. Open the main Weather screen with a loaded forecast.
2. Select **Map** in the Hourly, Daily, and Map navigation tabs.
3. Tap **Weather on route**.

## Page Order

The page contains one vertically scrolling flow in this exact order:

1. Start endpoint dropdown.
2. Destination endpoint dropdown.
3. **Show trip weather** action.
4. Start-time slider.
5. Speed slider.
6. Result map.
7. Result weather table.

The endpoint controls are compact. Route summary, route errors, weather
unavailability, and retry actions are inline result content associated with
the calculated result; they do not add ordered page sections before the map or
table.

Both endpoints must be resolved before calculation. Endpoints can come from
the configured place search provider, saved locations, the current GPS
location for the start, or the map picker. Search text alone never becomes an
endpoint, and selecting an endpoint does not edit or save a location.

The start time defaults to now and uses a discrete `0..72` hour slider with
`Now`, `+24h`, `+48h`, and `+72h` labels. The speed slider is integer
`40..130 km/h` with an `80 km/h` default. Temperature, wind, distance, and
precipitation use the existing app unit settings.

The table is horizontally scrollable only; the page owns the vertical scroll.
It shows hourly checkpoints plus the destination, including time, weather,
location, temperature, precipitation, wind, and distance. Unavailable values
remain visible as unavailable rather than hiding successful route samples.

## Provider Ownership

- Settings owns the persisted location-search provider: Photon or Open-Meteo.
- OSRM owns the driving route map, route geometry, route distance, and route
  timing data.
- Open-Meteo is the only route-weather provider. The route page has no weather
  provider selection or provider text and does not follow the app-wide
  Open-Meteo/MET.no weather setting.

Route weather uses one batched Open-Meteo request for the sampled coordinates.
Changing an endpoint, search input/provider, speed, or departure cancels
in-flight work. Moving the start-time slider uses the cached hourly snapshot
when it covers the selected trip; otherwise a cancellable 300 ms debounced
refresh fetches a new snapshot.

## Errors And Retry

- Routing failure preserves the endpoint inputs and offers inline **Retry**.
- Complete route-weather failure preserves the route and summary, marks
  weather unavailable, and offers inline **Retry weather**.
- Partial route-weather failure keeps successful samples and marks failed
  samples unavailable.
- Empty or failed place search leaves the current endpoint unchanged.
- GPS denial or unavailability leaves saved locations and map picking
  available.
- Map tile failure is separate from route data and displays **Map tiles
  unavailable**.

## Explicit V1 Exclusions

- Saved trips and recent trips.
- Persisted or shared route results.
- Multi-stop routes, alternatives, rerouting, and traffic-aware timing.
- Historical weather and weather-based route optimization.
- MET.no route weather.
- Automatic routing or weather-provider fallback.
