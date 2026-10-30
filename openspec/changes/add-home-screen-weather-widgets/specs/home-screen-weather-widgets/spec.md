## Purpose

Provide accessible small and medium Android home-screen weather cards that condense HluWeather's existing forecast presentation without redesigning the app.

## ADDED Requirements

### Requirement: Two launcher widgets without configuration

The app SHALL offer separately identifiable small 2x2 and medium 4x2 home-screen widgets in the Android widget picker, with descriptive labels and preview resources. Both widgets SHALL support launcher resizing and SHALL use the app's selected settings without requiring a widget configuration screen.

#### Scenario: Add either widget
- **WHEN** a user selects the small or medium widget in the launcher picker
- **THEN** the launcher can place the corresponding widget without a configuration activity
- **AND** the widget uses the app's location and weather settings

### Requirement: Small current-weather card

The small widget SHALL show a location icon and single-line location name at the top left, a refresh control at the top right, a large current-weather icon beside the visually dominant temperature, the app's current-condition label below, and a smaller muted apparent temperature when available and space permits. The small widget SHALL NOT contain an hourly forecast strip in v1.

#### Scenario: Current weather with apparent temperature
- **WHEN** current weather is available with a temperature of 23 degrees Celsius and an apparent temperature of 24 degrees Celsius at a normal 2x2 size
- **THEN** the card shows the location, shared clear-day icon, dominant 23-degree temperature, the app's clear-condition label, and a muted feels-like value of 24 degrees
- **AND** it does not show hourly columns

#### Scenario: Apparent temperature unavailable
- **WHEN** the forecast has no apparent temperature
- **THEN** the feels-like row is omitted without substituting a fabricated value

### Requirement: Medium current and hourly card

The medium widget SHALL show a single-line, location-prioritized header and, when it fits without sacrificing location space, only the forecast's local fetch time as visible update metadata. The time SHALL represent the forecast fetch instant in the forecast location's time zone, not the current clock or latest refresh attempt. If the time and full location cannot both fit, the time SHALL be omitted before the location is ellipsized on one line. The header SHALL NOT show an update date, an "Updated" label, status words, or a manual refresh control. The complete location and fetch date/time SHALL remain available to accessibility services. Its body SHALL allocate approximately 30–35 percent of usable width to current weather and the remaining width to the existing responsive three-, four-, or five-hourly-column layout, separated by subtle vertical dividers; it SHALL display no more than five hourly columns, with no requirement for an additional-hour or maximal-fit layout. Current weather SHALL include its icon, dominant temperature, condition, and apparent temperature when available and space permits. Each hourly column SHALL show the location-local hour, shared condition icon, temperature, and precipitation probability, with the probability using the app's blue accent and a proper droplet icon. Stale, failed, or in-progress update states SHALL use distinct, non-clickable status glyphs in the current-weather area with accessible descriptions, including when both stale/failure and updating apply; status SHALL not rely on color alone or add text or controls to the header.

#### Scenario: Location-first time-only header
- **WHEN** a 260×110 dp or 320×144 dp medium widget shows the location `Zaječar` with a forecast fetched at 10:17 UTC on 28 October 2026 in `Europe/Belgrade`
- **THEN** the complete location and local fetch time `11:17` are visible when they fit
- **AND** no date, "Updated" label, status text, or refresh control appears in the header
- **AND** the complete location and fetch date/time remain accessible

#### Scenario: Long medium location
- **WHEN** a long location and its fetch time cannot both fit in the medium header
- **THEN** the location receives priority and the time is omitted first
- **AND** the location is ellipsized on one line without overlapping other content

#### Scenario: Wide medium card with five upcoming hours
- **WHEN** a sufficiently wide medium widget has current clear weather at 23 degrees and upcoming hours at 22:00, 23:00, 00:00, 01:00, and 02:00
- **THEN** the current block appears to the left of five chronological columns
- **AND** no additional hourly column is required beyond those five
- **AND** the columns can show temperatures of 20, 18, 17, 16, and 15 degrees and probabilities of 0, 0, 10, 20, and 60 percent respectively
- **AND** the hours use the forecast location's time zone, including the midnight transition

#### Scenario: Partial or missing hourly forecast
- **WHEN** fewer upcoming hours are available or an hour has no precipitation probability
- **THEN** only available future hours are shown without repeating or inventing hours
- **AND** an unavailable probability is shown as an unavailable value rather than zero percent
- **AND** current weather remains visible even when no upcoming hours are available

### Requirement: Shared visual language and weather semantics

Both widgets SHALL reuse the app's condition labels, day/night-aware icon selection and icon style, temperature conversion, and relevant theme colors. They SHALL use rounded visual language, compact spacing, Material-inspired typography consistent with the app, and readable colorful weather icons. The small widget SHALL retain its existing rounded light or dark card surface. The medium widget SHALL draw no app-owned card background, scrim, or other background behind its content in either theme; its initial/loading presentation and picker previews SHALL also be transparent. Medium foreground content SHALL retain the selected theme's colors, and this presentation SHALL NOT change the small widget's surface. Neither widget SHALL use emoji, Unicode weather symbols, a separate condition-to-icon mapping, or a pure-black card surface.

#### Scenario: Icon and label parity
- **WHEN** the app and a widget display the same condition and day/night flag
- **THEN** they select the same weather glyph, condition label, and corresponding themed tint
- **AND** clear night uses the app's night icon rather than its sun icon

#### Scenario: Unknown weather condition
- **WHEN** a normalized forecast contains an unknown condition
- **THEN** the widget uses the same fallback label and icon as the app

#### Scenario: Size-specific surfaces in either theme
- **WHEN** small and medium widgets are rendered in either theme, including an empty medium state or its app-supplied initial and picker previews
- **THEN** the small widget retains its existing themed surface while the medium widget paints no app-owned background or scrim
- **AND** the medium foreground continues to use the selected theme's colors

### Requirement: Theme and unit synchronization

Widgets SHALL follow the app's explicit light or dark theme setting and follow system appearance when the app theme is System. Temperatures SHALL respect the app's Celsius or Fahrenheit setting, using the same conversions and rounding as the main forecast. Unit and theme changes SHALL update cached presentation without requiring a successful weather request.

#### Scenario: Explicit dark theme
- **WHEN** the app theme is Dark while the launcher system theme is light
- **THEN** the small widget uses its dark navy surface, while the medium widget remains transparent
- **AND** both widgets use near-white main text and muted blue-gray secondary text

#### Scenario: System theme changes
- **WHEN** the app theme is System and Android switches between light and dark appearance
- **THEN** the widget follows the new system appearance without changing the user's app preference

#### Scenario: Fahrenheit while offline
- **WHEN** cached current weather is 23 degrees Celsius and the user changes temperature units to Fahrenheit while offline
- **THEN** the widget shows 73 degrees Fahrenheit using the app's rounding and converts apparent and hourly temperatures as well
- **AND** it does not wait for a new network forecast

### Requirement: Responsive content priorities

The small layout SHALL preserve, in order, location, current icon, temperature, condition, and apparent temperature as space becomes constrained. It SHALL reduce icon and temperature size where necessary and hide lower-priority text before overflowing. The medium layout SHALL retain its existing three-, four-, or five-column responsive branches, with at most five displayed hours; this does not require a new additional-hour or maximal-fit selection strategy. Neither widget SHALL horizontally scroll or render content outside its bounds. Long location names SHALL ellipsize on one line; the small layout SHALL continue to reserve its refresh control, while the medium layout SHALL prioritize the location and omit its optional time before truncating the location.

#### Scenario: Constrained small card
- **WHEN** the launcher resizes a small widget to its supported minimum dimensions
- **THEN** the location, weather icon, and temperature remain visible within the card
- **AND** apparent temperature and then condition are omitted if they cannot fit

#### Scenario: Medium width changes
- **WHEN** a medium widget is resized between constrained, normal, and wide supported widths
- **THEN** the hourly column count changes between three, four, and five respectively, subject to forecast availability
- **AND** no more than five hourly columns are displayed
- **AND** the current-weather block remains visible without horizontal scrolling

#### Scenario: Long location and font scaling
- **WHEN** a widget displays a long location name at normal Android font scaling or a larger accessibility font size
- **THEN** the location truncates gracefully, the small widget's refresh remains available, and the medium widget omits its optional time before reducing location space
- **AND** the layout reduces lower-priority content instead of allowing overlapping text

### Requirement: Open app and refresh interactions

Tapping either widget's card or location name SHALL open HluWeather's main weather screen. Only the small widget SHALL expose a manual refresh control; tapping it SHALL trigger a weather refresh without opening the app or activating the card's open action. The medium widget SHALL have no manual refresh control, and its location and card-opening actions SHALL remain available. Automatic widget updates SHALL continue independently of the small widget's manual control. V1 SHALL NOT introduce a separate location picker or configuration flow for widgets.

#### Scenario: Open from card or location
- **WHEN** the user taps the card background or location label, including when the app is already running on another screen
- **THEN** the app opens or brings forward its main weather screen

#### Scenario: Refresh has its own action
- **WHEN** the user taps the refresh control
- **THEN** a manual refresh is requested
- **AND** the control belongs to the small widget; the medium widget has no manual refresh control
- **AND** the app is not opened by that tap

#### Scenario: Medium card opens the app without a refresh control
- **WHEN** the user taps the medium widget's location or card
- **THEN** the main weather screen opens
- **AND** the medium widget exposes no manual refresh action

### Requirement: Accessible weather and controls

Widgets SHALL provide meaningful descriptions for the weather location, current condition and temperature including units, hourly weather, and precipitation probability. The small widget SHALL provide a description for its refresh action; the medium widget SHALL NOT expose a refresh action and SHALL keep the complete location and forecast fetch date/time accessible even when the visible time is omitted. Medium stale/failure and in-progress status indications SHALL have informative accessible descriptions, remain distinguishable without relying on color alone, and remain available when the timestamp is hidden. Decorative icons SHALL NOT cause duplicate spoken weather descriptions. Text SHALL remain readable using supported Android font scaling and available card space.

#### Scenario: Screen reader weather announcement
- **WHEN** a screen reader focuses current weather in Zaječar with a clear condition and 23 degrees Celsius
- **THEN** it announces the location and understandable condition and temperature information rather than an unexplained symbol

#### Scenario: Screen reader refresh and precipitation
- **WHEN** a screen reader focuses the refresh control or a 60-percent precipitation value
- **THEN** it announces "Refresh weather" or "Rain probability 60 percent" respectively
- **AND** the refresh control is present only on the small widget

#### Scenario: Medium status and timestamp descriptions
- **WHEN** a screen reader focuses stale, failed, or updating medium-widget weather, including when its visible timestamp is omitted
- **THEN** it can identify the status and access the complete forecast fetch date/time without relying on color or header status text

### Requirement: Reference and edge-case example states

The feature SHALL include reproducible example states for a light small Zaječar card at 23 degrees with apparent temperature 24, a dark small Trieste partly-cloudy card at 18 degrees with apparent temperature 17, and a medium Zaječar card with the five reference hourly temperatures and probabilities. Examples SHALL use the app's actual shared condition labels and icons even where the artwork uses a different label. Additional examples or tests SHALL cover long locations, unavailable probabilities, stale cached data, refresh failure, both units, both themes, and supported size branches.

#### Scenario: Review references without live services
- **WHEN** a developer renders the reference and edge-case states without network access
- **THEN** both widget sizes can be inspected using deterministic weather data and the production visual components
