## MODIFIED Requirements

### Requirement: Dynamic frames and playback
The app SHALL obtain the tile host, frame paths and timestamps from current metadata for the selected provider, distinguish observed from forecast frames, and show radar frame times in the device timezone. The timeline SHALL mark the latest observation, allow requesting any available frame, play or pause at approximately 500–800 ms per completed frame handoff when ready, loop, and stop when Map is not visible. Playback SHALL wait for the requested frame's coarse viewport coverage, native drawable readiness, and correlated acknowledgment of its rendered swap before starting that frame's display interval; command submission alone SHALL NOT acknowledge display. The next playback frame SHALL be eligible for bounded native preparation during that interval without changing the requested selection or advancing early. Playback SHALL NOT depend on completion of unrelated basemap work or arbitrary additional delays. Forecast offsets SHALL be measured from the latest observation; absent forecasts SHALL not be invented. While a requested frame is pending, the timeline SHALL keep its text stable apart from the selected frame time and Observed/Forecast designation; it SHALL NOT add a transient loading message or duplicate requested and displayed time rows. Manual timeline selection SHALL pause playback.

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
- **WHEN** playback requests a frame that takes longer than the playback interval to become drawable
- **THEN** the previous same-provider frame remains visible while the replacement is pending, the timeline shows only the requested frame's selected time and type, playback does not continually supersede that request, and the display interval begins only after its completed handoff

#### Scenario: Native preparation completes before the next playback step
- **WHEN** the next available timeline frame is prepared while the current frame's display interval is running
- **THEN** the current selection and imagery remain unchanged until the interval ends, and the prepared frame is reused if it still matches the next request and viewport

#### Scenario: Unrelated basemap work remains pending
- **WHEN** the requested radar frame has valid drawable readiness and its rendered swap is acknowledged while unrelated map resources remain pending
- **THEN** radar playback proceeds without waiting for a full-map completion event

#### Scenario: Drag timeline during playback
- **WHEN** the user manually changes the timeline selection while playback is running
- **THEN** playback pauses, unrequested native preparation is cancelled, and the most recent slider selection becomes the requested frame

#### Scenario: Playback loops at the timeline boundary
- **WHEN** playback reaches the last available frame and its display interval ends
- **THEN** the first available frame becomes the next requested frame and uses the same safe handoff even if it was not prepared in advance

### Requirement: Best-effort continuous radar frame handoff
For timeline scrubbing and playback within the same provider, loaded map style, and unchanged visible tile set, the app SHALL retain the last good radar frame while a replacement is loading or undergoing native preparation. The app SHALL require successful coarse current-viewport tile coverage and evidence that the exact installed replacement imagery is drawable for that viewport before hiding the previous frame. Readiness and swap acknowledgment SHALL be correlated with the requested frame, visible tile set, source/style lifetime, and applicable render update; stale evidence SHALL NOT satisfy a newer request. Cached bytes, successful native resource delivery alone, command acceptance, fixed delays, and uncorrelated map-wide render events SHALL NOT count as drawable readiness. The replacement SHALL appear through one hard swap at the selected opacity without an intentional blank interval, crossfade, or full-opacity overlap; predecessor resources SHALL remain owned until the corresponding rendered swap is acknowledged. Successful transparent or no-content coverage SHALL be valid and SHALL remove obsolete precipitation rather than preserve it indefinitely. Rapid selection changes SHALL converge to the most recent request; superseded loads, readiness evidence, and native commands SHALL NOT replace newer selections. Failed, timed-out, or rate-limited replacements SHALL preserve or restore the last good same-provider frame with an unobtrusive error indication; the timeline SHALL retain the selected frame time and type without flashing pending/loading text or adding a duplicate displayed-time row. Before any frame has loaded, ordinary loading/empty states are permitted. Disabling radar, hiding Map, or stopping the app SHALL remove radar imagery and prevent late completions from restoring it. Provider and style changes SHALL invalidate outgoing imagery and readiness; continuity across those changes or newly exposed viewport regions is not guaranteed.

#### Scenario: Scrub to an uncached frame
- **WHEN** the user selects a time whose current-viewport coarse radar tiles are not ready
- **THEN** the current same-provider radar remains visible until the requested replacement is drawable and can be swapped without an intentional basemap-only interval

#### Scenario: Cached bytes precede native readiness
- **WHEN** replacement tile bytes are cached but native delivery, decoding, or preparation is delayed
- **THEN** the old frame remains visible, the replacement is not reported as displayed, and command acceptance or an unrelated completed map frame cannot trigger the handoff

#### Scenario: Drawable replacement is swapped
- **WHEN** the current replacement is confirmed drawable for the unchanged viewport
- **THEN** one guarded update replaces the old frame at the chosen opacity without a blank or double-opacity frame, and the old resources are released only after acknowledgment of that swap

#### Scenario: Superseded frame finishes late
- **WHEN** the user selects frame B then frame C and B becomes ready after C is requested
- **THEN** B cannot become the newly displayed selection, and C replaces the last good frame only using readiness evidence for C's current request

#### Scenario: Replacement cannot load
- **WHEN** the requested replacement fails, times out, or is rate-limited before its handoff is acknowledged
- **THEN** the last good same-provider imagery is preserved or restored, the error is distinguishable without a duplicate displayed-time row, and the map remains usable

#### Scenario: Valid replacement contains no precipitation
- **WHEN** all required replacement coverage is successfully transparent or no-content and native readiness is established
- **THEN** the handoff completes normally, the old precipitation disappears as valid weather data, and the absence of colored pixels is not classified as a failed load

#### Scenario: Disable during transition
- **WHEN** radar is disabled while a replacement is loading, being prepared, being swapped, or being retired
- **THEN** all radar imagery and the timeline are hidden and late completions or native commands cannot restore them

#### Scenario: Outgoing provider or style finishes late
- **WHEN** a provider or map-style change supersedes pending readiness, promotion, acknowledgment, or cleanup
- **THEN** outgoing imagery and readiness cannot be reused by the new provider/style and obsolete resources are released safely

#### Scenario: Viewport or opacity changes during preparation
- **WHEN** the visible radar tile set changes or the user adjusts opacity while a replacement is being prepared
- **THEN** incompatible viewport readiness is invalidated, any same-style front remains available where it has coverage, current opacity applies to the handoff, and opacity zero cannot be mistaken for proof of visible replacement readiness

### Requirement: Adjacent radar frame prefetch
While the main Map and radar are visible, the app SHALL opportunistically prefetch radar image tiles covering the current viewport for the immediately previous and next available timeline indices around the requested frame, for either provider. Steps SHALL mean adjacent entries in the provider's chronologically ordered timeline, not a hard-coded time interval. Prefetch SHALL use the same coarse-only tile policy as visible radar and SHALL NOT warm a higher-detail replacement. The neighborhood SHALL exclude the requested frame, clamp at timeline endpoints without wrapping, deduplicate resources, and reuse downloaded tiles for subsequent display. Prefetching SHALL use bounded storage, at most four simultaneous speculative requests within the shared eight-request limit, foreground priority, and existing provider rate budgets and backoff. It SHALL avoid unrelated regions and unsupported zoom levels. While playback is active, the app SHALL additionally attempt native preparation of at most the immediately next non-wrapping timeline frame, using only valid cached tiles, after the requested frame's handoff is complete and a spare rendering slot is available. Native preparation of an unrequested frame SHALL NOT initiate HTTP requests, advance selection, change timeline text, block requested work, or fail the currently displayed frame. Missing or unusable cached resources SHALL abandon that optional preparation; a later request SHALL use the normal safe foreground handoff. Requests and prepared imagery made obsolete by selection, viewport, provider, metadata, playback, or visibility changes SHALL be cancelled, discarded, or revalidated before reuse, and their completions SHALL NOT alter the active selection. No speculative radar requests or unrequested native preparation SHALL continue when Map/radar is hidden or the app is stopped. Radar data SHALL retain the existing memory-only, cache-control-aware storage policy.

#### Scenario: Middle of timeline
- **WHEN** the requested frame is at index i with a previous and next available frame and request budget is available
- **THEN** byte prefetch targets are i−1 and i+1 for the current coarse viewport tile set without duplicating the requested frame's tile requests, and active playback can prepare only i+1 natively from valid cached resources

#### Scenario: Timeline boundary or short timeline
- **WHEN** the requested frame is at an endpoint or the timeline has fewer than three frames
- **THEN** only valid immediately adjacent indices are prefetched, native preparation does not wrap, and no frames or duplicate loads are invented

#### Scenario: Reuse prefetched imagery
- **WHEN** the user selects a neighboring frame whose coarse viewport tiles have been prefetched and remain valid in cache
- **THEN** display uses those downloaded resources without downloading the same tiles again or requesting detailed replacements, and a matching prepared native frame is reused when available

#### Scenario: Native preparation misses the cache
- **WHEN** an unrequested native frame needs a tile or fallback tile absent from the valid cache
- **THEN** that preparation starts no HTTP work, is abandoned without changing the visible frame or reporting a display error, and does not prevent subsequent foreground loading of the frame

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
