package net.droopia.hluweather.ui.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import net.droopia.hluweather.data.model.ThemeMode
import net.droopia.hluweather.data.model.WeatherProvider
import net.droopia.hluweather.data.repository.applicationDataStore
import java.io.IOException

data class PersistedSettings(
    val provider: WeatherProvider = WeatherProvider.OPEN_METEO,
    val selectedLocationId: String? = null,
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

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    override val settings: Flow<PersistedSettings> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val defaults = PersistedSettings()
            PersistedSettings(
                provider = preferences.enum(providerKey, defaults.provider),
                selectedLocationId = preferences.getStringOrNull(selectedLocationIdKey) ?: defaults.selectedLocationId,
                trackMeEnabled = preferences.getBooleanOrNull(trackMeEnabledKey) ?: defaults.trackMeEnabled,
                themeMode = preferences.enum(themeModeKey, defaults.themeMode),
                temperatureUnit = preferences.enum(temperatureUnitKey, defaults.temperatureUnit),
                windUnit = preferences.enum(windUnitKey, defaults.windUnit),
                distanceUnit = preferences.enum(distanceUnitKey, defaults.distanceUnit),
                precipitationUnit = preferences.enum(precipitationUnitKey, defaults.precipitationUnit),
                weatherAlerts = preferences.getBooleanOrNull(weatherAlertsKey) ?: defaults.weatherAlerts,
                dailySummary = preferences.getBooleanOrNull(dailySummaryKey) ?: defaults.dailySummary,
                tripAlerts = preferences.getBooleanOrNull(tripAlertsKey) ?: defaults.tripAlerts
            )
        }

    override suspend fun save(settings: PersistedSettings) {
        dataStore.edit { preferences ->
            preferences[providerKey] = settings.provider.name
            if (settings.selectedLocationId == null) {
                preferences.remove(selectedLocationIdKey)
            } else {
                preferences[selectedLocationIdKey] = settings.selectedLocationId
            }
            preferences[trackMeEnabledKey] = settings.trackMeEnabled
            preferences[themeModeKey] = settings.themeMode.name
            preferences[temperatureUnitKey] = settings.temperatureUnit.name
            preferences[windUnitKey] = settings.windUnit.name
            preferences[distanceUnitKey] = settings.distanceUnit.name
            preferences[precipitationUnitKey] = settings.precipitationUnit.name
            preferences[weatherAlertsKey] = settings.weatherAlerts
            preferences[dailySummaryKey] = settings.dailySummary
            preferences[tripAlertsKey] = settings.tripAlerts
        }
    }

    private inline fun <reified T : Enum<T>> Preferences.enum(
        key: Preferences.Key<String>,
        default: T
    ): T = getStringOrNull(key)?.let { value -> enumValues<T>().firstOrNull { it.name == value } } ?: default

    private fun Preferences.getStringOrNull(key: Preferences.Key<String>): String? =
        asMap().entries.firstOrNull { it.key.name == key.name }?.value as? String

    private fun Preferences.getBooleanOrNull(key: Preferences.Key<Boolean>): Boolean? =
        asMap().entries.firstOrNull { it.key.name == key.name }?.value as? Boolean
}

fun settingsRepository(context: Context): SettingsRepository =
    DataStoreSettingsRepository(context.applicationDataStore)

private val providerKey = stringPreferencesKey("settings.provider")
private val trackMeEnabledKey = booleanPreferencesKey("settings.track_me_enabled")
private val selectedLocationIdKey = stringPreferencesKey("settings.selected_location_id")
private val themeModeKey = stringPreferencesKey("settings.theme_mode")
private val temperatureUnitKey = stringPreferencesKey("settings.temperature_unit")
private val windUnitKey = stringPreferencesKey("settings.wind_unit")
private val distanceUnitKey = stringPreferencesKey("settings.distance_unit")
private val precipitationUnitKey = stringPreferencesKey("settings.precipitation_unit")
private val weatherAlertsKey = booleanPreferencesKey("settings.weather_alerts")
private val dailySummaryKey = booleanPreferencesKey("settings.daily_summary")
private val tripAlertsKey = booleanPreferencesKey("settings.trip_alerts")
