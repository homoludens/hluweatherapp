package net.droopia.hluweather.ui.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import net.droopia.hluweather.data.model.ThemeMode
import net.droopia.hluweather.data.model.WeatherProvider
import java.io.IOException

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

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

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
            PersistedSettings(
                provider = preferences.enum(providerKey, PersistedSettings().provider),
                selectedLocationId = preferences[selectedLocationIdKey] ?: PersistedSettings().selectedLocationId,
                trackMeEnabled = preferences[trackMeEnabledKey] ?: PersistedSettings().trackMeEnabled,
                themeMode = preferences.enum(themeModeKey, PersistedSettings().themeMode),
                temperatureUnit = preferences.enum(temperatureUnitKey, PersistedSettings().temperatureUnit),
                windUnit = preferences.enum(windUnitKey, PersistedSettings().windUnit),
                distanceUnit = preferences.enum(distanceUnitKey, PersistedSettings().distanceUnit),
                precipitationUnit = preferences.enum(precipitationUnitKey, PersistedSettings().precipitationUnit),
                weatherAlerts = preferences[weatherAlertsKey] ?: PersistedSettings().weatherAlerts,
                dailySummary = preferences[dailySummaryKey] ?: PersistedSettings().dailySummary,
                tripAlerts = preferences[tripAlertsKey] ?: PersistedSettings().tripAlerts
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
    ): T = this[key]?.let { value -> enumValues<T>().firstOrNull { it.name == value } } ?: default
}

fun settingsRepository(context: Context): SettingsRepository =
    DataStoreSettingsRepository(context.settingsDataStore)

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
