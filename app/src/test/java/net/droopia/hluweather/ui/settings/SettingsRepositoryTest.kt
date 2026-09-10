package net.droopia.hluweather.ui.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalTime
import net.droopia.hluweather.data.model.ThemeMode
import net.droopia.hluweather.data.model.WeatherProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun notification_defaults_are_opt_in_with_an_eight_am_summary() {
        val settings = PersistedSettings()

        assertFalse(settings.weatherAlerts)
        assertFalse(settings.dailySummary)
        assertEquals(LocalTime(8, 0), settings.dailySummaryTime)
    }

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
            themeMode = ThemeMode.DARK,
            temperatureUnit = TemperatureUnit.FAHRENHEIT,
            windUnit = WindUnit.MPH,
            distanceUnit = DistanceUnit.MILES,
            precipitationUnit = PrecipitationUnit.INCH,
            weatherAlerts = false,
            dailySummary = true,
            dailySummaryTime = LocalTime(7, 30)
        )

        repository.save(expected)

        assertEquals(expected, repository.settings.first())
        assertEquals(
            "07:30",
            dataStore.data.first()[stringPreferencesKey("settings.daily_summary_time")]
        )
    }

    @Test
    fun generic_save_preserves_location_owned_fields() = runTest {
        val file = temporaryFolder.newFile("settings.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { file }
        )
        dataStore.edit {
            it[stringPreferencesKey("settings.selected_location_id")] = "trieste"
            it[booleanPreferencesKey("settings.track_me_enabled")] = true
        }
        val repository = DataStoreSettingsRepository(dataStore)

        repository.save(
            PersistedSettings(
                selectedLocationId = "belgrade",
                trackMeEnabled = false,
                themeMode = ThemeMode.DARK
            )
        )

        assertEquals("trieste", repository.settings.first().selectedLocationId)
        assertEquals(true, repository.settings.first().trackMeEnabled)
        assertEquals(ThemeMode.DARK, repository.settings.first().themeMode)
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
            it[booleanPreferencesKey("settings.theme_mode")] = true
        }

        assertEquals(
            PersistedSettings(
                provider = WeatherProvider.MET_NO,
                weatherAlerts = false,
                dailySummary = true
            ),
            DataStoreSettingsRepository(dataStore).settings.first()
        )
    }

    @Test
    fun malformed_summary_time_uses_the_default_without_affecting_other_fields() = runTest {
        val file = temporaryFolder.newFile("settings.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { file }
        )
        dataStore.edit {
            it[booleanPreferencesKey("settings.weather_alerts")] = true
            it[stringPreferencesKey("settings.daily_summary_time")] = "not-a-time"
        }

        val settings = DataStoreSettingsRepository(dataStore).settings.first()

        assertTrue(settings.weatherAlerts)
        assertEquals(LocalTime(8, 0), settings.dailySummaryTime)
    }

    @Test
    fun legacy_trip_alerts_preference_is_ignored() = runTest {
        val file = temporaryFolder.newFile("settings.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { file }
        )
        dataStore.edit {
            it[booleanPreferencesKey("settings.trip_alerts")] = true
        }

        assertEquals(PersistedSettings(), DataStoreSettingsRepository(dataStore).settings.first())
    }

    @Test
    fun non_canonical_short_summary_time_uses_the_default() = runTest {
        val file = temporaryFolder.newFile("settings.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { file }
        )
        dataStore.edit {
            it[stringPreferencesKey("settings.daily_summary_time")] = "8:00"
        }

        assertEquals(LocalTime(8, 0), DataStoreSettingsRepository(dataStore).settings.first().dailySummaryTime)
    }

    @Test
    fun non_canonical_seconds_summary_time_uses_the_default() = runTest {
        val file = temporaryFolder.newFile("settings.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { file }
        )
        dataStore.edit {
            it[stringPreferencesKey("settings.daily_summary_time")] = "08:00:00"
        }

        assertEquals(LocalTime(8, 0), DataStoreSettingsRepository(dataStore).settings.first().dailySummaryTime)
    }
}
