package net.droopia.hluweather.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalTime
import net.droopia.hluweather.data.repository.PlaceSearchProvider
import net.droopia.hluweather.data.model.ThemeMode
import net.droopia.hluweather.data.model.WeatherProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun notification_defaults_are_opt_in_with_an_eight_am_summary() {
        val settings = PersistedSettings()

        assertFalse(settings.weatherAlerts)
        assertFalse(settings.dailySummary)
        assertEquals(LocalTime(8, 0), settings.dailySummaryTime)
        assertEquals(WindDirectionDisplay.ARROW, settings.windDirectionDisplay)
    }

    @Test
    fun missing_or_invalid_place_search_provider_defaults_to_photon() = runTest {
        val file = temporaryFolder.newFile("settings.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { file }
        )
        dataStore.edit {
            it[stringPreferencesKey("settings.place_search_provider")] = "not-a-provider"
        }

        val state = DataStoreSettingsRepository(dataStore).settings.first()

        assertEquals(PlaceSearchProvider.PHOTON, state.placeSearchProvider)
    }

    @Test
    fun open_meteo_place_search_provider_survives_repository_reload() = runTest {
        val file = temporaryFolder.newFile("settings.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { file }
        )
        val repository = DataStoreSettingsRepository(dataStore)

        repository.save(PersistedSettings(placeSearchProvider = PlaceSearchProvider.OPEN_METEO))

        assertEquals(PlaceSearchProvider.OPEN_METEO, DataStoreSettingsRepository(dataStore).settings.first().placeSearchProvider)
        assertEquals(
            "OPEN_METEO",
            dataStore.data.first()[stringPreferencesKey("settings.place_search_provider")]
        )
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
            windDirectionDisplay = WindDirectionDisplay.EIGHT_POINT,
            hourlyTableColumns = setOf(
                HourlyTableColumn.WEATHER_ICON,
                HourlyTableColumn.WIND_SPEED,
                HourlyTableColumn.EVAPOTRANSPIRATION
            ),
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
    fun active_collectors_receive_effective_settings_changes() = runTest {
        val file = temporaryFolder.newFile("settings.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { file }
        )
        val repository = DataStoreSettingsRepository(dataStore)
        val emissions = Channel<PersistedSettings>(Channel.UNLIMITED)
        backgroundScope.launch {
            repository.settings.collect { emissions.send(it) }
        }
        val initialSettings = withTimeout(5_000) { emissions.receive() }

        val changedSettings = PersistedSettings(provider = WeatherProvider.MET_NO)
        repository.save(changedSettings)
        val actualChangedSettings = withTimeout(5_000) { emissions.receive() }

        assertEquals(PersistedSettings(), initialSettings)
        assertEquals(changedSettings, actualChangedSettings)
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
    fun invalid_hourly_table_columns_use_defaults_but_empty_columns_are_preserved() = runTest {
        val file = temporaryFolder.newFile("settings.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { file }
        )
        val key = stringPreferencesKey("settings.hourly_table_columns")
        val repository = DataStoreSettingsRepository(dataStore)

        dataStore.edit { it[key] = "NOT_A_COLUMN" }
        assertEquals(defaultHourlyTableColumns, repository.settings.first().hourlyTableColumns)

        dataStore.edit { it[key] = "" }
        assertTrue(repository.settings.first().hourlyTableColumns.isEmpty())
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

        assertFalse(settings.weatherAlerts)
        assertEquals(LocalTime(8, 0), settings.dailySummaryTime)
    }

    @Test
    fun io_read_fallback_does_not_attempt_weather_alerts_migration_write() = runTest {
        val dataStore = IoFailingDataStore()

        assertEquals(PersistedSettings(), DataStoreSettingsRepository(dataStore).settings.first())
        assertEquals(0, dataStore.updateDataCalls)
    }

    @Test
    fun legacy_enabled_weather_alerts_are_migrated_to_disabled_once() = runTest {
        val file = temporaryFolder.newFile("settings.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { file }
        )
        dataStore.edit {
            it[booleanPreferencesKey("settings.weather_alerts")] = true
        }
        val repository = DataStoreSettingsRepository(dataStore)

        assertFalse(repository.settings.first().weatherAlerts)
        assertFalse(repository.settings.first().weatherAlerts)
        assertEquals(
            false,
            dataStore.data.first()[booleanPreferencesKey("settings.weather_alerts")]
        )
        assertEquals(
            true,
            dataStore.data.first()[booleanPreferencesKey("settings.weather_alerts_migrated")]
        )
    }

    @Test
    fun legacy_disabled_weather_alerts_are_marked_migrated_and_stay_disabled() = runTest {
        val file = temporaryFolder.newFile("settings.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { file }
        )
        dataStore.edit {
            it[booleanPreferencesKey("settings.weather_alerts")] = false
        }

        assertFalse(DataStoreSettingsRepository(dataStore).settings.first().weatherAlerts)
        assertEquals(
            true,
            dataStore.data.first()[booleanPreferencesKey("settings.weather_alerts_migrated")]
        )
    }

    @Test
    fun explicit_new_weather_alert_values_are_preserved() = runTest {
        val enabledFile = temporaryFolder.newFile("enabled.preferences_pb")
        val enabledDataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { enabledFile }
        )
        val enabledRepository = DataStoreSettingsRepository(enabledDataStore)
        enabledRepository.save(PersistedSettings(weatherAlerts = true))

        val disabledFile = temporaryFolder.newFile("disabled.preferences_pb")
        val disabledDataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { disabledFile }
        )
        val disabledRepository = DataStoreSettingsRepository(disabledDataStore)
        disabledRepository.save(PersistedSettings(weatherAlerts = false))

        assertTrue(enabledRepository.settings.first().weatherAlerts)
        assertFalse(disabledRepository.settings.first().weatherAlerts)
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

    private class IoFailingDataStore : DataStore<Preferences> {
        var updateDataCalls = 0

        override val data: Flow<Preferences> = flow {
            throw java.io.IOException("read failed")
        }

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            updateDataCalls++
            return transform(emptyPreferences())
        }
    }
}
