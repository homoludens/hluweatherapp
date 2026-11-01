# Radar phase verification (task 2.5)

Checked 2026-11-01. This records available automated/provider evidence; no app implementation was changed.

## Automated verification

Command: `:app:testGoogleDebugUnitTest`, restricted to these radar/map/GPS/route cases:

```bash
ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testGoogleDebugUnitTest \
  --tests 'net.droopia.hluweather.ui.map.WeatherMapRadarTest' \
  --tests 'net.droopia.hluweather.ui.map.RadarPlaybackViewModelTest' \
  --tests 'net.droopia.hluweather.ui.map.RadarMapOverlayTest' \
  --tests 'net.droopia.hluweather.ui.map.WeatherMapTest.camera_center_only_animates_when_it_differs_from_the_map_target' \
  --tests 'net.droopia.hluweather.ui.map.WeatherMapTest.camera_idle_updates_are_only_reported_for_user_camera_moves' \
  --tests 'net.droopia.hluweather.ui.map.WeatherMapTest.camera_move_while_gesture_is_active_is_not_reported_as_idle' \
  --tests 'net.droopia.hluweather.ui.map.WeatherMapTest.marker_selection_reports_the_selected_location_and_recenter_runs_callback' \
  --tests 'net.droopia.hluweather.ui.map.WeatherMapTest.map_recenter_action_is_labeled_and_meets_touch_target' \
  --tests 'net.droopia.hluweather.ui.map.WeatherMapTest.selects_the_dark_open_free_map_style' \
  --tests 'net.droopia.hluweather.ui.weather.LocationQuickSwitcherTest.track_me_reports_a_callback_without_owning_location_permission' \
  --tests 'net.droopia.hluweather.ui.weather.WeatherViewModelTest.track_me_updates_the_display_but_refreshes_only_after_the_gate' \
  --tests 'net.droopia.hluweather.ui.weather.WeatherViewModelTest.track_me_routes_permission_and_disabled_statuses' \
  --tests 'net.droopia.hluweather.ui.weather.WeatherViewModelTest.reentering_track_me_requires_a_fresh_fix' \
  --tests 'net.droopia.hluweather.ui.weather.WeatherScreenTest.map_mode_hides_current_weather_card_and_expands_map_below_header' \
  --tests 'net.droopia.hluweather.ui.weather.WeatherScreenTest.map_mode_displays_weather_on_route_action'
```

Result: **32 tests passed; 0 failures, errors, or skips** (verified from `app/build/test-results/testGoogleDebugUnitTest/TEST-*.xml`). Coverage includes radar XYZ/source/layer identity and ordering, frame replacement/style reload races, playback/looping/visibility lifecycle, loading/no-frame/service-error states, radar controls and light/dark readability, gesture-camera callbacks/recenter, mocked Track Me/GPS state and permission paths, and the retained Map route shortcut.

## Public provider and Serbia tile check

- `GET https://api.librewxr.net/public/weather-maps.json` returned HTTP 200; response `Date` was Sun, 01 Nov 2026 21:22:14 GMT, `version` was `2.0`, and `host` was `https://api.librewxr.net`. It contained 12 observed (`radar.past`) and 6 nowcast frames. Latest observed was time `1793568000` (`2026-11-01 21:20 UTC`), path `/v2/radar/1793568000`. Metadata exposes no geographic coverage polygon/extent.
- The provider coverage guide (`https://github.com/JoshuaKimsey/LibreWXR/blob/main/docs/coverage.md`) describes observed RRQPE fill at all longitudes from 60°S–70°N, which includes Serbia; this is provider documentation, not per-frame metadata.
- Requested the documented current-frame tile for Belgrade (44.8176, 20.4633), XYZ z/x/y `7/71/46`: `https://api.librewxr.net/v2/radar/1793568000/256/7/71/46/2/1_1.png`. Result: HTTP 200, `image/png`, 27,091 bytes, 256×256, `X-Frame-Timestamp: 1793568000`, `Cache-Control: public, max-age=300`. The tile had non-transparent pixels; its exact Belgrade center pixel was transparent. This confirms successful tile delivery for a cell containing central Serbia, not precipitation at Belgrade, full-country visual coverage, or rendered map alignment. No missing geographic coverage was inferred from a blank pixel/tile.

## Device and MapLibre limits / not-run checks

- `adb devices -l` listed no attached devices; the SDK `emulator -list-avds` returned no AVDs. No device/emulator interaction was run. The adb client/server versions differed (36 vs 41) during enumeration and restarted the adb server; enumeration still showed no device.
- Real-map pan/zoom and raster alignment, live style reload, GPS permission/current-fix behavior, route shortcut interaction, and theme rendering were **not device-tested**. Automated tests cover map-style source/layer reconciliation, gesture callback logic, recenter affordance, mocked GPS/Track Me and the route callback, and both radar-control themes only.
- Missing metadata/service behavior is covered with simulated repository states by `RadarMapOverlayTest.status_and_attribution_are_nonblocking_and_required_credits_are_visible`; a real outage was not induced. Missing raster-tile failure behavior remains **unverified**: installed MapLibre Compose is 0.16.0, and the app does not configure a resource provider/interceptor. Its `MapEvent` API has no event for failures from MapLibre-owned raster HTTP requests (style-load and GeoJSON source-data failures are distinct). `RenderStats.Mode.Partial` only says a frame rendered before all required resources loaded and was not treated as a tile-failure signal. No tile-failure indication or missing-tile result is claimed.
- No broad suite, connected-device test, or Google debug build was run; those remain for task 4.1/4.2.
