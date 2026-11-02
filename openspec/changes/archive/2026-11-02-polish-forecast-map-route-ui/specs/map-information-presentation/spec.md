## Purpose

Keep the main map uncluttered on entry while preserving accessible map and radar attribution.

## ADDED Requirements

### Requirement: Main-map information starts collapsed
The main Map page SHALL initially show its basemap attribution information in a collapsed state with an accessible information control. The user SHALL be able to open and close the attribution and access its map credits and links. This initial-state change SHALL NOT alter other maps or remove their existing attribution controls.

#### Scenario: Opening the main map
- **WHEN** the user first opens the main Map page
- **THEN** the expanded basemap information is closed and its information control is available

#### Scenario: Inspecting and closing map credits
- **WHEN** the user activates the map information control and then closes it
- **THEN** the basemap credits and links are available while open and the map is unobstructed by the expanded information after closing

### Requirement: Concise radar attribution
The radar display SHALL NOT show the sentence beginning “Upstream may include…” and SHALL retain visible LibreWXR and applicable upstream credits as required by current provider terms, alongside existing map attribution. Radar controls and loading or error indications SHALL remain usable.

#### Scenario: Radar credits and controls
- **WHEN** radar is enabled on the main Map page
- **THEN** the specified sentence is absent, the required radar credits remain visible, and the radar timeline and controls remain usable
