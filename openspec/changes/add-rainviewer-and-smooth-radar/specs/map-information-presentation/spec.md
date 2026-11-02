## MODIFIED Requirements

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
