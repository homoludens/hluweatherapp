# Weather Refresh and Visual Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add cache-first weather loading with pull-to-refresh and a thin update indicator, then reduce hero space and correct dark-theme contrast.

**Architecture:** Keep `CachingWeatherRepository.getForecast()` as the network-first operation with stale-cache fallback, and add a cache-read operation that returns only entries no older than one hour. `WeatherViewModel` applies that cache before starting the network request and tracks initial loading separately from refresh loading. `WeatherScreen` owns pull-to-refresh and the top progress line; theme and hero changes remain local to their existing files.

**Tech Stack:** Kotlin 2.4, Jetpack Compose Material 3, Kotlin Coroutines, Kotlinx DateTime, DataStore Preferences, JUnit, Robolectric Compose tests.

**Spec:** `docs/superpowers/specs/2026-09-10-weather-refresh-and-visual-polish-design.md`

## Global Constraints

- A cache entry whose `fetchedAt` is no more than one hour old is applied immediately, then a force-network refresh runs in the background.
- Older cache entries remain available as failure fallback but do not qualify as the fast path.
- Pull-to-refresh always invokes the force-network path and leaves the current forecast visible.
- The full-screen `CircularProgressIndicator` remains only for the no-forecast state.
- While a forecast is visible and a refresh is active, a thin 2dp `LinearProgressIndicator` is pinned to the top of the weather screen.
- The regular `WeatherHero` will be reduced from its current 240dp height to exactly 176dp with tighter internal spacing.
- Only the decorative moon circle in the hero will be removed; the current-weather card's moon-phase indicator remains.
- Dark selected surfaces will use light selected text and icons, and body text will use Material 3 `onSurface` or `onSurfaceVariant` tokens.
- Provider URLs, response models, network timeouts, font scaling, map behavior, and unrelated settings/notification flows are out of scope.
- Run commands with `ANDROID_HOME=/home/homoludens/Android/Sdk` and `ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk`.

---

### Task 1: Add the One-Hour Cache Read Contract

**Files:**
- Modify: `app/src/main/java/net/droopia/hluweather/data/repository/WeatherRepository.kt:18-21`
- Modify: `app/src/main/java/net/droopia/hluweather/data/repository/CachingWeatherRepository.kt:13-75`
- Test: `app/src/test/java/net/droopia/hluweather/data/repository/CachingWeatherRepositoryTest.kt`

**Interfaces:**
- Add `suspend fun getCachedForecast(provider: WeatherProvider, location: ActiveLocation): ForecastLoad?` to `WeatherRepository`. Give the interface method a `null` default so existing repositories without a cache remain valid.
- `CachingWeatherRepository.getCachedForecast()` returns `ForecastLoad(cachedForecast, isStale = false)` only when the cache entry matches provider/location and `now() - fetchedAt <= 1.hours`; otherwise it returns `null`.
- Add an injectable `now: () -> Instant` constructor parameter to `CachingWeatherRepository`, defaulting to `Clock.System.now`, so age boundaries are deterministic in tests.
- Leave `getForecast()` network-first with its existing stale-cache-on-failure behavior. The new cache-read method must never call a `WeatherSource`.

- [ ] **Step 1: Add the failing cache-age tests**

Add tests using the existing fake source/cache helpers. Use a fixed `now` and forecasts with `fetchedAt` values at `now - 59.minutes`, `now - 1.hours`, and `now - 1.hours - 1.seconds`. Assert that the first two return a non-stale `ForecastLoad`, the older entry returns `null`, and the source request count remains zero for every cache read.

Also add a mismatch test proving that a cached forecast for a different provider or saved location returns `null`.

```kotlin
@Test
fun cache_read_accepts_entries_up_to_one_hour_old() = runTest {
    val now = Instant.parse("2026-09-10T12:00:00Z")
    val repository = repositoryWithCachedForecast(
        fetchedAt = now - 1.hours,
        now = { now }
    )

    val load = repository.getCachedForecast(
        WeatherProvider.OPEN_METEO,
        ActiveLocation.Saved(Svilajnac)
    )

    assertEquals(false, load?.isStale)
    assertEquals(0, source.calls)
}
```

- [ ] **Step 2: Run the focused tests and verify the new tests fail**

Run:

```bash
export ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk && ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.data.repository.CachingWeatherRepositoryTest.cache_read_accepts_entries_up_to_one_hour_old
```

Expected: compilation or assertion failure because the cache-read contract does not exist yet.

- [ ] **Step 3: Implement the minimal repository contract**

Add the default interface method, implement the cache read with the same `ForecastCacheKey` and location matching used by the existing fallback, and compare the forecast timestamp to the injected clock. Preserve cancellation propagation and swallow only the same cache-read persistence errors already handled by the existing repository path.

- [ ] **Step 4: Run the focused repository suite**

Run:

```bash
export ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk && ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.data.repository.CachingWeatherRepositoryTest
```

Expected: all caching repository tests pass, including the one-hour boundary and provider/location mismatch tests.

- [ ] **Step 5: Commit the cache contract**

```bash
git add app/src/main/java/net/droopia/hluweather/data/repository/WeatherRepository.kt app/src/main/java/net/droopia/hluweather/data/repository/CachingWeatherRepository.kt app/src/test/java/net/droopia/hluweather/data/repository/CachingWeatherRepositoryTest.kt
git commit -m "feat: expose fresh cached forecasts"
```

### Task 2: Make ViewModel Loading Cache-First and Refresh-Aware

**Files:**
- Modify: `app/src/main/java/net/droopia/hluweather/ui/weather/WeatherViewModel.kt:36-262`
- Modify: `app/src/main/java/net/droopia/hluweather/data/repository/MockWeatherRepository.kt`
- Test: `app/src/test/java/net/droopia/hluweather/ui/weather/WeatherViewModelTest.kt`

**Interfaces:**
- Add `isRefreshing: Boolean = false` to `WeatherUiState` while retaining `isLoading` for initial/no-forecast loading.
- `WeatherViewModel.refresh()` continues incrementing `refreshes`; refresh requests must skip `getCachedForecast()` and call the existing network operation directly.
- Initial/new-location/provider loads call `getCachedForecast()` first, apply a returned forecast without marking it stale, then call `getForecast()` for the network update.

- [ ] **Step 1: Add failing ViewModel tests**

Add a fake repository with a cached forecast and a `CompletableDeferred<ForecastLoad>` network result. Cover:

```kotlin
@Test
fun fresh_cache_is_shown_before_network_refresh_completes() = runTest {
    val networkResult = CompletableDeferred<ForecastLoad>()
    val repository = CacheFirstRecordingRepository(networkResult)
    val viewModel = WeatherViewModel(
        repository,
        TestSettingsRepository(),
        TestLocationRepository(ActiveLocation.Saved(Svilajnac))
    )

    assertEquals(cachedForecast, viewModel.state.value.forecast)
    assertTrue(viewModel.state.value.isRefreshing)
    assertFalse(viewModel.state.value.isLoading)
    assertEquals(1, repository.networkRequests)

    networkResult.complete(ForecastLoad(freshForecast))
    advanceUntilIdle()

    assertEquals(freshForecast, viewModel.state.value.forecast)
    assertFalse(viewModel.state.value.isRefreshing)
}
```

Add tests that pull refresh (call `viewModel.refresh()`) does not call the cache-read method, keeps the current forecast while the deferred network request is pending, and sets `isRefreshing` false after both success and failure. Add a no-cache test asserting the initial full-screen state remains `isLoading=true` until the network result completes.

- [ ] **Step 2: Run the focused ViewModel tests and verify they fail**

Run:

```bash
export ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk && ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.ui.weather.WeatherViewModelTest.fresh_cache_is_shown_before_network_refresh_completes
```

Expected: compilation or assertion failure because `isRefreshing` and cache-first loading are not implemented.

- [ ] **Step 3: Implement the minimal ViewModel state flow**

At the start of a load, determine whether this is an explicit refresh. For a non-refresh request, read and apply a fresh cached result before setting `isRefreshing=true` and starting the network call. For a refresh or a request without cache, preserve the current forecast if one exists and set `isRefreshing=true`; set `isLoading=true` only when there is no forecast. Remove the unconditional `forecast = null` assignment for refreshes.

On network success, replace the forecast, clear both loading flags, clear the error, and preserve existing generation checks. On failure, clear both loading flags; if a forecast is visible, retain it and set `isStale=true` without replacing it with the empty-state error view, otherwise set the existing error. Ensure cached data does not update `lastLoadedProvider` or location markers before the network result is accepted.

- [ ] **Step 4: Run the focused ViewModel and repository suites**

```bash
export ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk && ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.ui.weather.WeatherViewModelTest --tests net.droopia.hluweather.data.repository.CachingWeatherRepositoryTest
```

Expected: all existing tests and the new cache-first/refresh-state tests pass.

- [ ] **Step 5: Commit the loading behavior**

```bash
git add app/src/main/java/net/droopia/hluweather/ui/weather/WeatherViewModel.kt app/src/main/java/net/droopia/hluweather/data/repository/MockWeatherRepository.kt app/src/test/java/net/droopia/hluweather/ui/weather/WeatherViewModelTest.kt
git commit -m "feat: show cached weather while refreshing"
```

### Task 3: Add Pull-to-Refresh and the Thin Update Line

**Files:**
- Modify: `app/src/main/java/net/droopia/hluweather/ui/weather/WeatherScreen.kt:127-235,411-527`
- Test: `app/src/test/java/net/droopia/hluweather/ui/weather/WeatherScreenTest.kt`

**Interfaces:**
- Use Material 3 `PullToRefreshBox` with `isRefreshing = state.isRefreshing` and `onRefresh = viewModel::refresh` around the forecast content.
- Supply an empty/default indicator slot if needed so the custom 2dp line is the only update indicator.
- Add test tag `weather_refresh_indicator` to the `LinearProgressIndicator`.

- [ ] **Step 1: Add failing Compose tests**

Add a deferred repository to `WeatherScreenTest` and assert that cached content remains displayed while `isRefreshing` is true, the node tagged `weather_refresh_indicator` is displayed during refresh, and it disappears after the deferred result completes. Add a gesture test that starts at the top of `weather_scroll`, performs a downward swipe, waits for idle, and asserts the repository received a second network request.

Add a first-load test that verifies the full-screen progress indicator is present only while `forecast == null`; once cached or network data exists, the line is used instead.

- [ ] **Step 2: Run the focused UI tests and verify they fail**

```bash
export ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk && ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.ui.weather.WeatherScreenTest
```

Expected: the new indicator and pull gesture assertions fail because the screen has no pull-to-refresh wrapper or update line.

- [ ] **Step 3: Implement the screen interaction**

Keep the existing no-location/error branch unchanged except for initial loading semantics. Wrap the forecast branch in `PullToRefreshBox`; the hourly `LazyColumn`, daily list, and map remain the content children. Overlay a `LinearProgressIndicator` at the outer screen top with `fillMaxWidth().height(2.dp)` and the `weather_refresh_indicator` tag when `state.isRefreshing` is true. Do not show it for the initial full-screen state.

- [ ] **Step 4: Run the focused UI tests**

```bash
export ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk && ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.ui.weather.WeatherScreenTest
```

Expected: all weather screen tests pass, including the pull gesture and indicator assertions.

- [ ] **Step 5: Commit the refresh UI**

```bash
git add app/src/main/java/net/droopia/hluweather/ui/weather/WeatherScreen.kt app/src/test/java/net/droopia/hluweather/ui/weather/WeatherScreenTest.kt
git commit -m "feat: add pull to refresh feedback"
```

### Task 4: Reduce the Hero and Correct Dark-Theme Contrast

**Files:**
- Modify: `app/src/main/java/net/droopia/hluweather/ui/weather/WeatherHero.kt:58-151`
- Modify: `app/src/main/java/net/droopia/hluweather/ui/theme/Color.kt:41-57`
- Test: `app/src/test/java/net/droopia/hluweather/ui/weather/WeatherHeroTest.kt`
- Test: `app/src/test/java/net/droopia/hluweather/ui/theme/ThemeTest.kt`
- Test: `app/src/test/java/net/droopia/hluweather/ui/weather/WeatherScreenTest.kt`

**Interfaces:**
- Regular `WeatherHero` height becomes exactly `176.dp`; compact mode remains `96.dp`.
- Delete only the non-compact decorative moon `Box`; do not alter `CurrentWeatherCard` or `MoonPhase`.
- Set dark selected navigation/day surfaces to a dark blue that supports light text, and set `DarkHluColors.navSelectedText` and `daySelectedText` to light foreground colors. Keep `heroText` and `heroSecondaryText` light.

- [ ] **Step 1: Add failing visual/theme tests**

Extend `WeatherHeroTest` to assert the regular hero root height is `176.dp` and the decorative moon is absent by checking that no moon-specific test tag/content description exists after adding a stable test tag to the old moon location in the test setup. Extend `ThemeTest` to capture dark colors and assert the selected text values are light enough for the dark selected surfaces by comparing against the expected light token values.

Add a dark `WeatherScreenTest` that selects a navigation tab and a day chip and asserts their text nodes remain visible under `HluWeatherTheme(darkTheme = true)`.

- [ ] **Step 2: Run the focused visual tests and verify they fail**

```bash
export ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk && ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.ui.weather.WeatherHeroTest --tests net.droopia.hluweather.ui.theme.ThemeTest --tests net.droopia.hluweather.ui.weather.WeatherScreenTest
```

Expected: the height, moon, and dark-token assertions fail against the current 240dp hero and dark selected foregrounds.

- [ ] **Step 3: Implement the visual changes**

Change only the regular hero height and spacing required to fit the 176dp target, remove the decorative moon block, and leave the compact hero layout intact. Update dark selected surface/foreground pairs and replace any body-text color in touched weather components that is not readable on the dark surface with `MaterialTheme.colorScheme.onSurface` or `onSurfaceVariant`.

- [ ] **Step 4: Run the focused visual suites**

```bash
export ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk && ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.ui.weather.WeatherHeroTest --tests net.droopia.hluweather.ui.theme.ThemeTest --tests net.droopia.hluweather.ui.weather.WeatherScreenTest
```

Expected: all visual and contrast tests pass.

- [ ] **Step 5: Commit the visual polish**

```bash
git add app/src/main/java/net/droopia/hluweather/ui/weather/WeatherHero.kt app/src/main/java/net/droopia/hluweather/ui/theme/Color.kt app/src/test/java/net/droopia/hluweather/ui/weather/WeatherHeroTest.kt app/src/test/java/net/droopia/hluweather/ui/theme/ThemeTest.kt app/src/test/java/net/droopia/hluweather/ui/weather/WeatherScreenTest.kt
git commit -m "fix: improve weather refresh visuals and contrast"
```

### Task 5: Full Verification and Device Check

**Files:**
- Verify: all files changed by Tasks 1-4

- [ ] **Step 1: Run the complete verification suite**

```bash
export ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk && ./gradlew :app:testDebugUnitTest :app:lint :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`, zero unit-test failures, zero lint errors, and a generated debug APK at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 2: Check the final diff**

```bash
git diff --check && git status --short && git log --oneline -8
```

Expected: no whitespace errors; only intended commits/files in the feature worktree; unrelated dirty files in the main worktree remain untouched.

- [ ] **Step 3: Install and launch on the physical device**

```bash
adb devices
adb -s 2555a240 install -r -d -g app/build/outputs/apk/debug/app-debug.apk
adb -s 2555a240 shell am force-stop net.droopia.hluweather
adb -s 2555a240 shell monkey -p net.droopia.hluweather 1
```

Expected: the package installs successfully and launches. Verify manually that initial cached weather appears without a full-screen spinner, pull-down starts a fresh request with the thin top line, the hero is shorter, the top moon is gone, and dark mode shows readable text in tabs, day chips, tables, and the current card.

- [ ] **Step 4: Request final review and integrate**

Dispatch a reviewer against the feature branch and base `main`. Fix Critical or Important findings, rerun the complete verification command, then merge the approved feature branch into `main` without staging unrelated files.
