# Plan 2.2: Map Picker And Track Me Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users add/edit locations on an OpenFreeMap map and use foreground Track Me without startup permission.

**Architecture:** Extend the Plan 2.1 `LocationRepository` active-location flow with `ActiveLocation.Current`. Keep native map rendering behind a small composable seam; use Android `LocationManager` in `DeviceLocationSource`, leaving MapLibre responsible only for map display.

**Tech Stack:** MapLibre Compose 0.16.0, OpenFreeMap, Ktor, Android LocationManager, Compose Navigation, DataStore.

**Spec:** `docs/superpowers/specs/2026-09-09-plan-2-core-product-completion-design.md`

## Global Constraints

- Plan 2.1 is required before this plan.
- Add only coarse/fine foreground location permissions; never add background location permission.
- Nominatim requests use an identifying User-Agent and are globally limited to one per second.
- Native map surfaces are manually/device verified; unit tests use injected callbacks.

---

### Task 1: Add Location And Geocoding Boundaries

**Files:**
- Create: `data/model/GeoPoint.kt`, `data/device/DeviceLocationSource.kt`, `data/network/NominatimApi.kt`, `data/network/NominatimModels.kt`, `data/repository/ReverseGeocoder.kt`
- Create: `data/network/NominatimApiTest.kt`, `data/repository/ReverseGeocoderTest.kt`
- Modify: `HluWeatherApplication.kt`, `AndroidManifest.xml`, `gradle/libs.versions.toml`, `app/build.gradle.kts`

**Interfaces:**

```kotlin
data class GeoPoint(val latitude: Double, val longitude: Double)
sealed interface GpsResult {
    data class Success(val point: GeoPoint, val altitude: Int?) : GpsResult
    data object PermissionRequired : GpsResult
    data object LocationDisabled : GpsResult
    data object Unavailable : GpsResult
}
interface ReverseGeocoder { suspend fun reverse(point: GeoPoint): String? }
```

- [ ] **Step 1: Write failing Nominatim tests**

```kotlin
assertEquals("/reverse", request.url.encodedPath)
assertEquals("jsonv2", request.url.parameters["format"])
assertEquals("HluWeather/1.0 https://net.droopia.hluweather", request.headers["User-Agent"])
```

Test one-second throttling, replacement of an obsolete request, no result on HTTP/JSON failure, and parsed useful place names.

- [ ] **Step 2: Run the tests to verify RED**

Run: `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.data.network.NominatimApiTest --tests net.droopia.hluweather.data.repository.ReverseGeocoderTest`

Expected: compilation failures for missing boundaries.

- [ ] **Step 3: Implement Ktor Nominatim client and throttled geocoder**

Use `GET /reverse` with `format=jsonv2`, `lat`, and `lon`. Return null on controlled service failures; delay before requests as needed to maintain one request per second; cancel stale calls with the caller coroutine.

- [ ] **Step 4: Run the focused tests to verify GREEN**

Run the Step 2 command. Expected: all tests pass offline.

- [ ] **Step 5: Commit the boundaries**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/java/net/droopia/hluweather/data app/src/test/java/net/droopia/hluweather/data
```

### Task 2: Build The Location Picker

**Files:**
- Create: `ui/locationpicker/LocationPickerViewModel.kt`, `LocationPickerScreen.kt`, matching tests
- Modify: `navigation/HluNavHost.kt`, `ui/settings/SettingsViewModel.kt`, `SettingsScreen.kt`, `data/repository/LocationRepository.kt`

- [ ] **Step 1: Write failing picker tests**

```kotlin
viewModel.onCameraIdle(GeoPoint(44.8176, 20.4633))
assertEquals("New location", viewModel.state.value.name)
viewModel.onNameChanged("Belgrade")
viewModel.onReverseGeocoded("Stari Grad")
assertEquals("Belgrade", viewModel.state.value.name)
```

Add add/save, edit/save, GPS success, permission required, disabled, unavailable, and delete tests.

- [ ] **Step 2: Run picker tests to verify RED**

Run: `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.ui.locationpicker`

Expected: compilation failure for picker types.

- [ ] **Step 3: Implement picker state and navigation**

Use route `location_picker?locationId={locationId}`. Generate IDs with `UUID.randomUUID().toString()`, use `New location` when geocoding fails, and let Save work regardless of geocoding. Route runtime permission results from the activity/host back to the picker state.

- [ ] **Step 4: Run picker and navigation tests to verify GREEN**

Run: `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.ui.locationpicker --tests net.droopia.hluweather.navigation.HluNavHostTest`

Expected: passing tests.

- [ ] **Step 5: Commit the picker**

```bash
git add app/src/main/java/net/droopia/hluweather/ui/locationpicker app/src/main/java/net/droopia/hluweather/navigation app/src/main/java/net/droopia/hluweather/ui/settings app/src/test/java/net/droopia/hluweather/ui
```

### Task 3: Add Map And Foreground Track Me

**Files:**
- Create: `ui/map/WeatherMap.kt`, `ui/map/WeatherMapTest.kt`
- Delete: `ui/map/MapPlaceholder.kt`, `ui/map/MapPlaceholderTest.kt`
- Modify: Gradle files, `WeatherScreen.kt`, `WeatherViewModel.kt`, `LocationRepository.kt`, `SettingsScreen.kt`, related tests

- [ ] **Step 1: Write failing map and refresh-gate tests**

```kotlin
assertEquals("https://tiles.openfreemap.org/styles/dark", mapState.styleUrl)
assertTrue(shouldRefresh(lastPoint, movedFiveKm, lastFetch, now))
assertFalse(shouldRefresh(lastPoint, movedOneKm, now.minus(29.minutes), now))
```

Test marker selection, recenter callback, current-location flow, and the 30-minute threshold.

- [ ] **Step 2: Run tests to verify RED**

Run: `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.ui.map.WeatherMapTest --tests net.droopia.hluweather.ui.weather.WeatherViewModelTest`

Expected: missing map and Track Me behavior.

- [ ] **Step 3: Implement map and Track Me**

Add MapLibre Compose `0.16.0`, Liberty/dark styles, saved markers, active marker, and recenter control. `DeviceLocationSource` emits only while foreground. Weather refreshes on initial Track Me fix, 5 km movement, or 30 minutes; smaller moves update display only.

- [ ] **Step 4: Verify GREEN and device behavior**

Run: `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew testDebugUnitTest assembleDebug lintDebug`

Expected: build success. On an emulator verify grant/deny permission, disabled GPS, manual picker, OpenFreeMap render, tile failure message, and no updates after backgrounding.

- [ ] **Step 5: Commit the map slice**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main app/src/test
```
