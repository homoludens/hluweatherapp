## Purpose

Provide an animated precipitation radar on the existing interactive weather map while preserving map and forecast functionality.

## ADDED Requirements

### Requirement: Native radar map overlay
The main Map destination SHALL display semi-transparent precipitation radar over the existing OpenFreeMap basemap using the public LibreWXR service directly and no backend or automatic provider substitution. Radar SHALL default to enabled and approximately 75% opacity, remain aligned during map gestures, survive style reloads without duplicate layers, and appear below markers and labels where practical. Other maps SHALL remain unaffected.

#### Scenario: Map and radar display
- **WHEN** usable radar frames and tiles are available and the user opens Map
- **THEN** the latest observed frame appears on the interactive map without the large weather card, with location controls and map attribution intact

#### Scenario: Map style changes
- **WHEN** the basemap style reloads or the user pans, zooms, or rotates
- **THEN** the selected radar frame remains aligned and is restored once without duplicate overlays

### Requirement: Dynamic frames and playback
The app SHALL obtain the tile host, frame paths and timestamps from current provider metadata, distinguish observed from forecast frames, and show the selected frame's actual time in the device timezone. The timeline SHALL mark the latest observation, select any available frame, play or pause at approximately 500–800 ms per frame, loop, and stop when Map is not visible. Forecast offsets SHALL be measured from the latest observation; absent forecasts SHALL not be invented.

#### Scenario: Forecast available
- **WHEN** the user selects a forecast frame
- **THEN** its actual local time and Forecast designation and offset from the latest observation are displayed, never Observed

#### Scenario: Observations only
- **WHEN** metadata has observed frames but no forecast frames
- **THEN** the timeline displays observations only and hides the forecast section

#### Scenario: Leave map during playback
- **WHEN** the user leaves Map or the app stops displaying it
- **THEN** animation stops and unnecessary radar work is cancelled

### Requirement: Radar controls and preferences
The map SHALL provide compact, legible radar visibility and layer controls, including an opacity slider from 0 to 100 percent, and persist radar visibility through the existing preferences system. The timeline SHALL be hidden while radar is disabled. Existing basemap selection SHALL be exposed only if already supported.

#### Scenario: Visibility preference
- **WHEN** the user disables radar and later returns to Map or restarts the app
- **THEN** the overlay and timeline remain hidden until radar is enabled again, while the basemap and location controls remain usable

#### Scenario: Adjust opacity
- **WHEN** the user changes opacity
- **THEN** the visible radar layer reflects the selected value without changing the basemap

### Requirement: Best-effort availability and attribution
Radar requests SHALL use reasonable timeouts, bounded exponential retry for transient failures, periodic metadata refresh and cached metadata fallback where available; loading SHALL never block map interaction. Missing coverage, frames, metadata, or tiles SHALL degrade gracefully with an unobtrusive indication, and the app SHALL preserve existing map attribution and show provider and upstream attribution required by current terms.

#### Scenario: Provider unavailable
- **WHEN** the radar service fails or coverage is missing
- **THEN** the map remains usable, cached metadata is used when available, and an unobtrusive status indicates radar unavailability without silently switching providers

#### Scenario: Attribution displayed
- **WHEN** radar data is displayed
- **THEN** the required current LibreWXR and upstream data credits are visible alongside existing map credits
