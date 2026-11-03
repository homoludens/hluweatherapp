## MODIFIED Requirements

### Requirement: Dynamic frames and playback
The app SHALL obtain the tile host, frame paths and timestamps from current metadata for the selected provider, distinguish observed from forecast frames, and show radar frame times in the device timezone. The timeline SHALL mark the latest observation, allow requesting any available frame, play or pause at approximately 500–800 ms per accepted frame, loop, and stop when Map is not visible. Playback SHALL wait for the requested frame's full coarse viewport byte coverage, staging installation, applicable bounded app-controlled warm-up, and accepted native promotion before starting its display interval. Acceptance SHALL mean submitted commands, not proven native drawing or physical presentation. The next playback frame SHALL be eligible for native preparation during the current display interval without changing selection or advancing early; a matching warmed frame SHALL NOT incur a second warm-up. Playback and cleanup SHALL NOT require renderer readiness, render-frame callbacks, completion of unrelated map work, or an additional playback interval. Forecast offsets SHALL be measured from the latest observation; absent forecasts SHALL not be invented. While a requested frame is pending, the timeline SHALL keep its text stable apart from the selected frame time and Observed/Forecast designation; it SHALL NOT add a transient loading message or duplicate requested and displayed time rows. Manual timeline selection SHALL pause playback.

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
- **WHEN** playback requests a frame whose byte coverage or bounded warm-up takes longer than the playback interval
- **THEN** the previous same-provider frame remains selected for display while the replacement is pending, the timeline shows only the requested frame's selected time and type, playback does not continually supersede that request, and the display interval begins after accepted promotion

#### Scenario: Native preparation completes before the next playback step
- **WHEN** the next available timeline frame is prepared while the current frame's display interval is running
- **THEN** the current selection and imagery remain unchanged until the interval ends, and the prepared frame is reused if it still matches the next request and viewport

#### Scenario: No renderer events arrive
- **WHEN** requested coarse byte coverage, installation, and applicable warm-up succeed but no render-frame, Full, Idle, or source-readiness event arrives
- **THEN** promotion, playback acknowledgment, and bounded cleanup proceed without waiting for those events, even if unrelated map work remains pending

#### Scenario: Drag timeline during playback
- **WHEN** the user manually changes the timeline selection while playback is running
- **THEN** playback pauses, unrequested native preparation is cancelled, and the most recent slider selection becomes the requested frame

#### Scenario: Playback loops at the timeline boundary
- **WHEN** playback reaches the last available frame and its display interval ends
- **THEN** the first available frame becomes the next requested frame and uses the normal bounded foreground handoff even if it was not prepared in advance

### Requirement: Best-effort continuous radar frame handoff
For timeline scrubbing and playback within the same provider and loaded map style, the app SHALL use at most two native radar slots and retain the accepted front while preparing a replacement. Handoff SHALL require successful full coarse current-viewport byte coverage and staging installation, followed by a bounded app-controlled warm-up while the old front remains selected for display. Elapsed warm-up from a matching prepared frame SHALL be reused; metadata or a timer alone SHALL NOT substitute for successful byte coverage and installation. The app SHALL submit the replacement at the chosen opacity before hiding the old front, without deliberately blanking both layers, adding a crossfade, or holding both at the chosen opacity. Hidden predecessor resources SHALL be retained for a bounded cleanup grace that can yield immediately to new demand and SHALL NOT require a render acknowledgment. The timer and command acceptance SHALL NOT be represented as native drawable readiness; residual basemap-only gaps or opacity discontinuities remain possible and continuity is best effort, not universally blink-free. Successful transparent or no-content coverage SHALL be valid and SHALL remove obsolete precipitation rather than retain it indefinitely. Rapid selection changes SHALL converge to the latest request; obsolete preparations, timers, and native commands SHALL NOT replace newer selections. Failed, timed-out, or rate-limited replacements before accepted promotion SHALL preserve or restore the compatible front with an unobtrusive error indication; errors after accepted submission SHALL NOT be misrepresented as having been detectable before native drawing. The timeline SHALL retain the selected time and type without loading flashes or duplicate displayed-time rows. Ordinary initial loading is permitted without an artificial warm-up delay when no visible front exists. Disabling radar, hiding Map, or stopping the app SHALL remove imagery and fence late completions. Provider, style, surface, and visible-tile-set changes SHALL invalidate incompatible preparation and timers; continuity across those changes is not guaranteed.

#### Scenario: Scrub to an uncached frame
- **WHEN** the user selects a time whose current-viewport coarse radar tiles are not ready
- **THEN** the current same-provider radar remains selected for display through successful byte loading, installation, and bounded warm-up, then the app submits replacement before hiding the old front without claiming native readiness

#### Scenario: Cached bytes precede native drawing
- **WHEN** replacement tile bytes are cached but native delivery or decoding remains delayed beyond the app-controlled warm-up
- **THEN** the app can still promote and acknowledge the replacement without renderer events, and any resulting visual gap is recorded as a limitation rather than hidden by a readiness claim

#### Scenario: Matching warmed replacement is selected
- **WHEN** the user or playback selects a prepared replacement matching the frame, coarse viewport, style, and source lifetime whose warm-up has already elapsed
- **THEN** the app reuses its installed native slot, submits promotion without repeating warm-up, and recycles the hidden predecessor after bounded cleanup or earlier for new demand

#### Scenario: Superseded frame finishes late
- **WHEN** the user selects frame B then frame C and B's byte load, warm-up timer, or cleanup completes after C is requested
- **THEN** B cannot become the newly accepted selection and cannot alter C's slot, timing, opacity, or ownership

#### Scenario: Replacement cannot load
- **WHEN** the requested replacement fails, times out, or is rate-limited before accepted promotion
- **THEN** the compatible front is preserved or restored, the error is distinguishable without a duplicate displayed-time row, and the map remains usable

#### Scenario: Valid replacement contains no precipitation
- **WHEN** all required replacement byte coverage is successfully transparent or no-content and the normal installation and warm-up policy succeeds
- **THEN** promotion is accepted normally and the old precipitation is not retained indefinitely merely because the replacement has no colored pixels

#### Scenario: Disable during transition
- **WHEN** radar is disabled while a replacement is loading, being prepared, being swapped, or being retired
- **THEN** all radar imagery and the timeline are hidden and late completions or native commands cannot restore them

#### Scenario: Outgoing provider or style finishes late
- **WHEN** a provider, map-style, or surface change supersedes pending preparation, timers, promotion, or cleanup
- **THEN** outgoing work cannot be reused by the new provider/style/surface and obsolete resources are released safely

#### Scenario: Viewport or opacity changes during preparation
- **WHEN** the visible radar tile set changes or the user adjusts opacity while a replacement is being prepared
- **THEN** incompatible viewport preparation and warm-up are invalidated, the compatible front remains available where it has coverage, the latest opacity applies, and opacity zero stops optional preparation without waiting for a rendered invisible frame

### Requirement: Adjacent radar frame prefetch
While the main Map and radar are visible, the app SHALL opportunistically prefetch radar image tiles covering the current viewport for the immediately previous and next available timeline indices around the requested frame, for either provider. Steps SHALL mean adjacent entries in the provider's chronologically ordered timeline, not a hard-coded time interval. Prefetch SHALL use the same coarse-only policy as visible radar and SHALL NOT warm a higher-detail replacement. The neighborhood SHALL exclude the requested frame, clamp at endpoints without wrapping, deduplicate resources, and reuse downloaded tiles. Prefetch SHALL retain bounded storage, at most four simultaneous speculative requests within the shared eight-request limit, foreground priority, and existing provider budgets/backoff; it SHALL avoid unrelated regions and unsupported zooms.

During active playback, the app SHALL attempt native preparation of at most the next non-wrapping timeline frame from valid cached bytes when the front is accepted and a spare slot is available. A preparation opportunity SHALL be reconsidered when that next frame's byte prefetch completes, even if selection and viewport have not changed; retries SHALL NOT become a polling or recreation loop. Unrequested native preparation, including fallback tiles, SHALL NOT initiate HTTP requests, advance selection, alter timeline text, block demand, or fail the front. Missing or unusable required viewport bytes SHALL abandon optional preparation; additional optional parent/fallback cache misses SHALL NOT be misclassified as required coverage failures or replaced by invented empty weather data. Actual selection SHALL use normal foreground loading if no reusable slot exists.

Obsolete selection, viewport, provider, metadata, playback, or visibility work SHALL be cancelled, discarded, or revalidated before reuse, and late completions SHALL NOT alter selection. No speculation or unrequested preparation SHALL continue when Map/radar is hidden or the app stops. Existing memory-only and cache-control-aware storage policies SHALL remain unchanged.

#### Scenario: Middle of timeline
- **WHEN** the requested frame is at index i with a previous and next available frame and request budget is available
- **THEN** byte prefetch targets are i−1 and i+1 for the current coarse viewport tile set without duplicating the requested frame's tile requests, and active playback can prepare only i+1 natively from valid cached resources

#### Scenario: Timeline boundary or short timeline
- **WHEN** the requested frame is at an endpoint or the timeline has fewer than three frames
- **THEN** only valid immediately adjacent indices are prefetched, native preparation does not wrap, and no frames or duplicate loads are invented

#### Scenario: Reuse prefetched imagery
- **WHEN** the user selects a neighboring frame whose coarse viewport tiles have been prefetched and remain valid in cache
- **THEN** display uses those downloaded resources without downloading the same tiles again or requesting detailed replacements, and a matching prepared native frame is reused when available

#### Scenario: Native preparation misses required viewport bytes
- **WHEN** an unrequested native frame needs a required planned viewport tile absent from the valid cache
- **THEN** that preparation starts no HTTP work, is abandoned without changing the visible frame or reporting a display error, and does not prevent later byte-prefetch opportunities or subsequent foreground loading of the frame

#### Scenario: Optional coarse fallback misses the cache
- **WHEN** required planned viewport bytes are cached but native preparation requests an additional coarse parent or fallback that is not cached
- **THEN** the app starts no HTTP work and invents no empty weather data for that fallback, while allowing preparation of the covered planned tiles to continue where supported; an optional fallback miss alone does not become a failure of required viewport coverage

#### Scenario: Prefetch finishes after the first preparation attempt
- **WHEN** next-frame bytes were initially unavailable but matching scoped byte prefetch completes during the current frame's display interval
- **THEN** native preparation can start without a new selection, renderer event, or repeated polling, while the current frame stays selected

#### Scenario: Requested frame takes priority over prepared neighbor
- **WHEN** a manual request or new viewport conflicts with an unrequested prepared frame
- **THEN** the optional preparation yields the spare rendering slot and requested-frame loading takes priority without exceeding the existing HTTP limits

#### Scenario: Viewport changes
- **WHEN** the user pans or zooms to a different visible tile set
- **THEN** the requested frame's coarse new-viewport tiles take priority, incompatible native preparation is discarded, and the byte-prefetch neighborhood is recomputed within resource and provider limits

#### Scenario: Cancel obsolete prefetch work
- **WHEN** the user changes provider, hides radar, leaves Map, or the app stops
- **THEN** obsolete speculative requests and native preparation are cancelled, no new speculative requests start for the hidden or old-provider radar, and late completions cannot change the display

#### Scenario: Pause or refresh invalidates next-frame preparation
- **WHEN** playback pauses or refreshed metadata changes the identity of the next frame
- **THEN** optional native preparation is cancelled or discarded without changing the current requested/displayed frame, and future playback uses the current timeline
