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

The medium widget SHALL show a location header, a compact last-weather-update date and time when space permits, and a refresh control. Its body SHALL allocate approximately 30–35 percent of usable width to current weather and the remaining width to upcoming hourly columns, separated by subtle vertical dividers. Current weather SHALL include its icon, dominant temperature, condition, and apparent temperature when available and space permits. Each hourly column SHALL show the location-local hour, shared condition icon, temperature, and precipitation probability, with the probability using the app's blue accent and a proper droplet icon.

#### Scenario: Wide medium card with five upcoming hours
- **WHEN** a sufficiently wide medium widget has current clear weather at 23 degrees and upcoming hours at 22:00, 23:00, 00:00, 01:00, and 02:00
- **THEN** the current block appears to the left of five chronological columns
- **AND** the columns can show temperatures of 20, 18, 17, 16, and 15 degrees and probabilities of 0, 0, 10, 20, and 60 percent respectively
- **AND** the hours use the forecast location's time zone, including the midnight transition

#### Scenario: Partial or missing hourly forecast
- **WHEN** fewer upcoming hours are available or an hour has no precipitation probability
- **THEN** only available future hours are shown without repeating or inventing hours
- **AND** an unavailable probability is shown as an unavailable value rather than zero percent
- **AND** current weather remains visible even when no upcoming hours are available

### Requirement: Shared visual language and weather semantics

Both widgets SHALL reuse the app's condition labels, day/night-aware icon selection and icon style, temperature conversion, and relevant theme colors. They SHALL use rounded cards, compact spacing, Material-inspired typography consistent with the app, a soft tinted light surface or dark navy surface, and readable colorful weather icons. They SHALL NOT use emoji, Unicode weather symbols, a separate condition-to-icon mapping, or a pure-black card surface.

#### Scenario: Icon and label parity
- **WHEN** the app and a widget display the same condition and day/night flag
- **THEN** they select the same weather glyph, condition label, and corresponding themed tint
- **AND** clear night uses the app's night icon rather than its sun icon

#### Scenario: Unknown weather condition
- **WHEN** a normalized forecast contains an unknown condition
- **THEN** the widget uses the same fallback label and icon as the app

### Requirement: Theme and unit synchronization

Widgets SHALL follow the app's explicit light or dark theme setting and follow system appearance when the app theme is System. Temperatures SHALL respect the app's Celsius or Fahrenheit setting, using the same conversions and rounding as the main forecast. Unit and theme changes SHALL update cached presentation without requiring a successful weather request.

#### Scenario: Explicit dark theme
- **WHEN** the app theme is Dark while the launcher system theme is light
- **THEN** the widget uses dark navy surfaces, near-white main text, and muted blue-gray secondary text

#### Scenario: System theme changes
- **WHEN** the app theme is System and Android switches between light and dark appearance
- **THEN** the widget follows the new system appearance without changing the user's app preference

#### Scenario: Fahrenheit while offline
- **WHEN** cached current weather is 23 degrees Celsius and the user changes temperature units to Fahrenheit while offline
- **THEN** the widget shows 73 degrees Fahrenheit using the app's rounding and converts apparent and hourly temperatures as well
- **AND** it does not wait for a new network forecast

### Requirement: Responsive content priorities

The small layout SHALL preserve, in order, location, current icon, temperature, condition, and apparent temperature as space becomes constrained. It SHALL reduce icon and temperature size where necessary and hide lower-priority text before overflowing. The medium layout SHALL select three, four, or five hourly columns according to available width, with five on wide layouts and three on constrained supported layouts. Neither widget SHALL horizontally scroll or render content outside its card; long location names SHALL ellipsize on one line without obscuring refresh.

#### Scenario: Constrained small card
- **WHEN** the launcher resizes a small widget to its supported minimum dimensions
- **THEN** the location, weather icon, and temperature remain visible within the card
- **AND** apparent temperature and then condition are omitted if they cannot fit

#### Scenario: Medium width changes
- **WHEN** a medium widget is resized between constrained, normal, and wide supported widths
- **THEN** the hourly column count changes between three, four, and five respectively, subject to forecast availability
- **AND** the current-weather block remains visible without horizontal scrolling

#### Scenario: Long location and font scaling
- **WHEN** a widget displays a long location name at normal Android font scaling or a larger accessibility font size
- **THEN** the location truncates gracefully and refresh remains available
- **AND** the layout reduces lower-priority content instead of allowing overlapping text

### Requirement: Open app and refresh interactions

Tapping the general card or location name SHALL open HluWeather's main weather screen. The refresh control SHALL trigger a weather refresh without opening the app or activating the card's open action. V1 SHALL NOT introduce a separate location picker or configuration flow for widgets.

#### Scenario: Open from card or location
- **WHEN** the user taps the card background or location label, including when the app is already running on another screen
- **THEN** the app opens or brings forward its main weather screen

#### Scenario: Refresh has its own action
- **WHEN** the user taps the refresh control
- **THEN** a manual refresh is requested
- **AND** the app is not opened by that tap

### Requirement: Accessible weather and controls

Widgets SHALL provide meaningful descriptions for the weather location, current condition and temperature including units, hourly weather, precipitation probability, and refresh action. Decorative icons SHALL NOT cause duplicate spoken weather descriptions. Text SHALL remain readable using supported Android font scaling and available card space.

#### Scenario: Screen reader weather announcement
- **WHEN** a screen reader focuses current weather in Zaječar with a clear condition and 23 degrees Celsius
- **THEN** it announces the location and understandable condition and temperature information rather than an unexplained symbol

#### Scenario: Screen reader refresh and precipitation
- **WHEN** a screen reader focuses the refresh control or a 60-percent precipitation value
- **THEN** it announces "Refresh weather" or "Rain probability 60 percent" respectively

### Requirement: Reference and edge-case example states

The feature SHALL include reproducible example states for a light small Zaječar card at 23 degrees with apparent temperature 24, a dark small Trieste partly-cloudy card at 18 degrees with apparent temperature 17, and a medium Zaječar card with the five reference hourly temperatures and probabilities. Examples SHALL use the app's actual shared condition labels and icons even where the artwork uses a different label. Additional examples or tests SHALL cover long locations, unavailable probabilities, stale cached data, refresh failure, both units, both themes, and supported size branches.

#### Scenario: Review references without live services
- **WHEN** a developer renders the reference and edge-case states without network access
- **THEN** both widget sizes can be inspected using deterministic weather data and the production visual components
