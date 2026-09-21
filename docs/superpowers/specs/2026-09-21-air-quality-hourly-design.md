# Air Quality In Hourly Weather

## Goal

Add Open-Meteo air-quality data for European AQI, PM2.5, and PM10. Users can
enable each value as an hourly-table column. When at least one air-quality
column is enabled, the current-weather card replaces its "Feels like" metric
with the highest-priority enabled air-quality metric.

This is an additive feature. Existing weather providers, weather values,
navigation, location handling, and default hourly-table layout remain
unchanged.

## Decisions

- Air-quality columns are disabled by default.
- Card selection priority is `EUROPEAN_AQI`, then `PM2_5`, then `PM10`.
- If the selected card value is unavailable, show its label with `—` rather
  than falling back to "Feels like".
- Open-Meteo air-quality request failures are non-fatal. The normal weather
  forecast still loads, and air-quality values are null.
- MET Norway forecasts keep null air-quality values because this feature uses
  the Open-Meteo air-quality service.
- Existing cached forecasts remain readable; cached entries without the new
  fields expose null values.

## Network Layer

Add a dedicated `OpenMeteoAirQualityApi` interface and Ktor implementation.
The implementation calls:

`https://air-quality-api.open-meteo.com/v1/air-quality`

with the location coordinates, `timezone=auto`, `forecast_days=7`, and:

- `current=european_aqi,pm10,pm2_5`
- `hourly=european_aqi,pm10,pm2_5`

Use serializable air-quality DTOs for the response current block and hourly
arrays. The repository maps timestamps to `Instant` values and joins hourly
air-quality values to existing weather hours by timestamp, not by an
assumed array position.

The application creates the air-quality API with the existing HTTP client and
passes it to `OpenMeteoWeatherRepository`. The weather repository first loads
the normal weather forecast, then attempts to load and merge air quality. It
rethrows cancellation but converts other air-quality failures into an
unchanged weather forecast with null air-quality fields.

## Weather Models and Cache

Extend `CurrentWeather` and `HourForecast` with nullable fields:

- `europeanAqi: Double?`
- `pm10: Double?`
- `pm2_5: Double?`

The fields are appended with null defaults so existing model construction and
providers remain compatible.

Extend the forecast cache `CurrentDto` and `HourDto` with nullable fields and
null defaults. Map them in both directions. `Json { ignoreUnknownKeys = true }`
and nullable defaults preserve old cache entries.

## Settings

Extend `HourlyTableColumn` with:

- `EUROPEAN_AQI`
- `PM2_5`
- `PM10`

The existing `PersistedSettings`, `SettingsUiState`, `DataStoreSettingsRepository`,
and `SettingsViewModel` generic column persistence flow will be reused. Add
settings titles and subtitles and stable test tags. No new preference key is
needed. Existing serialized column names remain valid and the new columns are
absent from the default set.

## Hourly Table

Render enabled air-quality columns through the existing `ForecastColumnHeader`
and `ForecastRow` paths. Headers are `AQI`, `PM2.5`, and `PM10`. Values use:

- rounded integer output for European AQI;
- compact decimal output with `µg/m³` for PM2.5 and PM10;
- `-` for null table values.

Each column receives an existing-style weight so the table remains a single
responsive row and does not introduce a second scrolling mechanism.

## Current Weather Card

Pass the existing `hourlyTableColumns` set through `WeatherScreen` into both
current-card call sites. Add the same optional parameter to
`CurrentWeatherCard` so previews and existing direct callers retain the
current behavior by default.

The card resolves the selected metric in the fixed priority order. With no
air-quality column enabled, the third metric remains "Feels like". With one
enabled, it displays that metric's label and formatted value. A null selected
value displays `—`.

## Error Handling

- HTTP errors, decoding errors, invalid timestamps, and mismatched air-quality
  array lengths do not prevent normal weather rendering.
- Cancellation from either request remains cancellable.
- Missing optional air-quality fields are represented as null.
- A weather cache hit without air-quality fields remains usable.

## Testing

Add or update tests for:

- air-quality request URL, parameters, successful decoding, HTTP errors, and
  malformed payloads;
- repository mapping, timestamp joining, optional values, non-fatal air
  quality failures, and cancellation;
- cache round-tripping and compatibility with entries missing new fields;
- settings labels, tags, persistence, and toggle callbacks;
- hourly table headers and formatted values;
- card precedence, no-selection fallback, and unavailable selected values.

Run the focused tests during implementation, then:

`./gradlew :app:testGoogleDebugUnitTest :app:assembleGoogleDebug`
