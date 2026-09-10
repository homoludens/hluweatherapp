# Plan 3.2: Release Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a branded, accessible, installable release with repeatable verification evidence.

**Architecture:** Keep release configuration in Gradle and environment-provided signing values. Test semantic behavior in unit/Robolectric tests and verify native maps, permissions, notifications, and installation on devices.

**Tech Stack:** Android Gradle Plugin, Compose testing, Robolectric, Android instrumentation tests, adaptive icons.

**Spec:** `docs/superpowers/specs/2026-09-09-plan-3-notifications-release-design.md`

## Global Constraints

- Requires Plan 3.1.
- Never commit a keystore, signing password, or API credential.
- Remove the font-scale cap; do not mask accessibility layouts by forcing `fontScale = 1f`.
- Keep all unit tests offline; device tests may exercise Android services only through controlled/manual smoke procedures.

---

### Task 1: Make Primary Flows Accessible

**Files:** Modify `ui/theme/Theme.kt`, `ui/settings/SettingsScreen.kt`, `ui/weather/WeatherHero.kt`, `WeatherScreen.kt`, relevant Compose tests; create `ui/accessibility/PrimaryFlowsAccessibilityTest.kt`.

- [ ] **Step 1: Write failing accessibility tests**

```kotlin
composeRule.onNodeWithText("Weather alerts").assertHasClickAction().assertIsOn()
composeRule.onNodeWithContentDescription("Settings").assertHeightIsAtLeast(48.dp)
composeRule.onNodeWithText("Hourly").assertIsSelected()
```

Add a large-font composition test that fails if title or settings content clips.

- [ ] **Step 2: Run accessibility tests to verify RED**

Run: `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.ui.accessibility.PrimaryFlowsAccessibilityTest`

Expected: current row semantics and font-scale override fail assertions.

- [ ] **Step 3: Implement semantics and scaling fixes**

Remove the custom density font-scale cap. Give entire interactive settings rows merged labels plus `Role.Switch` or selection semantics; retain null descriptions on decorative icons; guarantee 48 dp targets on actions and tabs.

- [ ] **Step 4: Run accessibility and screen tests to verify GREEN**

Run: `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.ui.accessibility --tests net.droopia.hluweather.ui.settings --tests net.droopia.hluweather.ui.weather`

Expected: all selected tests pass.

- [ ] **Step 5: Commit accessibility work**

```bash
git add app/src/main/java/net/droopia/hluweather/ui app/src/test/java/net/droopia/hluweather/ui
```

### Task 2: Add Branding And Release Configuration

**Files:** Create adaptive icon XML/drawables under `app/src/main/res`; modify `AndroidManifest.xml`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `app/proguard-rules.pro`; create `docs/release-checklist.md`.

- [ ] **Step 1: Write failing resource/configuration checks**

```kotlin
assertEquals("ic_launcher", resources.getResourceEntryName(applicationInfo.icon))
assertEquals("mipmap", resources.getResourceTypeName(applicationInfo.icon))
```

Add a build-script assertion test or Gradle inspection that release signing reads only `HLUWEATHER_STORE_FILE`, `HLUWEATHER_STORE_PASSWORD`, `HLUWEATHER_KEY_ALIAS`, and `HLUWEATHER_KEY_PASSWORD` when all are provided.

- [ ] **Step 2: Run the resource test to verify RED**

Run: `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.HluWeatherApplicationTest`

Expected: current manifest references the system icon.

- [ ] **Step 3: Implement branded release assets**

Create adaptive foreground, monochrome, background, normal and round icon resources. Set manifest `icon` and `roundIcon`. Read signing values from environment only when all four variables exist; otherwise retain unsigned local release assembly. Set the release version before distribution and document the chosen value in the checklist.

- [ ] **Step 4: Build debug and release variants to verify GREEN**

Run: `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew assembleDebug assembleRelease lintDebug lintRelease`

Expected: all tasks succeed without committing credentials.

- [ ] **Step 5: Commit branding and checklist**

```bash
git add app/src/main app/build.gradle.kts app/proguard-rules.pro docs/release-checklist.md
git commit -m "chore: prepare weather app release"
```

### Task 3: Add Device Verification And Release Record

**Files:** Create `app/src/androidTest/java/net/droopia/hluweather/ReleaseSmokeTest.kt`, notification device tests; modify Gradle test configuration and release checklist.

- [ ] **Step 1: Write failing instrumented smoke test**

```kotlin
@Test fun launch_reaches_weather_or_location_setup_without_crashing() {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
        scenario.onActivity { assertFalse(it.isFinishing) }
    }
}
```

Add controlled checks for notification permission recovery and settings navigation; keep live weather/map calls outside automated assertions.

- [ ] **Step 2: Run instrumentation test to verify RED**

Run: `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew connectedDebugAndroidTest`

Expected: failure until Android test runner/dependencies are configured.

- [ ] **Step 3: Configure device tests and finish checklist**

Add AndroidX runner and Compose device-test dependencies. Record manual checks: signed install, TalkBack, large fonts, light/dark contrast, GPS grant/deny, map rendering, provider switch, cache fallback, notification permission, summary delivery, and alert deduplication.

- [ ] **Step 4: Run final release verification**

Run: `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease connectedDebugAndroidTest`

Expected: all configured tasks succeed. Record exact command output, APK version, device/API level, and manual results in `docs/release-checklist.md`.

- [ ] **Step 5: Commit verification record**

```bash
git add app/src/androidTest app/build.gradle.kts docs/release-checklist.md
```
