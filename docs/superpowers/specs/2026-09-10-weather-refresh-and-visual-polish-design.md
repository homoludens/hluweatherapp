# Weather Refresh and Visual Polish Design

Date: 2026-09-10

## Goal

Make weather updates feel immediate and understandable without changing either
provider. Pull-to-refresh must trigger a fresh request, cached forecasts must
remain useful for at least one hour, and an update must not replace visible
weather with a full-screen spinner. The weather hero should occupy less space,
the decorative hero moon should be removed, and all dark-theme foregrounds
must remain readable.

## Architecture and Data Flow

`WeatherRepository` will expose two distinct operations: reading a cached
forecast for a provider/location and fetching a forecast from the network. The
network operation always bypasses the cache and persists its successful result.
`CachingWeatherRepository` will continue validating provider and location keys.

On initial load, `WeatherViewModel` reads the cache first. A cache entry whose
`fetchedAt` is no more than one hour old is applied immediately, then a
force-network refresh runs in the background. Older cache entries remain
available as failure fallback but do not qualify as the fast path. A successful
network result replaces the cached forecast. A failed refresh retains the
visible forecast and exposes the existing stale/retry treatment.

`WeatherUiState` will distinguish initial loading from refreshing. Existing
request-generation checks will continue to discard results from canceled
location/provider requests. A pull gesture and the existing retry action will
increment the refresh request and use the force-network path.

## UI Behavior

The scrollable weather content will use Material 3 pull-to-refresh behavior.
The full-screen `CircularProgressIndicator` remains only for the no-forecast
state. While a forecast is visible and a refresh is active, a thin 2dp
`LinearProgressIndicator` is pinned to the top of the weather screen.

The regular `WeatherHero` will be reduced from its current 240dp height to
exactly 176dp with tighter internal spacing. The compact scrolled hero
will retain its current behavior. Only the decorative moon circle in the hero
will be removed; the current-weather card's moon-phase indicator remains.

## Theme Contrast

All custom dark palette combinations used by hero navigation, hourly day
chips, table surfaces, and weather cards will be audited. Dark selected
surfaces will use light selected text and icons, and body text will use
Material 3 `onSurface` or `onSurfaceVariant` tokens. No dark foreground will be
left on a dark surface where it cannot be read.

## Error Handling

- No cache and a pending initial request: show the full-screen spinner.
- Fresh cache and pending refresh: show cached weather and the thin progress
  line.
- Network success: show the fresh result and stop the progress line.
- Network failure with cached/current weather: retain it, mark it stale, and
  offer retry through the existing banner.
- Network failure without weather: stop loading and show the existing error
  and retry action.

## Verification

Unit tests will cover the one-hour cache boundary, cache-first rendering before
a delayed network response, forced refresh bypassing cache, retained forecast
on refresh failure, and refresh-state transitions. Compose tests will cover
pull-to-refresh wiring, the progress line, the reduced hero, absence of the
decorative hero moon, and readable dark-mode selected controls. Full unit tests,
lint, debug assembly, and an APK test on the physical device will be run.

## Scope Constraints

Provider URLs, response models, network timeouts, font scaling, map behavior,
and unrelated settings/notification flows are out of scope. The implementation
should prefer the existing repository and Compose patterns over a new generic
reactive abstraction.
