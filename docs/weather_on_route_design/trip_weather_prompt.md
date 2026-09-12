Redesign ONLY the UI of the existing Trip Weather screen so it visually matches the rest of the HluWeather app.

IMPORTANT:
- Do NOT change any existing functionality.
- Do NOT change repositories, ViewModels, APIs, routing logic, weather logic, map logic, state models, callbacks, navigation, or business logic.
- Do NOT rename existing state fields or event handlers unless strictly necessary for UI compilation.
- Do NOT replace working components with new implementations if the existing ones can be styled.
- Keep all current interactions exactly as they work now.
- This task is UI/Compose styling only.

Use the attached redesigned screenshot as the visual reference.

The screen should look like a natural part of the existing app:
- Material 3
- same app theme
- same colors
- same typography
- same cards
- same rounded corner radii
- same spacing rhythm
- same weather icons already used elsewhere in the app
- same map styling
- same light/dark theme behavior

Before editing:
1. Inspect the existing Trip Weather screen.
2. Inspect the existing Weather screen and Settings screen.
3. Reuse existing shared components and theme tokens.
4. Reuse existing weather icon composables/assets.
5. Reuse existing buttons, cards, text field styling and top bar styling where available.

Do not create a second design system.

## Screen layout

Keep the existing screen content and functionality, but rearrange/stylize it approximately like this:

### Header

Use the same top app bar style as the rest of the app.

Content:

Back arrow

Trip weather
Plan your journey. Know the weather ahead.

The subtitle should use the app's secondary/onSurfaceVariant text color.

Avoid excessive empty space below the header.

## Start / Destination card

Put the current Start and Destination controls inside one rounded elevated card.

Use the app's normal surface/card background.

For each location:

location icon

label:
Start
or
Destination

selected location text

Under each location provide the EXISTING actions:

Saved locations
Pick on map

These should be styled like small tonal/secondary buttons or chips, consistent with the rest of the app.

Do not change what these actions do.

Preserve the current search functionality.

If the search fields currently appear conditionally, preserve that exact behavior.

Add the existing swap start/destination action visually between/right of the two location sections.

Use an existing Material icon for swap.

Do not modify the swap logic.

## Speed card

Place speed in its own rounded card.

Layout:

car icon

Speed                           80 km/h
80 km/h

[ slider ]

30                               130

Use the existing speed value and slider logic.

Only restyle it.

Use the app's primary blue accent for:
- active slider track
- thumb
- selected values

Inactive track should use a subtle surface/outline color.

Make the card compact.

## Start time card

Move the existing start-time slider into a dedicated rounded card.

Header:

clock icon

Start time

formatted selected date/time

Optionally use the existing date/time picker action as a tonal button if one already exists.

Then show the current 72-hour slider:

Now      +24h      +48h      +72h

The currently selected time should be visually prominent.

For example:

21:02
12 Sep 2026

Reuse the existing slider logic exactly.

Do not change:
- range
- hour calculation
- date/time state
- refresh behavior

Only improve presentation.

Avoid showing raw values such as:

2026-09-12 at 21:02:31.766258

Format the existing value for display only.

Use something readable like:

Fri, 12 Sep 2026 · 21:02

Do not alter the underlying datetime type.

## Map section

Keep the existing MapLibre/OpenFreeMap implementation exactly as it is.

Do NOT change:
- route rendering
- coordinates
- camera
- map source
- route calculation
- markers
- map callbacks

Only change the surrounding Compose layout/styling.

Map should:
- span nearly full screen width
- have rounded corners
- be approximately 280–340 dp tall
- visually align with other cards

Keep weather icons along the route.

VERY IMPORTANT:
- Reuse the SAME weather icons used in the normal weather screen.
- Do not use emoji.
- Do not invent new weather icon graphics.

Weather markers on the map should be visually cleaner:
- smaller than current oversized green circles
- use a light circular/tonal background
- weather icon centered inside
- no green background unless green is already part of the app theme
- approximately 36–44 dp marker size
- subtle shadow/elevation if easy

Do not change marker positions or marker logic.

Preserve map attribution.

## Forecast table

Restyle the existing trip forecast list/table to match the hourly weather forecast table elsewhere in the app.

Columns:

Time
Weather
Location

Keep the current data and row logic.

Use:
- compact row height
- subtle dividers
- clear table header
- same typography used in normal hourly forecast
- existing weather icon component

Example:

21h    [partly cloudy icon] Partly cloudy    Route checkpoint 1
22h    [moon icon]          Clear sky        Route checkpoint 3
23h    [moon icon]          Clear sky        Route checkpoint 5
00h    [moon icon]          Clear sky        Route checkpoint 7
01h    [moon icon]          Clear sky        Route checkpoint 9

Do not change row ordering or data source.

If current checkpoint names come from existing logic, keep them exactly.

## Main action button

Keep the existing:

Show trip weather

function and callback unchanged.

Restyle it as the same full-width primary button used elsewhere in the app.

Use:
- large rounded shape
- primary blue
- white content
- existing weather/trip icon if available
- optional trailing arrow

Do not alter its behavior.

## Spacing

The current screen has too much empty vertical space.

Reduce spacing between:
- header and locations
- location sections
- speed card
- time card
- map
- forecast table

Use consistent spacing such as:
- 8dp
- 12dp
- 16dp
- 20dp

Avoid arbitrary large spacers.

## Cards

Use the same card style as the existing weather/settings screens.

Preferred:
- RoundedCornerShape around 20–24dp
- subtle elevation or tonal separation
- MaterialTheme.colorScheme.surface
- outlineVariant only where useful

Do not hardcode white backgrounds because dark mode must work.

## Typography

Reuse MaterialTheme typography and existing app typography.

Suggested hierarchy:

screen title:
MaterialTheme.typography.headlineSmall / titleLarge

card title:
titleMedium

main values:
titleLarge / headlineSmall

secondary text:
bodyMedium / bodySmall

Avoid custom font sizes unless necessary.

## Icons

Only use:
- existing app icons
- Material icons already available
- existing weather icon composables/assets

Do not use emoji.

Use icons consistently for:

Start/Destination:
LocationOn

Speed:
DirectionsCar

Start time:
Schedule

Map selection:
Map

Saved location:
Bookmark/Place

Swap:
SwapVert

Main CTA:
existing weather/trip icon + ArrowForward

## Dark mode

The screen must automatically work in the existing dark theme.

Do not introduce hardcoded:
Color.White
Color.Black
fixed gray backgrounds

unless the app theme already uses them intentionally.

Prefer:

MaterialTheme.colorScheme.background
surface
surfaceVariant
primary
primaryContainer
onSurface
onSurfaceVariant
outlineVariant

## Preserve functionality

The following must continue working exactly as now:

- start location selection
- destination selection
- saved locations
- pick on map
- location search
- use current location
- swap locations
- speed slider
- 72-hour start-time slider
- selected start time
- Show trip weather
- weather loading
- route loading
- route map
- weather markers
- forecast table
- navigation/back action

Do not change any of these flows.

## Code constraints

Prefer modifying the existing composables rather than replacing the entire feature.

Extract small UI-only composables if useful, such as:

TripLocationCard
TripSpeedCard
TripStartTimeCard
TripForecastTable

but keep state hoisted from the existing screen.

Do not move business logic into new composables.

Do not introduce a new ViewModel.

Do not introduce a new repository.

Do not add new network requests.

Do not add new dependencies unless absolutely necessary.

## Final result

The result should visually resemble the provided redesign while preserving 100% of the current Trip Weather functionality.

The Trip Weather screen should look like it was designed together with the existing HluWeather Weather and Settings screens, not as a separate feature.

After implementation:
- build the app
- fix Compose/UI compilation issues
- confirm the existing interactions still call the same handlers
- do not refactor unrelated code
