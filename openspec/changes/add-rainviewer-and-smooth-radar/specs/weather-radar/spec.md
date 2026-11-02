## MODIFIED Requirements

### Requirement: Native radar map overlay
The main Map destination SHALL display semi-transparent precipitation radar over the existing OpenFreeMap basemap using the user-selected public LibreWXR or RainViewer service directly, without a backend or automatic provider substitution. Radar SHALL default to enabled and approximately 75% opacity, remain aligned during map gestures, survive style reloads without duplicate layers, and appear below markers and labels where practical. Other maps SHALL remain unaffected. Tile requests SHALL respect each provider's supported tile options and native zoom range; zooming the map beyond that range SHALL overzoom available radar tiles instead of requesting unsupported zoom levels.

#### Scenario: Map and radar display
- **WHEN** usable radar frames and tiles are available and the user opens Map
- **THEN** the selected provider's latest observed frame appears on the interactive map without the large weather card, with location controls and map attribution intact

#### Scenario: Map style changes
- **WHEN** the basemap style reloads or the user pans, zooms, or rotates
- **THEN** the requested radar frame remains aligned and is restored once without duplicate overlays or work from an outgoing style overwriting the new style

#### Scenario: RainViewer at high map zoom
- **WHEN** RainViewer is selected and the user zooms beyond its native tile zoom limit of 7
- **THEN** radar remains visible by overzooming supported tiles, without requests above zoom 7 or unsupported color schemes

### Requirement: Dynamic frames and playback
The app SHALL obtain the tile host, frame paths and timestamps from current metadata for the selected provider, distinguish observed from forecast frames, and show radar frame times in the device timezone. The timeline SHALL mark the latest observation, allow requesting any available frame, play or pause at approximately 500–800 ms per rendered frame when ready, loop, and stop when Map is not visible. Playback SHALL wait for a pending frame to be displayed before advancing again when loading takes longer than that interval. Forecast offsets SHALL be measured from the latest observation; absent forecasts SHALL not be invented. While a requested frame is pending, the UI SHALL distinguish it from the frame still displayed rather than presenting the old imagery as the newly requested time. Manual timeline selection SHALL pause playback.

#### Scenario: Forecast available
- **WHEN** the user selects a forecast frame
- **THEN** its actual local time and Forecast designation and offset from the latest observation are displayed, never Observed, with a pending indication until that frame replaces the displayed imagery

#### Scenario: Observations only
- **WHEN** metadata has observed frames but no forecast frames, including current RainViewer past-only metadata
- **THEN** the timeline displays observations only and hides the forecast section without requiring or inventing forecast frames

#### Scenario: Leave map during playback
- **WHEN** the user leaves Map or the app stops displaying it
- **THEN** animation stops and unnecessary radar work is cancelled

#### Scenario: Playback encounters a cold frame
- **WHEN** playback requests a frame that takes longer than the playback interval to load
- **THEN** the previous frame remains visible with an accurate displayed-time indication, playback does not continually supersede the pending frame, and normal cadence resumes after it is displayed

#### Scenario: Drag timeline during playback
- **WHEN** the user manually changes the timeline selection while playback is running
- **THEN** playback pauses and the most recent slider selection becomes the requested frame

### Requirement: Radar controls and preferences
The map SHALL provide compact, legible radar visibility and layer controls, including an opacity slider from 0 to 100 percent and a mutually exclusive LibreWXR/RainViewer provider selector. Settings SHALL expose a default radar-provider selector separate from the forecast weather-provider setting. Both radar selectors SHALL read and update the same persisted preference, take effect on the active map, and remain synchronized across navigation and app restarts. Missing, invalid, or unknown saved radar-provider values SHALL default to LibreWXR without changing other settings. Radar visibility SHALL remain persisted through the existing preferences system. The timeline SHALL be hidden while radar is disabled; provider choice SHALL remain available without enabling radar or starting radar requests. Existing basemap selection SHALL be exposed only if already supported.

#### Scenario: Visibility preference
- **WHEN** the user disables radar and later returns to Map or restarts the app
- **THEN** the overlay and timeline remain hidden until radar is enabled again, while the basemap and location controls remain usable

#### Scenario: Adjust opacity
- **WHEN** the user changes opacity
- **THEN** the visible radar reflects the selected value, including during frame handoff, without changing the basemap

#### Scenario: Choose default in Settings
- **WHEN** the user selects RainViewer as the default radar provider in Settings and returns to Map or restarts the app
- **THEN** the map and layer selector use RainViewer, while the forecast weather provider and radar visibility preference remain unchanged

#### Scenario: Choose provider in map layers
- **WHEN** the user selects a different provider in map layers
- **THEN** the same saved default is updated, Settings reflects the choice, playback pauses, and the map selects the new provider's latest observation once its metadata is available without showing the previous provider's imagery as the new provider

#### Scenario: Change provider while radar is disabled
- **WHEN** radar is disabled and the user changes provider in either selector
- **THEN** the choice persists and is reflected in both selectors without enabling radar or fetching radar data

#### Scenario: Existing install or invalid preference
- **WHEN** no valid radar-provider preference exists
- **THEN** LibreWXR is selected and existing visibility and unrelated preferences are preserved

### Requirement: Best-effort availability and attribution
Radar requests SHALL use reasonable timeouts, bounded exponential retry for transient failures, periodic metadata refresh and cached metadata fallback where available; loading SHALL never block map interaction. Metadata and tile caches SHALL be provider-scoped so one provider's cached data cannot satisfy another provider's request. Missing coverage, frames, metadata, or tiles SHALL degrade gracefully with an unobtrusive indication, and the app SHALL preserve existing map attribution and show the displayed provider's source link and upstream attribution required by current terms. RainViewer requests issued by the app, including metadata, visible tiles, retries, and prefetching, SHALL share a rate budget not exceeding its documented 100 requests per minute; rate-limit responses SHALL cause bounded backoff honoring Retry-After when supplied. Speculative loads SHALL yield to requested imagery and rate limits.

#### Scenario: Provider unavailable
- **WHEN** the selected radar service fails or coverage is missing
- **THEN** the map remains usable, only that provider's cached data is used when available, and an unobtrusive status identifies radar unavailability without silently switching providers

#### Scenario: Attribution displayed
- **WHEN** radar data is displayed
- **THEN** the displayed provider's current required credits and source link are visible alongside existing map credits, including LibreWXR upstream credits when LibreWXR is displayed and linked RainViewer credit when RainViewer is displayed

#### Scenario: Provider-scoped fallback
- **WHEN** RainViewer is unavailable but only LibreWXR metadata or tiles are cached
- **THEN** the app reports RainViewer unavailability rather than displaying LibreWXR data as RainViewer

#### Scenario: RainViewer rate limit
- **WHEN** the app exhausts its RainViewer request allowance or receives a rate-limit response
- **THEN** speculative loading stops or waits, retries respect the shared budget and backoff, cached imagery remains usable, and the map remains interactive

## ADDED Requirements

### Requirement: Continuous radar frame handoff
For timeline scrubbing and playback within the same provider and loaded map style, the app SHALL retain the last successfully rendered radar frame until the latest requested replacement is ready for the current viewport, then use a render-ready handoff or short transition without an intentional blank interval or visible opacity flash. A fixed delay or receipt of metadata alone SHALL NOT count as readiness. Rapid selection changes SHALL converge to the most recent request; superseded loads SHALL NOT replace newer selections. Failed, timed-out, or rate-limited requests SHALL preserve the last good frame with an unobtrusive pending/error indication and an accurate displayed-time label. Before any frame has loaded, ordinary loading/empty states are permitted. Disabling radar SHALL remove all radar imagery even during a handoff.

#### Scenario: Scrub to an uncached frame
- **WHEN** the user selects a time whose current-viewport radar tiles are not ready
- **THEN** the current radar stays visible while the replacement loads and is replaced only when ready, without a clear-map flash between the frames

#### Scenario: Superseded frame finishes late
- **WHEN** the user selects frame B then frame C and B finishes after C is requested
- **THEN** B cannot become the newly displayed selection, and C replaces the last good frame once ready

#### Scenario: Replacement cannot load
- **WHEN** the requested replacement fails, times out, or is rate-limited
- **THEN** the last good frame and its time remain visible, the pending request or failure is distinguishable, and the map remains usable

#### Scenario: Disable during transition
- **WHEN** radar is disabled while a replacement is loading or transitioning
- **THEN** all radar imagery and timeline are hidden and late completions cannot restore them

### Requirement: Two-step radar neighborhood prefetch
While the main Map and radar are visible, the app SHALL opportunistically prefetch radar image tiles covering the current viewport for up to two available timeline indices before and two after the requested frame, for either provider. Steps SHALL mean adjacent entries in the provider's chronologically ordered timeline, not a hard-coded time interval. The neighborhood SHALL exclude the requested frame, clamp at timeline endpoints without wrapping, deduplicate resources, and reuse downloaded tiles for subsequent display. Prefetching SHALL use bounded storage and concurrency, prioritize the requested frame, avoid unrelated regions and unsupported zoom levels, and obey provider rate limits. Requests made obsolete by selection, viewport, provider, or metadata changes SHALL be cancelled or deprioritized and their completions SHALL NOT alter the active selection. No speculative radar requests SHALL continue when Map/radar is hidden or the app is stopped.

#### Scenario: Middle of timeline
- **WHEN** the requested frame is at index i with at least two available frames on each side and request budget is available
- **THEN** prefetch targets are exactly i−2, i−1, i+1, and i+2 for the current viewport, without duplicating the requested frame's tile requests

#### Scenario: Timeline boundary or short timeline
- **WHEN** the requested frame is near an endpoint or fewer than five frames exist
- **THEN** only valid neighboring indices within two steps are prefetched, with no wraparound, invented frames, or duplicate loads

#### Scenario: Reuse prefetched imagery
- **WHEN** the user selects a neighboring frame whose viewport tiles have been prefetched and remain valid in cache
- **THEN** display uses those downloaded resources without downloading the same tiles again

#### Scenario: Viewport changes
- **WHEN** the user pans or zooms to a different visible tile set
- **THEN** the requested frame's new viewport tiles take priority and the neighborhood is recomputed for that viewport within resource and provider limits

#### Scenario: Cancel obsolete prefetch work
- **WHEN** the user changes provider, hides radar, leaves Map, or the app stops
- **THEN** obsolete speculative requests are cancelled, no new speculative requests start for the hidden or old-provider radar, and late completions cannot change the display
