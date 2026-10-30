## Context

See `proposal.md` for the motivation and `specs/medium-widget-presentation/spec.md` for the observable contract. The widgets use shared Glance card/header components and a pure size policy. Before this change the medium layout reserved 48 dp for a refresh button, displayed a date-and-time string, and used a tinted surface. Its existing responsive layout displays up to five hours; the completed state-adapter task can retain six without requiring a sixth display column. Hourly text is embedded in auto-sizing native `TextView` slots so already-cached views can refit when the launcher changes font scale without restarting the app.

The prior `add-home-screen-weather-widgets` change is still in progress. Its spec deltas are not yet published as main specs and include several requirements this follow-up intentionally changes. Its refresh/recovery work and its outstanding verification are independent of this presentation change.

## Goals / Non-Goals

**Goals:**

- Keep the layout responsive at actual launcher dimensions and font scales, with production-rendered evidence rather than relying only on calculated sizes.
- Isolate medium-specific header, background, insets, and preview changes from the small widget's current appearance and refresh action.
- Preserve truthful cached-weather and accessibility information even though the medium header no longer has a refresh control or status words.
- Coordinate the follow-up's behavior contract with the in-progress widget change without silently completing or rewriting unrelated work.

**Non-Goals:**

- Changing weather fetching, refresh/recovery scheduling, provider selection, location tracking, or the small widget's behavior.
- Adding more hourly columns or a new extra-hour fitting algorithm; the user withdrew that part of the request on 2026-10-30.
- Adding a background, scrim, wallpaper sampling, manual medium refresh action elsewhere, new settings, or a new permission.
- Guaranteeing foreground contrast against every possible wallpaper or controlling backgrounds painted by a launcher outside app-owned views.

## Decisions

### Medium-only presentation options for shared components

Parameterize the shared card/header or introduce a small medium-specific wrapper, choosing the simpler implementation that preserves the existing small defaults. For medium, omit the background modifier entirely while keeping the full-size root and location actions clickable; omit the refresh action and reclaim its width and 48 dp header-height reservation. Keep the currently selected theme's foreground palette. Reduce medium outer insets, for example from 8 dp to 4 dp horizontally and from 4–8 dp to 2–4 dp vertically, while retaining the internal cell, text/icon, and probability gaps. Keep the policy's body and width calculations in sync with the actual rendered insets and measured header.

Use a separate transparent medium initial-layout resource, because the existing initial layout is shared with the small provider. Remove the background layer from both the medium picker layout and its fallback preview image without changing the small assets. Transparent means zero app-drawn background alpha, not an almost-transparent white or dark drawable; launcher-provided chrome is outside the app's control.

**Alternative:** remove the shared drawable or change shared defaults globally. Rejected because doing so changes the small widget and its preview as well.

### Location-first header and truthful status

Format a time-only value from the forecast's fetch instant and location time zone in `WeatherWidgetState.kt`, alongside the existing full date/time for accessibility; do not parse a localized date string or use the current clock. In the medium header render location plus the time when it fits, reserve only the actual time and gaps, and omit time before sacrificing more location space. Keep the full name and full fetch timestamp in content descriptions; if the time is omitted, attach the timestamp to an accessible current-weather or root description. No `Updated`, date, status word, or refresh button appears in the header. A missing forecast has no invented time.

For stale/failed or in-progress states, use compact, non-clickable, visually distinct status glyphs in the current-weather area rather than a header label or a refresh-like spinner button. A warning-shaped glyph can denote stale/failure and a distinct hourglass can denote updating; represent both when both apply. Give the status an informative content description, including when the timestamp is hidden, and do not rely on color alone. Keep the cached fetch time unchanged after failure. Existing empty-state text remains actionable, and its status must remain truthful.

**Alternative:** hide status or encode it only by changing the time's color. Rejected because it would conceal stale weather or be inaccessible; keeping the old status text would defeat the time-only header.

### Center and enlarge the existing hourly layout

Retain the existing responsive three-, four-, and five-column selection without adding a sixth display column or a new fitting algorithm. The adapter may retain six future hours as completed in task 1.1, but the existing medium layout displays at most five. Preserve filtering, instant ordering, time-zone conversion, missing-value semantics, and network-free rendering.

Use the existing centered native-text layout for hourly times. Increase the precipitation target and its native auto-size role's maximum as needed; do not change shared small-widget text roles accidentally. Size the actual line boxes using measured Android text metrics, including font padding, and verify the rendered `TextView` size, not just a policy target. Preserve the existing host-only reapply overflow guard and readable physical minimum; do not bypass autosizing or globally cap system font scale. The spec's 9sp and 11sp minima apply to actual probability text in its named normal-scale scenarios.

**Alternative:** only increase an XML text maximum. Rejected because native auto-sizing, narrow widths, and line heights can still shrink or clip the visible text.

### Reconcile the unfinished widget specification

There are currently no published main specs. This change adds the separate `medium-widget-presentation` capability and must not leave contradictory requirements when the earlier widget capability is later synced. Before either change's affected specs are synced or archived, update the in-progress parent deltas if they are still the source of truth, preserving complete unaffected requirement blocks and existing task/evidence states. At minimum reconcile its `home-screen-weather-widgets` requirements for medium content, shared surfaces, theme, responsive columns, interactions, and accessibility, plus `widget-weather-updates` requirements that imply every widget has a manual refresh control. Preserve the small widget's manual refresh and all cache, stale, and automatic-update guarantees.

If those capabilities have already been published by implementation time, instead add full `MODIFIED` requirement deltas against the published main specs and update this proposal's capability list accordingly. Validate that the resulting contracts agree. Do not sync or archive another change or mark its tasks complete without separate authorization.

**Alternative:** assume the unfinished parent will disappear or create duplicate contradictory requirements. Rejected because later spec synchronization would otherwise restore the removed medium surface and button on paper.

## Risks / Trade-offs

- [Transparency can reduce foreground contrast on arbitrary wallpapers] → Preserve theme-appropriate colors and test representative light and dark wallpapers; do not claim universal contrast or add an unrequested scrim.
- [Larger probabilities compete for space in the existing columns] → Measure complete text/icon rows, retain the existing responsive behavior, and prioritize readable output at supported font scales.
- [The launcher reapplies cached views without app recomposition] → Retain native auto-sizing and exercise the same translated views at 100% → 130% → 100%, including glyph ink and bounds.
- [Removing the button may obscure refresh or failure state] → Keep distinct non-text status glyphs, accessible descriptions, cached weather, and automatic work; verify the small widget's refresh action separately.
- [The earlier change is still unfinished and its spec conflicts] → Reconcile only the affected requirements and preserve its open tasks and recorded verification history.

## Migration Plan

1. Update the medium-specific rendering and state adapter with focused policy, state, and RemoteViews tests; preserve current widget IDs and persisted data.
2. Reconcile the earlier capability requirements, update medium previews and deterministic example images, and run the relevant widget tests plus the required Google-debug verification. Verify real launcher behavior where a device is available; record unavailable checks as not run.
3. Ship through the normal app update, without a schema migration or new permission. To roll back the presentation, restore the prior medium rendering/resources and corresponding spec contract without clearing weather cache, locations, widget instances, or pending refresh work.
