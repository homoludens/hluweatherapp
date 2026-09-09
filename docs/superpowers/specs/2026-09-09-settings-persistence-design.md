# Plan 2.1 Settings Persistence Design

## Status

Approved in conversation on 2026-09-09. This is the first sub-project of Plan 2. The remaining Plan 2 sub-projects are real provider networking, GPS/Track Me, map-based location editing, and notifications/cache storage.

## Goal

Persist the settings that already work in the current process so the same choices survive process recreation, without changing the settings UI contract or implementing future location, provider, GPS, map, notification, or cache behavior.

## Scope

Persist these values:

- Weather provider
- Track Me enabled state
- Selected saved-location ID
- Theme mode
- Temperature unit
- Wind unit
- Distance unit
- Precipitation unit
- Weather alerts enabled state
- Daily summary enabled state
- Trip alerts enabled state

Keep the current fixed sample locations in memory. Location creation, editing, deletion, and full location persistence belong to Plan 2.4.

Keep `clearCache()` as an explicit no-op. Cache storage and cache invalidation belong to Plan 2.5.

## Architecture

### Repository boundary

Add a `SettingsRepository` abstraction between the view model and storage. Its contract is:

```kotlin
data class PersistedSettings(
    val provider: WeatherProvider = WeatherProvider.OPEN_METEO,
    val selectedLocationId: String? = "svilajnac",
    val trackMeEnabled: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val windUnit: WindUnit = WindUnit.KMH,
    val distanceUnit: DistanceUnit = DistanceUnit.KM,
    val precipitationUnit: PrecipitationUnit = PrecipitationUnit.MM,
    val weatherAlerts: Boolean = true,
    val dailySummary: Boolean = false,
    val tripAlerts: Boolean = false
)

interface SettingsRepository {
    val settings: Flow<PersistedSettings>
    suspend fun save(settings: PersistedSettings)
}
```

The UI continues to consume `SettingsUiState`; it does not know about DataStore or Preferences keys. The view model maps between `SettingsUiState` and `PersistedSettings`, preserving the fixed in-memory location list while validating the stored selected-location ID.

Provide two implementations:

- `DataStoreSettingsRepository`: production implementation backed by Preferences DataStore.
- An in-memory fake used by unit tests.

Use one application-scoped Preferences DataStore instance. Do not create a DataStore instance per screen or per view model.

### View-model construction

`SettingsViewModel(repository: SettingsRepository)` receives the repository through its constructor. Production Compose code obtains it through an explicit `ViewModelProvider.Factory` that creates the application-backed repository from the application context. Tests inject the in-memory fake directly. Both `HluWeatherApp` and the default `HluNavHost` view-model path use that factory; when the app root passes the view model to `HluNavHost`, the same instance remains shared.

The existing `SettingsUiState` shape and mutation method names remain stable. This limits the persistence change to the state boundary and keeps `SettingsScreen` unchanged.

### State hydration

The view model starts with the current defaults, then collects the repository state in its `viewModelScope`. Once stored values are read, it replaces the corresponding fields in `SettingsUiState` while preserving the fixed in-memory location list.

Missing values use the existing defaults. Invalid enum names, invalid booleans, and an unknown selected-location ID also use safe defaults. An unknown location ID must not cause a crash or remove the fixed locations.

### Mutations and writes

Each existing state mutation keeps its current immediate state update, then saves the complete mapped `PersistedSettings` snapshot through the repository in `viewModelScope`. Selecting a saved location continues to disable Track Me before persistence. Enabling Track Me preserves the selected saved-location ID.

Writes are serialized by the repository/DataStore implementation. A failed write must not crash the view model or the UI; the in-memory state remains usable and the failure is not surfaced as a weather-screen error.

## Preferences Schema

Use stable, namespaced string keys. Enum values are stored using stable names rather than ordinal positions.

Suggested keys:

- `settings.provider`
- `settings.track_me_enabled`
- `settings.selected_location_id`
- `settings.theme_mode`
- `settings.temperature_unit`
- `settings.wind_unit`
- `settings.distance_unit`
- `settings.precipitation_unit`
- `settings.weather_alerts`
- `settings.daily_summary`
- `settings.trip_alerts`

The schema is intentionally flat and versionless for this first persistence step. Future migrations can be added when location records or provider credentials introduce a concrete migration need.

## Error Handling

- DataStore read failures fall back to default settings for the affected load.
- Invalid stored enum values fall back to the matching current default.
- Unknown selected-location IDs fall back to the current default selected location.
- DataStore write failures are contained and do not roll back the already-applied in-memory state.
- No persistence error is shown in the settings UI in this sub-project.

## Testing

Add tests for:

- View-model hydration from stored values.
- Persistence across two view-model instances sharing the same fake repository.
- Defaults when values are absent.
- Defaults when enum values are invalid.
- Safe handling of an unknown selected-location ID.
- Existing provider, location, Track Me, appearance, unit, and notification mutations.
- The production factory wiring through the app root, without creating a second settings state.

Use a real `SettingsViewModel` in state tests. Keep DataStore-specific Android wiring isolated from Compose UI tests; UI tests should verify the existing state-driven settings behavior remains unchanged.

## Success Criteria

- Settings choices survive recreation of `SettingsViewModel` using the same application DataStore.
- `SettingsScreen` requires no DataStore dependency and its public state/callback contract is unchanged.
- Missing, invalid, and unavailable persisted values do not crash startup.
- The fixed sample locations remain available and selecting one still disables Track Me.
- Focused unit tests, the full debug unit-test task, debug assembly, and lint are run. Any pre-existing suite failures are reported separately and are not hidden.

## Explicit Non-Goals

- Real weather provider networking or provider-specific requests.
- Location CRUD or user-created location persistence.
- GPS permissions or actual Track Me location updates.
- Reverse geocoding or map integration.
- Notification scheduling or delivery.
- Forecast/cache persistence and cache invalidation.
