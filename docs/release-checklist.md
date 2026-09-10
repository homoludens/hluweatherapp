# Release Checklist

## Metadata

- `versionCode`: `32`
- `versionName`: `3.2.0`
- The version is the existing Plan 3.2 release identifier and is declared in
  `app/build.gradle.kts`.

## Signing

- Release signing is enabled only when all four environment variables are set:
  `HLUWEATHER_STORE_FILE`, `HLUWEATHER_STORE_PASSWORD`,
  `HLUWEATHER_KEY_ALIAS`, and `HLUWEATHER_KEY_PASSWORD`.
- Gradle project properties are not accepted for signing credentials.
- Without all four variables, `assembleRelease` remains an unsigned local
  fallback.
- Never commit the keystore or any signing secret.

## Verification

- [ ] Run debug unit tests.
- [ ] Run debug and release lint.
- [ ] Assemble debug and release variants.
- [ ] Verify a signed release with an ephemeral keystore outside the repository.
- [ ] Verify APK signing and version metadata.
