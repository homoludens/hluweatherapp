# Offline provider mapping fixtures

These compact, deterministic JSON examples are for tests and planned provider mappings. They are not live API responses; values are illustrative, with response field names and units based on the upstream documentation linked in `openspec/changes/add-visual-crossing-brightsky-providers/design.md`.

- `open-meteo-weather.json` / `open-meteo-air-quality.json`: separate forecast and Air Quality API payloads.
- `met-no-complete.json`: `/complete` instant data and distinct next-one-hour / next-six-hour periods.
- `visual-crossing-timeline.json` / `visual-crossing-air-quality.json`: core Timeline and optional air-quality payloads with matching epochs.
- `bright-sky-weather.json` / `bright-sky-current-weather.json`: hourly forecast/observation records, source metadata, and current SYNOP fields.

No credentials, location history, or live network access are required to use these fixtures. The Visual Crossing examples intentionally omit `et0` (restricted reference ET, not the app's actual-ET field) and do not claim current AQI/PM availability.
