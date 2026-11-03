## MODIFIED Requirements

### Requirement: Native radar map overlay
The main Map destination SHALL display semi-transparent precipitation radar over the existing OpenFreeMap basemap using the user-selected public LibreWXR or RainViewer service directly, without a backend or automatic provider substitution. Radar SHALL default to enabled and approximately 75% opacity, remain aligned during map gestures, survive style reloads without duplicate layers, and appear below markers and labels where practical. Other maps SHALL remain unaffected. Tile requests SHALL respect each provider's supported tile options and native zoom range. For this speed-first experiment, radar SHALL use tiles two zoom levels below the normal source zoom for the current viewport, clamped to the provider's minimum and to two levels below its native maximum. Both providers SHALL retain supported 256-pixel image requests. Zooming beyond the coarse range SHALL overzoom coarse imagery rather than fetch higher-detail tiles; keeping a viewport stationary SHALL NOT trigger a later detail upgrade. Basemap resolution SHALL remain unchanged.

#### Scenario: Map and radar display
- **WHEN** usable radar frames and tiles are available and the user opens Map
- **THEN** the selected provider's latest observed frame appears on the interactive map without the large weather card, with location controls and map attribution intact

#### Scenario: Map style changes
- **WHEN** the basemap style reloads or the user pans, zooms, or rotates
- **THEN** the requested radar frame remains aligned and is restored once without duplicate overlays or work from an outgoing style overwriting the new style

#### Scenario: RainViewer at high map zoom
- **WHEN** RainViewer is selected and the user zooms beyond its coarse tile zoom cap of 5
- **THEN** radar remains visible by overzooming supported zoom-5 or coarser tiles, without higher-detail requests or unsupported color schemes

#### Scenario: LibreWXR at high map zoom
- **WHEN** LibreWXR is selected and the user zooms beyond its coarse tile zoom cap of 10
- **THEN** radar remains visible by overzooming supported zoom-10 or coarser tiles rather than requesting zoom 11 or 12

#### Scenario: Coarse imagery remains coarse
- **WHEN** radar has loaded for a stationary viewport whose normal source zoom is above the provider minimum
- **THEN** the displayed and prefetched radar use the same two-level-coarser policy and no later higher-detail upgrade is requested for that viewport

#### Scenario: Lowest zoom and viewport boundaries
- **WHEN** the two-level reduction reaches the minimum zoom or the viewport crosses tile boundaries, the antimeridian, or wrapped world copies
- **THEN** supported coarse tiles cover the entire visible area with canonical requests deduplicated, without assuming a single tile always covers the screen

### Requirement: Dynamic frames and playback
The app SHALL obtain the tile host, frame paths and timestamps from current metadata for the selected provider, distinguish observed from forecast frames, and show radar frame times in the device timezone. The timeline SHALL mark the latest observation, allow requesting any available frame, play or pause at approximately 500–800 ms per accepted frame when ready, loop, and stop when Map is not visible. Playback SHALL wait for a pending frame's coarse viewport coverage, staging installation, and accepted native promotion before advancing again when loading takes longer than that interval. In the speed-first path, that acceptance SHALL acknowledge the frame without waiting for full-map render events and SHALL NOT be represented as proof that pixels were drawn. Forecast offsets SHALL be measured from the latest observation; absent forecasts SHALL not be invented. While a requested frame is pending, the timeline SHALL keep its text stable apart from the selected frame time and Observed/Forecast designation; it SHALL NOT add a transient loading message or duplicate requested and displayed time rows. Manual timeline selection SHALL pause playback.

#### Scenario: Forecast available
- **WHEN** the user selects a forecast frame
- **THEN** its local time and Forecast designation and offset from the latest observation are displayed, never Observed, without a transient pending/loading message

#### Scenario: Observations only
- **WHEN** metadata has observed frames but no forecast frames, including current RainViewer past-only metadata
- **THEN** the timeline displays observations only and hides the forecast section without requiring or inventing forecast frames

#### Scenario: Leave map during playback
- **WHEN** the user leaves Map or the app stops displaying it
- **THEN** animation stops and unnecessary radar work is cancelled

#### Scenario: Playback encounters a cold frame
- **WHEN** playback requests a frame that takes longer than the playback interval to load
- **THEN** the renderer attempts to retain the previous frame while coarse coverage and installation are pending, the timeline continues to show only the requested frame's selected time and type, and playback does not continually supersede the pending frame

#### Scenario: Playback receives no full-map render events
- **WHEN** coarse coverage, staging installation, and native promotion have succeeded but no full-map render event arrives
- **THEN** the frame is acknowledged and normal playback cadence can resume without an additional render-event wait

#### Scenario: Drag timeline during playback
- **WHEN** the user manually changes the timeline selection while playback is running
- **THEN** playback pauses and the most recent slider selection becomes the requested frame

### Requirement: Best-effort availability and attribution
Radar requests SHALL use reasonable timeouts, bounded exponential retry for transient failures, periodic metadata refresh and cached metadata fallback where available; loading SHALL never block map interaction. Metadata and tile caches SHALL be provider-scoped so one provider's cached data cannot satisfy another provider's request. Missing coverage, frames, metadata, or tiles SHALL degrade gracefully with an unobtrusive indication, and the app SHALL preserve existing map attribution and show the displayed provider's source link and upstream attribution required by current terms. The app SHALL allow up to eight simultaneous radar HTTP attempts across metadata, visible tiles, retries, and prefetch, of which at most four SHALL be speculative. The HTTP transport SHALL permit eight eligible requests to the same host rather than imposing a lower client-side concurrency limit. Pending foreground requests SHALL take priority over pending speculative requests; queue, retry, cache, and cancellation bounds SHALL remain in effect. RainViewer requests SHALL retain the existing shared app budget of at most 90 attempts per rolling 60 seconds, including a 20-attempt foreground reserve against speculative loading, below its documented 100 requests per minute. Rate-limit responses SHALL cause bounded backoff honoring Retry-After when supplied. Increased concurrency SHALL NOT bypass those budgets or cause automatic provider substitution.

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

#### Scenario: Eight requests share the transport
- **WHEN** eight foreground radar requests to the same provider host are eligible and their responses are held by a controlled server
- **THEN** all eight can reach that server concurrently and a ninth waits until capacity is released

#### Scenario: Speculative limit leaves foreground capacity
- **WHEN** four speculative radar requests are active and further speculative and foreground requests are eligible
- **THEN** no fifth speculative request starts while four remain active, foreground requests can use the remaining capacity, and pending foreground work is admitted before pending speculation

### Requirement: Best-effort continuous radar frame handoff
For timeline scrubbing and playback within the same provider and loaded map style, the app SHALL attempt to retain the last successfully accepted radar frame while the latest replacement is loading. The normal speed-first handoff SHALL require successful coarse current-viewport tile coverage and staging installation, then acknowledge the new frame when the current request's native promotion is accepted. It SHALL NOT wait for full-map render events before promotion, after promotion, or during normal cleanup. A fixed delay or metadata alone SHALL NOT count as readiness. Acknowledgment SHALL NOT imply that native tile delivery, image decoding, or drawing has completed. Temporary basemap-only intervals, opacity discontinuities, or increased blinking remain accepted trade-offs, and the behavior SHALL NOT be described as universally blink-free. Rapid selection changes SHALL converge to the most recent request; superseded loads and stale native commands SHALL NOT replace newer selections. Failed, timed-out, or rate-limited replacements before accepted promotion SHALL preserve the last good frame with an unobtrusive error indication; the timeline SHALL retain the selected frame time and type without flashing pending/loading text. Before any frame has loaded, ordinary loading/empty states are permitted. Disabling radar, hiding Map, or stopping the app SHALL remove radar imagery and prevent late completions from restoring it; provider and style changes SHALL invalidate outgoing work.

#### Scenario: Scrub to an uncached frame
- **WHEN** the user selects a time whose current-viewport coarse radar tiles are not ready
- **THEN** the renderer attempts to retain the current radar while coverage and staging are pending and promotes the replacement once coverage and installation succeed, without a full-map render-event gate

#### Scenario: Cached coverage needs no render events
- **WHEN** coarse tile coverage is cached or successfully empty/transparent and native installation and promotion succeed without any full-map render event
- **THEN** the requested frame is acknowledged and predecessor resources can be released without waiting for such events

#### Scenario: Superseded frame finishes late
- **WHEN** the user selects frame B then frame C and B finishes after C is requested
- **THEN** B cannot become the newly accepted selection, and C replaces the last good frame once its coverage, installation, and promotion succeed

#### Scenario: Replacement cannot load
- **WHEN** the requested replacement fails, times out, or is rate-limited before promotion
- **THEN** the last good frame remains selected for display, the error is distinguishable without a duplicate displayed-time row, and the map remains usable

#### Scenario: Disable during transition
- **WHEN** radar is disabled while a replacement is loading, being promoted, or being cleaned up
- **THEN** all radar imagery and timeline are hidden and late completions or native commands cannot restore them

#### Scenario: Outgoing provider or style finishes late
- **WHEN** a provider or map-style change supersedes pending coverage, promotion, or cleanup
- **THEN** only the new provider/style can receive accepted promotions, old work cannot change the new selection, and obsolete resources are released

### Requirement: Adjacent radar frame prefetch
While the main Map and radar are visible, the app SHALL opportunistically prefetch radar image tiles covering the current viewport for the immediately previous and next available timeline indices around the requested frame, for either provider. Steps SHALL mean adjacent entries in the provider's chronologically ordered timeline, not a hard-coded time interval. Prefetch SHALL use the same coarse-only tile policy as visible radar and SHALL NOT warm a higher-detail replacement. The neighborhood SHALL exclude the requested frame, clamp at timeline endpoints without wrapping, deduplicate resources, and reuse downloaded tiles for subsequent display. Prefetching SHALL use bounded storage, at most four simultaneous speculative requests within the shared eight-request limit, foreground priority, and provider rate limits. It SHALL avoid unrelated regions and unsupported zoom levels. Requests made obsolete by selection, viewport, provider, or metadata changes SHALL be cancelled or deprioritized and their completions SHALL NOT alter the active selection. No speculative radar requests SHALL continue when Map/radar is hidden or the app is stopped.

#### Scenario: Middle of timeline
- **WHEN** the requested frame is at index i with a previous and next available frame and request budget is available
- **THEN** prefetch targets are i−1 and i+1 for the current coarse viewport tile set, without duplicating the requested frame's tile requests

#### Scenario: Timeline boundary or short timeline
- **WHEN** the requested frame is at an endpoint or the timeline has fewer than three frames
- **THEN** only valid immediately adjacent indices are prefetched, with no wraparound, invented frames, or duplicate loads

#### Scenario: Reuse prefetched imagery
- **WHEN** the user selects a neighboring frame whose coarse viewport tiles have been prefetched and remain valid in cache
- **THEN** display uses those downloaded resources without downloading them again or requesting detailed replacements

#### Scenario: Viewport changes
- **WHEN** the user pans or zooms to a different visible tile set
- **THEN** the requested frame's coarse new-viewport tiles take priority and the neighborhood is recomputed for that viewport within resource and provider limits

#### Scenario: Cancel obsolete prefetch work
- **WHEN** the user changes provider, hides radar, leaves Map, or the app stops
- **THEN** obsolete speculative requests are cancelled, no new speculative requests start for the hidden or old-provider radar, and late completions cannot change the display
