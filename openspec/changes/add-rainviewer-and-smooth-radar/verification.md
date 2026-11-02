# Verification

## Task 1.1 — provider-neutral models and LibreWXR migration

- Passed focused Google debug unit tests with `ANDROID_HOME=/home/homoludens/Android/Sdk` and `ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk`: `:app:testGoogleDebugUnitTest` for `RadarModelsTest`, `LibreWxrRadarApiTest`, `RadarMetadataCacheTest`, `RadarRepositoryTest`, `RadarPlaybackViewModelTest`, `RadarMapOverlayTest`, and `WeatherMapRadarTest` — 62 tests, 0 failures, 0 errors.
- Passed `git diff --check` with no output.

## Task 1.2 — RainViewer metadata adapter

- Official references reviewed 2026-11-02:
  - [RainViewer API and terms](https://www.rainviewer.com/api.html): public API use is limited to personal and educational use; RainViewer asks for linked source attribution. This is not evidence or authorization for public distribution.
  - [Weather Maps API](https://www.rainviewer.com/api/weather-maps-api.html): documents `https://api.rainviewer.com/public/weather-maps.json`, past-frame `host`/`path` metadata, past-only frame data, tile URL parameters, and supported 256/512 tile sizes.
  - [Color schemes](https://www.rainviewer.com/api/color-schemes.html): documents color 2 as Universal Blue.
  - [API transition summary](https://www.rainviewer.com/api/transition-faq.html): states nowcast and other color schemes were discontinued January 1, 2026, native maximum zoom is 7, and the service limit is 100 requests per IP per minute.
- Distribution eligibility remains unresolved; no permission beyond the terms above is assumed. The RainViewer request budget is not implemented by this adapter and remains for the later shared-loader task.
- Passed focused Google debug unit tests with `ANDROID_HOME=/home/homoludens/Android/Sdk` and `ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk`: `:app:testGoogleDebugUnitTest` for `RainViewerRadarApiTest`, `LibreWxrRadarApiTest`, `RadarModelsTest`, and `WeatherMapRadarTest` — 30 tests, 0 failures, 0 errors. These cover past-only/no-nowcast parsing, empty and malformed metadata, invalid and negative timestamps, HTTPS/path validation, HTTP failures, exact tile options/zoom limits, and LibreWXR forecast preservation.

## Task 1.3 — provider-scoped repository and cache

- Passed focused Google debug unit tests with `ANDROID_HOME=/home/homoludens/Android/Sdk` and `ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk`: `:app:testGoogleDebugUnitTest` for `RadarMetadataCacheTest`, `RadarRepositoryTest`, `HluWeatherApplicationTest`, `LibreWxrRadarApiTest`, and `RainViewerRadarApiTest` — 58 tests, 0 failures, 0 errors.
- Cache tests cover validated legacy LibreWXR migration to the new scoped envelope without deleting the old key, distinct provider keys/isolation, invalid timestamps/hosts/paths, corrupt and unsupported-schema entries, envelope/frame provider mismatches, empty frames, and wrong preference types.
- Repository/application tests cover freshness, cached fallback after failures, bounded transient retries/timeouts, cancellation when the last observer leaves, a RainViewer-specific budget injection hook per request attempt, and lazy wiring for both providers without collecting the unselected repository.
- Passed `git diff --check` with no output.
- The application currently injects `RadarRequestBudget.None`; task 3.2 must provide and share the concrete RainViewer metadata/tile budget. No rate limiter was implemented here.

## Task 2.1 — persisted radar-provider selection

- Passed focused Google debug unit tests with `ANDROID_HOME=/home/homoludens/Android/Sdk` and `ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk`: `:app:testGoogleDebugUnitTest` for `SettingsRepositoryTest` and `SettingsViewModelTest` — 47 tests, 0 failures, 0 errors, 0 skipped.
- Repository/view-model coverage verifies RainViewer persistence across repository reload, LibreWXR fallback for missing/invalid/wrongly typed values, preservation of forecast provider/radar visibility and unrelated preferences, an early provider selection surviving settings hydration and being saved, and the setter's independence from visibility and forecast-provider state.
- Passed `git diff --check` with no output.

## Task 2.2 — Settings radar-provider selector

- Passed focused Google debug Compose tests with `ANDROID_HOME=/home/homoludens/Android/Sdk` and `ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk`: `:app:testGoogleDebugUnitTest` for `SettingsScreenTest` and `PrimaryFlowsAccessibilityTest` — 27 tests, 0 failures, 0 errors, 0 skipped.
- `SettingsScreenTest` verifies separate Radar Provider labels, LibreWXR/RainViewer choices and tags, selected radio semantics, one callback per choice change, and independence from forecast-provider changes. Accessibility tests verify existing Settings touch targets and layout remain usable after adding the section.
- Passed `git diff --check` with no output.

## Task 2.3 — shared map-layer provider selection

- Passed focused Google debug Compose tests with `ANDROID_HOME=/home/homoludens/Android/Sdk` and `ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk`: `:app:testGoogleDebugUnitTest --tests 'net.droopia.hluweather.navigation.HluNavHostTest' --tests 'net.droopia.hluweather.ui.map.RadarMapOverlayTest' --tests 'net.droopia.hluweather.ui.weather.WeatherScreenTest'` — 57 tests, 0 failures, 0 errors, 0 skipped.
- `HluNavHostTest` verifies map-layer selection updates the shared persisted preference and Settings selection is reflected back in map layers while radar remains disabled. `RadarMapOverlayTest` verifies the provider choice works while disabled, invokes one callback, and preserves visibility and opacity. `WeatherScreenTest` verifies changing the provider while disabled does not enable radar or start repository collection and leaves the basemap and location controls present.
- Passed `git diff --check` with no output.
- Live radar-data provider switching is intentionally deferred to tasks 4.2 and 5.1.

## Task 3.1 — timeline neighborhood planner

- Passed focused Google debug unit tests with `ANDROID_HOME=/home/homoludens/Android/Sdk` and `ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk`: `:app:testGoogleDebugUnitTest --tests 'net.droopia.hluweather.data.model.RadarTimelineNeighborhoodPlannerTest'` — 7 tests, 0 failures, 0 errors.
- Tests cover empty, missing, single-frame, short, endpoint, and middle requests; chronological index behavior with irregular timestamps; duplicate entries and provider identity collisions; and replanning after a metadata-window refresh.
- Passed `git diff --check` with no output.
