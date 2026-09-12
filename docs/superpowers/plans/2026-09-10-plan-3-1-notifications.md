# Plan 3.1: Notifications Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver opt-in thunderstorm alerts and scheduled daily summaries for the selected saved location.

**Architecture:** WorkManager workers reread Plan 2 settings, selected saved location, and provider-aware cache-backed forecasts. A notification-state repository stores the most recently delivered alert event per provider/location pair; UI settings only persist preferences and trigger scheduling reconciliation.

**Tech Stack:** WorkManager, Android notifications, DataStore, Compose, Kotlin datetime, JUnit, Robolectric.

**Spec:** `docs/superpowers/specs/2026-09-09-plan-3-notifications-release-design.md`

## Global Constraints

- Plan 2.3 must be complete first.
- Remove Trip Alerts completely from app state, UI, and persistence mapping; ignore any old preference key.
- Weather Alerts and Daily Summary default to false; summary time defaults to `08:00`.
- Never request background location and never notify for Track Me when closed.
- WorkManager delivery is best effort, not exact-alarm delivery.

---

### Task 1: Replace Notification Settings

**Files:** Modify `ui/settings/SettingsState.kt`, `SettingsRepository.kt`, `SettingsViewModel.kt`, `SettingsScreen.kt`, `navigation/HluNavHost.kt`, and existing settings tests.

- [ ] **Step 1: Write failing settings tests**

```kotlin
assertFalse(PersistedSettings().weatherAlerts)
assertEquals(kotlinx.datetime.LocalTime(8, 0), PersistedSettings().dailySummaryTime)
```

Add invalid `settings.daily_summary_time` fallback, persistence, and settings-screen absence of the Trip Alerts label.

- [ ] **Step 2: Run settings tests to verify RED**

Run: `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.ui.settings`

Expected: defaults and state shape do not match.

- [ ] **Step 3: Implement the settings migration**

Remove `tripAlerts`, its setter/callback, and `settings.trip_alerts` read/write. Add `dailySummaryTime: kotlinx.datetime.LocalTime`, serialize it as ISO `HH:mm` under `settings.daily_summary_time`, and show a time row with the copy `Best effort; delivery may be delayed by Android.`

- [ ] **Step 4: Run settings tests to verify GREEN**

Run the Step 2 command. Expected: all settings tests pass.

- [ ] **Step 5: Commit settings**

```bash
git add app/src/main/java/net/droopia/hluweather/ui/settings app/src/main/java/net/droopia/hluweather/navigation app/src/test/java/net/droopia/hluweather/ui/settings
git commit -m "feat: configure weather notifications"
```

### Task 2: Add Notification Scheduling And Delivery Boundaries

**Files:** Create `notifications/NotificationChannels.kt`, `WeatherNotificationPublisher.kt`, `NotificationScheduler.kt`, `NotificationStateRepository.kt`, and their tests; modify Gradle catalog/build and `HluWeatherApplication.kt`.

**Interfaces:**

```kotlin
data class WeatherAlertEvent(
    val provider: WeatherProvider,
    val locationId: String,
    val periodStart: Instant
)
interface NotificationStateRepository {
    suspend fun wasDelivered(eventKey: String): Boolean
    suspend fun markDelivered(eventKey: String)
}
interface NotificationScheduler { fun reconcile(settings: PersistedSettings) }
```

- [ ] **Step 1: Write failing scheduler and state tests**

```kotlin
assertEquals("weather-alert:OPEN_METEO:belgrade:1780000000", event.key)
assertEquals(Duration.ZERO, scheduler.delayUntil(LocalTime(8, 0), localDateTimeAtEight))
assertEquals(23.hours, scheduler.delayUntil(LocalTime(8, 0), localDateTimeAtNine))
```

Test independent `weather_alerts` and `daily_summary` channels, disabled-setting cancellation, and DST next-day calculation.

- [ ] **Step 2: Run infrastructure tests to verify RED**

Run: `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.notifications.NotificationSchedulerTest --tests net.droopia.hluweather.notifications.NotificationStateRepositoryTest`

Expected: missing notification infrastructure.

- [ ] **Step 3: Implement infrastructure**

Add `work-runtime-ktx` and `work-testing`. Use unique periodic work for alerts with `NetworkType.CONNECTED`; use unique one-time work for the next summary and let completed summary work enqueue its successor. Create low-importance channels and persist the last event key per provider/location in a dedicated notification DataStore.

- [ ] **Step 4: Run infrastructure tests to verify GREEN**

Run the Step 2 command. Expected: passing tests.

- [ ] **Step 5: Commit infrastructure**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/net/droopia/hluweather/notifications app/src/test/java/net/droopia/hluweather/notifications app/src/main/java/net/droopia/hluweather/HluWeatherApplication.kt
```

### Task 3: Implement Workers And Permission Recovery

**Files:** Create `notifications/WeatherAlertWorker.kt`, `DailySummaryWorker.kt`, `AppWorkerFactory.kt`, worker tests; modify application, manifest, settings UI/host, strings, and tests.

- [ ] **Step 1: Write failing worker and permission tests**

```kotlin
assertEquals(Result.success(), worker.doWork()) // disabled or no saved location
assertEquals(Result.retry(), worker.doWork()) // no live or matching cached forecast
assertEquals(1, publisher.alerts.size) // repeated event remains deduplicated
```

Test thunderstorm-only hours within 24 hours, matching cached data, denied notification permission, post failure, summary content, and successor scheduling.

- [ ] **Step 2: Run worker tests to verify RED**

Run: `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.notifications.WeatherAlertWorkerTest --tests net.droopia.hluweather.notifications.DailySummaryWorkerTest`

Expected: missing workers.

- [ ] **Step 3: Implement workers and permission path**

Add `POST_NOTIFICATIONS`. At execution, skip disabled settings, Track Me, missing location, and denied permission with `Result.success()`. Retry only transient forecast/cache failures. Atomically mark an alert event only after posting succeeds. Request permission only after enabling either setting; retain the setting on denial and expose an action for `Settings.ACTION_APP_NOTIFICATION_SETTINGS`.

- [ ] **Step 4: Run focused tests to verify GREEN**

Run: `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.notifications --tests net.droopia.hluweather.ui.settings`

Expected: all selected tests pass.

- [ ] **Step 5: Commit workers**

```bash
git add app/src/main app/src/test
```
