# HluWeatherApp Design

Date: 2026-09-08  
Status: Approved  
Application ID: `net.droopia.hluweather`

## 1. Overview

HluWeatherApp is a new Android weather app with a simple, clean interface. It shows current conditions, hourly and daily forecasts, and a location map. The app supports both Open-Meteo and MET.no as switchable weather providers, saved locations, GPS-based location selection, reverse geocoding, and a Track Me mode for travelers.

The first version implements the full documented app scope:

- Jetpack Compose interface matching the light and dark screenshots
- Hourly, Daily, and Map modes
- Current-weather card with real moon phase
- Open-Meteo and MET.no behind one shared forecast model
- Saved locations plus Track Me
- Full-screen MapLibre/OpenFreeMap location picker
- GPS assistance and OpenStreetMap Nominatim reverse geocoding
- Settings for provider, locations, and appearance
- Cached-forecast fallback when a live request fails

## 2. Confirmed decisions

- Architecture: single Gradle module with separate `data` and `ui` layers
- Minimum Android version: API 28, Android 9.0
- Compile and target Android version: API 36
- Application ID: `net.droopia.hluweather`
- Map tab behavior: location map, not a weather-overlay map
- Location naming: OSM Nominatim reverse geocoding with mandatory user ability to rename
- Provider failure behavior: show the last cached forecast for that location with a stale-data banner; no silent provider fallback
- Forecast horizon: 7 days
- Time display: device’s current time zone in v1
- Language: English only in v1
- API keys: none
- Background location tracking: not included

## 3. Product scope

### Included in v1

1. Weather screen
   - Illustrated header matching the provided design
   - Hourly/Daily/Map tab state
   - Current temperature, condition, humidity, feels-like temperature, dew point, and precipitation
   - Real moon phase rendered as a Compose canvas graphic
   - Hourly forecast table
   - Daily forecast list
   - Location quick-switcher bottom sheet
   - Pull-to-refresh
   - Loading, empty, error, and stale-cache states

2. Hourly mode
   - Day selector for the available forecast days
   - Compact table with time, weather icon and condition, temperature, dew point, humidity, and precipitation
   - Selecting a day in Daily mode returns to Hourly mode for that day

3. Daily mode
   - Seven forecast days
   - Condition, minimum and maximum temperature, and precipitation for each day
   - Tap a day to view its hourly forecast

4. Map mode
   - OpenFreeMap base map through MapLibre Compose
   - Saved-location markers
   - Active-location indicator
   - Track Me follows the current device location
   - Tapping a saved-location marker activates that location
   - Light Liberty style in light theme and dark style in dark theme

5. Location management
   - Saved locations with name, latitude, longitude, and optional altitude
   - Track Me as a special non-saved active location
   - Add location from Settings or the Weather screen
   - Edit and delete saved locations
   - Full-screen map picker with a fixed centered selection marker
   - GPS button in the picker
   - Reverse-geocoded editable location name
   - Permission request only when GPS or Track Me is invoked

6. Settings
   - Weather provider selection
   - Location list and active-location selection
   - Appearance selection: System, Light, Dark

7. Weather providers
   - Open-Meteo
   - MET.no
   - Normalized shared forecast model
   - Provider can be changed without restarting the app

### Excluded from v1

- Weather data overlays on the map
- City search
- Offline map downloads
- Automatic fallback from one weather provider to another
- Background location tracking
- Notifications
- Account, cloud sync, or multi-device synchronization
- Localization beyond English
- Unit settings beyond Celsius and millimetres, matching the design

## 4. Technology stack

- Kotlin
- Jetpack Compose with Material 3
- Compose Navigation
- Android ViewModels and `StateFlow`
- DataStore Preferences
- Retrofit and kotlinx.serialization
- OkHttp
- MapLibre Compose `0.15.0` with OpenFreeMap styles
- MapLibre GMS location runtime, with framework fallback where Google Play services is unavailable
- kotlinx-datetime
- JUnit and kotlinx-coroutines-test for unit tests
- Compose UI tests for key screens and states

The implementation should use current stable versions compatible with compile API 36. The documentation suggests Compose BOM `2026.08.00`, Retrofit `3.0.0`, kotlinx-serialization `1.9.0`, and OkHttp `5.1.0`; final compatible versions should be pinned during implementation planning.

Required Android permissions:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

Location permission must not be requested at startup.

## 5. Module and package structure

The project uses one Android module to keep the architecture simple:

```text
app/src/main/java/net/droopia/hluweather/
├── MainActivity.kt
├── navigation/
│   └── HluNavHost.kt
├── data/
│   ├── model/
│   │   ├── WeatherLocation.kt
│   │   ├── ActiveLocation.kt
│   │   ├── WeatherForecast.kt
│   │   ├── WeatherProvider.kt
│   │   └── AppSettings.kt
│   ├── remote/
│   │   ├── openmeteo/
│   │   ├── metno/
│   │   └── nominatim/
│   ├── repository/
│   │   ├── WeatherRepository.kt
│   │   ├── LocationRepository.kt
│   │   └── SettingsRepository.kt
│   ├── device/
│   │   └── DeviceLocationSource.kt
│   └── persistence/
│       └── ForecastCache.kt
└── ui/
    ├── theme/
    │   ├── Color.kt
    │   └── Theme.kt
    ├── components/
    │   ├── MoonPhase.kt
    │   ├── WeatherIcon.kt
    │   └── ForecastTable.kt
    ├── weather/
    │   ├── WeatherScreen.kt
    │   └── WeatherViewModel.kt
    ├── daily/
    │   └── DailyForecastList.kt
    ├── map/
    │   └── WeatherMap.kt
    ├── locationpicker/
    │   ├── LocationPickerScreen.kt
    │   └── LocationPickerViewModel.kt
    └── settings/
        ├── SettingsScreen.kt
        └── SettingsViewModel.kt
```

The UI depends on ViewModels and shared models. The `data` layer owns networking, persistence, device location, normalization, and repository behavior. No separate Gradle modules or domain layer are introduced in v1.

## 6. Navigation

Navigation routes:

```text
weather
settings
location_picker?locationId={locationId}
```

- `weather` is the start destination.
- The Settings icon in the hero opens `settings`.
- Add location opens `location_picker` with no argument.
- Edit location opens `location_picker` with the location ID.
- Location picker is full screen and returns the saved result through repository state, not a separate navigation payload.

Hourly, Daily, and Map are not separate navigation destinations. They are a `ForecastMode` state inside Weather screen:

```kotlin
enum class ForecastMode {
    HOURLY,
    DAILY,
    MAP
}
```

This preserves the fixed hero and current-weather card while switching only the lower content.

## 7. UI design

### 7.1 Weather screen

The screen consists of:

1. Hero header
   - Gradient background
   - App title: HluWeatherApp
   - Tagline: Simple weather. Clear view.
   - Settings action
   - Decorative sun/moon and mountain silhouettes
   - Hourly/Daily/Map navigation pills

2. Current-weather card
   - Active location name
   - Date and time
   - Large current temperature
   - Moon phase graphic
   - Condition text
   - Humidity
   - Feels-like temperature
   - Dew point
   - Precipitation

3. Mode content
   - Hourly table
   - Daily list
   - Location map

The current-weather card remains visible in all three modes.

### 7.2 Location display

For a saved location:

```text
Svilajnac
Sun, Sep 7, 2026 • 23:00
```

For Track Me:

```text
Current location
Optional reverse-geocoded place name
```

Tapping the location area opens a bottom sheet containing:

- Track me
- All saved locations
- Add location
- Manage locations

The selected item becomes the active location immediately.

### 7.3 Hourly table

Columns:

- Time
- Weather icon and condition
- Temperature
- Dew point
- Humidity
- Precipitation

Rows represent one hour for the selected forecast day. Missing values display `—`.

### 7.4 Daily list

Each row represents one day and shows:

- Date
- Condition icon and text
- Minimum temperature
- Maximum temperature
- Precipitation

Tapping a row switches to Hourly mode with that day selected.

### 7.5 Map

Map style:

- Light theme: `https://tiles.openfreemap.org/styles/liberty`
- Dark theme: `https://tiles.openfreemap.org/styles/dark`

Map behavior:

- Saved locations are shown as tappable markers.
- The active saved location is visually distinguished.
- In Track Me mode, the map shows and follows the current device location.
- A GPS/recenter control returns the camera to the active location.
- Map mode is read-only for coordinates; editing happens in the full-screen picker.

### 7.6 Location picker

The picker is full screen and uses a fixed centered marker. The user moves the map, and the selected coordinate is the camera center when movement settles.

Bottom panel shows:

- Editable location name
- Latitude and longitude
- Optional altitude
- GPS button
- Save button

When the coordinate changes:

1. Update the displayed coordinate.
2. Debounce reverse geocoding.
3. Fill the name field if the user has not replaced the generated name.
4. Always allow the user to edit the name before saving.

If reverse geocoding fails, use `New location` as the default editable name.

### 7.7 Settings

Settings sections:

1. Weather provider
   - Open-Meteo
   - MET Norway

2. Locations
   - Track me
   - Saved locations
   - Add location
   - Edit/delete menu for saved locations

3. Appearance
   - System
   - Light
   - Dark

## 8. Theme

Theme follows the selected appearance mode. The default is System.

The implementation should use the custom palette derived from the provided Compose starting point and screenshots.

### Light palette

```text
primary          #5367E8
background       #F7F8FC
surface          #F7F8FC
surfaceVariant   #EDEFFC
onSurfaceVariant #5D6278
outlineVariant   #DDE0EB

heroTop          #6779ED
heroBottom       #4D55C6
heroText         #FFFFFF
heroSecondary    #E4E7FF
mountain         #242B7A
moon             #FFF0BD
moonAccent       #5865DA
cloudAccent      #9FADEB

navSelected      #F5F6FF
navSelectedText  #3040A7
weatherCard      #FBFBFE
tableHeader      #EFF1FA
tableRow         #FAFBFD
daySelected      #5969E9
daySelectedText  #FFFFFF
```

### Dark palette

```text
primary          #9AA7FF
background       #09111E
surface          #101A2B
surfaceVariant   #18243A
onSurfaceVariant #B7C0D9
outlineVariant   #26334B

heroTop          #071225
heroBottom       #10264B
heroText         #F5F7FF
heroSecondary    #B8C4EA
mountain         #020A18
moon             #E4D7B7
moonAccent       #7789FF
cloudAccent      #8493D7

navSelected      #9DA8FF
navSelectedText  #131A3C
weatherCard      #111C2D
tableHeader      #1A2740
tableRow         #101A2B
daySelected      #8492FF
daySelectedText  #101632
```

## 9. Shared data model

The UI and repository layer use provider-independent models:

```kotlin
enum class WeatherProvider {
    OPEN_METEO,
    MET_NO
}

enum class LocationMode {
    SAVED_LOCATION,
    TRACK_ME
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

data class WeatherLocation(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Int? = null
)

sealed interface ActiveLocation {
    data class Saved(
        val location: WeatherLocation
    ) : ActiveLocation

    data class Current(
        val latitude: Double,
        val longitude: Double,
        val displayName: String? = null
    ) : ActiveLocation
}

enum class WeatherCondition {
    CLEAR,
    MOSTLY_CLEAR,
    PARTLY_CLOUDY,
    CLOUDY,
    FOG,
    DRIZZLE,
    RAIN,
    SNOW,
    THUNDERSTORM,
    UNKNOWN
}

data class CurrentWeather(
    val temperature: Double,
    val apparentTemperature: Double?,
    val humidity: Int?,
    val dewPoint: Double?,
    val precipitation: Double?,
    val condition: WeatherCondition,
    val isDay: Boolean?
)

data class HourForecast(
    val time: Instant,
    val temperature: Double,
    val apparentTemperature: Double?,
    val humidity: Int?,
    val dewPoint: Double?,
    val precipitation: Double,
    val precipitationProbability: Int?,
    val condition: WeatherCondition,
    val isDay: Boolean?
)

data class DayForecast(
    val date: LocalDate,
    val condition: WeatherCondition,
    val temperatureMin: Double,
    val temperatureMax: Double,
    val precipitation: Double?,
    val sunrise: Instant?,
    val sunset: Instant?
)

data class WeatherForecast(
    val location: WeatherLocation,
    val provider: WeatherProvider,
    val fetchedAt: Instant,
    val current: CurrentWeather,
    val hourly: List<HourForecast>,
    val daily: List<DayForecast>,
    val moonPhase: Double
)
```

For Track Me, the repository creates a transient `WeatherLocation` with a stable current-location identifier and the available display name. This keeps the UI model simple without persisting the moving location as a saved place.

Moon phase is a value from `0.0` to `1.0`:

```text
0.00  new moon
0.25  first quarter
0.50  full moon
0.75  last quarter
1.00  new moon
```

The moon phase is calculated locally and is not taken directly from the active weather provider.

## 10. Settings and persistence

`SettingsRepository` uses DataStore Preferences and exposes:

```kotlin
data class AppSettings(
    val provider: WeatherProvider,
    val themeMode: ThemeMode,
    val locations: List<WeatherLocation>,
    val selectedLocationId: String?,
    val locationMode: LocationMode
)
```

Stored values:

- active provider
- theme mode
- serialized saved locations
- selected saved-location ID
- active location mode

Behavior:

- If no location exists, the default mode is `SAVED_LOCATION` and the Weather screen shows the empty-location state.
- Selecting Track Me sets `locationMode = TRACK_ME`.
- Selecting a saved location sets `locationMode = SAVED_LOCATION` and updates `selectedLocationId`.
- Deleting the active saved location selects the first remaining saved location, or shows the empty state if none remain.
- Theme changes apply immediately through `collectAsStateWithLifecycle`.

Forecast cache:

- One cached `WeatherForecast` per location.
- Cache key is the saved-location ID, or a rounded coordinate key for Track Me.
- Cache entry includes forecast JSON and fetched time.
- Keep at most 20 total cache entries, retaining the most recently fetched entries.
- Cached data has no automatic expiration; the UI always labels it with its fetched time when used after a failed live request.
- Cached data is used only when a live request for the same active location fails.

## 11. Weather provider architecture

```text
WeatherViewModel
       │
       ▼
WeatherRepository
       │
       ├── SettingsRepository.activeProvider
       │
       ├── OpenMeteoDataSource
       │
       └── MetNoDataSource
       │
       ▼
WeatherForecast
```

`WeatherRepository` is the only component that selects a provider.

Responsibilities:

- observe active location
- read active provider
- call the selected data source
- normalize provider output
- calculate moon phase
- cache successful forecasts
- expose latest forecast and error state

The UI never calls Open-Meteo or MET.no directly.

## 12. Open-Meteo integration

Base URL:

```text
https://api.open-meteo.com/
```

Endpoint:

```text
v1/forecast
```

Request parameters:

- latitude
- longitude
- `forecast_days = 7`
- `timezone = auto`
- current variables: temperature, relative humidity, apparent temperature, precipitation, weather code, day flag
- hourly variables: temperature, relative humidity, dew point, apparent temperature, precipitation, precipitation probability, weather code, day flag
- daily variables: weather code, temperature max, temperature min, precipitation sum, sunrise, sunset, moon phase

Normalization rules:

- Map WMO weather codes to `WeatherCondition`.
- Convert relative humidity to integer percent.
- Keep apparent temperature and precipitation probability when present.
- Use the hourly entry matching the current time for current dew point when the current response does not provide it.
- Use daily temperature min/max and precipitation sum for Daily mode.
- Parse Open-Meteo local time values using the response’s `timezone`, then format all UI times in the device zone.
- Use daily weather code for daily condition.
- Ignore API moon phase for display; use the local moon calculation for consistency.
- Request metric units for temperature and precipitation.

WMO mapping:

```text
0                  CLEAR
1                  MOSTLY_CLEAR
2                  PARTLY_CLOUDY
3                  CLOUDY
45, 48             FOG
51, 53, 55,
56, 57             DRIZZLE
61, 63, 65,
66, 67,
80, 81, 82         RAIN
71, 73, 75, 77,
85, 86             SNOW
95, 96, 99         THUNDERSTORM
other              UNKNOWN
```

## 13. MET.no integration

Base URL:

```text
https://api.met.no/
```

Endpoint:

```text
weatherapi/locationforecast/2.0/compact
```

Request parameters:

- `lat`
- `lon`
- optional `altitude`

MET.no requires a non-generic identifying User-Agent. The app should send:

```text
HluWeather/1.0 https://net.droopia.hluweather
```

Normalization rules:

- Use the first suitable timeseries entry as current weather.
- Use each timeseries entry as one hourly forecast.
- Use `instant.details` for temperature, humidity, and dew point.
- Use `next_1_hours.summary.symbol_code` for condition.
- Use `next_1_hours.details.precipitation_amount` for precipitation.
- Apparent temperature is unavailable and should be `null`.
- Precipitation probability is unavailable and should be `null`.
- Trim the response to the first seven days relative to the current date.
- Parse MET.no UTC timestamps as instants and format all UI times in the device zone.
- Derive daily min/max and total precipitation by grouping hourly entries by local display date.
- MET.no values are already metric; no unit conversion is required.

MET symbol mapping:

```text
clearsky             CLEAR
fair                 MOSTLY_CLEAR
partlycloudy         PARTLY_CLOUDY
cloudy               CLOUDY
fog                  FOG
thunder              THUNDERSTORM
snow, sleet          SNOW
rain                 RAIN
other or missing     UNKNOWN
```

Matching should be case-insensitive and should handle day/night symbol variants.

## 14. Location architecture

```text
SettingsRepository
       │
       ▼
LocationRepository
       │
       ├── saved locations
       │
       └── DeviceLocationSource
       │
       ▼
ActiveLocation
       │
       ▼
WeatherRepository
```

`LocationRepository` exposes:

- saved locations flow
- active location flow
- select saved location
- enable Track Me
- add/update/delete saved locations

`DeviceLocationSource` wraps MapLibre’s Android `LocationProvider`, created by `createDefaultLocationProvider(context)`, and exposes current location updates.

Track Me behavior:

- Request location permission only when enabled.
- Use single or low-frequency updates appropriate for weather, not continuous high-accuracy navigation tracking.
- Update the displayed location when a new fix arrives.
- Refresh weather when either condition is true:
  - movement is at least 5 km from the last weather fetch
  - at least 30 minutes have elapsed since the last successful weather fetch
- Reverse-geocode the current position only when a weather refresh is triggered, and use the result as an optional display name beside **Current location**
- Do not run background location updates while the app is closed.

If location permission is denied:

- Track Me shows a permission explanation.
- Saved locations remain fully usable.
- The app can offer to open system app settings.

If GPS is unavailable:

- The location picker still allows manual map selection.
- The GPS button shows an appropriate unavailable state.

## 15. Reverse geocoding

Use OpenStreetMap Nominatim:

```text
https://nominatim.openstreetmap.org/reverse
```

Parameters:

- `format = jsonv2`
- latitude
- longitude

Use the same identifying app User-Agent as the rest of the app’s HTTP clients.

Behavior:

- Trigger only after map movement settles, after a successful GPS fix, or after a Track Me weather refresh.
- Debounce requests while the user is moving the map and keep request frequency at or below one request per second.
- Fill the location name with the most specific useful returned place name.
- Never block saving on a geocoding request.
- On failure, use `New location` and let the user rename the place.
- Cache recent coordinate-to-name results in DataStore to reduce repeated requests.

## 16. State management

### Weather screen state

```kotlin
data class WeatherUiState(
    val activeLocation: ActiveLocation?,
    val locations: List<WeatherLocation>,
    val locationMode: LocationMode,
    val forecast: WeatherForecast?,
    val isStale: Boolean,
    val isLoading: Boolean,
    val error: String?,
    val selectedDayIndex: Int
)
```

States:

- No active location: show setup prompt.
- Loading with cached data: keep cached data visible and show refresh progress.
- Loading without data: show loading placeholder.
- Success: show live forecast.
- Failure with cache: show cached forecast and stale banner.
- Failure without cache: show error and Retry.

Selected tab and selected day can be retained in the ViewModel so configuration changes preserve the user’s context.

### Settings screen state

```kotlin
data class SettingsUiState(
    val provider: WeatherProvider,
    val themeMode: ThemeMode,
    val locations: List<WeatherLocation>,
    val selectedLocationId: String?,
    val locationMode: LocationMode
)
```

### Location picker state

```kotlin
data class LocationPickerUiState(
    val latitude: Double,
    val longitude: Double,
    val altitude: Int?,
    val name: String,
    val isNameEditing: Boolean,
    val gpsStatus: GpsStatus
)
```

`GpsStatus` covers idle, locating, success, permission required, location disabled, and unavailable.

## 17. Data flow

### Initial load

```text
App starts
  → SettingsRepository emits settings
  → LocationRepository emits active location
  → WeatherViewModel requests forecast
  → WeatherRepository selects provider
  → data source returns provider response
  → repository normalizes and caches forecast
  → WeatherScreen renders state
```

### Provider change

```text
User selects provider
  → SettingsRepository updates DataStore
  → WeatherViewModel observes provider change
  → WeatherRepository refetches using same active location
  → UI updates without navigation or restart
```

### Saved-location change

```text
User selects saved location
  → LocationRepository updates active location
  → WeatherViewModel cancels obsolete request
  → WeatherRepository fetches for new coordinates
  → UI updates
```

### Track Me movement

```text
DeviceLocationSource emits new position
  → LocationRepository emits ActiveLocation.Current
  → WeatherViewModel checks refresh threshold
  → if threshold met, fetch new forecast
  → if threshold not met, update location display only
```

## 18. Request cancellation and freshness

- Use `collectLatest` or equivalent so an obsolete location/provider request does not overwrite newer state.
- Each forecast request should carry the active-location key and provider used.
- A response is applied only if it still matches the current active location and provider.
- Pull-to-refresh forces a new request even if a recent successful forecast exists.

## 19. Error handling

Weather errors:

- Network unavailable
- HTTP failure
- Timeout
- Unexpected JSON shape
- Missing required provider value

User-facing behavior:

- If a cached forecast exists for the active location, show it with:
  - stale banner
  - last updated time
  - Retry action
- If no cache exists, show:
  - concise error message
  - Retry action
- Never show stack traces or raw HTTP bodies in the UI.
- Missing optional values render as `—`.

Location errors:

- Permission denied: explain and offer system settings.
- GPS disabled: offer system location settings but allow manual selection.
- Reverse geocoding failed: continue with manual naming.

Map errors:

- If map tiles fail, show a non-blocking map error message.
- Location list and weather data remain usable.

## 20. Moon phase

Moon phase is calculated locally from the current date. This gives:

- identical behavior across providers
- offline availability
- no dependency on provider-specific astronomy fields

The UI renders the phase on a Compose `Canvas`:

- dark moon disc
- illuminated region based on phase
- waxing/waning direction
- color taken from the active theme palette

The exact astronomical formula may be the simple synodic-month approximation from the design documentation, provided unit tests verify known moon dates within an acceptable tolerance.

## 21. Testing strategy

### Unit tests

- WMO code mapping
- MET.no symbol mapping, including day/night variants
- Open-Meteo DTO normalization
- MET.no DTO normalization
- Daily aggregation from MET.no hourly data
- Moon phase calculation for known dates
- Track Me refresh threshold logic
- Forecast cache save/read, keying, and bounded eviction behavior
- Location list CRUD behavior
- Settings defaults and migration behavior

### Repository and ViewModel tests

- Provider switch triggers refetch
- Location change cancels obsolete request
- Live success updates cache
- Live failure with cache returns stale forecast
- Live failure without cache returns error
- Empty location state
- Track Me location update below and above refresh threshold

Use fake data sources, fake DataStore, and controlled coroutines.

### UI tests

Compose tests should verify:

- Weather screen renders current conditions from state
- Hourly/Daily/Map tabs switch content
- Empty state shows location setup actions
- Error state shows Retry
- Stale-cache state shows banner
- Settings provider and location selections update state
- Location picker save emits a valid location

### Manual verification

- Light theme compared with `docs/light_theme.jpg`
- Dark theme compared with `docs/dark_theme.jpg`
- Provider switch between Open-Meteo and MET.no
- Add location using map movement
- Add location using GPS
- Edit and delete saved location
- Track Me on a device or emulator with mocked location
- Permission denied flow
- Airplane-mode cached fallback

## 22. Build and quality gates

Implementation is complete when:

- `./gradlew assembleDebug` succeeds
- `./gradlew test` succeeds
- `./gradlew lint` succeeds or reports only accepted warnings
- Compose preview renders both light and dark themes
- Debug APK installs on an API 28+ target
- Main flows described in manual verification work without crashes

## 23. Risks and mitigations

| Risk | Mitigation |
|---|---|
| MET.no rejects generic HTTP client identity | Use dedicated OkHttp client with app-specific User-Agent |
| Open-Meteo and MET.no have different horizons and fields | Normalize to shared model and allow optional fields |
| Track Me can consume battery | Refresh only after 5 km or 30 minutes, and only while app is active |
| Map picker coordinate updates are noisy | Debounce reverse geocoding and use camera idle events |
| Cached forecast may belong to a different place | Cache and validate by active-location key |
| Devices without Google Play services have limited fused-location support | Continue supporting manual map selection; show Track Me as unavailable when fused location cannot be used |
| Compose + MapLibre dependency versions may conflict | Pin compatible stable versions during implementation planning and run dependency checks |
| Device time zone differs from forecast location time zone | v1 explicitly displays device time zone; document as a known limitation |

## 24. Known v1 limitations

- Forecast times are shown in the device time zone, not necessarily the forecast location’s time zone.
- MET.no does not provide apparent temperature or precipitation probability; those values display as `—`.
- Map mode does not show weather overlays.
- Offline map regions are not downloaded.
- There is no city search; locations are added through GPS or manual map selection.
- There is no automatic provider fallback.
