# Location Provider Distributions Design

## Goal

Make current-location lookup reliable indoors and on devices without GPS while
shipping a Google Play build with fused location and an F-Droid build without
Google Play Services.

## Constraints

- Keep the existing `DeviceLocationSource` interface and its `GpsResult` states.
- Keep only foreground coarse/fine location permissions.
- Track Me remains foreground-only and must stop requesting updates when its
  lifecycle collection stops.
- The F-Droid variant must not compile or package `play-services-location`.
- Do not log coordinates or other sensitive location data in release builds.
- Preserve the current picker, repository, ViewModel, and refresh behavior.

## Distribution Architecture

Add a `distribution` flavor dimension with `google` and `fdroid` flavors.

- `googleRelease` uses `FusedLocationProviderClient` from
  `play-services-location`.
- `fdroidRelease` has no Google Play Services dependency and uses the existing
  Android `LocationManager` APIs as its open-platform implementation.
- Shared code depends only on `DeviceLocationSource`; the concrete class is
  supplied by the selected flavor source set.
- Both variants retain the same application ID. They are separate distribution
  channels, not side-by-side installs, because their signing keys differ.

The flavor-specific implementations use the same class/package name where the
application constructs the source, so no UI or ViewModel code needs to know
which provider is present. The shared interface remains straightforward to
test with fakes.

## Location Behavior

The Google implementation checks permission and whether the device location
service is enabled before accessing the fused client.

- `currentLocation()` first requests the fused client's cached `lastLocation`.
- If no cached fix is available, it requests a fresh fix with a bounded timeout.
- Fine permission uses high accuracy; coarse-only permission uses balanced
  power accuracy.
- `foregroundLocations()` emits a cached fix when available, then registers
  continuous fused updates using the same permission-based priority.
- Cancellation, lifecycle collection completion, timeout, and provider errors
  remove the active callback and map to the existing `GpsResult` values.

The F-Droid implementation keeps the same externally visible behavior as far
as Android platform APIs allow, but uses `LocationManager` without pretending
that a GPS provider is required. It selects an enabled network provider first
and GPS only when fine permission is available, preserving compatibility for
devices without Google Play Services.

## Error Handling And Logging

Permission failures return `GpsResult.PermissionRequired`. Disabled system
location returns `GpsResult.LocationDisabled`. Missing fixes, provider errors,
and timed-out requests return `GpsResult.Unavailable`. Cancellation is not
converted into an application error.

Provider diagnostics use debug-only Android logging. Release builds do not log
latitude, longitude, altitude, or raw provider exceptions.

## Testing

Shared ViewModel tests remain unchanged except where a provider-specific
integration exposes a real behavior issue. Provider tests cover:

- coarse versus fine priority/provider selection;
- cached location success;
- fresh-fix fallback;
- timeout and callback cleanup;
- permission and disabled-service states;
- provider/security failures;
- foreground cancellation cleanup.

Google provider tests inject a small fused-client gateway rather than requiring
Google Play Services at test runtime. F-Droid tests exercise the
`LocationManager` implementation. Gradle variant tests verify both flavor
source sets compile without cross-referencing unavailable classes.

## Verification And Release

Run focused provider tests, the full unit suite, lint, and both debug builds.
When signing credentials are available through `.env`, build and inspect both
release variants. The release APKs must be checked to ensure the F-Droid
artifact has no Google Play Services location dependency.
