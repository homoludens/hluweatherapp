# Location Provider Distributions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a Google Play flavor using fused location and an F-Droid flavor without Google Play Services while preserving the existing location UI and ViewModel contracts.

**Architecture:** Keep `DeviceLocationSource` and `GpsResult` in the shared source set. Supply same-named `AndroidDeviceLocationSource` implementations from `google` and `fdroid` source sets; the Google implementation uses a small injected gateway around `FusedLocationProviderClient`, and the F-Droid implementation uses `LocationManager`.

**Tech Stack:** Android Gradle product flavors, Kotlin coroutines, Google Play Services Location, Android `LocationManager`, Robolectric, JUnit.

**Spec:** `docs/superpowers/specs/2026-09-13-location-provider-distributions-design.md`

## Global Constraints

- Keep the existing `DeviceLocationSource` interface and its `GpsResult` states.
- Keep only foreground coarse/fine location permissions.
- Track Me remains foreground-only and must stop requesting updates when its lifecycle collection stops.
- The F-Droid variant must not compile or package `play-services-location`.
- Do not log coordinates or other sensitive location data in release builds.
- Preserve the current picker, repository, ViewModel, and refresh behavior.
- Both variants retain the same application ID. They are separate distribution channels, not side-by-side installs, because their signing keys differ.

---

### Task 1: Configure Distribution Flavors

**Files:**
- Modify: `gradle/libs.versions.toml:1-56`
- Modify: `app/build.gradle.kts:25-118`
- Modify: `docs/release-checklist.md` commands that invoke generic debug/release variants
- Modify: `scripts/verify-release-signing-matrix.sh` to use flavor-specific release tasks and APK paths
- Test: Gradle task graph and dependency configurations

**Interfaces:**
- Produces variants `googleDebug`, `googleRelease`, `fdroidDebug`, and `fdroidRelease`.
- Produces dependency configurations `googleReleaseRuntimeClasspath` and `fdroidReleaseRuntimeClasspath`.
- Makes `play-services-location` available only through `googleImplementation`.

- [ ] **Step 1: Add the Google location dependency alias**

Add the version and library entries without placing the library in shared `implementation` dependencies:

```toml
[versions]
playServicesLocation = "21.3.0"

[libraries]
play-services-location = { module = "com.google.android.gms:play-services-location", version.ref = "playServicesLocation" }
```

- [ ] **Step 2: Add the `google` and `fdroid` flavors**

Inside `android {}` configure one flavor dimension and two flavors:

```kotlin
flavorDimensions += "distribution"

productFlavors {
    create("google") {
        dimension = "distribution"
    }
    create("fdroid") {
        dimension = "distribution"
    }
}
```

Add the dependency only to the Google variant:

```kotlin
googleImplementation(libs.play.services.location)
```

- [ ] **Step 3: Update release commands to name the flavor**

Replace active generic release-checklist commands with explicit commands so the documented checks exercise both distributions. Mark the full-suite checklist item unchecked while the existing `WeatherScreenTest.empty_saved_locations_show_setup_action` failure remains, rather than retaining a false passing result. Do not run those commands during implementation; the two debug variants are compiled only by Task 4's final verification command.

```bash
ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}" ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}" ./gradlew testFdroidDebugUnitTest testGoogleDebugUnitTest assembleFdroidDebug assembleGoogleDebug lintFdroidDebug lintGoogleDebug
```

When signing environment variables are available, use:

```bash
ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}" ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}" ./gradlew assembleFdroidRelease assembleGoogleRelease
```

- [ ] **Step 4: Verify the variant task graph**

Run:

```bash
ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:tasks --all
```

Expected: the four flavor/build-type variants and their unit-test tasks are listed. Do not continue if a generic source set is still the only provider implementation path.

- [ ] **Step 5: Verify dependency isolation**

Run:

```bash
ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:dependencies --configuration fdroidReleaseRuntimeClasspath :app:dependencies --configuration googleReleaseRuntimeClasspath
```

Expected: `googleReleaseRuntimeClasspath` contains `com.google.android.gms:play-services-location`; `fdroidReleaseRuntimeClasspath` does not contain `com.google.android.gms`.

- [ ] **Step 6: Commit the flavor configuration**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts docs/release-checklist.md scripts/verify-release-signing-matrix.sh
git commit -m "build: add Google and F-Droid location flavors"
```

### Task 2: Extract Shared Location Mapping And F-Droid Provider

**Files:**
- Modify: `app/src/main/java/net/droopia/hluweather/data/device/DeviceLocationSource.kt` to retain only the shared interface
- Create: `app/src/main/java/net/droopia/hluweather/data/device/LocationMapping.kt`
- Create: `app/src/fdroid/java/net/droopia/hluweather/data/device/AndroidDeviceLocationSource.kt`
- Create: `app/src/test/java/net/droopia/hluweather/data/device/LocationMappingTest.kt`
- Create: `app/src/fdroidTest/java/net/droopia/hluweather/data/device/DeviceLocationSourceTest.kt`
- Delete: `app/src/test/java/net/droopia/hluweather/data/device/DeviceLocationSourceTest.kt`

**Interfaces:**
- Consumes shared `DeviceLocationSource`, `GeoPoint`, and `GpsResult`.
- Produces `AndroidDeviceLocationSource(Context, ...) : DeviceLocationSource` for the `fdroid` variants.
- Keeps `selectLocationProvider(...)` available only to the F-Droid implementation/tests.

- [ ] **Step 1: Move the location mapping test to shared code**

Create a shared test for the Android-to-domain conversion:

```kotlin
@Test
fun location_without_altitude_preserves_a_null_altitude() {
    val location = Location("network").apply {
        latitude = 44.8176
        longitude = 20.4633
    }

    assertEquals(
        GpsResult.Success(GeoPoint(44.8176, 20.4633), altitude = null),
        location.toGpsResult()
    )
}
```

Move `Location.toGpsResult()` to `LocationMapping.kt` so both flavor implementations share it.

- [ ] **Step 2: Write F-Droid provider tests that enforce network-first behavior**

Create flavor-specific tests with constructor-injected provider functions. Include these assertions:

```kotlin
assertEquals(
    LocationManager.NETWORK_PROVIDER,
    selectLocationProvider(hasFinePermission = true) { provider ->
        provider == LocationManager.GPS_PROVIDER || provider == LocationManager.NETWORK_PROVIDER
    }
)
assertEquals(
    LocationManager.NETWORK_PROVIDER,
    selectLocationProvider(hasFinePermission = false) { it == LocationManager.NETWORK_PROVIDER }
)
```

Also retain tests proving that `currentLocation()` and `foregroundLocations()` return `Unavailable` after the injected timeout and remove the listener exactly once.

- [ ] **Step 3: Inspect the F-Droid source-set transition without compiling**

Do not run a Gradle test task here because the user requested no intermediate compilation. Instead, confirm that the shared source set contains only the interface and mapping, the F-Droid source set contains the provider, and the F-Droid test source set contains the provider tests.

```bash
rg -n "class AndroidDeviceLocationSource|interface DeviceLocationSource|fun Location.toGpsResult" app/src/main app/src/fdroid app/src/fdroidTest
```

Expected: the provider class appears only under `app/src/fdroid`, the interface and mapping appear under `app/src/main`, and the provider tests appear under `app/src/fdroidTest`.

- [ ] **Step 4: Implement the shared interface and F-Droid provider**

Leave only the contract in the shared source set:

```kotlin
interface DeviceLocationSource {
    suspend fun currentLocation(): GpsResult

    fun foregroundLocations(): Flow<GpsResult> = flow {
        emit(currentLocation())
    }
}
```

Move the current `LocationManager` implementation into the F-Droid source set. Preserve permission checks, timeout cancellation, `SecurityException` mapping, listener cleanup, and constructor injection. Change fine-permission provider ordering to network first, then GPS, so a fine-permission device does not require GPS.

- [ ] **Step 5: Defer F-Droid compilation to final verification**

The F-Droid provider tests are run once by Task 4's final combined debug verification command.

- [ ] **Step 6: Commit the shared and F-Droid implementation**

```bash
git add app/src/main/java/net/droopia/hluweather/data/device app/src/fdroid app/src/test/java/net/droopia/hluweather/data/device
git commit -m "feat: add F-Droid location provider"
```

### Task 3: Implement Google Fused Location Provider

**Files:**
- Create: `app/src/google/java/net/droopia/hluweather/data/device/FusedLocationGateway.kt`
- Create: `app/src/google/java/net/droopia/hluweather/data/device/GoogleFusedLocationGateway.kt`
- Create: `app/src/google/java/net/droopia/hluweather/data/device/AndroidDeviceLocationSource.kt`
- Create: `app/src/googleTest/java/net/droopia/hluweather/data/device/DeviceLocationSourceTest.kt`

**Interfaces:**
- Consumes shared `DeviceLocationSource`, `LocationMapping`, `GeoPoint`, and `GpsResult`.
- `FusedLocationGateway.lastLocation(): Location?` is a suspending method that returns the cached Android location.
- `FusedLocationGateway.currentLocation(priority: Int): Location?` is a suspending method that requests one fresh location.
- `FusedLocationGateway.requestLocationUpdates(priority: Int, listener: (Location) -> Unit): LocationUpdateRegistration` is a suspending method that registers updates.
- `LocationUpdateRegistration.remove()` unregisters all callbacks.
- Produces `AndroidDeviceLocationSource(Context, fusedLocationGateway: FusedLocationGateway = GoogleFusedLocationGateway(...), ...)` for Google variants.

- [ ] **Step 1: Write fake-gateway tests before the Google implementation**

The fake gateway must record the requested priority, return a configured cached/fresh location, capture the update listener, and count removals. Test the following concrete cases:

```kotlin
@Test
fun current_location_returns_cached_location_without_requesting_a_fresh_fix() = runTest {
    val cached = Location("fused").apply {
        latitude = 44.8176
        longitude = 20.4633
    }
    val gateway = RecordingFusedLocationGateway(last = cached)
    val source = source(gateway, hasFinePermission = true)

    assertEquals(GpsResult.Success(GeoPoint(44.8176, 20.4633), null), source.currentLocation())
    assertEquals(0, gateway.currentLocationCalls)
}

@Test
fun coarse_permission_uses_balanced_power_accuracy() = runTest {
    val gateway = RecordingFusedLocationGateway(current = location())
    source(gateway, hasFinePermission = false).currentLocation()
    assertEquals(Priority.PRIORITY_BALANCED_POWER_ACCURACY, gateway.lastPriority)
}

@Test
fun fine_permission_uses_high_accuracy() = runTest {
    val gateway = RecordingFusedLocationGateway(current = location())
    source(gateway, hasFinePermission = true).currentLocation()
    assertEquals(Priority.PRIORITY_HIGH_ACCURACY, gateway.lastPriority)
}
```

Add tests for fresh fallback, permission required, disabled service, timeout, provider exception mapping, cached-then-streamed foreground updates, and removal when the foreground flow is cancelled.

- [ ] **Step 2: Inspect the Google source-set transition without compiling**

Do not run a Gradle test task here because the user requested no intermediate compilation. Confirm that Google-only types occur only under `app/src/google` and that the fake gateway tests occur only under `app/src/googleTest`.

```bash
rg -n "FusedLocationProviderClient|LocationServices|FusedLocationGateway|class AndroidDeviceLocationSource" app/src/main app/src/google app/src/googleTest
```

Expected: Google Play Services types and the Google provider class appear only under `app/src/google`, while shared code contains no Google imports.

- [ ] **Step 3: Implement the fused gateway**

Wrap `FusedLocationProviderClient` in `GoogleFusedLocationGateway`. Convert Google `Task` callbacks to cancellable suspending functions; cancel `CancellationTokenSource` when the caller is cancelled. Build continuous requests with:

```kotlin
LocationRequest.Builder(priority, 5_000L)
    .setMinUpdateIntervalMillis(2_000L)
    .setMinUpdateDistanceMeters(10f)
    .build()
```

Use `LocationServices.getFusedLocationProviderClient(context)` as the production default. Return a `LocationUpdateRegistration` whose `remove()` calls `fusedClient.removeLocationUpdates(callback)`.

- [ ] **Step 4: Implement the Google `DeviceLocationSource`**

Check coarse/fine permission and `LocationManager.isLocationEnabled` before gateway calls. Use `lastLocation()` first, then `currentLocation(priority)` inside `withTimeoutOrNull(LOCATION_TIMEOUT_MILLIS)`. In `foregroundLocations()`, emit the cached location if present, register updates, send the first-fix timeout result when there is no cached fix, and always remove the registration from `awaitClose`.

Map `SecurityException` to `PermissionRequired`, all controlled provider/runtime failures to `Unavailable`, and rethrow `CancellationException`. Wrap diagnostic logging in `if (BuildConfig.DEBUG)` and log only state/provider events, never coordinates or raw exception text in release.

- [ ] **Step 5: Defer Google compilation to final verification**

The Google provider tests are run once by Task 4's final combined debug verification command.

- [ ] **Step 6: Commit the Google implementation**

```bash
git add app/src/google app/src/googleTest
git commit -m "feat: use fused location in Google build"
```

### Task 4: Integrate Variants And Verify Distribution Artifacts

**Files:**
- Modify only if required: `app/src/main/java/net/droopia/hluweather/HluWeatherApplication.kt`
- Modify only if required: `app/src/main/java/net/droopia/hluweather/ui/weather/WeatherViewModel.kt`
- Modify only if required: `app/src/main/java/net/droopia/hluweather/ui/locationpicker/LocationPickerViewModel.kt`
- Modify only if required: `app/src/main/java/net/droopia/hluweather/ui/weatherroute/WeatherRouteViewModel.kt`
- Test: existing application, picker, weather, route, and navigation tests in both flavor variants

**Interfaces:**
- Both flavor classes satisfy the shared `DeviceLocationSource` constructor used by `HluWeatherApplication.deviceLocationSource`.
- No consumer imports Google Play Services or `LocationManager` directly.

- [ ] **Step 1: Run the only debug compilation and test verification**

Run:

```bash
ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testFdroidDebugUnitTest :app:testGoogleDebugUnitTest :app:assembleFdroidDebug :app:assembleGoogleDebug :app:lintFdroidDebug :app:lintGoogleDebug
```

Expected: this is the only post-implementation debug compilation. Existing ViewModel, picker, weather route, navigation, application, and provider tests pass in both variants, and both debug artifacts/lint tasks succeed.

- [ ] **Step 2: Confirm no consumer coupling**

Search application sources outside flavor provider files:

```bash
rg -n "FusedLocationProviderClient|LocationServices|LocationManager|GPS_PROVIDER|NETWORK_PROVIDER" app/src/main app/src/google app/src/fdroid
```

Expected: Google APIs appear only under `app/src/google`; `LocationManager` provider APIs appear only under `app/src/fdroid` and the shared service-enabled check if needed.

- [ ] **Step 3: Record debug artifacts**

Record the two artifacts produced by Step 1: `app/build/outputs/apk/fdroid/debug/app-fdroid-debug.apk` and `app/build/outputs/apk/google/debug/app-google-debug.apk`.

- [ ] **Step 4: Defer signed release builds to manual testing**

Do not run release builds in this implementation pass. When the user begins manual testing, build the signed Google and F-Droid release artifacts with the appropriate channel signing process.

- [ ] **Step 5: Verify F-Droid dependency isolation without compiling**

Run:

```bash
ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:dependencies --configuration fdroidReleaseRuntimeClasspath
```

Expected: no `com.google.android.gms` dependency is present in the F-Droid runtime classpath. APK inspection is deferred until the manual signed-release testing phase.

- [ ] **Step 6: Commit integration-only changes**

```bash
git add app/src/main app/src/google app/src/fdroid app/src/googleTest app/src/fdroidTest
git commit -m "test: verify location distribution variants"
```
