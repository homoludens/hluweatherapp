# Release Checklist

## Metadata

- `versionCode`: `32`
- `versionName`: `3.2.0`
- Declared in `app/build.gradle.kts` for the Plan 3.2 release.

## Signing

- Release signing is enabled only when all four environment variables are
  nonblank: `HLUWEATHER_STORE_FILE`, `HLUWEATHER_STORE_PASSWORD`,
  `HLUWEATHER_KEY_ALIAS`, and `HLUWEATHER_KEY_PASSWORD`.
- Gradle project properties are not accepted for signing credentials.
- Without all four environment variables, `assembleRelease` produces the
  unsigned local fallback.
- No keystore or signing secret is committed.

## Local Verification

- [x] Focused metadata and resource tests.

  Command:

  ```text
  ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}" ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}" ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.HluWeatherApplicationTest --tests net.droopia.hluweather.ReleaseResourceSourceTest
  ```

  Result: `BUILD SUCCESSFUL`; 2 targeted test classes completed without
  failures.

- [x] Full debug unit test suite.

  Command:

  ```text
  ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}" ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}" ./gradlew test
  ```

  Result: `BUILD SUCCESSFUL`; `:app:test` completed with 28 actionable tasks
  and no test failures.

- [x] Debug and release lint, and debug and release assembly.

  Command:

  ```text
  ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}" ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}" ./gradlew assembleDebug assembleRelease lintDebug lintRelease
  ```

  Result: `BUILD SUCCESSFUL`; 94 actionable tasks, including `assembleDebug`,
  `assembleRelease`, `lintDebug`, and `lintRelease`.

  APK paths:

  ```text
  app/build/outputs/apk/debug/app-debug.apk
  app/build/outputs/apk/release/app-release-unsigned.apk
  ```

- [x] Signing matrix for no credentials, partial credentials, project
  properties only, and all four environment values.

  Command:

  ```text
  ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}" ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}" ./scripts/verify-release-signing-matrix.sh
  ```

  Result:

  ```text
  Signing matrix uses a temporary keystore outside the repository under /tmp.
  PASS no-signing fallback: app/build/outputs/apk/release/app-release-unsigned.apk
  PASS no-signing fallback: app/build/outputs/apk/release/app-release-unsigned.apk
  PASS no-signing fallback: app/build/outputs/apk/release/app-release-unsigned.apk
  PASS signed release: app/build/outputs/apk/release/app-release.apk
  ```

  The script creates and removes its keystore outside the repository under
  `/tmp`.

- [x] Signed release APK verification.

  Command:

  ```text
  "${ANDROID_HOME:-$HOME/Android/Sdk}/build-tools/36.0.0/apksigner" verify --verbose app/build/outputs/apk/release/app-release.apk
  ```

  Result:

  ```text
  Verifies
  Verified using v1 scheme (JAR signing): false
  Verified using v2 scheme (APK Signature Scheme v2): true
  Verified using v3 scheme (APK Signature Scheme v3): false
  Verified using v3.1 scheme (APK Signature Scheme v3.1): false
  Verified using v4 scheme (APK Signature Scheme v4): false
  Verified for SourceStamp: false
  Number of signers: 1
  ```

- [x] Unsigned release limitation documented and verified.

  Command:

  ```text
  "${ANDROID_HOME:-$HOME/Android/Sdk}/build-tools/36.0.0/apksigner" verify --verbose app/build/outputs/apk/release/app-release-unsigned.apk
  ```

  Expected result for the intentionally unsigned local fallback: exit code
  `1`, `DOES NOT VERIFY`, `ERROR: Missing META-INF/MANIFEST.MF`.

- [x] Release version metadata verified from the signed APK.

  Command:

  ```text
  "${ANDROID_HOME:-$HOME/Android/Sdk}/build-tools/36.0.0/aapt" dump badging app/build/outputs/apk/release/app-release.apk
  ```

  Relevant output:

  ```text
  package: name='net.droopia.hluweather' versionCode='32' versionName='3.2.0' platformBuildVersionName='17' platformBuildVersionCode='37' compileSdkVersion='37' compileSdkVersionCodename='17'
  ```

## Device Verification

The local environment detected a connected Android device (`M2012K11AG`, API
33), but it was unusable for debug verification. The initial direct adb attempt
for the `connectedDebugAndroidTest` debug APK returned
`INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`; a later bounded
retry of that debug APK installation returned
`INSTALL_FAILED_UPDATE_INCOMPATIBLE` because the existing app signature did not
match. These outcomes were from debug APK attempts only; no signed-release
installation was attempted. No AVD was available.

- [ ] `connectedDebugAndroidTest` activity-launch smoke test.
- [ ] Install and launch a signed release APK manually on an unrestricted
  device. This remains unattempted; the recorded installation errors belong to
  the `connectedDebugAndroidTest` debug APK attempts and do not prove
  signed-release execution.
- [ ] TalkBack labels and actions.
- [ ] Large-font layout and text wrapping.
- [ ] Light/dark contrast.
- [ ] GPS grant and deny flows.
- [ ] Map rendering.
- [ ] Provider switch.
- [ ] Cache fallback.
- [ ] Notification permission recovery.
- [ ] Daily summary delivery.
- [ ] Weather alert deduplication.

## Task 3 Verification Record

Verification date: `2026-09-10`.

### Device-Test Configuration

- [x] AndroidX instrumentation runner configured as
  `androidx.test.runner.AndroidJUnitRunner`.
- [x] AndroidX core, runner, rules, AndroidX JUnit, JUnit, and Compose UI test
  dependencies are available to the `androidTest` source set.
- [x] Device-test sources compile.

  Command:

  ```text
  ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}" ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}" ./gradlew :app:compileDebugAndroidTestKotlin
  ```

  Result: `BUILD SUCCESSFUL in 4s`; 24 actionable tasks, 3 executed and 21
  up-to-date.

- [x] `MainActivityLaunchTest` checks only that `MainActivity` does not finish
  immediately after launch; it does not assert settings navigation or a signed
  release execution.
- [x] `NotificationDeviceTest` uses isolated test-owned channels and a test-owned
  notification ID, cleans them up in `finally`, proves the exact published
  notification, makes delivery permission an explicit prerequisite, and covers
  Android 13+ permission denial/recovery separately.
- [x] Existing Robolectric/unit coverage was reviewed for the remaining
  deterministic seams:
  `SettingsScreenTest` covers blocked-notification settings recovery,
  `AndroidWeatherNotificationPublisherTest` covers the MainActivity tap
  PendingIntent, `WeatherAlertWorkerTest` covers permission skips/cache use/
  deduplication, `DailySummaryWorkerTest` covers summary delivery and
  rescheduling, `NotificationSchedulerTest` covers scheduling and cancellation,
  `DeviceLocationSourceTest` covers provider selection, and
  `PrimaryFlowsAccessibilityTest` covers labels, roles, touch targets, large
  fonts, and constrained layouts.

### Fresh Local Verification

- [x] Full requested local matrix.

  Command:

  ```text
  ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}" ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}" ./gradlew testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease
  ```

  Result: `BUILD SUCCESSFUL in 46s`; 102 actionable tasks, 6 executed and 96
  up-to-date. `testDebugUnitTest`, `lintDebug`, `lintRelease`, `assembleDebug`,
  and `assembleRelease` completed successfully.

- [x] Full unit-test aggregate.

  Command:

  ```text
  ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}" ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}" ./gradlew test
  ```

  Result: `BUILD SUCCESSFUL in 1m 1s`; 28 actionable tasks, 8 executed and 20
  up-to-date, completed without test
  failures.

- [x] Four-case signing matrix.

  Command:

  ```text
  ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}" ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}" ./scripts/verify-release-signing-matrix.sh
  ```

  Result:

  ```text
  Signing matrix uses a temporary keystore outside the repository under /tmp.
  Case: no credentials
  PASS no-signing fallback: app/build/outputs/apk/release/app-release-unsigned.apk
  Case: partial credentials
  PASS no-signing fallback: app/build/outputs/apk/release/app-release-unsigned.apk
  Case: project properties only
  PASS no-signing fallback: app/build/outputs/apk/release/app-release-unsigned.apk
  Case: all four environment values
  PASS signed release: app/build/outputs/apk/release/app-release.apk
  ```

  The temporary keystore was outside the repository and removed by the script.

- [x] Signed APK signature verification.

  Command:

  ```text
  "${ANDROID_HOME:-$HOME/Android/Sdk}/build-tools/36.0.0/apksigner" verify --verbose app/build/outputs/apk/release/app-release.apk
  ```

  Result: `Verifies`; v2 `true`; v1, v3, v3.1, v4, and SourceStamp `false`; one
  signer.

- [x] Unsigned fallback limitation verified.

  Command:

  ```text
  env -u HLUWEATHER_STORE_FILE -u HLUWEATHER_STORE_PASSWORD -u HLUWEATHER_KEY_ALIAS -u HLUWEATHER_KEY_PASSWORD ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}" ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}" ./gradlew :app:clean :app:assembleRelease
  "${ANDROID_HOME:-$HOME/Android/Sdk}/build-tools/36.0.0/apksigner" verify --verbose app/build/outputs/apk/release/app-release-unsigned.apk
  ```

  Build result: `BUILD SUCCESSFUL in 40s`; 48 actionable tasks, 47 executed
  and 1 up-to-date. Signature result: `DOES NOT VERIFY`,
  `ERROR: Missing META-INF/MANIFEST.MF`, exit code `1`.

- [x] Signed APK metadata verified.

  Command:

  ```text
  "${ANDROID_HOME:-$HOME/Android/Sdk}/build-tools/36.0.0/aapt" dump badging app/build/outputs/apk/release/app-release.apk
  ```

  Relevant output:

  ```text
  package: name='net.droopia.hluweather' versionCode='32' versionName='3.2.0' platformBuildVersionName='17' platformBuildVersionCode='37' compileSdkVersion='37' compileSdkVersionCodename='17'
  sdkVersion:'28'
  targetSdkVersion:'36'
  launchable-activity: name='net.droopia.hluweather.MainActivity'
  ```

- [x] Release APK paths recorded.

  ```text
  app/build/outputs/apk/debug/app-debug.apk
  app/build/outputs/apk/release/app-release.apk
  app/build/outputs/apk/release/output-metadata.json
  ```

### Device Results

- [ ] Connected instrumentation tests. The exact command was attempted:

  ```text
  ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}" ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}" ./gradlew connectedDebugAndroidTest
  ```

  The device was detected as `M2012K11AG`, API `33`, but it was unusable because
  the debug APK installation was blocked in the earlier direct adb attempt:
  `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`. A later bounded
  retry of the debug APK installation failed in `41s` with
  `INSTALL_FAILED_UPDATE_INCOMPATIBLE` because the existing app signature did
  not match; test-APK cleanup also returned `DELETE_FAILED_INTERNAL_ERROR`.
  It started `0` tests, so no instrumentation assertion result was obtained.
  `connectedDebugAndroidTest` installs the debug APK, not the signed release
  APK.
- [ ] Signed release install and launch. Not attempted. No signed-release
  installation command was run; the two installation errors above came only
  from debug APK attempts.
- [ ] TalkBack labels and actions. Manual device check remains open.
- [ ] Large-font layout and text wrapping. Automated constrained-font coverage
  passed; manual device check remains open.
- [ ] Light/dark contrast. Manual device check remains open.
- [ ] GPS grant and deny flows. Automated permission/provider unit coverage
  passed; manual device check remains open.
- [ ] Map rendering. Manual device check remains open.
- [ ] Provider switch. Automated provider UI/repository coverage passed; manual
  device check remains open.
- [ ] Cache fallback. Automated caching repository and worker coverage passed;
  manual device check remains open.
- [ ] Notification permission recovery. Automated blocked-settings recovery
  coverage passed; manual device check remains open.
- [ ] Daily summary delivery. Automated worker/scheduling coverage passed;
  manual device check remains open.
- [ ] Weather alert deduplication. Automated worker/state coverage passed;
  manual device check remains open.

### Hygiene

- [x] `bash -n scripts/verify-release-signing-matrix.sh` completed with no
  output.
- [x] `git diff --check` completed with no output.
- [x] No keystore, credential value, or signing artifact is tracked. The
  signing matrix temporary keystore was created below `/tmp` and cleaned up.
