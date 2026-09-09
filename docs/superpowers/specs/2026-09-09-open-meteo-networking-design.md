# Open-Meteo Networking Design

## Goal

Replace the production mock weather source with a real Open-Meteo forecast
repository while keeping the existing `WeatherRepository` and weather UI
contracts stable. This slice supports Open-Meteo only. `MET_NO` remains a
future provider.

## Scope

- Add a Ktor HTTP client with kotlinx.serialization JSON support.
- Request seven days of current, hourly, and daily forecast data.
- Map Open-Meteo responses into the existing weather domain models.
- Use the real repository in the production `WeatherViewModel.Factory`.
- Preserve `MockWeatherRepository` for deterministic tests and previews.
- Surface network, HTTP, timeout, and malformed-response failures through the
  existing `WeatherUiState.error` path.
- Add focused API and repository tests without changing the screen contract.

The following remain out of scope: MET Norway support, location CRUD, GPS,
reverse geocoding, maps, notifications, forecast caching, and settings UI
changes.

## Architecture

The existing boundary remains unchanged:

```text
WeatherViewModel
    -> WeatherRepository
        -> OpenMeteoWeatherRepository
            -> OpenMeteoApi
                -> Ktor HttpClient
```

`OpenMeteoApi` owns the HTTP request and typed transport DTOs. It does not
know about `WeatherForecast` or UI state. `OpenMeteoWeatherRepository` owns
response validation and mapping into `WeatherForecast`, `CurrentWeather`,
`HourForecast`, and `DayForecast`.

Production wiring creates one configured client and repository through the
weather ViewModel factory. Tests inject either a Ktor `MockEngine` API or the
existing fake/mock repository. The UI and `WeatherViewModel` public methods
remain unchanged.

## Open-Meteo Request

Use `https://api.open-meteo.com/v1/forecast` with:

- `latitude` and `longitude` from `WeatherLocation`.
- `timezone=auto` so daily boundaries and sunrise/sunset match the location.
- `forecast_days=7`.
- `current=temperature_2m,relative_humidity_2m,apparent_temperature,dew_point_2m,precipitation,weather_code,is_day`.
- `hourly=temperature_2m,relative_humidity_2m,dew_point_2m,apparent_temperature,precipitation,precipitation_probability,weather_code,is_day`.
- `daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,sunrise,sunset,moon_phase`.
- `temperature_unit=celsius`, `wind_speed_unit=kmh`, and
  `precipitation_unit=mm` for stable domain units.

Transport timestamps are decoded as strings. The response timezone is used
to convert local ISO date-times to `kotlinx.datetime.Instant`; daily dates
are decoded as local dates. The response's timezone must be present and
valid when local date-times require it.

## Mapping

Open-Meteo WMO weather codes map as follows:

- `0` -> `CLEAR`.
- `1` -> `MOSTLY_CLEAR`.
- `2` -> `PARTLY_CLOUDY`.
- `3` -> `CLOUDY`.
- `45`, `48` -> `FOG`.
- `51..57` -> `DRIZZLE`.
- `61..67`, `80..82` -> `RAIN`.
- `71..77`, `85..86` -> `SNOW`.
- `95`, `96`, `99` -> `THUNDERSTORM`.
- Any other code -> `UNKNOWN`.

`is_day` values of `1` and `0` map to `true` and `false`. Optional API
values map to nullable domain fields. Hourly and daily arrays must contain
all required fields with matching lengths; missing, null, or mismatched
required values cause a controlled repository error instead of a partial
forecast. The domain `fetchedAt` is set when the repository completes a
successful mapping.

## Failure Handling

Configure Ktor with JSON content negotiation, `expectSuccess = false`, and a
15-second request timeout. Non-2xx responses, transport failures, timeouts,
invalid JSON, invalid timestamps, and incomplete payloads become a concise
weather repository error. The existing `WeatherViewModel` catches the
failure, keeps the prior forecast when refreshing, and exposes the message in
`WeatherUiState.error`.

No retry, cache fallback, or background refresh is added in this slice. Those
behaviors belong to the later caching/network reliability work.

## Dependencies and Wiring

- Add Ktor client core and OkHttp engine.
- Add Ktor content negotiation and kotlinx.serialization JSON support.
- Apply the Kotlin serialization plugin and add its version catalog aliases.
- Add Android `INTERNET` permission.
- Keep one configured production `HttpClient` in an application-scoped weather
  container; the ViewModel must not create or close a client per instance.
- Make the API base URL and client construction explicit so tests never make
  real network calls.

## Testing

### API tests

Use Ktor `MockEngine` to verify:

- the forecast path and required query parameters;
- successful JSON decoding;
- non-2xx responses becoming controlled failures;
- malformed JSON or transport failures being surfaced.

### Repository tests

Use a representative Open-Meteo fixture to verify:

- current, hourly, and daily mapping;
- timezone-aware timestamps and daily dates;
- every weather-code mapping group and unknown fallback;
- optional values;
- incomplete or mismatched arrays being rejected.

### ViewModel and UI tests

Keep existing ViewModel tests on injected fake repositories. Add or update
the factory test so production construction selects the real repository
without performing a network request. Existing Compose screen tests remain
offline and unchanged except for any required factory injection.

## Acceptance Criteria

- Production weather loading uses Open-Meteo rather than
  `MockWeatherRepository`.
- All existing tests remain deterministic and do not access the network.
- `WeatherRepository` and `WeatherUiState` public contracts remain stable.
- Successful responses render current, hourly, and daily forecast data.
- Network and malformed-response failures reach the existing error UI.
- `testDebugUnitTest`, `assembleDebug`, and `lintDebug` pass.
