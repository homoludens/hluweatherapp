# Plan 3: Notifications And Release Completion Design

## Status

Approved in conversation on 2026-09-09. This plan follows Plan 2 and delivers
weather notifications plus release-quality accessibility and device validation.

## Goals

1. Add useful weather-alert and daily-summary notifications without background
   location tracking.
2. Remove the unsupported Trip Alerts setting and behavior.
3. Prepare a branded, accessible, testable release build.

## Non-Goals

- Background location tracking or background Track Me behavior.
- Notifications for changing city, GPS movement, or a Track Me location while
  the app is closed.
- Weather alerts from a provider other than the selected provider.
- Exact-to-the-minute notification delivery guarantees.

## Notification Settings

Trip Alerts is removed from the settings UI, `SettingsUiState`, persistence
mapping, and tests. Existing installations may retain an unused preference key;
no migration is required because it has no effect.

Weather Alerts and Daily Summary default to disabled. Enabling either requires
Android notification permission on Android 13 and later. If permission is
denied, preserve the enabled preference but clearly explain that notifications
are blocked and offer a path to system app settings. The weather screen remains
fully usable.

Daily Summary gains a local delivery-time preference with a default of 08:00.
It reports current conditions and the day's high, low, and precipitation for the
currently selected saved location.

## Scheduling And Delivery

Use WorkManager for best-effort, battery-aware delivery. It may run later than
the chosen time; the settings UI must not promise exact alarms. Workers use the
Plan 2 weather repository and cache semantics. They never initiate a new device
location request and therefore work only for the selected saved location.

Weather Alert work checks the selected provider forecast periodically for a
thunderstorm condition in the next 24 hours. It records a deduplication key
containing the provider, location, and forecast period, preventing repeated
alerts for the same event. A worker network failure is retried through
WorkManager and is never reported as a foreground weather-screen error.

Daily Summary work is scheduled for the next selected local time and schedules
the following delivery after completion. This avoids pretending periodic work
is exact while keeping one clear delivery sequence. Notification channels keep
alerts and summaries independently controllable by the system.

## Release Hardening

- Replace the system default launcher icon with adaptive application branding.
- Set release version metadata and verify a non-debug release APK can be built
  and installed with the project signing configuration.
- Verify TalkBack labels and states, 48 dp touch targets, keyboard/focus order,
  large font scales, contrast, and light/dark themes across weather, settings,
  map, quick-switcher, and location picker.
- Add device/emulator smoke checks for notification permission, scheduled
  summary, severe-weather deduplication, denied GPS, cache fallback, provider
  switch, and map rendering.
- Record a release checklist covering network-free unit tests, location and
  notification permission behavior, privacy disclosure needs, versioning, and
  installation validation.

## Error Handling

- Permission denial does not crash or disable settings; the notification remains
  suppressed until permission is granted.
- A missing saved location cancels or skips worker execution.
- A live provider failure may use only a matching Plan 2 cache entry; otherwise
  a worker retries silently and sends no incomplete notification.
- Notification posting failures are logged for diagnosis and do not affect the
  weather UI.

## Testing

- Unit tests cover notification settings persistence, schedule calculation,
  worker input validation, forecast condition detection, deduplication, cache
  fallback, and retry behavior.
- Robolectric or focused Android tests cover channels and Android 13+
  permission states.
- Instrumented/device smoke tests verify scheduled delivery, notification tap
  behavior, denied permission recovery, and accessibility flows.
- Run debug unit tests, lint, debug/release assembly, and manual installation
  before release.

## Acceptance Criteria

- Only Weather Alerts and Daily Summary remain as notification options.
- Daily Summary uses the configured local time on a best-effort basis for the
  selected saved location.
- Weather Alerts send at most one notification per provider/location/forecast
  thunderstorm event.
- No closed-app background location tracking is added.
- The app has branded adaptive icon assets, accessible primary flows, a tested
  installable release build, and an explicit release verification record.
