# Plan 2.1 Final Fix Report

## Changed Files

- `app/src/main/java/net/droopia/hluweather/data/repository/LocationRepository.kt`
- `app/src/main/java/net/droopia/hluweather/ui/settings/SettingsRepository.kt`
- `app/src/main/java/net/droopia/hluweather/ui/settings/SettingsViewModel.kt`
- `app/src/main/java/net/droopia/hluweather/ui/weather/WeatherViewModel.kt`
- `app/src/test/java/net/droopia/hluweather/data/repository/LocationRepositoryTest.kt`
- `app/src/test/java/net/droopia/hluweather/ui/settings/SettingsRepositoryTest.kt`
- `app/src/test/java/net/droopia/hluweather/ui/settings/SettingsViewModelTest.kt`
- `app/src/test/java/net/droopia/hluweather/ui/weather/WeatherScreenTest.kt`
- `app/src/test/java/net/droopia/hluweather/ui/weather/WeatherViewModelTest.kt`

## Ownership Decision

`LocationRepository` is now the sole writer of saved selection and location mode. `DataStoreSettingsRepository.save` no longer writes `settings.selected_location_id` or `settings.track_me_enabled`; it writes only generic settings fields. The `PersistedSettings` read model retains those fields for compatibility reads, while `LocationRepository.locationMode` is the canonical source for Track Me state.

`SettingsViewModel` reconciles selected location and Track Me state from the canonical location flows. The location repository can hydrate Track Me from the legacy boolean only when no canonical mode has been persisted, so existing data remains readable without adding a production Svilajnac default.

Added coverage for:

- Interleaved generic settings and canonical location writes, including repository recreation.
- Settings hydration from canonical Track Me mode.
- A cancellation-swallowing stale weather request followed by active-location transition to null.

## Verification

### Covering Tests

Command:

```text
ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.ui.settings.SettingsRepositoryTest --tests net.droopia.hluweather.ui.settings.SettingsViewModelTest --tests net.droopia.hluweather.data.repository.LocationRepositoryTest --tests net.droopia.hluweather.ui.weather.WeatherViewModelTest
```

Output: `BUILD SUCCESSFUL in 14s`; 29 actionable tasks, 5 executed and 24 up-to-date. The focused tests passed.

Command:

```text
ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testDebugUnitTest --tests 'net.droopia.hluweather.ui.settings.*' --tests 'net.droopia.hluweather.data.repository.*' --tests 'net.droopia.hluweather.ui.weather.*' && git diff --check
```

Output: `BUILD SUCCESSFUL in 32s`; 29 actionable tasks, 1 executed and 28 up-to-date. `git diff --check` produced no output and passed.

### Full Unit Suite

Command:

```text
ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew testDebugUnitTest && git diff --check
```

Output: `BUILD SUCCESSFUL in 32s`; 29 actionable tasks, 1 executed and 28 up-to-date. `git diff --check` produced no output and passed.

An initial non-wildcard package filter selected no tests (`No tests found for given includes`); the wildcard command above is the successful covering run.

## Commit

- Fix commit: `b45fd4f fix: centralize location state persistence`

## Residual Concerns

- Gradle 8.14.3 remains deprecated; Kotlin 2.5.0 will require at least Gradle 8.14.4.
- Existing `kotlinx.datetime.Instant` deprecation warnings remain in tests.
- Track Me coordinate acquisition and permission handling remain deferred to Plan 2.2.
- Add/edit/delete location screens remain deferred; existing Add and Manage actions remain navigation boundaries.
- Transferred plan/spec documents remain untracked and untouched. No plan/spec document or controller ledger was modified.
