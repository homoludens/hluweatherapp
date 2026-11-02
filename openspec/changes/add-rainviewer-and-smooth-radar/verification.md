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
