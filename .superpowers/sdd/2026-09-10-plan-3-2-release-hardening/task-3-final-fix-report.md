# Plan 3.2 Task 3 Final Fix Report

## Status

The scoped verification review findings are fixed. Production notification
behavior, release policy, plan/spec files, ledger files, and generated files
were not changed.

## Changes

- `NotificationDeviceTest` now captures the exit status of every `pm grant` or
  `pm revoke` shell command and throws on failure.
- Permission restoration verifies the final permission state in `finally`,
  including when the restoration command fails.
- The published-summary check uses dedicated deterministic test notification ID
  `2_000_001` instead of a hash-derived ID.
- `docs/release-checklist.md` now describes an activity-launch smoke test only
  and records both `INSTALL_FAILED_USER_RESTRICTED` and the later
  `INSTALL_FAILED_UPDATE_INCOMPATIBLE` result.

## Verification

- `./gradlew test --rerun-tasks`: `BUILD SUCCESSFUL`; 28 actionable tasks.
- `./gradlew :app:compileDebugAndroidTestKotlin`: passed.
- `./gradlew lintDebug lintRelease assembleDebug assembleRelease`: passed.
- `./scripts/verify-release-signing-matrix.sh`: all four signing cases passed.
- `git diff --check`: passed.

## Concerns

- Connected instrumentation was not rerun because the device remains unusable;
  the checklist records the initial `INSTALL_FAILED_USER_RESTRICTED` and later
  `INSTALL_FAILED_UPDATE_INCOMPATIBLE` outcomes.
- Existing AGP/compile-SDK and Kotlin deprecation warnings remain.
