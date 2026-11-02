# HluWeatherApp — Four-Tab Bottom Navigation Redesign

## Objective

Replace the existing top navigation tabs with a compact Material 3 bottom navigation bar containing four destinations:

1. **Hourly** — clock icon
2. **Daily** — bar chart icon
3. **Map** — location pin icon
4. **Route** — route/path icon

Keep the app's existing design language, colors, typography, and functionality.

This is primarily a UI and navigation refactor. Avoid changing unrelated business logic.

## 1. Simplify the top header

Remove:
- The existing Hourly / Daily / Map navigation tabs.
- The subtitle "Simple weather. Clear view."
- Any unused spacing previously reserved for the subtitle or tabs.

Keep:
- **HluWeatherApp** title, left-aligned.
- Settings gear icon, right-aligned.
- Existing blue/purple gradient background.

Make the header significantly more compact, approximately 56–64 dp tall excluding the system status bar.

Vertically center the title and settings icon.

Preserve the existing behavior of the settings button.

Do not introduce another toolbar or screen title unless necessary.

## 2. Add bottom navigation

Use Jetpack Compose Material 3 `NavigationBar` and `NavigationBarItem`, or the equivalent components already used in the project.

### Appearance

- Four equally spaced destinations.
- Icons above labels.
- Labels: Hourly, Daily, Map, Route.
- Rounded pill-shaped selection indicator.
- Selected icon and label use the existing primary blue/purple color.
- Unselected icons and labels use muted colors.
- Navigation bar background matches the existing theme.
- Support light and dark themes.
- Respect Android system navigation insets.
- Use existing app icons and typography wherever possible.

Keep the navigation bar compact and consistent with Material 3 conventions.

Do not add a large floating or oversized navigation component.

## 3. Navigation destinations

| Tab | Screen |
|---|---|
| Hourly | Existing hourly forecast |
| Daily | Existing daily forecast |
| Map | Existing MapLibre map and radar |
| Route | Existing Weather on Route feature |

Reuse the existing screens, ViewModels, repositories, and navigation routes.

Do not create a second implementation of Weather on Route.

Make Route a normal top-level destination, accessible with one tap from any tab.

## 4. Remove redundant Route shortcuts

Since Route now has its own bottom navigation destination:

- Remove the "Weather on route" shortcut from the Hourly screen, if present.
- Remove the floating "Weather on route" button from the Map screen.
- Keep the existing Route screen and all its functionality unchanged.

Do not remove other map controls or radar controls.

## 5. Navigation behavior

- Keep Hourly as the default tab unless existing settings specify otherwise.
- Highlight the selected tab.
- Preserve scroll position and screen state when switching destinations where practical.
- Avoid unnecessary network requests or screen recreation.
- Keep existing back-navigation behavior sensible.
- Avoid creating duplicate destinations on the navigation back stack.
- Keep the bottom navigation visible on all four main screens.
- Hide it on secondary screens only where appropriate, following the existing navigation architecture.

## 6. Screen layout adjustments

### Hourly
- Move content upward to use the space freed by the smaller header.
- Preserve the weather card, date selector, and forecast table.
- Keep existing scrolling behavior.

### Daily
- Move the daily forecast list upward.
- Preserve existing forecast cards and scrolling.

### Map
- Expand the MapLibre map to fill the available space between the compact header and bottom navigation.
- Preserve radar overlays, animation timeline, GPS, and layer controls.
- Ensure map controls do not overlap the bottom navigation.
- Respect the new map viewport size.

### Route
- Reuse the existing Weather on Route interface.
- Ensure the route planner and map fit correctly above the bottom navigation.
- Preserve all route calculation and weather functionality.

## 7. Architecture and implementation

Before modifying code:

1. Inspect the existing Compose navigation structure.
2. Identify the shared header and current top tabs.
3. Locate the existing Weather on Route screen.
4. Identify how screen state and navigation are currently managed.

Then implement the new bottom navigation using the project's existing architecture.

Avoid introducing unnecessary dependencies or rewriting screens.

## 8. Testing

Verify that:

- The subtitle is completely removed.
- The header contains only the app title and settings icon.
- The old top navigation is removed.
- All four bottom destinations work.
- The selected tab indicator updates correctly.
- Hourly and Daily forecasts still work.
- MapLibre and radar controls still work.
- Weather on Route works as a top-level destination.
- No redundant Route shortcuts remain.
- Screen state is preserved where practical.
- The layout works on small phones.
- Light and dark themes work.
- Android system navigation and status bars are handled correctly.
- No unnecessary API requests or navigation regressions are introduced.

## Important

**Prioritize a minimal, clean navigation refactor.**

Do not redesign the forecast cards, radar visualization, route planner, or settings.

The final app should have a compact gradient header with just **HluWeatherApp** and the settings gear, plus a persistent four-destination bottom navigation bar.
