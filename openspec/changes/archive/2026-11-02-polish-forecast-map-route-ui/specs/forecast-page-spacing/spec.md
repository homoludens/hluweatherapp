## Purpose

Provide consistent, readable page-level spacing across the hourly and daily forecasts.

## ADDED Requirements

### Requirement: Consistent forecast page spacing
The Hourly and Daily pages SHALL use the same 16 dp page-level spacing for outer content margins and gaps between peer sections or cards. The top edge of the Hourly current-weather card and the top and bottom edges of the Daily list SHALL use that same spacing within the content area. Corresponding section edges SHALL align horizontally. System-bar and navigation insets SHALL be applied separately without duplicating them; component-internal spacing needed for readability and compact forecast table rows SHALL remain appropriate to those components.

#### Scenario: Hourly page at its initial position
- **WHEN** the user opens Hourly at the top of the page
- **THEN** the current-weather card has 16 dp of page content spacing above and at both sides, and the forecast sections share aligned page gutters and consistent peer-section gaps

#### Scenario: Daily list at its boundaries
- **WHEN** the user views the start and end of the Daily forecast list
- **THEN** the list has 16 dp page content spacing at its top, bottom and sides and 16 dp between daily cards

#### Scenario: Forecast scrolling and small screens
- **WHEN** the user scrolls either forecast page or views it on a small screen with system insets
- **THEN** its content remains reachable and unclipped, inset space is not doubled, and hourly sticky headers and day selection continue to work
