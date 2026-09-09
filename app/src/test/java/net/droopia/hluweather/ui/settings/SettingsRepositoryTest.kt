package net.droopia.hluweather.ui.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.droopia.hluweather.data.model.ThemeMode
import net.droopia.hluweather.data.model.WeatherProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun saves_and_reads_the_complete_persisted_settings_snapshot() = runTest {
        val file = temporaryFolder.newFile("settings.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { file }
        )
        val repository = DataStoreSettingsRepository(dataStore)
        val expected = PersistedSettings(
            provider = WeatherProvider.MET_NO,
            selectedLocationId = "trieste",
            trackMeEnabled = true,
            themeMode = ThemeMode.DARK,
            temperatureUnit = TemperatureUnit.FAHRENHEIT,
            windUnit = WindUnit.MPH,
            distanceUnit = DistanceUnit.MILES,
            precipitationUnit = PrecipitationUnit.INCH,
            weatherAlerts = false,
            dailySummary = true,
            tripAlerts = true
        )

        repository.save(expected)

        assertEquals(expected, repository.settings.first())
    }

    @Test
    fun missing_and_invalid_preferences_use_persisted_defaults() = runTest {
        val file = temporaryFolder.newFile("settings.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { file }
        )
        dataStore.edit {
            it[stringPreferencesKey("settings.provider")] = "not-a-provider"
            it[stringPreferencesKey("settings.theme_mode")] = "not-a-theme"
        }

        val settings = DataStoreSettingsRepository(dataStore).settings.first()

        assertEquals(PersistedSettings(), settings)
    }

    @Test
    fun malformed_typed_preferences_default_only_the_affected_fields() = runTest {
        val file = temporaryFolder.newFile("settings.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { file }
        )
        dataStore.edit {
            it[stringPreferencesKey("settings.provider")] = "MET_NO"
            it[stringPreferencesKey("settings.track_me_enabled")] = "not-a-boolean"
            it[booleanPreferencesKey("settings.weather_alerts")] = false
            it[booleanPreferencesKey("settings.daily_summary")] = true
            it[booleanPreferencesKey("settings.trip_alerts")] = true
            it[booleanPreferencesKey("settings.theme_mode")] = true
        }

        assertEquals(
            PersistedSettings(
                provider = WeatherProvider.MET_NO,
                weatherAlerts = false,
                dailySummary = true,
                tripAlerts = true
            ),
            DataStoreSettingsRepository(dataStore).settings.first()
        )
    }
}
