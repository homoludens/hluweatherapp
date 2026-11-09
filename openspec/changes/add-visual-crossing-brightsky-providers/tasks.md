## 1. Field Semantics and Existing Provider

- [x] 1.1 [low] Verify the design's four-provider matrix against current documentation for every `HourlyTableColumn` and existing current/daily field; record supported, unavailable, restricted, interval, unit, and AQI-scale cases, and add representative offline fixtures for the planned mappings.
- [x] 1.2 [high] Implement an off-main-thread, cached coordinate-to-IANA-zone resolver for Bright Sky and MET Norway using a pinned Android-compatible offline dataset with required notices and an explicitly labeled UTC fallback; verify different device/location zones, DST gap and repeated hours, midnight, unknown zones, and saved forecast round-trips with focused tests.
- [x] 1.3 [low] Request MET Norway's complete product and map its available one-hour precipitation probabilities to the matching preceding-hour row without inventing six-hour probabilities; verify request path, zero/null, region coverage, sparse timestamps, interval boundary, unit conversion, and widget-facing normalized values in focused tests.

## 2. New Forecast Sources

- [x] 2.1 [low] Implement Visual Crossing's keyless metric Timeline request and current, hourly, daily, time, condition, and compatible weather-field mapping; verify gzip negotiation/decoding, query scope, units, daylight-saving records, missing fields, HTTP errors, and no leaked request details with mocked API and mapper tests.
- [x] 2.2 [low] Add Visual Crossing's accessible PM and European AQI fields with explicit categorical-versus-numeric AQI presentation, and isolate denied optional products without mapping reference ET₀ as actual evapotranspiration; verify scale labels, matching time, cache persistence, omitted values, and 401/403/429 responses do not destroy core weather.
- [x] 2.3 [low] Implement Bright Sky weather/current API clients with coordinate queries, DWD units, its 50 km station limit, bounded forecast window, error handling, and cancellation; verify request shape, observation and forecast source handling, sparse/out-of-range stations, and mocked failures.
- [x] 2.4 [low] Normalize Bright Sky's current and hourly records and derive honest local-day summaries and moon phase without fabricating partial days or missing fields; verify wind, dew point, humidity, precipitation probability, conditions, observation fallback, interval alignment, DST, and absent data using deterministic fixtures.

## 3. Selection, Cache, and Lifecycle

- [x] 3.1 [low] Register both new providers in settings, persistent preferences, application routing, cache identity, and forecast-based background consumers without changing radar or route weather; verify selection, restart, old preferences, invalid-value fallback, and unrelated settings with focused repository/settings tests.
- [x] 3.2 [high] Preserve provider/location identity across in-flight forecast changes, stale fallback, widgets, and notifications so late results and credits cannot overwrite the current selection; verify delayed success, failure, cancellation, provider/location switches, and cache isolation with deterministic concurrency tests.
- [x] 3.3 [low] Extend forecast serialization compatibly for new providers and AQI scales while preserving old cached numeric entries; verify pre-change fixtures, new-provider round trips, corrupt/unknown fields, process restart, and stale matching-provider fallback.

## 4. Attribution and Presentation

- [ ] 4.1 [low] Display required Visual Crossing and Bright Sky/DWD weather credits near forecast data, with a practical source link and correct cached-provider attribution; update credits documentation and verify relevant foreground views, provider switches, accessibility, and attribution links.
- [ ] 4.2 [low] Add visible, legible provider attribution to applicable widgets and forecast notifications without breaking existing widget sizing, header priorities, or actions; verify both widget sizes, minimum dimensions, font scaling, cached/stale state, notifications, and provider-specific accessibility.

## 5. End-to-End Verification

- [ ] 5.1 [low] Run the relevant provider, cache, settings, forecast, widget, and notification tests and final Google debug build, then exercise both providers and MET probability on the `small_phone` emulator with offline/limited-coverage states; record exact results and explicitly mark any unavailable live or device checks as not run.
