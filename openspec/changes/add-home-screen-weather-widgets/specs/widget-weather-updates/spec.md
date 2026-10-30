## Purpose

Keep home-screen widgets aligned with HluWeather's selected weather, settings, and location using cached forecasts and battery-conscious background updates.

## ADDED Requirements

### Requirement: Use the app's weather pipeline

Widget weather SHALL come from the same repository and normalized forecast model as the app, honoring the selected Open-Meteo or MET.no provider. Widgets SHALL NOT implement provider-specific networking, instantiate a second weather data stack, or perform weather requests during layout rendering. Adding widgets SHALL preserve existing app forecast, cache-policy, location-selection, and notification behavior.

#### Scenario: Selected provider is MET.no
- **WHEN** MET.no is selected and a widget refreshes
- **THEN** it requests normalized weather through the app's repository for MET.no
- **AND** unsupported fields remain unavailable rather than being fetched through a separate provider stack

#### Scenario: No widgets installed
- **WHEN** the user has no installed weather widgets
- **THEN** the app continues its existing behavior without widget-specific periodic weather requests or GPS activity

### Requirement: Cache-first rendering across process restarts

A widget SHALL render a matching persisted forecast, including one older than the app's normal fresh-cache window, before waiting for any network request. An old forecast SHALL remain visibly distinguishable as stale. Cache matching SHALL respect provider and location identity, including relevant coordinates and altitude, so data from another request is not mislabeled. Cache-only rendering SHALL work after process death and SHALL NOT alter existing app callers' freshness policy.

#### Scenario: Offline launch with old cache
- **WHEN** a widget renders after process restart with no connectivity and a matching forecast older than one hour
- **THEN** it displays that cached forecast with a stale indication without first attempting a weather request in the rendering path

#### Scenario: Mismatched cache
- **WHEN** the only cached forecast belongs to a different provider or location, or the saved location's relevant coordinates or altitude have changed
- **THEN** that forecast is not displayed as weather for the new selection

### Requirement: Resolve saved location consistently

In saved-location mode widgets SHALL use the app's canonical selected saved location. If the selected saved location no longer exists, they SHALL use the same remaining-location fallback as the app. A location rename SHALL update the widget label without requiring new weather data.

#### Scenario: Selected location changes
- **WHEN** the user selects another saved location
- **THEN** widgets immediately reconcile to that location's cached weather or an honest unavailable state
- **AND** they do not label the previous location's temperature with the new name

#### Scenario: Selected location deleted
- **WHEN** the user deletes the selected location and another saved location remains
- **THEN** widgets follow the app's replacement selection

### Requirement: Passive Track Me fallback

In Track Me mode widgets SHALL use the latest valid current location already obtained by the app, including a passively persisted last-known fix after process restart. If no valid fix is available, widgets SHALL fall back to the last selected saved location, then the app's first saved location if necessary. Widgets SHALL NOT initiate device-location acquisition, add background-location permissions, or start GPS tracking. Reading a widget-only last-known fix SHALL NOT change the main app's active-location behavior.

#### Scenario: App has a valid current fix
- **WHEN** Track Me is enabled and the app has already obtained a valid current location
- **THEN** the widget uses that fix without requesting a new one

#### Scenario: Track Me after process restart
- **WHEN** Track Me is enabled and the app process restarts with a valid persisted last-known fix
- **THEN** the widget can use that passive fix and matching cached weather without waking GPS
- **AND** the main app retains its existing current-location acquisition behavior

#### Scenario: No valid current fix
- **WHEN** Track Me is enabled but no valid current fix exists and a previously selected saved location remains
- **THEN** the widget displays weather for that saved location with its correct name

### Requirement: Honest empty and unavailable states

When no usable location or matching weather exists, widgets SHALL show a compact actionable state instead of fabricated weather or an empty card. With no location they SHALL prompt the user to select one in the app. With a location but no forecast they SHALL identify the location and provide an app-opening action; the small widget's existing manual refresh control SHALL remain available as its retry action, while the medium widget SHALL have no manual refresh control. An initial weather request SHALL be asynchronous to rendering.

#### Scenario: Fresh install without locations
- **WHEN** a user adds a widget before selecting any location
- **THEN** the widget prompts them to open HluWeather and choose a location
- **AND** it does not request weather for an invented default location

#### Scenario: Location without a forecast
- **WHEN** a location is selected but no matching cached forecast exists
- **THEN** the widget shows the location and a no-weather state promptly
- **AND** an asynchronous refresh can subsequently replace it with weather

### Requirement: Battery-conscious periodic lifecycle

Installed widgets SHALL share a single periodic weather-refresh schedule with a nominal interval between 30 and 60 minutes, subject to Android scheduling and connectivity constraints. Adding additional widget instances SHALL NOT multiply periodic network requests. Adding the first widget SHALL establish the schedule, and removing the last widget of either size SHALL cancel widget refresh work. Scheduling SHALL recover after reboot or process restart without relying on the foreground app remaining open.

#### Scenario: Multiple widget instances
- **WHEN** two small widgets and one medium widget are installed
- **THEN** they share one periodic refresh cycle for the app's selected location and provider
- **AND** all installed instances receive the resulting update

#### Scenario: Remove widgets across both sizes
- **WHEN** the last small widget is removed but a medium widget remains
- **THEN** periodic widget refresh remains scheduled
- **WHEN** the final remaining medium widget is removed
- **THEN** widget-specific periodic and pending refresh work is canceled

#### Scenario: Android defers a scheduled update
- **WHEN** Doze or lack of connectivity delays the nominal update interval
- **THEN** cached weather remains visible with its real update time where space permits and is marked stale at the next rendering opportunity once it exceeds the freshness window
- **AND** widgets do not compensate with frequent polling or GPS requests

### Requirement: Small-widget manual refresh preserves the displayed forecast

The small widget's refresh control SHALL request a new forecast through the existing repository and update every installed weather widget after completion. Existing valid weather SHALL remain visible during refresh. A subtle refreshing state SHALL indicate an in-progress request, repeated taps SHALL be coalesced, and the refreshing state SHALL clear on completion or failure without becoming permanent after interruption. The medium widget SHALL continue to receive automatic and relevant-change updates but SHALL NOT expose a manual refresh control.

#### Scenario: Successful refresh
- **WHEN** the user taps refresh while cached weather is displayed
- **THEN** the current forecast remains visible with a subtle refreshing indication
- **AND** the resulting forecast and last-weather-update time replace it after the repository succeeds

#### Scenario: Repeated refresh taps
- **WHEN** the user taps refresh repeatedly during one in-progress request
- **THEN** the taps do not start parallel duplicate weather requests or an unbounded refresh queue

#### Scenario: Interrupted refresh
- **WHEN** Android interrupts a refresh or recreates the process during it
- **THEN** cached weather remains usable and the widget does not remain permanently stuck in a loading state

### Requirement: Refresh failure retains last valid matching data

A refresh failure SHALL retain the last valid forecast for the same provider and location, preserve its original weather timestamp, end loading, and show a subtle stale or failure indication. A repository result marked stale SHALL NOT be presented as a successful fresh update. The medium widget's stale, failed, or in-progress status SHALL use accessible non-text status glyphs rather than a header label or refresh control; its status SHALL remain distinguishable without relying on color alone. Without a matching valid forecast the widget SHALL remain in an actionable unavailable state: the medium widget SHALL offer its app-opening action, and the small widget SHALL retain its manual refresh retry action. Cancellation SHALL NOT discard the last valid forecast.

#### Scenario: Repository falls back to cache
- **WHEN** a refresh returns an older cached forecast marked stale because the provider request failed
- **THEN** that forecast remains visible as stale with its actual last-weather-update time
- **AND** the widget does not advance the weather timestamp to the refresh attempt time
- **AND** the medium widget communicates stale/failure status accessibly without adding header status text or a refresh control

#### Scenario: Request fails without cache
- **WHEN** the refresh fails and no matching weather has been stored
- **THEN** the widget shows an actionable unavailable state rather than temperatures from a previous selection
- **AND** the medium widget provides its app-opening action without a refresh control, while the small widget retains its refresh retry action

### Requirement: Relevant changes update without refresh loops

Provider, canonical location, temperature-unit, theme, and newly cached app-weather changes SHALL reconcile installed widgets without waiting for the next periodic refresh. Presentation-only changes SHALL reuse cached weather without requesting another forecast solely for the change. Forecast-cache writes, refresh-state writes, and unrelated settings changes SHALL NOT cause recursive weather refreshes. A completion for an obsolete location or provider SHALL NOT overwrite the current selection.

#### Scenario: Provider changes while refreshing
- **WHEN** a refresh for one provider is running and the user selects the other provider
- **THEN** the widget switches to the new provider's matching data or unavailable state
- **AND** the old request's later completion is not presented as the new provider's forecast

#### Scenario: New forecast from the foreground app
- **WHEN** the app successfully stores newer weather for the widget's selected location and provider
- **THEN** installed widgets render that weather without issuing another provider request just because the cache changed
- **AND** a fresh matching forecast fetched strictly after a completed failed refresh supersedes that failure in widget status, without clearing an active or newer failure
- **AND** stale, equal-time, older, mismatched, or missing cached weather does not supersede the failure

#### Scenario: Presentation-only or unrelated setting change
- **WHEN** temperature units or theme change, or an unrelated setting such as notification preferences changes
- **THEN** a relevant presentation change updates the widget from cache
- **AND** no additional weather request is issued solely because of those changes

### Requirement: Upcoming hourly forecasts use location time

Hourly weather SHALL be selected chronologically from forecast instants later than the current instant and displayed in the forecast location's time zone. Expired hours SHALL NOT be recycled to fill columns. Missing probability SHALL remain distinct from zero, and stale hourly forecasts with no future entries SHALL leave current weather visible without fabricated hourly data.

#### Scenario: Device and location time zones differ
- **WHEN** the device's time zone differs from the forecast location's time zone
- **THEN** the displayed hourly labels and weather-update timestamp use the forecast location's zone
- **AND** hourly selection remains chronological across midnight and daylight-saving transitions

#### Scenario: Cached hours have all expired
- **WHEN** an offline cached forecast has no hourly entries later than now
- **THEN** current cached weather remains visible as stale
- **AND** the widget does not repeat expired hours to fill its hourly area
