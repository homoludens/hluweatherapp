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
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.LocalTime
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
    val weatherAlerts: Boolean = false,
    val dailySummary: Boolean = false,
    val dailySummaryTime: LocalTime = LocalTime(8, 0)
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
        .onEach(::migrateLegacyWeatherAlerts)
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
                weatherAlerts = if (preferences[weatherAlertsMigrationKey] == true) {
                    preferences.getBooleanOrNull(weatherAlertsKey) ?: defaults.weatherAlerts
                } else {
                    defaults.weatherAlerts
                },
                dailySummary = preferences.getBooleanOrNull(dailySummaryKey) ?: defaults.dailySummary,
                dailySummaryTime = preferences.getStringOrNull(dailySummaryTimeKey)
                    ?.let(::parseSummaryTime)
                    ?: defaults.dailySummaryTime
            )
        }

    override suspend fun save(settings: PersistedSettings) {
        dataStore.edit { preferences ->
            preferences[providerKey] = settings.provider.name
            preferences[themeModeKey] = settings.themeMode.name
            preferences[temperatureUnitKey] = settings.temperatureUnit.name
            preferences[windUnitKey] = settings.windUnit.name
            preferences[distanceUnitKey] = settings.distanceUnit.name
            preferences[precipitationUnitKey] = settings.precipitationUnit.name
            preferences[weatherAlertsKey] = settings.weatherAlerts
            preferences[weatherAlertsMigrationKey] = true
            preferences[dailySummaryKey] = settings.dailySummary
            preferences[dailySummaryTimeKey] = settings.dailySummaryTime.toPreferenceValue()
        }
    }

    private suspend fun migrateLegacyWeatherAlerts(preferences: Preferences) {
        if (preferences[weatherAlertsMigrationKey] == true) return

        dataStore.edit { mutablePreferences ->
            if (mutablePreferences[weatherAlertsMigrationKey] != true) {
                if (mutablePreferences[weatherAlertsKey] == true) {
                    mutablePreferences[weatherAlertsKey] = false
                }
                mutablePreferences[weatherAlertsMigrationKey] = true
            }
        }
    }

    private fun parseSummaryTime(value: String): LocalTime? =
        runCatching { LocalTime.parse(value) }
            .getOrNull()
            ?.takeIf { it.toPreferenceValue() == value }

    private fun LocalTime.toPreferenceValue(): String =
        "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

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
private val weatherAlertsMigrationKey = booleanPreferencesKey("settings.weather_alerts_migrated")
private val dailySummaryKey = booleanPreferencesKey("settings.daily_summary")
private val dailySummaryTimeKey = stringPreferencesKey("settings.daily_summary_time")
