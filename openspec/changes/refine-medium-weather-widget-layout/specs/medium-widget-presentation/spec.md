## Purpose

Make the medium home-screen weather widget more compact and readable while allowing the wallpaper to remain visible behind its weather information.

## ADDED Requirements

### Requirement: Location-focused medium header

The medium widget SHALL prioritize the location name in a single-line header and display only the weather forecast's local update time as visible update metadata. It SHALL NOT display an update date, an "Updated" label, or a refresh button. The displayed time SHALL represent the forecast fetch time in the forecast location's time zone, not the current time or the latest refresh attempt. The widget SHALL omit the time when necessary to preserve the location and SHALL ellipsize longer names without overlapping other content. The full location and update date/time SHALL remain available to accessibility services.

#### Scenario: Typical location and update time
- **WHEN** a 260×110 dp or 320×144 dp medium widget at normal system font scale shows the location `Zaječar` with a forecast fetched at 10:17 UTC on 28 October 2026 in `Europe/Belgrade`
- **THEN** the complete seven-letter location and the local update time `11:17` are visible
- **AND** no date, "Updated" label, or refresh button appears in the header

#### Scenario: Very long location or increased font size
- **WHEN** the complete location and update time cannot both fit
- **THEN** the location receives priority, and the time is omitted if needed
- **AND** any truncated location is ellipsized on one line while its complete name remains accessible

#### Scenario: Missing weather
- **WHEN** no matching forecast is available
- **THEN** the widget does not invent an update time and retains its existing actionable no-location or no-weather state

### Requirement: Transparent medium widget surface

The medium widget SHALL draw no opaque or translucent white, dark, or other card background or scrim behind its content in either theme. Its app-owned initial loading presentation and medium picker previews SHALL also have transparent backgrounds. Foreground text, icons, dividers, and status indications SHALL remain visible, use the selected theme's colors, and preserve their existing meanings. The small widget's background SHALL remain unchanged.

#### Scenario: Wallpaper shows through the widget
- **WHEN** a medium widget with available weather, no weather, or no location is rendered in either theme
- **THEN** the launcher wallpaper remains visible through app-owned background pixels around and between its content
- **AND** the widget does not add a white or dark surface over the wallpaper

#### Scenario: Widget is being added
- **WHEN** the launcher displays the medium widget's app-supplied preview or initial loading state
- **THEN** those app-supplied assets do not paint a white or dark card surface
- **AND** the small widget retains its existing surface

### Requirement: Centered and readable hourly information

Each displayed hourly column SHALL center its time label over that column's weather information. The precipitation probability SHALL be larger than the existing medium-widget presentation while preserving its accent color, droplet, and distinct values for zero and unavailable data. At normal system font scale with ordinary current and hourly temperatures, a 320×144 dp medium widget displaying `100%` probability SHALL render each displayed probability at no less than 11sp, and the 260×110 dp minimum SHALL render it at no less than 9sp. Content SHALL remain within its bounds and avoid overlap or clipped glyphs.

#### Scenario: Centered times and larger probability
- **WHEN** the medium widget displays upcoming hourly data with ordinary temperatures at 320×144 dp and normal font scale
- **THEN** each visible hour's time is horizontally centered in its own column
- **AND** a `100%` probability is rendered at 11sp or larger without clipping its text or droplet

#### Scenario: Compact, missing, and zero probabilities
- **WHEN** the minimum-size widget displays ordinary temperatures and `100%`, `0%`, and an unavailable probability at normal font scale
- **THEN** the visible probability text is at least 9sp and fits its assigned space
- **AND** zero is displayed as `0%` while unavailable data is displayed and announced as unavailable

### Requirement: Fit an additional upcoming hour when practical

The medium widget SHALL try to show one additional chronological future hour compared with the previous three-, four-, or five-column layouts, up to six hours. It SHALL choose the greatest supported count that fits the available dimensions, current weather, text, and system font scaling without overlap, clipping, or horizontal scrolling; it SHALL fall back to fewer columns when necessary. Only actual forecast hours later than the current instant SHALL be shown, in chronological order and the forecast location's time zone, without repeating or inventing data.

#### Scenario: Six hours on a wide widget
- **WHEN** a 400×160 dp medium widget at normal font scale has current weather `23°C` and six future hours with temperatures `20°C`, `18°C`, `17°C`, `16°C`, `15°C`, and `14°C`, including a `100%` probability
- **THEN** it displays six chronological hourly columns with their time, icon, temperature, and probability
- **AND** the current weather and all six columns fit without overlap or clipped text

#### Scenario: Constrained or incomplete forecast
- **WHEN** a widget is too small for the next column, uses a larger system font, or has fewer available future hours
- **THEN** it shows only the available columns that fit and keeps current weather visible
- **AND** it never reuses an expired hour or fabricates an hourly value

### Requirement: Reduced outer spacing without compressed data

The medium widget SHALL reduce the space between its outer bounds and its content relative to the previous medium presentation while preserving the spacing within and between weather data elements. Its content SHALL stay inside the widget bounds at supported sizes. The existing small widget layout SHALL not be compressed by this change.

#### Scenario: Compact outer insets
- **WHEN** the medium widget is displayed at a supported normal or compact size
- **THEN** the outside insets are smaller than the prior corresponding medium insets
- **AND** spacing between hour text, icons, probabilities, and neighboring data remains adequate without overlaps

### Requirement: Preserve interactions and refresh truthfulness

Tapping the medium widget's location or non-content area SHALL continue to open the app's main weather screen. Automatic widget refreshes and cache-only rendering SHALL remain unchanged; removing the medium refresh button SHALL NOT add a background request or affect the small widget's refresh action. A stale forecast, failed update, or in-progress update SHALL retain a distinguishable non-text visual indication and an accessible status description, without adding a status label or button to the medium header. Status SHALL remain understandable without relying on color alone, and the displayed weather timestamp SHALL not advance on failure.

#### Scenario: Stale weather after an unsuccessful refresh
- **WHEN** a refresh fails while matching cached weather is available
- **THEN** the cached weather and its actual update time remain visible where space permits
- **AND** stale or failed status is visually distinguishable without header status text and is announced to accessibility services

#### Scenario: Opening and refreshing
- **WHEN** a user taps the medium location or an otherwise empty part of the widget
- **THEN** the main weather screen opens
- **AND** automatic refresh remains available while the small widget retains its manual refresh control

#### Scenario: Font scale changes on cached widget views
- **WHEN** the system font scale changes from 100% to 130% and back while the launcher reapplies cached medium widget views
- **THEN** the medium location, hourly text, and status remain within their allocated bounds without overlapping or clipped glyphs
- **AND** the widget does not require a weather request merely to refit its text
