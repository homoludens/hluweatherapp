#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
gradlew="$repo_root/gradlew"
android_home=${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK path}
apksigner="$android_home/build-tools/36.0.0/apksigner"
password=changeit
alias_name=release
temporary_directory=$(mktemp -d "${TMPDIR:-/tmp}/hluweather-signing-matrix.XXXXXX")
keystore="$temporary_directory/release.jks"

cleanup() {
    rm -rf "$temporary_directory"
}
trap cleanup EXIT

keytool -genkeypair \
    -keystore "$keystore" \
    -storepass "$password" \
    -keypass "$password" \
    -alias "$alias_name" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 1 \
    -dname "CN=HluWeather temporary release verification" \
    >/dev/null 2>&1

run_gradle_without_environment() {
    env \
        -u HLUWEATHER_STORE_FILE \
        -u HLUWEATHER_STORE_PASSWORD \
        -u HLUWEATHER_KEY_ALIAS \
        -u HLUWEATHER_KEY_PASSWORD \
        ANDROID_HOME="$android_home" \
        ANDROID_SDK_ROOT="$android_home" \
        "$gradlew" :app:clean :app:assembleRelease "$@" >/dev/null
}

run_gradle_with_environment() {
    env \
        -u HLUWEATHER_STORE_FILE \
        -u HLUWEATHER_STORE_PASSWORD \
        -u HLUWEATHER_KEY_ALIAS \
        -u HLUWEATHER_KEY_PASSWORD \
        HLUWEATHER_STORE_FILE="$keystore" \
        HLUWEATHER_STORE_PASSWORD="$password" \
        HLUWEATHER_KEY_ALIAS="$alias_name" \
        HLUWEATHER_KEY_PASSWORD="$password" \
        ANDROID_HOME="$android_home" \
        ANDROID_SDK_ROOT="$android_home" \
        "$gradlew" :app:clean :app:assembleRelease >/dev/null
}

assert_unsigned() {
    local apk="$repo_root/app/build/outputs/apk/release/app-release-unsigned.apk"
    test -f "$apk"
    if "$apksigner" verify "$apk" >/dev/null 2>&1; then
        printf 'Expected unsigned APK, but apksigner accepted: %s\n' "$apk" >&2
        exit 1
    fi
    printf 'PASS no-signing fallback: %s\n' "$apk"
}

assert_signed() {
    local apk="$repo_root/app/build/outputs/apk/release/app-release.apk"
    test -f "$apk"
    "$apksigner" verify --verbose "$apk" >/dev/null
    printf 'PASS signed release: %s\n' "$apk"
}

printf 'Signing matrix uses temporary keystore outside repository: %s\n' "$keystore"

printf 'Case: no credentials\n'
run_gradle_without_environment
assert_unsigned

printf 'Case: partial credentials\n'
env \
    -u HLUWEATHER_STORE_PASSWORD \
    -u HLUWEATHER_KEY_ALIAS \
    -u HLUWEATHER_KEY_PASSWORD \
    HLUWEATHER_STORE_FILE="$keystore" \
    ANDROID_HOME="$android_home" \
    ANDROID_SDK_ROOT="$android_home" \
    "$gradlew" :app:clean :app:assembleRelease >/dev/null
assert_unsigned

printf 'Case: project properties only\n'
run_gradle_without_environment \
    -PHLUWEATHER_STORE_FILE="$keystore" \
    -PHLUWEATHER_STORE_PASSWORD="$password" \
    -PHLUWEATHER_KEY_ALIAS="$alias_name" \
    -PHLUWEATHER_KEY_PASSWORD="$password"
assert_unsigned

printf 'Case: all four environment values\n'
run_gradle_with_environment
assert_signed
