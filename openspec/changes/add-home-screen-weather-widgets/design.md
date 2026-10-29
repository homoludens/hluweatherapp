## Context

See `proposal.md` for motivation and the two capability specs for the behavior contract. This design is warranted because widgets introduce a new Android rendering surface and lifecycle, cross repository and location persistence boundaries, and require one new library family.

Relevant existing code:

- `HluWeatherApplication` owns the shared `CachingWeatherRepository`, settings/location repositories, DataStore, HTTP client, and WorkManager initialization. Its worker factory already serves notification workers.
- `WeatherRepository.getCachedForecast` is network-free but rejects entries older than one hour. `getForecast` refreshes through the selected source and returns matching cached weather with `isStale = true` on request failure. `ForecastCache` already persists normalized forecasts, including their location, provider, timestamp, and time zone.
- `DataStoreLocationRepository` persists saved locations, selection, and location mode, but its current fix is a `MutableStateFlow` that starts empty after process death. `PersistedSettings.trackMeEnabled` is legacy data; canonical mode comes from `LocationRepository.locationMode` and active selection from `activeLocation`.
- `HluWeatherIcon` currently owns the sole condition/day-night-to-Material-vector and tint mapping. `WeatherCondition.label()` and `data/Format.kt` own labels and unit conversion. `ui/theme/Color.kt` supplies branded card and icon colors; `Theme.kt` supplies the Material light/dark palettes.
- `MainActivity` hosts a navigation graph whose initial destination is `weather`. There is no existing widget architecture or external main-weather navigation contract.
- Existing tests use JUnit, coroutine test utilities, Robolectric, Compose UI testing, and WorkManager testing. The minimum SDK is 28.

Reference artwork: `docs/widgets/widget1.png` and `docs/widgets/ChatGPT Image Oct 28, 2026, 04_16_24 PM-1.png`. Their composition informs spacing and hierarchy, not replacement icons or a redesign. The written small-widget requirement overrides the second image's hourly strip. Shared condition labels override the artwork's example wording: clear weather is currently "Clear sky", not a widget-only "Sunny" label.

## Goals / Non-Goals

**Goals:**

- Keep one authoritative weather cache and one repository-owned provider pipeline; introduce only presentation adapters and refresh coordination.
- Make widget rendering a bounded, local-data operation independent of network availability or foreground ViewModels.
- Make request identity explicit so cache reuse, passive location fallback, and in-flight updates cannot mix providers or locations.
- Share presentation primitives across both sizes and preserve existing shared-component output for in-app callers.

**Non-Goals:**

- Per-widget provider, location, or unit configuration; widgets share the app's selection.
- Continuous tracking, background-location permission, new geocoding requests, exact alarms, foreground services, or exact refresh deadlines.
- A second persisted forecast schema, provider failover, or changing the app's cache-clearing behavior.
- Refactoring notification scheduling, altering existing app font-scaling behavior, or globally changing launch/navigation semantics.

## Decisions

### 1. Jetpack Glance with two thin providers and one presentation layer

Add the stable compatible `androidx.glance:glance-appwidget` dependency through the version catalog. Reuse the existing Compose runtime, WorkManager, and DataStore. Do not add a dependency-injection framework or a new networking dependency. Add Glance testing/preview artifacts only if necessary for reliable rendering verification; prefer the existing test stack otherwise.

Use `SmallWeatherWidget` and `MediumWeatherWidget` as `GlanceAppWidget` entry points, each with a receiver and provider XML. Share card, header, current-weather block, hourly cell, colors, empty state, and action implementation. Use `SizeMode.Responsive` and `LocalSize` rather than independent hand-maintained layouts per launcher cell count. Receivers are registered according to Android widget-provider requirements, without new permissions.

Suggested cohesive package under `app/src/main/java/net/droopia/hluweather/widget/`:

| File or responsibility | Role |
| --- | --- |
| `WeatherWidgetState.kt` | Presentation-only current/hourly state, loading/stale/empty information, and pure responsive policy |
| `WeatherWidgetRepository.kt` | Resolve settings/location and adapt repository cache/refresh results; no provider clients |
| `WeatherWidgetContent.kt` | Shared Glance card/header/current/hourly components and generic icon bitmap adapter |
| `SmallWeatherWidget.kt`, `MediumWeatherWidget.kt` | Small and medium Glance entry points |
| `WeatherWidgetReceiver.kt` | Both receiver classes and installed-instance lifecycle reconciliation |
| `WeatherWidgetUpdater.kt` | Shared schedule, immediate refresh enqueueing, and update-all coordination |
| `WeatherWidgetWorker.kt` | CoroutineWorker using application-owned repositories |
| `RefreshWeatherAction.kt` | Glance callback that enqueues work rather than holding a broadcast open for networking |

Merge files when that makes the implementation simpler; the responsibility boundaries matter more than filenames.

**Alternative rejected:** bespoke RemoteViews would duplicate more layout plumbing with no existing architecture to justify it. A single widget that changes identity when resized would not provide the two clear picker entries requested.

### 2. Additive access to the existing durable weather cache

Add a cache-only `getLastCachedForecast(provider, location)` operation to `WeatherRepository`, with a compatible default for alternate implementations. In `CachingWeatherRepository`, share provider/location validation with the existing cache read, but permit old matching entries for this new operation and compute `isStale` using the existing one-hour window. Keep the existing two-argument `getCachedForecast` acceptance boundary, return semantics, live-fetch behavior, cache key rounding, and error/cancellation handling unchanged.

Expose a narrowly scoped cache-change observation signal backed by the existing cache's DataStore, not a second stored forecast. Observe without invoking the LRU-mutating cache getter on every preference emission. Distinctly map the selected forecast's content/identity before dispatching updates so cache-entry reordering and unrelated preference writes cannot create a render loop. The app's existing `clearCache` remains authoritative; widgets do not maintain a hidden second copy to bypass it.

Both Glance entry points resolve local settings/location and load this cache before `provideContent`, then observe cache-only snapshots inside the long-lived composition. Glance updates do not restart an already-running `provideGlance`; observation is required for prompt recomposition, while update calls remain necessary when no session is running. When weather is missing or due, request asynchronous work separately; the composition itself never calls `getForecast`. The worker calls the existing `getForecast` for live updates, and then requests re-rendering from the authoritative cache. A live result that could not be persisted can be used for the immediate update, but no second durable forecast store is introduced.

Maintain only small refresh metadata separately: request identity, attempt start/end, last failed attempt, and optional short-lived result handoff. Never store converted strings as the weather source of truth. Staleness is the repository stale flag or cache age, with a visible failure state after failed attempts; the weather timestamp is always `forecast.fetchedAt`, not the attempt time. Old data is marked stale on the next rendering opportunity; no alarms are added just to change the stale badge during Doze.

**Alternative rejected:** loosening `getCachedForecast` globally would change the app's startup behavior and violate existing tests. Accessing provider caches or duplicating serialization inside the widget package would bypass repository validation and create another data stack.

### 3. Persist passive fixes without changing active-location semantics

Add read-only last-known-current-location access to `LocationRepository` with a safe default for fakes. Persist valid coordinates and optional altitude when the app already calls `setCurrentLocation`. Read the snapshot for widget resolution, but do not initialize the existing in-memory `currentLocation` from it or change `activeLocation`'s restart behavior. Invalid, non-finite, or out-of-range coordinates are ignored. Preserve cancellation and prevent persistence errors from discarding the already-available in-memory fix.

Resolve a widget request from the canonical location mode:

1. In saved mode, use the repository's canonical saved selection and fallback behavior.
2. In Track Me mode, prefer a valid live in-memory current fix, then the durable last-known fix.
3. Without a valid current fix, look up `PersistedSettings.selectedLocationId` in saved locations, then use the first remaining saved location.
4. Without any usable location, show an open-app/select-location state and skip weather requests.

Use the saved location's current name even if its cache contains an older name. For a current fix use the existing "Current location" fallback; do not reverse-geocode from the widget or pretend a saved place name describes different coordinates. A previously obtained fix is intentionally last-known, not a claim of active tracking.

**Alternative rejected:** starting `DeviceLocationSource` from a worker would wake location hardware and require lifecycle/permission logic outside the requested scope. Restoring the main app's active current location from disk would alter app and notification behavior; the separate passive accessor avoids that regression.

### 4. Pure presentation adapters and a single icon selection function

Build `WeatherWidgetState` and `WidgetHour` from `ForecastLoad`, resolved location, `PersistedSettings`, and an injected clock. Carry existing `WeatherCondition` and `isDay`; do not define a second condition enum. Keep only formatted display fields and accessibility descriptions beyond the original domain fields.

- Use `WeatherCondition.label()` for labels and existing `temperatureValueText`/`temperatureText(unit)` for conversion, rounding, and spoken units. A compact degree-only display can omit the already-formatted unit suffix where needed; conversion is never reimplemented.
- Filter hours by `time > now`, sort by instant, take up to five, and format `HH:mm` using `forecast.timezone`. Format the last-weather-update timestamp in the same zone. Use a safe system-zone fallback for invalid stored zone identifiers. Do not derive chronology from local-hour strings, which can repeat around daylight-saving changes.
- Keep precipitation probability nullable, format missing as an em dash, and distinguish it from 0 percent in visible and spoken output. Omit missing apparent temperature.
- Extract the current `HluWeatherIcon` selection into one reusable icon specification returning its existing `ImageVector` and themed tint. The app's composable consumes the same specification without changing visuals. Glance renders that exact vector to a small ARGB bitmap through a generic offscreen vector/painter adapter and uses `ImageProvider(bitmap)`. Cache only a small bounded number of rendered icons keyed by vector, tint, density, and size; no Activity or hidden Compose UI hierarchy is required.
- Reuse Material location, refresh, and droplet icons through the same generic bitmap adapter. All weather-condition branching stays in the shared selector.
- Expose the existing Material palette through a side-effect-free accessor alongside `LightHluColors`/`DarkHluColors`; Glance must not invoke the Activity/window side effects in `HluWeatherTheme`. Use the app's weather-card, primary, text, secondary, and outline tokens without changing them. Glance uses system sans-serif typography consistent with the app's default Material typography.

**Alternative rejected:** a widget-specific icon `when` expression, emoji, downloaded icon pack, or independent Celsius/Fahrenheit formula would drift from the main screen. Full domain-model copies and duplicated forecast JSON are unnecessary.

### 5. Responsive layout policy with bounded content

Represent layout choices as a small pure policy computed from available dp width, height, and effective font scaling; test it separately from Glance translation. Use a finite set of responsive sizes covering minimum, compact, normal, and wide cases, then select/hide content using the actual `LocalSize`. Include small heights as well as small widths so shrinking vertically cannot overflow.

- Small: top location row and independent refresh target; central icon/temperature row; condition and apparent temperature below. Omit apparent temperature first, then condition as height shrinks. Reduce icon and temperature size for narrow widths, long signed temperatures, and three-digit Fahrenheit values. Keep the location single-line with end ellipsis and reserve refresh width.
- Medium: header plus a single body row, with current weather approximately one third of the body width. Divide the remainder evenly among three, four, or five cells. Start with width bands near 260, 320, and 400 dp and tune the exact breakpoints against translated RemoteViews and launcher screenshots. The compact 260×110 dp branch reduces vertical card padding and the header/body gap, and scales the hourly rows to retain time, icon, temperature, and accented droplet/probability in all three columns. Padding and gap interpolate back to the normal 8 dp and 4 dp values by 144 dp high. Reduce cell count rather than squeezing five unreadable columns into a nominal 4x2 launcher grid.
- Show last-weather-update date/time only when the header has room. Compact modes can use a short update-time/stale label; small widgets prioritize weather over metadata. Never replace the forecast timestamp with the current clock just to match the reference image.
- Use subtle vertical rules and blue droplet/probability content. No scrolling container is needed. Support an empty hourly area when the cache has no future hours.
- Use a rounded background drawable or Glance background/corner support that clips correctly on API 28 as well as modern launchers. Keep refresh at least the standard accessible touch target at normal widget dimensions and avoid clipping the card into its hit area.

Normal Android font scaling is not globally capped by widget code; the app's existing scale cap remains untouched. The widget policy responds by reducing secondary content and column count where necessary.

**Alternative rejected:** hardcoding cell counts or inferring layout solely from the name "4x2" fails across launchers, display densities, resizing, and accessibility font sizes.

### 6. One hourly schedule, coalesced immediate work, no recursive refresh

`WeatherWidgetUpdater` manages one unique 60-minute periodic work request for both receiver types, with connected-network constraints and Android's normal inexact execution. Set provider XML periodic updates to zero to avoid a competing refresh timer. Enumerate installed IDs for both sizes before scheduling or canceling; `onDisabled` for one receiver alone does not mean no widgets remain. Reconcile on first add, deletion/disable, app startup, and provider update/restore as needed; WorkManager supplies reboot persistence.

Manual refresh enqueues one unique immediate work request with `KEEP` semantics for repeated taps on the same request. Serialize periodic and immediate widget repository calls through the same coordinator to avoid parallel duplicate widget fetches. Re-read current settings/location when work starts, rather than trusting stale queued inputs. Provider/location changes must not be dropped behind a kept request: after an in-flight request completes, re-resolve identity and reconcile the latest selection, enqueueing a follow-up only if its data is absent or due. No unbounded chain of taps or retries is created.

The worker publishes a bounded refreshing indicator while preserving cached data, calls the application-owned repository, and updates all installed widgets after success or stale/failure fallback. Clear active loading in completion/cancellation paths and expire orphaned attempt metadata on later renders. Connectivity constraints on periodic work prevent unnecessary offline attempts; manual work can run once while offline to give prompt cached/failure feedback. Use bounded WorkManager backoff for transient failures, not a few-minute periodic schedule or a foreground service.

Use two reconciliation paths in an application-scope collector, guarded by the existence of widgets:

- **Request changes:** provider and canonical location identity changes cause cache-first re-rendering and a coalesced asynchronous refresh when missing or due. Location identity uses the same relevant coordinates, altitude, and current-fix rounding as the repository; a label-only rename is presentation-only.
- **Presentation/cache changes:** units, theme, selected cached forecast contents, and update metadata cause re-rendering only. Distinct projection prevents unrelated settings and cache-LRU bookkeeping from creating loops. Cache writes never directly enqueue another weather request.

Check request identity again before publishing a completed refresh and before rendering. Results for obsolete requests remain only in their correctly keyed cache, never under the new location label. A settings change does not discard valid matching cached data, and app-originated new weather updates widgets without an extra live call.

Use a normal `CoroutineWorker` constructor that obtains widget dependencies from the application, leaving `AppWorkerFactory`'s existing notification branches unchanged unless an additive branch is actually needed for construction. Keep dependencies injectable for tests without a new DI framework. Widget work names are separate from all notification work names.

**Alternative rejected:** per-instance workers multiply identical requests, XML timers plus WorkManager duplicate schedules, and observing all DataStore writes as refresh triggers creates a network/render feedback loop.

### 7. Simple, isolated app-opening behavior

Use `actionStartActivity` with an explicit `MainActivity` intent for both card and location actions. Because the navigation graph already starts at `weather`, use widget-specific fresh-entry flags (`NEW_TASK | CLEAR_TASK`) so a widget tap reliably opens the main forecast even if another app screen was previously active. This reset applies only to an explicit widget-open action, not normal launcher behavior, and is covered by interaction tests. Do not add location-selection routing or a configuration activity in v1. Refresh uses `actionRunCallback` and is not nested into the open action semantically.

Provide weather/location descriptions, spoken unit-aware temperatures, meaningful precipitation descriptions, and a refresh label; mark supporting decorative glyphs accordingly to avoid duplicate speech. Render unavailable or stale status as understandable text rather than icon-only information.

**Alternative rejected:** relying on a generic launcher intent can restore Settings rather than the requested main weather screen. Adding a general deep-link/navigation framework is unnecessary for this feature.

### 8. Tests and reference fixtures use production adapters

Use deterministic normalized forecasts and an injected clock for the three requested examples. The Zaječar clear fixtures keep 23/24 degrees and the supplied five hourly values but display the shared label "Clear sky". The Trieste dark fixture uses partly cloudy at 18/17 degrees. Render examples through the same widget components, not a separate Compose imitation.

Add unit tests for adapters, units, nullable fields, time-zone/hour selection, layout policy, and request identity; repository/location tests for additive persistence access and unchanged legacy behavior; WorkManager/Robolectric tests for scheduling, coalescing, stale/failure behavior, instance lifecycle, and settings reconciliation. Exercise actual translated RemoteViews for both sizes, themes, loading/error states, descriptions, and actions where the test stack permits. Finish with launcher verification on API 28 and a modern Android version, or explicitly document any unavailable runtime coverage.

Run the narrowest relevant Google-debug tests while iterating. Because the completed feature crosses presentation, application startup, persistence, and background work, use the Google-debug suite and `:app:assembleGoogleDebug` as final verification. Never build or sign a release for this change.

### 9. Let bounded native text views fit when the launcher reapplies cached RemoteViews

The API 35 `CACHED_EMPTY` reproduction recorded at `b8d1126` shows a case that cannot be fixed reliably by the active Glance composition's render-configuration flow: after all sessions end, Android can defer waking the widget-only app process while the launcher reapplies the existing `RemoteViews` using its new font resources. More observers, explicit invalidations, or refresh work cannot guarantee a callback in this state, and polling is not an acceptable substitute. The user approved a rendering adaptation under task 5.3.

Use Glance 1.2.0's public `AndroidRemoteViews` composable to embed bounded native `TextView` slots for widget text that must survive host-only configuration reapplication. Their small set of XML role styles uses Android's native uniform text auto-sizing, with a readable minimum physical size and role-appropriate maximum `sp` sizes. The launcher can then fit glyphs to the already-assigned text bounds when it reapplies the same `RemoteViews`; no app callback, new composition, translation, network access, or worker is required. Use only public Android/Glance APIs, add no dependency or permission, and do not cap font scale globally or convert all widget text to fixed pixels.

Keep `WeatherWidgetSizePolicy` authoritative for the initial layout: it continues to choose supported dimensions, column count, content priorities, box sizes, and preferred role sizes. Native auto-sizing is only the host-owned overflow guard within those bounded boxes, preserving the large current-temperature hierarchy, accent colors, accessible descriptions/actions, and the distinction between missing and zero precipitation. The same render must continue to fit at normal and resized two-row sizes and at the compact 260×110 dp minimum.

Add a regression that translates production content once at 1.0 font scale, then changes only the host resource configuration to 1.3 and back to 1.0 and applies/reapplies that exact `RemoteViews`; do not invoke a Glance callback, recompose, or run an app worker between host changes. Check text ink/baselines and row/card bounds for current and hourly text, including signed Fahrenheit, long labels, missing probability, and both themes. Use native Robolectric graphics on SDK 34 to avoid the known SDK 35 native-font ZIP collision. Existing active-composition configuration and widget UI coverage remain required and unchanged.

## Risks / Trade-offs

- [Launcher cells do not imply fixed dp size] → Base layout on actual size, test three-to-five-hour branches, and verify real launcher resize behavior and font scaling.
- [Glance and Compose versions or vector offscreen rendering may expose compatibility issues] → Select a stable compatible Glance version, add icon/RemoteViews tests early, and keep the generic vector adapter isolated from condition selection.
- [Android background limits delay refresh] → Advertise an approximate hourly interval, preserve durable cache, show the real update time, and avoid exact alarms or aggressive retries.
- [Passive Track Me fixes can be old and have only a generic label] → Explicitly use last-known coordinates and "Current location" rather than claiming a newly acquired fix; never wake GPS or geocode just for widgets.
- [Persistence changes accidentally change existing app behavior] → Leave the normal cache method and active-location restoration untouched and retain regression tests at the one-hour boundary and process restart.
- [Cache/preference observation creates self-triggering work] → Separate data-request identity from presentation updates and test stable behavior after cache writes, LRU changes, status writes, and unrelated settings changes.
- [Foreground app and widget can independently request weather] → Reuse new matching app cache results, coalesce widget-originated work, and avoid an unrelated global repository concurrency refactor in v1.
- [A process can die during loading or a request can outlive its selection] → Persist only bounded attempt metadata, expire orphaned loading, and validate identity before presenting any completion.
- [Widget-specific fresh-entry intent resets the current app navigation stack] → Restrict flags to explicit widget taps and test that ordinary app launch/navigation behavior is unchanged.

## Migration Plan

1. Add backwards-compatible cache access and passive last-known-fix storage. Existing installations without the new optional keys continue to work; malformed or absent values resolve to no passive fix.
2. Add shared presentation extraction and widget implementation, then register receivers and metadata. There is no database migration, configuration step, new permission prompt, or change to notification schedules.
3. Validate reference and edge states, run targeted tests and final Google-debug verification, then make both widget entries available with the normal app update.
4. If rolling back, cancel the widget-specific unique work and remove or disable its receiver entries with the implementation. Optional passive-location and refresh-metadata keys can remain ignored; existing forecast cache data stays compatible. Launchers can remove unavailable widget instances through their normal UI. Do not clear user locations, settings, or forecasts as part of rollback.
