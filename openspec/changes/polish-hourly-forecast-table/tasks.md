## 1. Sticky hourly headings

- [x] 1.1 [low] Keep the Hourly date selector and column headings in one unobscured pinned layout on the main screen and reusable Hourly table; add focused UI assertions for separated header bounds at the top, during scrolling, and across a date boundary, and run the relevant `WeatherScreenTest` and `HourlyForecastTest` cases.

## 2. Scroll-synchronized date selector

- [ ] 2.1 [high] Synchronize the selected date chip with the first visible table date, reveal the active chip in the horizontal selector, and preserve chip-tap navigation without scroll feedback; test manual scrolling across dates, dates near both ends, chip selection, timezone boundaries, and no snap-back in focused Hourly screen tests.

## 3. Precipitation unit presentation

- [ ] 3.1 [low] Move the configured `mm`/`in` unit into the precipitation column heading and show converted numeric values without repeated unit suffixes while preserving precision and missing-value placeholders; test metric, inch, and missing values in focused Hourly UI tests.

## 4. Integration verification

- [ ] 4.1 [low] Run the broader Google-debug automated tests and `:app:assembleGoogleDebug`; when a device or emulator is available, verify pinned headings, date synchronization, and precipitation labels on a small screen in both themes, recording commands, outcomes, and unavailable checks as not run.
