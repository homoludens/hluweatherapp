# Task 5 Report

## Files

- `app/src/main/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteViewModel.kt`
- `app/src/test/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteViewModelTest.kt`

The ViewModel provides the prescribed `WeatherRouteUiState`, endpoint and
search actions, debounced provider search, current-location handling,
generation-guarded route calculation, route-preserving weather failures,
weather retry, device-timezone next-full-hour departure, and an injectable
`Factory`.

## Commands And Output

All commands were run from:
`/home/homoludens/projekti/hluweatherapp/.worktrees/weather-on-route`

Environment used for Gradle commands:
`ANDROID_HOME=/home/homoludens/Android/Sdk`
`ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk`

The command stated in the brief was run first:

```text
env ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew test --tests net.droopia.hluweather.ui.weatherroute.WeatherRouteViewModelTest
```

Exact result:

```text
Problem configuring task :app:test from command line.
> Unknown command-line option '--tests'.
BUILD FAILED in 1s
```

The equivalent Android unit-test task was used thereafter:

```text
env ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.ui.weatherroute.WeatherRouteViewModelTest
```

The initial red run failed compilation because the new ViewModel API was not
present, after correcting test-only fixture errors:

```text
> Task :app:compileDebugUnitTestKotlin FAILED
Unresolved reference 'RouteEndpointSlot'.
Unresolved reference 'WeatherRouteViewModel'.
BUILD FAILED
```

Intermediate implementation runs caught and fixed the following compile and
behavior issues:

- `DateTimeUnit.HOUR` was not valid for `LocalDateTime.plus` in the installed
  kotlinx-datetime version.
- The timezone helper needed explicit date rollover at midnight.
- Test fixtures initially asserted un-enriched samples and used a departure
  inside the forecast window.
- Test fixture validation order initially reused a real-clock default with an
  injected fixed clock.

Final targeted test command:

```text
env ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.ui.weatherroute.WeatherRouteViewModelTest
```

Exact final Gradle result:

```text
BUILD SUCCESSFUL in 7s
```

The generated test report confirms:

```text
10 tests
0 failures
0 skipped
100% successful
```

Final whitespace check:

```text
git diff --check
```

Exact result: no output.

## Self-Review

- State fields match the brief exactly.
- Route calculation validates endpoints, integer speed `50..240`, past
  departures, and the 16-day forecast boundary.
- Route, sample construction, and weather enrichment run in the required
  order.
- Routing failures clear the result and set `routeError`.
- Weather failures preserve the route result with un-enriched samples and set
  `weatherError`.
- Weather retry reuses the existing route and does not call routing again.
- Calculation generations prevent superseded route and weather jobs from
  publishing state.
- Cancellation exceptions are rethrown rather than converted to user errors.
- Search uses `debounce(300)` and `flatMapLatest` over query, provider, and
  endpoint slot.
- Current GPS is requested only from the explicit current-location action.
- `nextFullHour` converts through `TimeZone.currentSystemDefault()` and has a
  DST-boundary test.
- No earlier source interfaces were modified.

## Concerns

- The project emits existing-compatible warnings for the deprecated
  `kotlinx.datetime.Instant` typealias used by the earlier route interfaces.
- Gradle emits the project-wide warning that Android Gradle Plugin 9.1.0 is
  tested through compile SDK 36.1 while this project uses compile SDK 37.
- Navigation wiring and the route-planner UI remain outside Task 5.
