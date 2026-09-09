package net.droopia.hluweather.ui.weather

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.swipe
import org.junit.Assert.assertEquals
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import net.droopia.hluweather.data.dayText
import net.droopia.hluweather.data.model.ActiveLocation
import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherProvider
import net.droopia.hluweather.data.repository.LocationRepository
import net.droopia.hluweather.data.toAppLocalDate
import net.droopia.hluweather.data.repository.MockWeatherRepository
import net.droopia.hluweather.data.repository.Svilajnac
import net.droopia.hluweather.data.repository.WeatherRepository
import net.droopia.hluweather.data.repository.buildMockForecast
import net.droopia.hluweather.ComposeTestActivity
import net.droopia.hluweather.ui.theme.HluWeatherTheme
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WeatherScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeTestActivity>()

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun renders_hourly_content_by_default() {
        renderWeather(baseTime = Instant.fromEpochSeconds(0L))

        composeRule.onNodeWithTag("weather_scroll").assertIsDisplayed()
        composeRule.onNodeWithText("Svilajnac").assertIsDisplayed()
    }

    @Test
    fun hourly_screen_renders_rows_from_the_next_day_without_switching_tables() {
        renderWeather(baseTime = Instant.fromEpochSeconds(0L))

        composeRule.onNodeWithTag("weather_scroll").performScrollToIndex(28)

        composeRule.onNodeWithTag("hourly_day_boundary_1").assertIsDisplayed()
    }

    @Test
    fun selecting_a_day_chip_jumps_to_that_day() {
        renderWeather(baseTime = Instant.fromEpochSeconds(0L))

        composeRule.onNodeWithTag("hourly_day_chip_1").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("hourly_selected_day_1").assertIsDisplayed()
    }

    @Test
    fun manually_scrolling_to_a_day_updates_the_selected_day() {
        val viewModel = renderWeather(baseTime = Instant.fromEpochSeconds(0L))

        composeRule.onNodeWithTag("weather_scroll").performScrollToIndex(28)
        composeRule.waitForIdle()

        assertEquals(1, viewModel.state.value.selectedDayIndex)
    }

    @Test
    fun selecting_a_day_from_daily_content_positions_hourly_list_on_that_day() {
        val viewModel = renderWeather(baseTime = Instant.fromEpochSeconds(0L))
        val selectedDayText = viewModel.state.value.forecast!!.daily[2].date.dayText()

        composeRule.onNodeWithText("Daily").performClick()
        composeRule.onNodeWithText(selectedDayText).performClick()
        composeRule.waitForIdle()

        assertEquals(2, viewModel.state.value.selectedDayIndex)
        composeRule.onNodeWithTag("hourly_selected_day_2").assertIsDisplayed()
    }

    @Test
    fun scrolling_to_an_hourly_date_without_a_daily_entry_keeps_that_date_selected() {
        val baseForecast = buildMockForecast(
            location = Svilajnac,
            baseTime = Instant.fromEpochSeconds(0L)
        )
        val forecast = baseForecast.copy(
            hourly = baseForecast.hourly + baseForecast.hourly.take(24).mapIndexed { index, hour ->
                hour.copy(
                    time = Instant.fromEpochSeconds(
                        7L * 24L * 60L * 60L + index * 60L * 60L
                    )
                )
            }
        )
        val tableData = forecast.toHourlyTableData()
        val syntheticDayIndex = tableData.days.last().dayIndex
        val viewModel = WeatherViewModel(object : WeatherRepository {
            override suspend fun getForecast(
                provider: WeatherProvider,
                location: WeatherLocation
            ): WeatherForecast = forecast
        }, Svilajnac)
        renderWeather(viewModel)
        viewModel.onDaySelected(1)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("weather_scroll")
            .performScrollToIndex(tableData.firstItemIndexForDay(syntheticDayIndex)!! + 3)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("hourly_day_start_$syntheticDayIndex").assertIsDisplayed()
        composeRule.onNodeWithTag("hourly_day_chip_$syntheticDayIndex").assertIsSelected()
        composeRule.onNodeWithTag("hourly_day_chip_1").assertIsNotSelected()

        viewModel.onDaySelected(0)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("hourly_day_chip_0").assertIsSelected()
        composeRule.onNodeWithTag("hourly_day_chip_$syntheticDayIndex").assertIsNotSelected()
    }

    @Test
    fun selecting_a_daily_date_without_hourly_entries_uses_nearest_hourly_date() {
        val baseForecast = buildMockForecast(
            location = Svilajnac,
            baseTime = Instant.fromEpochSeconds(0L)
        )
        val missingDate = baseForecast.daily[2].date
        val forecast = baseForecast.copy(
            hourly = baseForecast.hourly.filter { it.time.toAppLocalDate() != missingDate }
        )
        val tableData = forecast.toHourlyTableData()
        val nearestDay = tableData.nearestDayForIndex(2)!!
        val viewModel = WeatherViewModel(object : WeatherRepository {
            override suspend fun getForecast(
                provider: WeatherProvider,
                location: WeatherLocation
            ): WeatherForecast = forecast
        }, Svilajnac)
        renderWeather(viewModel)

        composeRule.onNodeWithText("Daily").performClick()
        composeRule.onNodeWithText(missingDate.dayText()).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("hourly_day_chip_${nearestDay.dayIndex}").assertIsSelected()
        composeRule.onNodeWithTag("hourly_selected_day_${nearestDay.dayIndex}").assertIsDisplayed()
    }

    @Test
    fun day_strip_contains_only_jumpable_hourly_dates() {
        val baseForecast = buildMockForecast(
            location = Svilajnac,
            baseTime = Instant.fromEpochSeconds(0L)
        )
        val forecast = baseForecast.copy(
            hourly = baseForecast.hourly
                .filter { it.time.toAppLocalDate() == baseForecast.daily[0].date }
                .plus(
                    baseForecast.hourly.take(24).mapIndexed { index, hour ->
                        hour.copy(
                            time = Instant.fromEpochSeconds(
                                7L * 24L * 60L * 60L + index * 60L * 60L
                            )
                        )
                    }
                )
        )
        val tableData = forecast.toHourlyTableData()
        val syntheticDayIndex = tableData.days.last().dayIndex
        val viewModel = WeatherViewModel(object : WeatherRepository {
            override suspend fun getForecast(
                provider: WeatherProvider,
                location: WeatherLocation
            ): WeatherForecast = forecast
        }, Svilajnac)
        renderWeather(viewModel)

        assertTrue(
            composeRule.onAllNodesWithTag("hourly_day_chip_2")
                .fetchSemanticsNodes()
                .isEmpty()
        )
        composeRule.onNodeWithTag("hourly_day_chip_$syntheticDayIndex").assertIsDisplayed()
        composeRule.onNodeWithTag("hourly_day_chip_$syntheticDayIndex").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("hourly_day_start_$syntheticDayIndex").assertIsDisplayed()
        assertEquals(0, viewModel.state.value.selectedDayIndex)
    }

    @Test
    fun sticky_day_strip_precedes_sticky_column_header() {
        renderWeather(baseTime = Instant.fromEpochSeconds(0L))

        val initialDayStrip = composeRule.onNodeWithTag("hourly_day_strip")
            .fetchSemanticsNode().boundsInRoot
        val initialColumnHeader = composeRule.onNodeWithTag("hourly_column_header")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(initialDayStrip.top <= initialColumnHeader.top)

        composeRule.onNodeWithTag("weather_scroll").performScrollToIndex(20)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("hourly_day_strip").assertIsDisplayed()
        composeRule.onNodeWithTag("hourly_column_header").assertIsDisplayed()
    }

    @Test
    fun switches_to_daily_content() {
        val viewModel = WeatherViewModel(MockWeatherRepository(), Svilajnac)

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                WeatherScreen(
                    viewModel = viewModel,
                    onSettingsClick = {}
                )
            }
        }

        composeRule.onNodeWithText("Daily").performClick()
        composeRule.onNodeWithTag("daily_list").assertIsDisplayed()
    }

    @Test
    fun switches_to_map_placeholder() {
        val viewModel = WeatherViewModel(MockWeatherRepository(), Svilajnac)

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                WeatherScreen(
                    viewModel = viewModel,
                    onSettingsClick = {}
                )
            }
        }

        composeRule.onNodeWithText("Map").performClick()
        composeRule.onNodeWithTag("map_placeholder").assertIsDisplayed()
    }

    @Test
    fun settings_icon_reports_click() {
        val viewModel = WeatherViewModel(MockWeatherRepository(), Svilajnac)
        var settingsClicked = false

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                WeatherScreen(
                    viewModel = viewModel,
                    onSettingsClick = { settingsClicked = true }
                )
            }
        }

        composeRule.onNodeWithContentDescription("Settings").performClick()
        assertTrue(settingsClicked)
    }

    @Test
    fun clicking_current_location_opens_the_quick_switcher() {
        val viewModel = WeatherViewModel(MockWeatherRepository(), Svilajnac)
        renderWeather(viewModel)

        composeRule.onNodeWithTag("current_location").performClick()

        composeRule.onNodeWithText("Add location").assertIsDisplayed()
        composeRule.onNodeWithText("Manage locations").assertIsDisplayed()
    }

    @Test
    fun empty_saved_locations_show_setup_action() {
        val viewModel = WeatherViewModel(
            repository = MockWeatherRepository(),
            settingsRepository = object : net.droopia.hluweather.ui.settings.SettingsRepository {
                override val settings = MutableStateFlow(
                    net.droopia.hluweather.ui.settings.PersistedSettings()
                )

                override suspend fun save(
                    settings: net.droopia.hluweather.ui.settings.PersistedSettings
                ) = Unit
            },
            locationRepository = EmptyLocationRepository()
        )
        var settingsClicked = false
        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                WeatherScreen(viewModel = viewModel, onSettingsClick = { settingsClicked = true })
            }
        }

        composeRule.onNodeWithText("Add your first location").assertIsDisplayed()
        composeRule.onNodeWithText("Add location").performClick()

        assertTrue(settingsClicked)
    }

    @Test
    fun scrolling_hourly_collapses_header_to_icons_and_days() {
        val viewModel = WeatherViewModel(
            MockWeatherRepository(baseTime = Instant.fromEpochSeconds(0L)),
            Svilajnac
        )

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                WeatherScreen(
                    viewModel = viewModel,
                    onSettingsClick = {}
                )
            }
        }

        composeRule.onNodeWithText("HluWeatherApp").assertIsDisplayed()
        composeRule.onNodeWithTag("weather_scroll").performScrollToIndex(20)
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Hourly").assertIsDisplayed()
        assertTrue(
            composeRule.onNodeWithContentDescription("Hourly")
                .fetchSemanticsNode()
                .config[SemanticsProperties.Selected]
        )
        composeRule.onNodeWithTag("hourly_day_strip").assertIsDisplayed()
        composeRule.onNodeWithTag("weather_scroll").assertIsDisplayed()
        val compactHero = composeRule.onNodeWithTag("compact_weather_hero")
            .fetchSemanticsNode()
            .boundsInRoot
        val dayStrip = composeRule.onNodeWithTag("hourly_day_strip")
            .fetchSemanticsNode()
            .boundsInRoot
        assertTrue(
            "Day strip must start below the compact hero",
            dayStrip.top >= compactHero.bottom
        )
        assertTextOutsideViewport("HluWeatherApp")
        assertTextOutsideViewport("Hourly")
        assertTextOutsideViewport("Svilajnac")
    }

    @Test
    fun tiny_hourly_scroll_does_not_show_compact_header_early() {
        val viewModel = WeatherViewModel(MockWeatherRepository(), Svilajnac)

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                WeatherScreen(
                    viewModel = viewModel,
                    onSettingsClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("weather_scroll").performTouchInput {
            swipe(
                start = Offset(200f, 300f),
                end = Offset(200f, 180f),
                durationMillis = 100
            )
        }
        composeRule.waitForIdle()

        assertTrue(
            composeRule.onAllNodesWithContentDescription("Hourly")
                .fetchSemanticsNodes()
                .isEmpty()
        )
    }

    private fun assertTextOutsideViewport(text: String) {
        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val nodes = composeRule.onAllNodesWithText(text).fetchSemanticsNodes()

        assertTrue(
            "$text should not remain visible after collapsing",
            nodes.all { node ->
                node.boundsInRoot.bottom <= rootBounds.top ||
                    node.boundsInRoot.top >= rootBounds.bottom
            }
        )
    }

    private fun renderWeather(baseTime: Instant): WeatherViewModel {
        return renderWeather(
            WeatherViewModel(MockWeatherRepository(baseTime = baseTime), Svilajnac)
        )
    }

    private fun renderWeather(viewModel: WeatherViewModel): WeatherViewModel {

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                WeatherScreen(
                    viewModel = viewModel,
                    onSettingsClick = {}
                )
            }
        }

        return viewModel
    }

    private class EmptyLocationRepository : LocationRepository {
        override val locations = MutableStateFlow(emptyList<WeatherLocation>())
        override val activeLocation = MutableStateFlow<ActiveLocation?>(null)

        override suspend fun add(location: WeatherLocation) = Unit

        override suspend fun update(location: WeatherLocation) = Unit

        override suspend fun delete(id: String) = Unit

        override suspend fun selectSaved(id: String) = Unit

        override suspend fun setTrackMe(enabled: Boolean) = Unit
    }
}
