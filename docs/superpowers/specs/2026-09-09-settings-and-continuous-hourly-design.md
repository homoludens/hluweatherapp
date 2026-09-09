# Settings And Continuous Hourly Forecast Design

## Status

Approved in conversation on 2026-09-09. This work is intentionally scoped before the real-provider and persistence work planned for Plan 2.

## Goals

1. Replace the placeholder settings destination with the supplied state-driven Compose design.
2. Make settings interactions work in memory without introducing persistence yet.
3. Render every hourly forecast day in one chronological table.
4. Keep the hourly day strip synchronized with the visible table day and use it for day jumps.
5. Preserve the existing compact hourly header, system-bar handling, and test coverage.

## Explicit Non-Goals

- DataStore or any other persistence.
- Real provider networking or provider-specific fetch behavior.
- GPS permissions, reverse geocoding, map-based location editing, or Track Me implementation.
- Notification scheduling or notification delivery.
- Cache storage beyond an in-memory clear-cache action where needed by the UI.

## Settings Architecture

### State ownership

Add an app-level, in-memory `SettingsViewModel` exposing `SettingsUiState`. The app root and the settings destination consume the same state so appearance changes apply immediately across the app. The view model has no persistence dependency; replacing its state source with DataStore is reserved for Plan 2.

Reuse existing domain models where available:

- `WeatherProvider`
- `ThemeMode`
- `WeatherLocation`

Add unit selections and the remaining settings state as simple enums/data held by `SettingsUiState`:

- Temperature: Celsius/Fahrenheit
- Wind: km/h/mph
- Distance: km/miles
- Precipitation: mm/inches
- Weather alerts, daily summary, and trip alerts
- Provider, locations, selected location, and Track Me state

The initial in-memory state uses the current mock location set and existing app defaults. Process death resets it by design.

### Screen structure

Implement `SettingsScreen` as a scrollable Material 3 `LazyColumn` matching `docs/settings_design/settings_light.jpg` and the supplied Compose reference:

- Header with back navigation and title/subtitle.
- Weather Provider card with Open-Meteo and MET Norway radio rows.
- Locations card with Track Me, saved locations, active-location radio buttons, location options, and Add Location row.
- Appearance card with System, Light, and Dark selectors.
- Units card with four two-option selectors.
- Notifications card with three switches.
- Data & Cache card with a Clear cache action.

Every interactive control gets stable semantics/test tags where useful. Selection controls expose selected/checked state to accessibility services. Future-only actions such as Add Location and location menus remain callback boundaries; they do not open a map or mutate persistent data in this work.

### Theme behavior

`HluWeatherApp` derives the active Material theme from the shared settings state:

- System follows `isSystemInDarkTheme()`.
- Light forces the light palette.
- Dark forces the dark palette.

Changing the appearance selector updates the shared in-memory state immediately.

## Continuous Hourly Forecast

### Data preparation

Stop rendering only the selected day. Prepare a chronological list from all `WeatherForecast.hourly` entries and associate each entry with its app-local `LocalDate` using the existing timezone behavior.

Derive a day index model containing:

- The day/date shown in the strip.
- The first table item index for that day.
- The date used to identify the currently visible day.

Only days with hourly entries are jump targets. The implementation must preserve the source order and use stable keys based on the date/time identity of each row.

### List layout

Use one `LazyColumn` for the complete hourly experience:

1. Full weather hero and current-weather card at the top.
2. Sticky day strip.
3. Sticky forecast column header.
4. Date boundary rows as visual separators within the same table.
5. All hourly rows for all available days in chronological order.

The existing compact hero appears only after the full header item has left the viewport. The sticky day strip reserves the compact hero height before the compact hero is drawn. The column header remains visible below the day strip while hourly rows scroll beneath it.

### Day strip behavior

- On initial entry, use `selectedDayIndex` as the initial jump target when valid.
- On day-chip click, animate the list to that day’s first hourly row and update the selected day state.
- During manual scrolling, observe the first visible hourly/date-boundary row and update the selected day strip item without triggering a second jump.
- If the forecast has no hourly rows for a daily date, that date is not presented as an enabled jump target.

The existing daily view can continue to select a day before switching to hourly; that selection becomes the initial hourly jump target.

## Error And Empty States

- Existing loading and forecast error states remain unchanged.
- An empty hourly list shows the existing screen-level content area without fabricated rows.
- Missing day metadata does not crash rendering; the available hourly dates remain scrollable and selectable.
- Settings actions that require future subsystems remain visually available but only invoke their current callback boundary.

## Testing

### Settings

- Render all reference sections and key labels.
- Verify provider, Track Me, location selection, appearance, units, notification, and clear-cache callbacks update state.
- Verify appearance selection changes the app theme through shared in-memory state.
- Verify back navigation remains functional.

### Hourly forecast

- Verify rows from all mock forecast days appear in one chronological table.
- Verify date boundary rows and stable ordering.
- Verify tapping a day chip scrolls to that day’s first row.
- Verify manual scrolling changes the selected day strip item.
- Verify initial selected-day navigation from the daily view.
- Retain existing tests for compact collapse, sticky spacing, insets, navigation, and no redundant `Hourly Forecast` heading.

## Success Criteria

- Settings visually matches the supplied design on the existing light/dark theme system.
- Settings interactions work during the current process without persistence.
- The hourly user can scroll continuously from the first available hour through the last available day.
- The day strip accurately identifies the visible day and jumps to any available day.
- All existing and new unit/UI tests, debug assembly, and lint pass.
