## Context

See `proposal.md` for motivation and `specs/hourly-forecast-table/spec.md` for behavior contracts. The main Hourly page is composed in `WeatherScreen.kt`: its `LazyColumn` currently pins the date selector and column header separately and also draws a collapsed date-selector overlay. Visible table dates already drive selected-day state, while the selector is a horizontally scrolling row that does not reveal an off-screen selected chip. `HourlyForecast.kt` contains the shared date selector, column header, and row presentation used by the standalone forecast preview and UI tests. Precipitation formatting currently combines the converted number and unit, and the column heading has no unit.

## Goals / Non-Goals

**Goals:** Keep one clearly stacked pinned date-and-column header; synchronize the selected and visible date chip with the table's visible date without causing scroll feedback; show the selected precipitation unit once in the column heading while preserving conversion and missing values.

**Non-Goals:** Redesigning forecast columns, changing the table's date/timezone model, altering daily forecasts or other precipitation displays, changing forecast data, or persisting new scroll state.

## Decisions

1. **Pin the date selector and column labels as one stacked header region.** Replace the separate/overlaid competing presentations on the main Hourly page with one opaque pinned region whose date selector sits above the column header. This keeps their relative positions stable as list rows move. Prefer this over z-order or offset fixes, which would leave duplicated header surfaces that can still cover one another. Keep the initial weather card and table scrolling behavior intact.

2. **Derive date selection from the visible table entry; scroll the table only for explicit chip taps.** Continue using the first visible date boundary/hour entry and the forecast timezone to identify the active date. Render the date chips in a horizontally lazy/scrollable container and reveal the selected chip when that date changes. Keep chip-tap navigation as the explicit action that scrolls the vertical table; visibility-driven selection must not trigger another vertical scroll. This avoids a feedback loop while preserving the existing selected-day state and cross-screen date selection behavior.

3. **Pass the configured precipitation unit to the heading and numeric cell formatter.** Format the existing converted value and precision without its suffix, and put `mm` or `in` in the precipitation heading according to settings. Keep the current missing-value placeholder. Do not change the separate precipitation-probability or evapotranspiration columns. A fixed `mm` label would conflict with the existing inch preference, while repeating a unit in cells would not meet the compact presentation requirement.

4. **Keep the reusable table presentation consistent.** Apply the same unit-aware column heading and value formatting to the `HourlyForecast` presentation that reuses these table components, and keep its sticky date/header ordering consistent. The production Hourly page remains the primary scroll-synchronization owner; avoid duplicating screen-level state in the preview-oriented composable.

## Risks / Trade-offs

- [The combined pinned region uses more vertical space on small screens] → Keep spacing compact and verify both headers remain usable at a small viewport.
- [Scroll-derived date updates could cause repeated vertical navigation] → Only chip taps initiate a vertical table scroll; assert that manual scrolling updates selection without snapping the table back.
- [A selected date chip may be outside the horizontal viewport] → Automatically reveal the selected chip and test dates near both ends of the selector.
- [Moving the unit from values could make a value ambiguous when read alone] → Keep the unit visible in the column heading and preserve it in the row/column semantics where available.

## Migration Plan

No data or persistence migration is needed. Ship as a UI-only change with the Google debug variant and focused Hourly UI tests. Rollback is a code rollback.
