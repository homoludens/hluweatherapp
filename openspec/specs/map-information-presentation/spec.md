# map-information-presentation Specification

## Purpose

Keep the main map uncluttered on entry while preserving accessible map and radar attribution.

## Requirements

### Requirement: Main-map information starts collapsed
The main Map page SHALL initially show its basemap attribution information in a collapsed state with an accessible information control. The user SHALL be able to open and close the attribution and access its map credits and links. This initial-state change SHALL NOT alter other maps or remove their existing attribution controls.

#### Scenario: Opening the main map
- **WHEN** the user first opens the main Map page
- **THEN** the expanded basemap information is closed and its information control is available

#### Scenario: Inspecting and closing map credits
- **WHEN** the user activates the map information control and then closes it
- **THEN** the basemap credits and links are available while open and the map is unobstructed by the expanded information after closing

### Requirement: Concise radar attribution
The radar display SHALL NOT show the sentence beginning “Upstream may include…” and SHALL retain concise visible credits and source links for the provider whose radar imagery is displayed, alongside existing map attribution. LibreWXR imagery SHALL retain LibreWXR and applicable upstream credits required by current terms; RainViewer imagery SHALL display RainViewer credit with an accessible link to https://www.rainviewer.com/. Provider changes SHALL NOT relabel one provider's imagery with another provider's credits. Radar controls and loading or error indications SHALL remain usable, and the basemap information's initial collapsed state SHALL remain unchanged.

#### Scenario: Radar credits and controls
- **WHEN** LibreWXR radar is enabled on the main Map page
- **THEN** the specified sentence is absent, the required LibreWXR and upstream radar credits remain visible, and the radar timeline and controls remain usable

#### Scenario: RainViewer credits and controls
- **WHEN** RainViewer radar imagery is displayed on the main Map page
- **THEN** concise RainViewer source credit and its accessible source link are visible, the specified sentence is absent, basemap credits remain accessible, and the radar timeline and controls remain usable

#### Scenario: Provider switch attribution
- **WHEN** the user switches radar provider
- **THEN** attribution stays consistent with any imagery actually displayed and old-provider credits do not remain as the sole credits for new-provider imagery
