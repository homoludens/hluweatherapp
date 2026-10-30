## Why

The medium weather widget currently truncates location names too aggressively, uses valuable header space for update metadata and a refresh control, and makes the hourly precipitation values hard to read. A denser, transparent presentation can show more useful weather information without obscuring the home-screen wallpaper.

## What Changes

- Give the location more header space; show only the forecast's local update time where the update label, date, and refresh button currently appear. Remove the medium widget's refresh button; retain automatic updates and the small widget's refresh control.
- Center each upcoming-hour label and increase the readability of hourly precipitation probabilities.
- Retain the existing responsive three-to-five-hour layout; additional-hour fitting is out of scope at the user's request.
- Reduce the medium widget's outer padding while keeping the existing internal spacing between data elements.
- Remove the medium widget's app-drawn white or dark surface so the launcher wallpaper is visible through it, including the medium-specific initial and picker previews.
- Preserve truthful stale/refresh status, cached-weather semantics, app-opening interactions, accessibility, and fit across supported widget and system font sizes. The small widget is otherwise unchanged.

## Capabilities

### New Capabilities

- `medium-widget-presentation`: The medium widget's compact header, transparent surface, responsive hourly display, and readability/interaction requirements.

### Modified Capabilities

- None currently published under `openspec/specs/`. The in-progress `add-home-screen-weather-widgets` change already proposes `home-screen-weather-widgets` and `widget-weather-updates`; its conflicting requirements must be reconciled with this follow-up before either change's specs are synced or archived.

## Impact

- Android Glance medium layout, shared card/header configuration, responsive sizing policy, forecast-to-widget state adapter, and native auto-sizing text styles.
- Medium provider initial/preview resources, widget rendering/size/state tests, reference images, and launcher QA. Shared components must not change the small widget's presentation or actions.
- The existing widget change has unfinished refresh/recovery and verification work. This follow-up does not implement or mark those tasks complete; integration must preserve their state and avoid conflicting spec requirements.
- No new weather provider, network path, permission, or release/signing workflow is introduced.
