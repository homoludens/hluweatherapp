## Context

See `proposal.md` for motivation. The Hourly page is built by `HourlyWeatherContent` in `WeatherScreen.kt`: its weather card has 16 dp horizontal padding but a -8 dp vertical offset, while its day selector and table use different outer treatments. `DailyForecastList` uses 16 dp sides, 4 dp top, 24 dp bottom and 10 dp between cards. The main shell already owns system and bottom-navigation insets.

The main map uses shared `WeatherMap`, also used by other flows. MapLibre Compose 0.16.0's `MapOverlay.Default` includes an attribution button whose internal initial state is expanded and is not configurable. `RadarMapOverlay` displays the specified sentence as part of its radar timeline credits. The in-progress `add-radar-and-bottom-navigation` change requires map and applicable radar credits; its attribution tests currently expect the long text.

`WeatherRouteScreen` has a `LazyColumn` with a result map after the input controls. Its ViewModel publishes a current result asynchronously on success or weather-only failure and can retain an outdated previous result while calculating. Changes to departure and weather retries can also update a result without a button press.

## Goals / Non-Goals

**Goals:** Implement predictable page-level spacing, a main-map-only collapsed attribution control, compliant concise radar credits, and a one-shot scroll tied to the explicit route request. Keep existing screen and state ownership simple.

**Non-Goals:** Redesigning individual forecast card internals, removing required credits, changing other maps' initial behavior, changing route calculation/provider logic, adding a new navigation framework, or modifying persistence.

## Decisions

1. **Use 16 dp as the shared page spacing.** Reuse the existing forecast side gutter for page margins and peer-section gaps, including Hourly's card top and Daily's list boundaries. Remove the card's negative offset, align the day strip and table with the card, and account for sticky headers and both initial and collapsed selector presentations. Keep compact internal row and card spacing where it serves legibility. This avoids applying a blanket modifier to every nested child or duplicating shell insets. Verify actual measured bounds rather than relying only on modifier values.

2. **Replace only the main map's default attribution presentation.** Give the shared map an explicit opt-in for a collapsed initial attribution, enabled by the main Map caller only. Compose the equivalent MapLibre logo, scale/compass, style-sourced attribution links and a user-toggleable collapsed-first control using supported library APIs or a small local equivalent; do not also include the expanded default attribution button. Preserve map gestures, semantics, links and the existing controls. The installed default has a hardcoded expanded state, so simply including it cannot meet the requirement; changing all `WeatherMap` callers would unnecessarily alter picker maps. Test using a controllable overlay/style fixture where possible and an actual map on a device if available.

3. **Remove the sentence, not provider obligations.** Replace the long “Upstream may include…” sentence with concise separately presented credits as appropriate, retaining the existing LibreWXR credit and all currently required applicable upstream wording visibly with map credits. Check current provider and upstream terms before choosing the exact presentation, using the documented evidence in the in-progress radar design as a starting point. Update the existing radar UI tests to assert the sentence is absent and required credits remain; do not delete attribution tests or silently weaken the other change's requirement. If compliant concise attribution cannot be established, record the blocker rather than removing required credit.

4. **Associate the scroll with a successful explicit calculation, not any result change.** Keep a remembered list state and assign a stable key to the result-map item. Track a one-time pending explicit request and associate the published current result with that request/generation; use a small transient completion marker or event if needed to distinguish equal results, very fast completion, old results, automatic refreshes and superseded requests. Initiate scrolling only after the matching current map item is in the list, consume the request once, and cancel/clear it on validation failure, routing failure, invalidation, supersession or leaving the screen. A weather error with a valid current route still qualifies. Avoid using result object changes alone or fixed item indexes, either of which can scroll on unrelated updates or to the wrong item. Do not restore a consumed event on screen recreation.

## Risks / Trade-offs

- [Sticky headers or smaller screens regress after spacing changes] → Measure section bounds at rest and after scrolling, exercise day selection and verify insets and small screens.
- [Attribution control replacement omits links, credits, logo or other default controls] → Reuse supported MapLibre primitives, retain style-sourced credits, test open/close and check a real map when possible; leave other maps unchanged.
- [The removed text contains upstream credits required by current terms] → Verify and retain applicable credits in a concise visible format rather than dropping them; coordinate with the active radar change.
- [Scroll races with fast results, invalidation or list composition] → Tie the one-shot action to the matching calculation and map key; test delayed and immediate completion, re-calculation, weather failure and cancellation, and assert unrelated updates do not scroll.

## Migration Plan

This is a UI-only change with no data migration. Ship with the normal Google debug verification; rollback is a code rollback, with no stored settings to migrate.
