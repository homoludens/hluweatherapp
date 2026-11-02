## Why

The Hourly table's date strip can cover its column labels during scrolling, and the visible date selector should stay in sync with the date currently shown in the table. Repeating the precipitation unit in every cell also uses scarce table width that could be avoided by showing the unit in the column heading.

## What Changes

- Keep the Hourly date strip and column labels visible together while scrolling, without the date strip obscuring the labels.
- Update the selected date in the visible date strip as the table scrolls to another date, and keep that selected date accessible in the strip.
- Show the configured precipitation unit in the column heading and omit the repeated unit from precipitation cell values.

## Capabilities

### New Capabilities

- `hourly-forecast-table`: Clear sticky table headings, date selection synchronized with table scrolling, and compact unit presentation for precipitation values.

### Modified Capabilities

None.

## Impact

Hourly forecast Compose layout and scroll/date selection behavior, precipitation column formatting, and focused Hourly UI tests. No API, data model, dependency, or persistence changes are intended.
