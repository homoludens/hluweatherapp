# Plan 3.2 Task 3 Final Fix Report

## Status

The remaining scoped review findings are fixed. Production notification
defaults, release policy, plan/spec files, ledger files, and generated files
were not changed.

## Changes

- `AndroidWeatherNotificationPublisher` accepts an optional nullable
  `notificationTag`. The default remains untagged and uses the existing
  `notify(id, notification)` path; tagged publishing uses the Android
  `notify(tag, id, notification)` overload.
- `NotificationDeviceTest` uses a per-run unique tag with deterministic test ID
  `2_000_001`, asserts both tag and ID on the published notification, and
  cancels with the same tag/ID pair in `finally`.
- `AndroidWeatherNotificationPublisherTest` asserts default production
  publishing remains untagged and retains the expected alert ID.
- `docs/release-checklist.md` attributes both installation errors to debug APK
  attempts associated with `connectedDebugAndroidTest` and states separately
  that signed-release installation was not attempted.

## Verification

- `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew test :app:compileDebugAndroidTestKotlin`: `BUILD SUCCESSFUL`; full unit-test aggregate and androidTest Kotlin compilation passed.
- `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew lintDebug lintRelease assembleDebug assembleRelease`: `BUILD SUCCESSFUL`; both lint variants and both assemblies passed.
- `ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./scripts/verify-release-signing-matrix.sh`: all four cases passed: no credentials, partial credentials, project properties only, and all environment credentials.
- `git diff --check`: passed with no output.

## Concerns

- Connected instrumentation was not rerun because the device remains unusable;
  the checklist records the debug APK outcomes
  `INSTALL_FAILED_USER_RESTRICTED` and later
  `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.
- Signed-release installation and launch remain unchecked and unattempted.
- Existing AGP/compile-SDK, native-library stripping, and Kotlin deprecation
  warnings remain; no new warning blocked verification.
