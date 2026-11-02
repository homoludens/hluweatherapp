## Purpose

Make the Hourly forecast table readable while scrolling, keep its date selector aligned with the visible forecast date, and present precipitation values compactly without losing their units.

## ADDED Requirements

### Requirement: Hourly column headings remain unobscured
The Hourly table SHALL keep its date selector and column headings visible together while the user scrolls through forecast rows. The date selector SHALL NOT cover or obscure any column heading.

#### Scenario: Scrolling through hourly rows
- **WHEN** the user scrolls the Hourly table so forecast rows move beneath its pinned headers
- **THEN** the date selector and all configured column headings remain visible in separate, unobscured positions

#### Scenario: Scrolling across a date boundary
- **WHEN** the user scrolls from one forecast date to another while the headers are pinned
- **THEN** date labels and column headings remain readable and do not overlap

### Requirement: The date selector follows the visible table date
When the user scrolls the Hourly table, the date selector SHALL select the forecast date of the first currently visible date boundary or hourly entry. If the selected date chip is outside the visible portion of the date selector, the selector SHALL bring that chip into view. Selecting a date chip SHALL continue to move the table to that date's first hourly entry.

#### Scenario: Scrolling to another forecast date
- **WHEN** the first visible Hourly table entry belongs to a different date in the forecast's timezone
- **THEN** the matching date chip becomes selected and is visible in the date selector

#### Scenario: Scrolling within one date
- **WHEN** the user scrolls among hourly entries on the same date
- **THEN** that date remains selected without selecting a different date

#### Scenario: Selecting a date chip
- **WHEN** the user selects a date in the date selector
- **THEN** the Hourly table moves to the first hourly entry for that date and the chip remains selected

### Requirement: Precipitation unit appears in the column heading
The Hourly precipitation column heading SHALL identify the currently configured precipitation unit. Precipitation cell values SHALL show the converted numeric value without repeating the unit. Existing numeric precision and missing-value presentation SHALL be preserved.

#### Scenario: Metric precipitation display
- **WHEN** the configured precipitation unit is millimeters
- **THEN** the precipitation heading identifies millimeters and populated cells show numeric values without an `mm` suffix

#### Scenario: Imperial precipitation display
- **WHEN** the configured precipitation unit is inches
- **THEN** the precipitation heading identifies inches and populated cells show converted numeric values without an `in` suffix

#### Scenario: Missing precipitation value
- **WHEN** a forecast hour has no precipitation value
- **THEN** the cell retains the existing missing-value placeholder and the heading still identifies the configured unit
