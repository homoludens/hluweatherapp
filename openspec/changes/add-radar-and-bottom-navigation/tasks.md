## 1. Radar provider and data

- [x] 1.1 [low] Verify current LibreWXR public metadata, documented tile URL/options, caching, permitted usage and provider/upstream attribution; record sources and any blocker, and verify the documented endpoint and sample response before integration.
- [x] 1.2 [low] Implement metadata parsing and documented tile URL construction using the existing network stack; run focused tests for dynamic hosts/paths, timestamps, observed/forecast frames, empty and malformed responses.
- [x] 1.3 [high] Implement lifecycle-cancellable metadata refresh, bounded transient exponential retry and cached fallback; run focused coroutine tests covering cancellation, refresh, recovery, stale cache and failure without provider substitution.
- [ ] 1.4 [low] Add radar visibility to existing DataStore settings with enabled default and preserve unrelated preferences; run focused persistence and migration/default tests.

## 2. Radar map and controls

- [ ] 2.1 [high] Integrate radar raster sources/layers into the existing main WeatherMap with stable identities, efficient frame changes and style reload handling, without affecting picker maps; run focused map tests for layer ordering, alignment, duplicate prevention and reload/frame-change races.
- [ ] 2.2 [low] Implement selected frame, play/pause, looping, observed/forecast classification, local-time labels and visibility-scoped playback; run focused state tests including missing forecasts, refresh reconciliation, 500–800 ms cadence and leaving Map.
- [ ] 2.3 [low] Add compact Material 3 radar toggle, layer/opacity menu, timeline, provider-required attribution and unobtrusive loading/error states; run focused UI tests for controls, opacity, visibility, accessibility and theme readability.
- [ ] 2.4 [low] Remove the Map weather card and expand the map while retaining the existing header, location functions and route shortcut for this phase; run focused WeatherScreen tests for map layout, route and unaffected Hourly/Daily content.
- [ ] 2.5 [low] Verify the radar phase before navigation work: test Serbia coverage when available, pan/zoom, playback, style reload, GPS, route shortcut, missing tiles/service and both themes; record device/provider limitations and test results.

## 3. Four-tab navigation (after radar phase)

- [ ] 3.1 [high] Refactor the shared main-screen shell and navigation coordination to reuse existing weather and route owners, support four destinations and sensible single-top/back/state restoration; run focused navigation tests for switching, settings return, back stack, retained state and no unnecessary refresh.
- [ ] 3.2 [low] Replace top tabs and subtitle with a compact gradient title/settings header and themed Material 3 four-item bottom bar; run UI tests for labels, selected indicator, default Hourly, themes and inset handling.
- [ ] 3.3 [low] Integrate the existing Route screen as a top-level destination and remove redundant Hourly/Map route shortcuts while keeping radar and route behavior; run focused route and map navigation/UI tests.
- [ ] 3.4 [low] Adjust main-screen content and insets for compact header and bottom bar without redesigning forecasts or route planner; verify small-screen layouts, forecast scrolling, radar controls and route planner in light and dark themes.

## 4. Final verification

- [ ] 4.1 [low] Run relevant broader automated tests and the Google debug build with the configured Android SDK; record commands, results and any unavailable checks.
- [ ] 4.2 [low] Perform available device/emulator end-to-end checks for all four tabs, radar, location, route, settings, lifecycle and offline behavior; document changed files, verified provider endpoints/tile format/attribution, limitations and remaining issues.
