# route-result-navigation Specification

## Purpose

Bring a requested trip-weather map into view when the route result becomes available.

## Requirements

### Requirement: Navigate to a requested route result
After the user activates “Show trip weather” and that request produces a current route result, the Route page SHALL scroll to bring the resulting map into view once it is available. This SHALL work for a new calculation and for a recalculation, including when the route is available but weather data fails. The map SHALL be visible within the unobstructed scrollable content area, and the user SHALL remain able to scroll manually afterward.

#### Scenario: Successful calculation
- **WHEN** the user requests trip weather with valid inputs and a current route result becomes available
- **THEN** the page scrolls to show the resulting route map without requiring a manual scroll

#### Scenario: Recalculate or weather is unavailable
- **WHEN** the user explicitly requests another calculation and its current route result is available, including a result with a weather error
- **THEN** the page scrolls to that result's map and preserves any relevant error or retry controls

### Requirement: Avoid unrelated or stale automatic scrolling
Automatic scrolling SHALL be tied to the latest explicit “Show trip weather” request and consumed once for its current result. Validation failure, routing failure, cancellation, or an outdated result SHALL NOT scroll to an old or absent map. Recomposition, reopening a previously displayed result, changing controls, selecting a sample, automatic updates, and weather-only retries SHALL NOT independently trigger another automatic scroll.

#### Scenario: Invalid, failed, or superseded request
- **WHEN** a calculation cannot produce a current route result because validation or routing fails, or because the request is cancelled or superseded
- **THEN** the page does not automatically navigate to an old or absent map and the existing feedback remains accessible

#### Scenario: Interacting after a result
- **WHEN** the user manually scrolls away from the result or changes selection or other controls after the request has completed
- **THEN** the page does not pull the user back to the map without another explicit trip-weather request
