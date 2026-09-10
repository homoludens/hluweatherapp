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
  ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew :app:testDebugUnitTest --tests net.droopia.hluweather.HluWeatherApplicationTest --tests net.droopia.hluweather.ReleaseResourceSourceTest
  ```

  Result: `BUILD SUCCESSFUL`; 2 targeted test classes completed without
  failures.

- [x] Full debug unit test suite.

  Command:

  ```text
  ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew test
  ```

  Result: `BUILD SUCCESSFUL`; `:app:test` completed with 28 actionable tasks
  and no test failures.

- [x] Debug and release lint, and debug and release assembly.

  Command:

  ```text
  ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./gradlew assembleDebug assembleRelease lintDebug lintRelease
  ```

  Result: `BUILD SUCCESSFUL`; 94 actionable tasks, including `assembleDebug`,
  `assembleRelease`, `lintDebug`, and `lintRelease`.

  APK paths:

  ```text
  /home/homoludens/projekti/hluweatherapp/.worktrees/plan-3-2-release-hardening/app/build/outputs/apk/debug/app-debug.apk
  /home/homoludens/projekti/hluweatherapp/.worktrees/plan-3-2-release-hardening/app/build/outputs/apk/release/app-release-unsigned.apk
  ```

- [x] Signing matrix for no credentials, partial credentials, project
  properties only, and all four environment values.

  Command:

  ```text
  ANDROID_HOME=/home/homoludens/Android/Sdk ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk ./scripts/verify-release-signing-matrix.sh
  ```

  Result:

  ```text
  Signing matrix uses temporary keystore outside repository: /tmp/hluweather-signing-matrix.T5NTz9/release.jks
  PASS no-signing fallback: /home/homoludens/projekti/hluweatherapp/.worktrees/plan-3-2-release-hardening/app/build/outputs/apk/release/app-release-unsigned.apk
  PASS no-signing fallback: /home/homoludens/projekti/hluweatherapp/.worktrees/plan-3-2-release-hardening/app/build/outputs/apk/release/app-release-unsigned.apk
  PASS no-signing fallback: /home/homoludens/projekti/hluweatherapp/.worktrees/plan-3-2-release-hardening/app/build/outputs/apk/release/app-release-unsigned.apk
  PASS signed release: /home/homoludens/projekti/hluweatherapp/.worktrees/plan-3-2-release-hardening/app/build/outputs/apk/release/app-release.apk
  ```

  The script creates and removes its keystore outside the repository under
  `/tmp`.

- [x] Signed release APK verification.

  Command:

  ```text
  /home/homoludens/Android/Sdk/build-tools/36.0.0/apksigner verify --verbose /home/homoludens/projekti/hluweatherapp/.worktrees/plan-3-2-release-hardening/app/build/outputs/apk/release/app-release.apk
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
  /home/homoludens/Android/Sdk/build-tools/36.0.0/apksigner verify --verbose /home/homoludens/projekti/hluweatherapp/.worktrees/plan-3-2-release-hardening/app/build/outputs/apk/release/app-release-unsigned.apk
  ```

  Expected result for the intentionally unsigned local fallback: exit code
  `1`, `DOES NOT VERIFY`, `ERROR: Missing META-INF/MANIFEST.MF`.

- [x] Release version metadata verified from the signed APK.

  Command:

  ```text
  /home/homoludens/Android/Sdk/build-tools/36.0.0/aapt dump badging /home/homoludens/projekti/hluweatherapp/.worktrees/plan-3-2-release-hardening/app/build/outputs/apk/release/app-release.apk
  ```

  Relevant output:

  ```text
  package: name='net.droopia.hluweather' versionCode='32' versionName='3.2.0' platformBuildVersionName='17' platformBuildVersionCode='37' compileSdkVersion='37' compileSdkVersionCodename='17'
  ```

## Device Verification

The local environment has no connected device or available AVD. These checks
remain blocking and intentionally unchecked.

- [ ] `connectedDebugAndroidTest` launch and navigation smoke test.
- [ ] Install and launch the signed APK on a device.
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
