## 1. Forecast layout

- [x] 1.1 [low] Standardize Hourly and Daily page-level margins and peer-section spacing at 16 dp, remove the Hourly card's negative offset, and align the day strip and table without changing necessary internal spacing; run focused forecast UI tests measuring the initial card and section bounds, Daily edge/card gaps, sticky scrolling/day selection, small-screen reachability and single application of insets.

## 2. Map information and credits

- [x] 2.1 [low] Make the main Map's basemap information initially collapsed with an accessible open/close control, preserving style-provided links, MapLibre logo and other map controls without changing other `WeatherMap` callers; run focused overlay/map UI tests for initial state, opening and closing, credits, controls and unaffected picker maps.
- [ ] 2.2 [low] Check current LibreWXR and applicable upstream attribution terms, remove the sentence beginning “Upstream may include…” from the radar display, and retain required credits in a concise visible presentation; update and run focused radar UI tests for sentence absence, credits, timeline, controls and error states, recording any attribution blocker rather than dropping required credit.

## 3. Route result navigation

- [ ] 3.1 [high] Add a one-shot scroll to the stable route-map item for the current explicit “Show trip weather” request, including recalculation and valid routes with weather errors, while clearing pending requests on failure, cancellation, supersession or leaving the screen; run focused route screen/state tests for immediate and delayed completion, equal results, invalid/routing failures, stale results, manual scrolling, sample/departure changes, weather retry and screen recreation.

## 4. Integration verification

- [ ] 4.1 [low] Run relevant broader Google-debug automated tests and the Google debug build using the configured Android SDK; when a device or emulator is available, check forecast spacing, map information/credits and route scrolling on a small screen and in both themes, recording commands, outcomes and any unavailable checks as not run.
