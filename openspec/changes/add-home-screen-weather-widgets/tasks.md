## Model routing

For this change, `[low]` tasks use Luna and `[high]` tasks use Terra, including each task's verification. For task 2.4, the user confirmed the exact low model/variant as `openai/gpt-6-luna-fast#max`. Terra is the user's explicit override of the default high-complexity model in `AGENTS.md`.

High-complexity tasks are limited to interrupted-refresh recovery, concurrent request coordination, worker failure/cancellation transitions, reactive reconciliation without feedback loops, and shared lifecycle scheduling across receivers. The remaining tasks have defined implementation and verification approaches.

## 1. Backwards-compatible cached weather and passive location access

- [x] 1.1 [low] Add `WeatherRepository.getLastCachedForecast` and implement matching stale-cache access in `CachingWeatherRepository` without changing the existing fresh-cache API; verify `CachingWeatherRepositoryTest` covers the one-hour boundary, old cache, zero network calls on reads, provider/location/altitude mismatch, and cancellation.
- [x] 1.2 [low] Expose distinct read-only cache-change observation through the shared cache/repository without a second forecast store or LRU write loop; verify focused cache tests cover put, clear, unrelated preference writes, and stable observation after a cache read.
- [x] 1.3 [low] Add passive durable last-known-current-location access, persisting valid fixes already supplied to `setCurrentLocation` without restoring the main app's active fix; verify focused location tests cover restart, absent/corrupt/invalid fixes, saved selection, and unchanged `activeLocation` behavior.

## 2. Shared visual resources and pure widget presentation

- [x] 2.1 [low] Add a stable compatible Glance app-widget dependency in `gradle/libs.versions.toml` and `app/build.gradle.kts`, reusing existing WorkManager/DataStore dependencies; verify dependency resolution and `:app:compileGoogleDebugKotlin` succeed without adding provider clients or a DI framework.
- [x] 2.2 [low] Extract the existing weather-vector/tint selection and expose side-effect-free existing theme tokens for both app and widget use; verify focused tests cover every `WeatherCondition`, day/night selection, both themes, and unchanged main-app icon output.
- [x] 2.3 [low] Implement a generic bounded vector-to-bitmap adapter for shared weather and Material utility icons without an Activity or another condition mapping; verify deterministic rendering tests produce readable, correctly tinted icons at current/hourly sizes in both themes.
- [x] 2.4 [low] Implement presentation state and forecast mapping using shared labels and temperature formatting; verify widget adapter tests cover Celsius/Fahrenheit rounding, null apparent temperature, missing versus zero precipitation probability, spoken units, and stale timestamps.
- [x] 2.5 [low] Implement future-hour selection and location-zone date/time formatting with an injected clock; verify tests cover unsorted hours, midnight, differing device/location zones, daylight-saving transitions, invalid-zone fallback, fewer than five hours, and fully expired cache.
- [x] 2.6 [low] Implement a pure responsive size/font policy for small priorities and medium three/four/five-column branches; verify table-driven tests cover width and height minimums, larger font scaling, long signed temperatures, and three-digit Fahrenheit values.

## 3. Widget request resolution and refresh coordination

- [x] 3.1 [low] Implement `WeatherWidgetRepository` resolution using canonical location mode, saved selection, live/passive Track Me fixes, and saved-location fallback; verify fake-repository tests cover all fallbacks, renamed/deleted locations, no locations, and no calls to device location or geocoding.
- [x] 3.2 [low] Implement cache-first state loading and honest no-location/no-weather states with provider/location identity validation; verify tests can render persisted old weather after repository/process recreation while the fake network is blocked or failing.
- [x] 3.3 [high] Implement small identity-keyed refresh metadata with bounded loading lifetime and optional immediate live-result handoff, not duplicated durable forecasts; verify tests cover preserved timestamps, stale repository results, persistence failure, and recovery from interrupted refresh metadata.
- [x] 3.4 [high] Implement a coordinator that serializes widget refreshes, coalesces repeated same-request taps, and rechecks the latest identity before publishing; verify coroutine tests cover simultaneous periodic/manual requests, provider/location changes during a suspended request, and follow-up refresh for the latest uncached selection.

## 4. WorkManager updates and change reconciliation

- [ ] 4.1 [low] Implement one unique connected-network 60-minute periodic schedule shared across both widget sizes, plus unique coalesced immediate work; verify WorkManager tests inspect interval, constraints, work names, repeated enqueue behavior, and no schedule when no widgets exist.
- [ ] 4.2 [high] Implement the widget CoroutineWorker using application-owned repositories, preserving displayed data through loading, success, stale fallback, failure, and cancellation; verify worker tests cover each outcome, update-all delivery, bounded retries, and no corruption from obsolete request completion.
- [ ] 4.3 [low] Implement the Glance refresh callback that enqueues work without opening an Activity or doing network work in the callback; verify callback/action tests confirm one coalesced request and that loading state clears on completion or failure.
- [ ] 4.4 [high] Add installed-widget-guarded application reconciliation with separate request-change and presentation/cache-change paths; verify tests show provider/location changes reconcile promptly, units/theme/renames re-render from cache, foreground cache writes do not refetch, and unrelated/status/LRU writes cause no recursive work.
- [ ] 4.5 [low] Integrate worker construction and startup without changing existing notification factory/scheduler behavior; verify focused `HluWeatherApplicationTest`, notification worker-factory tests, and widget worker creation tests pass with separate widget work identities.

## 5. Glance layouts, interactions, and launcher registration

- [ ] 5.1 [low] Implement shared rounded card, header, refresh target, weather-icon, current-weather, and status components using existing theme tokens and accessible descriptions; verify translated rendering tests for light/dark, empty, refreshing, and stale states show readable text without emoji or duplicate spoken icons.
- [ ] 5.2 [low] Implement `SmallWeatherWidget` with responsive location/icon/temperature priorities and optional condition/feels-like rows, with no hourly strip; verify production-component renders at minimum, compact, and normal sizes preserve priorities and ellipsize a long location without hiding refresh.
- [ ] 5.3 [low] Implement `MediumWeatherWidget` with the current block near one third width, conditional update timestamp, vertical rules, and three-to-five hourly columns with accented droplet/probability values; verify production-component renders cover all column branches, missing probabilities, no future hours, and no horizontal overflow.
- [ ] 5.4 [low] Wire card/location `actionStartActivity` to widget-specific main-weather entry and refresh to its independent callback; verify interaction tests cover cold launch, an app already on Settings or another screen, refresh without Activity launch, and unchanged ordinary app launch behavior.
- [ ] 5.5 [low] Add both receivers, manifest entries, descriptive strings, provider XML, API-compatible rounded backgrounds, and picker previews for 2x2 and 4x2 defaults; verify manifest/resource tests confirm resizing metadata, no configuration activity or new permissions, and XML periodic updates disabled in favor of WorkManager.
- [ ] 5.6 [high] Reconcile receiver add/remove/disable/restore events across all installed instances; verify lifecycle tests confirm the first widget schedules work, additional widgets share it, removal of one size preserves it for the other, and removal of the last instance cancels periodic and pending widget work.
- [ ] 5.7 [low] Ensure System theme changes re-render both widget sizes even when the foreground app is closed, while explicit app light/dark preferences take precedence; verify configuration-change rendering tests for both explicit and System settings.

## 6. Reference fixtures and cross-feature verification

- [ ] 6.1 [low] Add deterministic production-rendered examples for light Zaječar 23/24, dark partly-cloudy Trieste 18/17, and medium Zaječar with reference hours 22:00–02:00, temperatures 20/18/17/16/15, and probabilities 0/0/10/20/60; verify generated previews or test-rendered artifacts use shared labels/icons and require no live services.
- [ ] 6.2 [low] Add edge-case render coverage for long names, missing data, signed/extreme temperatures, both units/themes, constrained dimensions, larger font scaling, cached offline states, and failed refresh; verify the focused widget test package passes and its size branches never overlap or clip primary content.
- [ ] 6.3 [low] Run cross-feature regression tests after integrating widgets, including repository/cache, passive location, app startup, existing weather UI, and notification workers; verify `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testGoogleDebugUnitTest` passes, reporting any unrelated pre-existing failure separately.
- [ ] 6.4 [low] Perform final Google debug compilation and packaging only; verify `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:assembleGoogleDebug` succeeds without release build or signing.
- [ ] 6.5 [low] Add a concise widget usage and launcher QA checklist covering picker entries, resize, font scaling, TalkBack, theme, manual refresh, offline cache, Track Me fallback without GPS, reboot/process death, and removal of the last widget; verify on API 28 and a modern Android launcher when available, and record screenshots/results plus any runtime checks that could not be performed without claiming them as tested.
