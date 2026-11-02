# main-bottom-navigation Specification

## Purpose

Provide a compact, consistent entry point to forecasts, the radar map, and route planning across the app's main screens.

## Requirements

### Requirement: Compact main application shell
The four main destinations SHALL share a compact blue/purple gradient header approximately 56–64 dp high excluding status bar, with the app title and working settings control only. The old top tabs and subtitle SHALL be removed. The shell SHALL respect system bars and display cutouts and remain legible in light and dark themes.

#### Scenario: Main screen header
- **WHEN** a user opens any main destination
- **THEN** the compact title and settings header appears without the subtitle or top navigation tabs

### Requirement: Four persistent top-level destinations
The app SHALL provide a compact themed bottom navigation bar with equally spaced Hourly, Daily, Map, and Route destinations, icons and labels, and a rounded selected indicator. Hourly SHALL be the default unless existing settings specify otherwise. The bar SHALL remain visible on all four destinations, respect navigation insets, indicate the current destination, and avoid duplicate back-stack entries; secondary screens MAY hide it.

#### Scenario: Switch destinations
- **WHEN** the user taps any bottom destination from a main screen
- **THEN** the corresponding existing screen is shown with that destination selected and no duplicate top-level stack entry

#### Scenario: Navigate to settings and back
- **WHEN** the user opens settings and returns
- **THEN** the previous main destination is restored and back navigation remains sensible

### Requirement: Preserve screen functionality and state
Hourly SHALL retain its weather card, date selector, forecast table and scrolling; Daily SHALL retain its forecast cards and scrolling; Map SHALL retain radar, location, gestures and controls; Route SHALL reuse the existing route planner and calculation. Screen state and scroll position SHALL be preserved when practical without unnecessary requests. Main content and controls SHALL fit above the bottom navigation on small screens.

#### Scenario: Switch away and back
- **WHEN** a user switches away from a forecast, map, or route and returns
- **THEN** existing state is retained where practical and the screen continues to work without unnecessary refreshes

#### Scenario: Route available from every main destination
- **WHEN** the user selects Route
- **THEN** the existing route planning interface opens above the bottom bar without a duplicate route implementation

### Requirement: Remove redundant route shortcuts
Once Route is a bottom destination, the app SHALL remove Weather on route shortcuts from the main Hourly and Map screens without removing route functionality or radar controls.

#### Scenario: Main map after navigation redesign
- **WHEN** the user opens Map after the redesign
- **THEN** no floating Weather on route shortcut is shown and Route remains one tap away in bottom navigation
