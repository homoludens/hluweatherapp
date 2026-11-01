# Device and end-to-end verification (task 4.2)

Checked 2026-11-01. **Blocked / pending:** no device or AVD is available. Device E2E checks below are **NOT RUN (unavailable)**, not passed. Task 4.2 remains unchecked in `tasks.md`. No device was created, downloaded, or launched, and no live-provider request was made for this task.

## Device enumeration

- `adb devices -l` listed no devices. The PATH adb client (version 36) reported a mismatch with the running server (version 41) and restarted it; the listing remained empty.
- `/home/homoludens/Android/Sdk/platform-tools/adb devices -l` also listed no devices. The SDK adb client (version 41) reported the inverse server/client mismatch and restarted the server; the listing remained empty.
- `emulator -list-avds` was not on PATH. `/home/homoludens/Android/Sdk/emulator/emulator -list-avds` exited successfully with no AVD names.

## Existing automated evidence (not device E2E)

These results are carried forward from the prior verification records; they were not rerun for task 4.2.

- `verification-4.1.md`: `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testGoogleDebugUnitTest` succeeded: 82 suites, 736 tests, 0 failures/errors/skips. `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:assembleGoogleDebug` also succeeded. This is automated verification, not proof of device behavior.
- `verification-2.5.md`: the focused radar/map/GPS/route unit/Compose test selection passed 32 tests (0 failures/errors/skips). That record contains the exact command and selectors. It covers simulated map/radar state, mocked Track Me/GPS paths, and the then-existing map route callback; it does not establish rendered live tiles or real GPS behavior.
- The full 4.1 suite includes focused navigation/layout/settings tests such as `HluNavHostTest`, `MainScreenChromeTest`, `CompactMainScreenLayoutTest`, `MainActivityInsetsTest`, and settings tests. They exercise four-tab selection/default, route and settings navigation/back, saved state, compact layouts/insets, and radar/settings UI with test fixtures.
- `RadarRepositoryTest`, `RadarMapOverlayTest`, `RadarPlaybackViewModelTest`, and `SettingsViewModelTest` exercise cached metadata, retry/failure state, playback visibility/cancellation, attribution/status UI, and persisted radar preference with simulated repositories. `WeatherViewModelTest` and `LocationQuickSwitcherTest` cover mocked location/permission paths.

| Flow | Automated evidence above | Device E2E |
| --- | --- | --- |
| Hourly, Daily, Map, Route tabs | Navigation, selected state, default Hourly, back stack, layout and retention tests | **NOT RUN — unavailable** |
| Radar, gestures, playback and map rendering | Focused radar/map tests and compact UI tests; no live-map rendering assertion | **NOT RUN — unavailable** |
| Location and permissions | Mocked GPS/Track Me and permission-state tests only | **NOT RUN — unavailable** |
| Route planning | Route screen/navigation/ViewModel tests with test data | **NOT RUN — unavailable** |
| Settings and return navigation | Settings persistence/UI tests and navigation return tests | **NOT RUN — unavailable** |
| Lifecycle and restoration | Simulated playback visibility/cancellation and navigation recreation/state tests | **NOT RUN — unavailable** |
| Offline behavior | Simulated repository failures, retry/cache fallback, and unavailable-status UI; no airplane-mode/network outage test | **NOT RUN — unavailable** |

## Provider contract and attribution evidence

No endpoint was re-requested. Per `design.md` and the dated live checks in `verification-2.5.md` (2026-11-01):

- Metadata endpoint: `GET https://api.librewxr.net/public/weather-maps.json` returned HTTP 200, version `2.0`, with host `https://api.librewxr.net` and observed/nowcast frame paths.
- Documented tile URL form: `{host}{path}/{size}/{z}/{x}/{y}/{color}/{smooth}_{snow}.{ext}`. The recorded Serbia example was `https://api.librewxr.net/v2/radar/1793568000/256/7/71/46/2/1_1.png`, built for Belgrade (44.8176, 20.4633), XYZ `7/71/46`. It returned HTTP 200, `image/png`, 27,091 bytes, 256×256, with frame timestamp `1793568000` and public caching. The tile contained non-transparent pixels, but its Belgrade-center pixel was transparent; this is not evidence of precipitation there, full-country coverage, or rendered alignment.
- Required provider credit is **“Weather data via LibreWXR (librewxr.net)”**. The app’s attribution text also names possible upstream credits: EUMETNET OPERA, **“Precipitation data from NOAA Enterprise Rain Rate (RRQPE)”**, and **“DPC's Radar-DPC (Italy)”**. Applicable upstream credit must be retained; metadata does not map upstream sources to individual frames/tiles.

## Changed files and remaining limitations

Tracked change files were derived with `git diff --name-status origin/main...HEAD`; the new verification file is this task’s record and is not in that pre-existing diff. App implementation paths (status from that diff):

- `app/src/main/java/net/droopia/hluweather/`: `HluWeatherApplication.kt` (M); `data/cache/RadarMetadataCache.kt` (A); `data/network/LibreWxrRadarApi.kt`, `LibreWxrRadarModels.kt`, `LibreWxrRadarTileUrlBuilder.kt` (A); `data/repository/RadarRepository.kt` (A); `navigation/HluNavHost.kt` (M), `navigation/MainNavigation.kt` (A); `ui/app/MainScreenChrome.kt` (A); `ui/map/RadarMapOverlay.kt`, `RadarPlaybackViewModel.kt`, `WeatherMapRadar.kt` (A), `WeatherMap.kt` (M); `ui/preview/Previews.kt` (M); `ui/settings/SettingsRepository.kt`, `SettingsState.kt`, `SettingsViewModel.kt` (M); `ui/weather/DailyForecastList.kt`, `WeatherScreen.kt` (M), `WeatherHero.kt` (D); `ui/weatherroute/WeatherRouteScreen.kt`, `WeatherRouteViewModel.kt` (M).
- `app/src/test/java/net/droopia/hluweather/`: `data/cache/RadarMetadataCacheTest.kt`, `data/network/LibreWxrRadarApiTest.kt`, `data/repository/RadarRepositoryTest.kt` (A); `navigation/HluNavHostTest.kt` (M); `ui/accessibility/PrimaryFlowsAccessibilityTest.kt` (M); `ui/app/CompactMainScreenLayoutTest.kt`, `MainScreenChromeTest.kt` (A); `ui/map/RadarMapOverlayTest.kt`, `RadarPlaybackViewModelTest.kt`, `WeatherMapRadarTest.kt` (A); `ui/settings/SettingsRepositoryTest.kt`, `SettingsViewModelTest.kt` (M); `ui/weather/MainActivityInsetsTest.kt`, `WeatherScreenTest.kt` (M), `WeatherHeroTest.kt` (D); `ui/weatherroute/WeatherRouteViewModelTest.kt` (M).
- Tracked OpenSpec artifacts in the same diff: `openspec/changes/add-radar-and-bottom-navigation/.openspec.yaml`, `design.md`, `proposal.md`, `specs/main-bottom-navigation/spec.md`, `specs/weather-radar/spec.md`, `tasks.md`, `verification-2.5.md`, and `verification-4.1.md` (A).

Remaining issues: real-device tab/radar/location/route/settings/lifecycle/offline behavior remains unverified because no target exists. Provider metadata has no geographic coverage polygon/extent. MapLibre Compose 0.16.0 exposes no raster-tile URL failure or missing-coverage callback in the app’s integration, so individual tile failures/missing tiles cannot currently be detected or reported; a real tile outage and live raster alignment were not verified. The Serbia request proves delivery of one tile cell only, not complete Serbia coverage.

The supplied untracked `docs/Four-Tab Bottom Naviga.md` and `docs/radar/` were excluded from the diff summary and left untouched.
