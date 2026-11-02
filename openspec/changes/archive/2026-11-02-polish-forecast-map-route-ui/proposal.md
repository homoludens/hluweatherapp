## Why

Inconsistent forecast spacing and initially expanded map information make the main screens feel crowded. Route results can also appear below the current viewport, forcing the user to search for the map after requesting trip weather.

## What Changes

- Standardize the outer margins and spacing between peer sections on the Hourly and Daily pages, including the top of the current weather card.
- Open the main Map page with the basemap attribution information collapsed but still available through its information control.
- Remove the sentence beginning “Upstream may include…” from the radar display while retaining applicable provider and upstream credits required by current terms.
- After an explicit “Show trip weather” request produces a current route result, scroll the route page to its resulting map.
- Preserve existing forecast, map/radar, attribution, route, accessibility, and navigation behavior.

## Capabilities

### New Capabilities
- `forecast-page-spacing`: Consistent, inset-aware outer spacing and section alignment on Hourly and Daily forecast pages.
- `map-information-presentation`: Initially collapsed main-map attribution information and concise radar credits without the specified sentence.
- `route-result-navigation`: One-time navigation to the resulting route map after an explicit trip-weather calculation.

### Modified Capabilities

None. The existing `weather-radar` specification is part of an in-progress change rather than the main spec inventory; coordinate with it and retain its attribution requirements.

## Impact

Jetpack Compose forecast layouts, main-map attribution controls, radar attribution presentation, route screen result handling, and focused UI tests are affected. The existing MapLibre overlay is shared with other maps, so changes to its initial state must be scoped to the main Map page. No backend, provider, or dependency change is intended.
