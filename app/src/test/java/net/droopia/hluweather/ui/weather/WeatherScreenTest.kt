package net.droopia.hluweather.ui.weather

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.swipe
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import java.util.TimeZone
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import net.droopia.hluweather.data.dayText
import net.droopia.hluweather.data.model.ActiveLocation
import net.droopia.hluweather.data.model.ForecastMode
import net.droopia.hluweather.data.model.LocationMode
import net.droopia.hluweather.data.model.RadarProvider
import net.droopia.hluweather.data.model.RadarFrame
import net.droopia.hluweather.data.model.RadarFrameType
import net.droopia.hluweather.data.model.RadarMetadata
import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherProvider
import net.droopia.hluweather.data.repository.LocationRepository
import net.droopia.hluweather.data.toAppLocalDate
import net.droopia.hluweather.data.repository.MockWeatherRepository
import net.droopia.hluweather.data.repository.RadarMetadataState
import net.droopia.hluweather.data.repository.RadarRepository
import net.droopia.hluweather.data.repository.Svilajnac
import net.droopia.hluweather.data.repository.WeatherRepository
import net.droopia.hluweather.data.repository.ForecastLoad
import net.droopia.hluweather.data.repository.buildMockForecast
import net.droopia.hluweather.ComposeTestActivity
import net.droopia.hluweather.ui.map.RadarPlaybackViewModel
import net.droopia.hluweather.ui.map.RadarDisplayState
import net.droopia.hluweather.ui.settings.HourlyTableColumn
import net.droopia.hluweather.ui.settings.PrecipitationUnit
import net.droopia.hluweather.ui.settings.defaultHourlyTableColumns
import net.droopia.hluweather.ui.theme.HluWeatherTheme
import androidx.compose.material3.LocalContentColor
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
    private val radarRepositories = mutableListOf<TrackingRadarRepository>()

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun teardown() {
        radarRepositories.forEach(TrackingRadarRepository::close)
        Dispatchers.resetMain()
    }

    @Test
    fun renders_hourly_content_by_default() {
        renderWeather(baseTime = Instant.fromEpochSeconds(0L))

        composeRule.onNodeWithTag("weather_scroll").assertIsDisplayed()
        composeRule.onNodeWithText("Svilajnac").assertIsDisplayed()
    }

    @Test
    fun hourly_page_sections_use_aligned_16dp_gutters_and_gaps() {
        renderWeather(baseTime = Instant.fromEpochSeconds(0L))

        val spacing = with(composeRule.density) { 16.dp.toPx() }
        val content = composeRule.onNodeWithTag("weather_scroll")
            .fetchSemanticsNode().boundsInRoot
        val card = composeRule.onNodeWithTag("current_weather_card")
            .fetchSemanticsNode().boundsInRoot
        val dayStrip = composeRule.onNodeWithTag("hourly_day_strip")
            .fetchSemanticsNode().boundsInRoot
        val tableHeader = composeRule.onNodeWithTag("hourly_column_header")
            .fetchSemanticsNode().boundsInRoot

        assertEquals(
            "Card starts one page gutter below the content top",
            content.top + spacing,
            card.top,
            1f
        )
        assertEquals("Card has a 16dp leading gutter", content.left + spacing, card.left, 1f)
        assertEquals("Card has a 16dp trailing gutter", content.right - spacing, card.right, 1f)
        assertEquals("Day strip aligns with card leading edge", card.left, dayStrip.left, 1f)
        assertEquals("Day strip aligns with card trailing edge", card.right, dayStrip.right, 1f)
        assertEquals(
            "Day strip follows the card by one page gap",
            spacing,
            dayStrip.top - card.bottom,
            1f
        )
        assertEquals("Table header aligns with the page gutter", card.left, tableHeader.left, 1f)
        assertEquals(
            "Table header follows the day strip by one page gap",
            spacing,
            tableHeader.top - dayStrip.bottom,
            1f
        )
    }

    @Test
    fun passes_selected_columns_to_current_card_in_hourly_path() {
        renderWeather(
            baseTime = Instant.fromEpochSeconds(0L),
            hourlyTableColumns = setOf(HourlyTableColumn.PM10)
        )

        assertCurrentCardMetric("PM10")
    }

    @Test
    fun map_mode_hides_current_weather_card_and_fills_weather_screen() {
        val viewModel = WeatherViewModel(MockWeatherRepository(), Svilajnac)
        viewModel.onForecastModeSelected(ForecastMode.MAP)

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                WeatherScreen(
                    viewModel = viewModel,
                    mapContent = { _, _, _, _, _ ->
                        Box(Modifier.fillMaxSize().testTag("weather_map"))
                    }
                )
            }
        }

        composeRule.onNodeWithTag("weather_map").assertIsDisplayed()
        composeRule.onAllNodesWithTag("weather_hero").assertCountEquals(0)
        assertTrue(
            composeRule.onAllNodesWithTag("current_weather_card")
                .fetchSemanticsNodes()
                .isEmpty()
        )

        val mapBounds = composeRule.onNodeWithTag("weather_map")
            .fetchSemanticsNode().boundsInRoot
        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals("Map should fill the weather content", rootBounds.top, mapBounds.top, 1f)
        assertEquals("Map should fill the weather content", rootBounds.bottom, mapBounds.bottom, 1f)
    }

    @Test
    fun changing_layer_provider_while_disabled_does_not_start_radar_or_change_map_controls() {
        val repository = TrackingRadarRepository(RadarMetadataState())
        radarRepositories += repository
        val radarViewModel = RadarPlaybackViewModel(repository)
        val weatherViewModel = WeatherViewModel(MockWeatherRepository(), Svilajnac)
        weatherViewModel.onForecastModeSelected(ForecastMode.MAP)
        val enabled = mutableStateOf(false)
        val provider = mutableStateOf(RadarProvider.LIBREWXR)
        var providerChanges = 0

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                WeatherScreen(
                    viewModel = weatherViewModel,
                    radarPlaybackViewModel = radarViewModel,
                    radarEnabled = enabled.value,
                    onRadarEnabledChange = { enabled.value = it },
                    radarProvider = provider.value,
                    onRadarProviderChange = {
                        provider.value = it
                        providerChanges++
                    },
                    mapContent = { _, _, _, _, _ ->
                        Column {
                            Text("Basemap attribution", Modifier.testTag("test_basemap_attribution"))
                            Text("Location control", Modifier.testTag("test_map_location_control"))
                        }
                    }
                )
            }
        }
        composeRule.waitForIdle()

        assertFalse(radarViewModel.state.value.isVisible)
        assertEquals(0, repository.activeCollectors)
        composeRule.onNodeWithTag("radar_visibility_toggle").assertIsDisplayed()
        composeRule.onNodeWithTag("test_basemap_attribution").assertIsDisplayed()
        composeRule.onNodeWithTag("test_map_location_control").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("radar_timeline").fetchSemanticsNodes().isEmpty())

        composeRule.onNodeWithTag("radar_layer_options").performClick()
        composeRule.onNodeWithText("75%").assertIsDisplayed()
        composeRule.onNodeWithTag("radar_layer_visibility").assertIsDisplayed()
        assertEquals(
            "Off",
            composeRule.onNodeWithTag("radar_layer_visibility")
                .fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        )
        composeRule.onNodeWithTag("radar_layer_provider_rainviewer").performClick()
        composeRule.waitForIdle()

        assertEquals(RadarProvider.RAINVIEWER, provider.value)
        assertEquals(1, providerChanges)
        assertEquals(RadarProvider.RAINVIEWER, radarViewModel.state.value.selectedProvider)
        assertFalse(enabled.value)
        assertFalse(radarViewModel.state.value.isVisible)
        assertEquals(0, repository.activeCollectors)
        assertTrue(composeRule.onAllNodesWithTag("radar_timeline").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithText("75%").assertIsDisplayed()
        composeRule.onNodeWithTag("test_basemap_attribution").assertIsDisplayed()
        composeRule.onNodeWithTag("test_map_location_control").assertIsDisplayed()
    }

    @Test
    fun settings_provider_is_synchronized_before_collection_and_layer_change_switches_live() {
        val libreWxr = TrackingRadarRepository(RadarMetadataState())
        val rainViewer = TrackingRadarRepository(RadarMetadataState())
        radarRepositories += libreWxr
        radarRepositories += rainViewer
        val radarViewModel = RadarPlaybackViewModel(
            mapOf(
                RadarProvider.LIBREWXR to libreWxr,
                RadarProvider.RAINVIEWER to rainViewer
            )
        )
        val weatherViewModel = WeatherViewModel(MockWeatherRepository(), Svilajnac)
        weatherViewModel.onForecastModeSelected(ForecastMode.MAP)
        val provider = mutableStateOf(RadarProvider.RAINVIEWER)

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                WeatherScreen(
                    viewModel = weatherViewModel,
                    radarPlaybackViewModel = radarViewModel,
                    radarProvider = provider.value,
                    onRadarProviderChange = { provider.value = it },
                    mapContent = { _, _, _, _, _ -> Box(Modifier.fillMaxSize().testTag("provider_test_map")) }
                )
            }
        }
        composeRule.waitForIdle()

        assertEquals(RadarProvider.RAINVIEWER, radarViewModel.state.value.selectedProvider)
        assertEquals(0, libreWxr.activeCollectors)
        assertEquals(1, rainViewer.activeCollectors)
        composeRule.onNodeWithTag("radar_layer_options").performClick()
        composeRule.onNodeWithTag("radar_layer_provider_librewxr").performClick()
        composeRule.waitForIdle()

        assertEquals(RadarProvider.LIBREWXR, provider.value)
        assertEquals(RadarProvider.LIBREWXR, radarViewModel.state.value.selectedProvider)
        assertEquals(1, libreWxr.activeCollectors)
        assertEquals(0, rainViewer.activeCollectors)
        composeRule.onNodeWithTag("provider_test_map").assertIsDisplayed()
    }

    @Test
    fun destination_and_lifecycle_visibility_cancel_collection_and_ignore_late_display_ack() {
        val libreWxr = TrackingRadarRepository(RadarMetadataState())
        val rainViewer = TrackingRadarRepository(RadarMetadataState())
        radarRepositories += libreWxr
        radarRepositories += rainViewer
        val radarViewModel = RadarPlaybackViewModel(
            mapOf(
                RadarProvider.LIBREWXR to libreWxr,
                RadarProvider.RAINVIEWER to rainViewer
            )
        )
        val weatherViewModel = WeatherViewModel(MockWeatherRepository(), Svilajnac)
        weatherViewModel.onForecastModeSelected(ForecastMode.MAP)
        val mainMapVisible = mutableStateOf(true)
        val lifecycleOwner = MutableTestLifecycleOwner(Lifecycle.State.RESUMED)

        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                HluWeatherTheme(darkTheme = false) {
                    WeatherScreen(
                        viewModel = weatherViewModel,
                        radarPlaybackViewModel = radarViewModel,
                        mainMapDestinationVisible = mainMapVisible.value,
                        mapContent = { _, _, _, _, _ -> Box(Modifier.fillMaxSize().testTag("lifecycle_test_map")) }
                    )
                }
            }
        }
        composeRule.waitForIdle()
        assertTrue(radarViewModel.state.value.isVisible)
        assertEquals(1, libreWxr.activeCollectors)

        val oldFrame = testRadarFrame(RadarProvider.LIBREWXR, 1_793_477_400L)
        val requestedFrame = testRadarFrame(RadarProvider.LIBREWXR, 1_793_478_000L)
        val metadata = RadarMetadata.normalized(
            RadarProvider.LIBREWXR,
            "https://tiles.librewxr.example.test",
            listOf(oldFrame, requestedFrame)
        )
        libreWxr.publish(metadata)
        composeRule.waitForIdle()
        val requestedIdentity = requireNotNull(radarViewModel.state.value.requestedFrameIdentity)
        val oldIdentity = oldFrame.identity(metadata.host)

        mainMapVisible.value = false
        composeRule.waitForIdle()
        assertFalse(radarViewModel.state.value.isVisible)
        assertEquals(0, libreWxr.activeCollectors)
        radarViewModel.onDisplayChanged(RadarDisplayState(requestedIdentity, requestedIdentity, pending = false))
        assertNull(radarViewModel.state.value.displayedFrameIdentity)

        mainMapVisible.value = true
        composeRule.waitForIdle()
        assertTrue(radarViewModel.state.value.isVisible)
        radarViewModel.onDisplayChanged(RadarDisplayState(requestedIdentity, oldIdentity, pending = true))
        assertEquals(oldIdentity, radarViewModel.state.value.displayedFrameIdentity)

        composeRule.runOnIdle { lifecycleOwner.lifecycle.currentState = Lifecycle.State.CREATED }
        composeRule.waitForIdle()
        assertFalse(radarViewModel.state.value.isVisible)
        assertEquals(0, libreWxr.activeCollectors)
        radarViewModel.onDisplayChanged(RadarDisplayState(requestedIdentity, requestedIdentity, pending = false))
        assertNull(radarViewModel.state.value.displayedFrameIdentity)
        composeRule.onNodeWithTag("lifecycle_test_map").assertIsDisplayed()
    }

    @Test
    fun initial_loading_uses_full_screen_spinner_only_without_forecast() {
        val initialResult = CompletableDeferred<ForecastLoad>()
        val refreshResult = CompletableDeferred<ForecastLoad>()
        val repository = DeferredWeatherRepository(initialResult, refreshResult)
        val viewModel = WeatherViewModel(repository, Svilajnac)
        renderWeather(viewModel)

        composeRule
            .onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
            .assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithTag("weather_refresh_indicator")
                .fetchSemanticsNodes()
                .isEmpty()
        )

        initialResult.complete(
            ForecastLoad(buildMockForecast(Svilajnac, Instant.fromEpochSeconds(1L)))
        )
        composeRule.waitForIdle()

        composeRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertDoesNotExist()
        viewModel.refresh()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("weather_refresh_indicator").assertIsDisplayed()
    }

    @Test
    fun refresh_keeps_forecast_content_visible_and_shows_indicator_until_completion() {
        val initialForecast = buildMockForecast(Svilajnac, Instant.fromEpochSeconds(1L))
        val refreshResult = CompletableDeferred<ForecastLoad>()
        val repository = DeferredWeatherRepository(
            CompletableDeferred(ForecastLoad(initialForecast)),
            refreshResult
        )
        val viewModel = WeatherViewModel(repository, Svilajnac)
        renderWeather(viewModel)
        composeRule.waitForIdle()

        viewModel.refresh()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("current_temperature").assertIsDisplayed()
        composeRule.onNodeWithTag("weather_refresh_indicator").assertIsDisplayed()

        refreshResult.complete(
            ForecastLoad(buildMockForecast(Svilajnac, Instant.fromEpochSeconds(2L)))
        )
        composeRule.waitForIdle()

        assertTrue(
            composeRule.onAllNodesWithTag("weather_refresh_indicator")
                .fetchSemanticsNodes()
                .isEmpty()
        )
    }

    @Test
    fun pulling_down_from_top_starts_refresh_request() {
        val refreshResult = CompletableDeferred<ForecastLoad>()
        val repository = DeferredWeatherRepository(
            CompletableDeferred(
                ForecastLoad(buildMockForecast(Svilajnac, Instant.fromEpochSeconds(1L)))
            ),
            refreshResult
        )
        val viewModel = WeatherViewModel(repository, Svilajnac)
        renderWeather(viewModel)
        composeRule.waitForIdle()

        assertEquals(1, repository.networkRequests)
        composeRule.onNodeWithTag("weather_scroll").performTouchInput {
            swipe(
                start = Offset(200f, 12f),
                end = Offset(200f, 600f),
                durationMillis = 600
            )
        }
        composeRule.waitForIdle()

        assertEquals(2, repository.networkRequests)
        assertTrue(viewModel.state.value.isRefreshing)
    }

    @Test
    fun hourly_screen_renders_rows_from_the_next_day_without_switching_tables() {
        val now = Instant.fromEpochSeconds(0L)
        val viewModel = renderWeather(baseTime = now)
        val forecast = viewModel.state.value.forecast!!
        val nextDayBoundaryIndex = 2 + forecast.toHourlyTableData(now = now)
            .firstItemIndexForDay(1)!!

        composeRule.onNodeWithTag("weather_scroll")
            .performScrollToIndex(nextDayBoundaryIndex)

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
    fun scrolling_between_dates_reveals_selected_chips_without_vertical_snap_back() {
        val now = Instant.fromEpochSeconds(0L)
        val viewModel = renderWeather(baseTime = now)
        val table = viewModel.state.value.forecast!!.toHourlyTableData(now)
        val scroll = composeRule.onNodeWithTag("weather_scroll")

        for (dayIndex in listOf(1, 6, 0)) {
            val hourIndex = table.firstHourIndexForDay(dayIndex)!! + 8
            scroll.performScrollToIndex(2 + hourIndex)
            composeRule.waitForIdle()

            assertEquals(dayIndex, viewModel.state.value.selectedDayIndex)
            assertChipFullyVisible(dayIndex)
            val position = scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
            composeRule.mainClock.advanceTimeBy(2_000)
            composeRule.waitForIdle()
            assertEquals(
                "Visibility selection must not navigate back to the first hour",
                position,
                scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value(),
                0.01f
            )
            composeRule.onAllNodesWithTag("hourly_day_start_$dayIndex").assertCountEquals(0)
        }

        scroll.performTouchInput {
            swipe(start = Offset(200f, 600f), end = Offset(200f, 400f), durationMillis = 500)
        }
        composeRule.waitForIdle()
        assertChipFullyVisible(0)
        composeRule.onNodeWithTag("hourly_day_chip_1").performClick()
        composeRule.waitForIdle()
        assertChipFullyVisible(1)
        composeRule.onNodeWithTag("hourly_selected_day_1").assertIsDisplayed()
    }

    @Test
    fun swiping_across_a_date_boundary_updates_selection_without_jumping_to_day_start() {
        val now = Instant.fromEpochSeconds(0L)
        val viewModel = renderWeather(baseTime = now)
        val table = viewModel.state.value.forecast!!.toHourlyTableData(now)
        val nextDayStart = table.firstHourIndexForDay(1)!!
        val scroll = composeRule.onNodeWithTag("weather_scroll")
        scroll.performScrollToIndex(2 + nextDayStart - 2)
        composeRule.waitForIdle()
        assertChipFullyVisible(0)

        scroll.performTouchInput {
            swipe(
                start = Offset(centerX, height * 0.8f),
                end = Offset(centerX, height * 0.4f),
                durationMillis = 500
            )
        }
        composeRule.waitForIdle()
        assertEquals(1, viewModel.state.value.selectedDayIndex)
        assertChipFullyVisible(1)
        composeRule.onAllNodesWithTag("hourly_day_start_1").assertCountEquals(0)
        val position = scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        assertEquals(position, scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value(), 0.01f)
    }

    @Test
    fun forecast_timezone_boundary_controls_selection_during_manual_scroll() {
        val now = Instant.parse("2026-09-02T00:00:00Z")
        val forecast = buildMockForecast(Svilajnac, now).copy(timezone = "America/New_York")
        val table = forecast.toHourlyTableData(now)
        val firstDay = table.days.first()
        val nextDay = table.days[1]
        assertTrue(firstDay.dayIndex < 0)
        assertEquals("2026-09-01", firstDay.date.toString())
        assertEquals("2026-09-02", nextDay.date.toString())
        val viewModel = WeatherViewModel(object : WeatherRepository {
            override suspend fun getForecast(provider: WeatherProvider, location: ActiveLocation) =
                ForecastLoad(forecast)
            override suspend fun clearCache() = Unit
        }, Svilajnac)
        renderWeather(viewModel, now)

        val scroll = composeRule.onNodeWithTag("weather_scroll")
        scroll.performScrollToIndex(2 + table.firstHourIndexForDay(firstDay.dayIndex)!!)
        composeRule.waitForIdle()
        assertChipFullyVisible(firstDay.dayIndex)

        scroll.performScrollToIndex(2 + table.firstHourIndexForDay(nextDay.dayIndex)!! + 3)
        composeRule.waitForIdle()
        assertChipFullyVisible(nextDay.dayIndex)
        assertEquals(nextDay.dayIndex, viewModel.state.value.selectedDayIndex)
        composeRule.onAllNodesWithTag("hourly_day_start_${nextDay.dayIndex}").assertCountEquals(0)
    }

    @Test
    fun selecting_a_day_from_daily_content_positions_hourly_list_on_that_day() {
        val viewModel = renderWeather(baseTime = Instant.fromEpochSeconds(0L))
        val selectedDayText = viewModel.state.value.forecast!!.daily[2].date.dayText()

        viewModel.onForecastModeSelected(ForecastMode.DAILY)
        composeRule.waitForIdle()
        composeRule.onNodeWithText(selectedDayText).performClick()
        composeRule.waitForIdle()

        assertEquals(2, viewModel.state.value.selectedDayIndex)
        viewModel.onForecastModeSelected(ForecastMode.HOURLY)
        composeRule.waitForIdle()
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
        val tableData = forecast.toHourlyTableData(now = forecast.hourly.first().time)
        val syntheticDayIndex = tableData.days.last().dayIndex
        val viewModel = WeatherViewModel(object : WeatherRepository {
            override suspend fun getForecast(
                provider: WeatherProvider,
                location: ActiveLocation
            ): ForecastLoad = ForecastLoad(forecast)

            override suspend fun clearCache() = Unit
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
        val tableData = forecast.toHourlyTableData(now = forecast.hourly.first().time)
        val nearestDay = tableData.nearestDayForIndex(2)!!
        val viewModel = WeatherViewModel(object : WeatherRepository {
            override suspend fun getForecast(
                provider: WeatherProvider,
                location: ActiveLocation
            ): ForecastLoad = ForecastLoad(forecast)

            override suspend fun clearCache() = Unit
        }, Svilajnac)
        renderWeather(viewModel)

        viewModel.onForecastModeSelected(ForecastMode.DAILY)
        composeRule.waitForIdle()
        composeRule.onNodeWithText(missingDate.dayText()).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("hourly_day_chip_${nearestDay.dayIndex}").assertIsSelected()
        viewModel.onForecastModeSelected(ForecastMode.HOURLY)
        composeRule.waitForIdle()
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
        val tableData = forecast.toHourlyTableData(now = forecast.hourly.first().time)
        val syntheticDayIndex = tableData.days.last().dayIndex
        val viewModel = WeatherViewModel(object : WeatherRepository {
            override suspend fun getForecast(
                provider: WeatherProvider,
                location: ActiveLocation
            ): ForecastLoad = ForecastLoad(forecast)

            override suspend fun clearCache() = Unit
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
    fun sticky_day_strip_and_column_header_remain_separated_at_top_and_across_date_boundary() {
        val viewModel = renderWeather(baseTime = Instant.fromEpochSeconds(0L))

        assertHourlyHeadersDoNotOverlap()

        composeRule.onNodeWithTag("weather_scroll").performScrollToIndex(20)
        composeRule.waitForIdle()

        assertHourlyHeadersDoNotOverlap()

        val forecast = viewModel.state.value.forecast!!
        val nextDayBoundaryIndex = 2 + forecast.toHourlyTableData(
            now = Instant.fromEpochSeconds(0L)
        ).firstItemIndexForDay(1)!!
        composeRule.onNodeWithTag("weather_scroll")
            .performScrollToIndex(nextDayBoundaryIndex)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("hourly_day_boundary_1").assertIsDisplayed()
        assertHourlyHeadersDoNotOverlap()
    }

    @Test
    fun hourly_precipitation_heading_and_values_use_selected_unit() {
        val baseForecast = buildMockForecast(Svilajnac, Instant.fromEpochSeconds(0L))
        val firstHour = baseForecast.hourly.first().copy(precipitation = 10.0)
        val forecast = baseForecast.copy(hourly = listOf(firstHour))
        val viewModel = WeatherViewModel(object : WeatherRepository {
            override suspend fun getForecast(
                provider: WeatherProvider,
                location: ActiveLocation
            ): ForecastLoad = ForecastLoad(forecast)

            override suspend fun clearCache() = Unit
        }, Svilajnac)

        renderWeather(
            viewModel,
            now = firstHour.time,
            hourlyTableColumns = setOf(HourlyTableColumn.PRECIPITATION),
            precipitationUnit = PrecipitationUnit.INCH
        )

        composeRule.onNodeWithText("Precip. (in)").assertIsDisplayed()
        composeRule.onNodeWithText("0.39").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("0.39 in").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun switches_to_daily_content() {
        val viewModel = WeatherViewModel(MockWeatherRepository(), Svilajnac)
        viewModel.onForecastModeSelected(ForecastMode.DAILY)

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                WeatherScreen(
                    viewModel = viewModel,
                    onSettingsClick = {},
                    now = Instant.fromEpochSeconds(0L)
                )
            }
        }

        composeRule.onAllNodesWithTag("weather_hero").assertCountEquals(0)
        assertTrue(
            composeRule.onAllNodesWithTag("current_weather_card")
                .fetchSemanticsNodes()
                .isEmpty()
        )
        composeRule.onNodeWithTag("daily_list").assertIsDisplayed()
    }

    @Test
    fun dark_theme_keeps_selected_navigation_and_day_text_visible() {
        val viewModel = WeatherViewModel(MockWeatherRepository(), Svilajnac)
        viewModel.onForecastModeSelected(ForecastMode.DAILY)

        composeRule.setContent {
            HluWeatherTheme(darkTheme = true) {
                WeatherScreen(
                    viewModel = viewModel,
                    onSettingsClick = {},
                    now = Instant.fromEpochSeconds(0L)
                )
            }
        }

        composeRule.onNodeWithTag("daily_list").assertIsDisplayed()

        viewModel.onForecastModeSelected(ForecastMode.HOURLY)
        composeRule.waitForIdle()
        val forecast = viewModel.state.value.forecast!!
        val day = forecast.toHourlyTableData().days[1]
        val dayText = day.date.dayText(ZoneId.of(forecast.timezone))
        composeRule.onNodeWithTag("hourly_day_chip_${day.dayIndex}").performClick()
        composeRule.onNodeWithText(dayText).assertIsDisplayed()
    }

    @Test
    fun stale_forecast_does_not_show_a_banner_or_retry_action() {
        val forecast = buildMockForecast(Svilajnac, Instant.fromEpochSeconds(123L))
        val viewModel = WeatherViewModel(object : WeatherRepository {
            override suspend fun getForecast(
                provider: WeatherProvider,
                location: ActiveLocation
            ): ForecastLoad = ForecastLoad(forecast, isStale = true)

            override suspend fun clearCache() = Unit
        }, Svilajnac)
        renderWeather(viewModel)

        assertTrue(
            composeRule.onAllNodesWithTag("stale_forecast_banner")
                .fetchSemanticsNodes()
                .isEmpty()
        )
        assertTrue(
            composeRule.onAllNodesWithText("Showing cached data from ${forecast.fetchedAt}")
                .fetchSemanticsNodes()
                .isEmpty()
        )
        assertTrue(
            composeRule.onAllNodesWithText("Retry")
                .fetchSemanticsNodes()
                .isEmpty()
        )
    }

    @Test
    fun switches_to_map_content() {
        val viewModel = WeatherViewModel(MockWeatherRepository(), Svilajnac)
        viewModel.onForecastModeSelected(ForecastMode.MAP)
        var mapDarkTheme = false

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                WeatherScreen(
                    viewModel = viewModel,
                    onSettingsClick = {},
                    darkTheme = true,
                    mapContent = { _, _, _, darkTheme, _ ->
                        mapDarkTheme = darkTheme
                        Text("Map", Modifier.testTag("weather_map"))
                    }
                )
            }
        }

        composeRule.onNodeWithTag("weather_map").assertIsDisplayed()
        assertEquals(true, mapDarkTheme)
    }

    @Test
    fun route_shortcut_is_absent_from_hourly_and_map_while_map_radar_controls_remain() {
        val viewModel = WeatherViewModel(MockWeatherRepository(), Svilajnac)

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                WeatherScreen(
                    viewModel = viewModel,
                    onSettingsClick = {},
                    mapContent = { _, _, _, _, _ ->
                        Text("Map", Modifier.testTag("weather_map"))
                    }
                )
            }
        }

        composeRule.onAllNodesWithText("Weather on route").assertCountEquals(0)
        composeRule.onAllNodesWithTag("weather_route_open").assertCountEquals(0)

        viewModel.onForecastModeSelected(ForecastMode.MAP)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("weather_map").assertIsDisplayed()
        composeRule.onAllNodesWithText("Weather on route").assertCountEquals(0)
        composeRule.onAllNodesWithTag("weather_route_open").assertCountEquals(0)
        composeRule.onNodeWithTag("radar_visibility_toggle").assertIsDisplayed()
    }

    @Test
    fun location_manage_action_reports_settings_click() {
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

        composeRule.onNodeWithTag("current_location").performClick()
        composeRule.onNodeWithText("Manage locations").performClick()
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
    fun large_font_weather_screen_keeps_the_current_card_within_the_screen_width() {
        val viewModel = WeatherViewModel(MockWeatherRepository(), Svilajnac)

        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.5f)) {
                HluWeatherTheme(darkTheme = false) {
                    Box(
                        modifier = Modifier
                            .width(320.dp)
                            .fillMaxHeight()
                    ) {
                        WeatherScreen(viewModel = viewModel, onSettingsClick = {})
                    }
                }
            }
        }

        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        composeRule.onAllNodesWithText("21°C").fetchSemanticsNodes().forEach { node ->
            assertTrue(
                "Current temperature must fit at 1.5x font scale",
                node.boundsInRoot.left >= root.left && node.boundsInRoot.right <= root.right
            )
        }
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
        var pickerClicked = false
        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                WeatherScreen(
                    viewModel = viewModel,
                    onSettingsClick = { settingsClicked = true },
                    onAddLocationClick = { pickerClicked = true }
                )
            }
        }

        composeRule.onNodeWithText("Add your first location").assertIsDisplayed()
        composeRule.onNodeWithText("Add location").performClick()

        assertTrue(pickerClicked)
        assertFalse(settingsClicked)
    }

    @Test
    fun empty_saved_locations_add_action_reports_direct_picker_navigation() {
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
        var pickerClicked = false

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                WeatherScreen(
                    viewModel = viewModel,
                    onAddLocationClick = { pickerClicked = true }
                )
            }
        }

        composeRule.onNodeWithText("Add location").performClick()

        assertTrue(pickerClicked)
    }

    @Test
    fun empty_saved_locations_use_light_foreground_in_dark_theme() {
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

        composeRule.setContent {
            HluWeatherTheme(darkTheme = true) {
                CompositionLocalProvider(LocalContentColor provides Color.Black) {
                    WeatherScreen(viewModel = viewModel)
                }
            }
        }

        listOf(
            "Add your first location",
            "Choose a saved place to see the weather.",
            "Add location"
        ).forEach { text ->
            assertTrue(
                "$text should use a light foreground",
                composeRule.onNodeWithText(text).captureToImage().hasLightForeground()
            )
        }
    }

    @Test
    fun scrolling_hourly_does_not_create_a_duplicate_navigation_header() {
        val viewModel = WeatherViewModel(
            MockWeatherRepository(baseTime = Instant.fromEpochSeconds(0L)),
            Svilajnac
        )

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                WeatherScreen(
                    viewModel = viewModel,
                    onSettingsClick = {},
                    now = Instant.fromEpochSeconds(0L)
                )
            }
        }

        composeRule.onNodeWithTag("weather_scroll").performScrollToIndex(20)
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag("weather_hero").assertCountEquals(0)
        composeRule.onAllNodesWithTag("compact_weather_hero").assertCountEquals(0)
        composeRule.onNodeWithTag("hourly_day_strip").assertIsDisplayed()
        composeRule.onNodeWithTag("weather_scroll").assertIsDisplayed()
    }

    @Test
    fun tiny_hourly_scroll_does_not_create_a_compact_navigation_header() {
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

        composeRule.onAllNodesWithTag("compact_weather_hero").assertCountEquals(0)
    }

    private fun assertCurrentCardMetric(title: String) {
        composeRule.onNode(
            hasTestTag("current_metric_feels_like") and
                hasAnyDescendant(hasText(title, substring = false))
        ).assertIsDisplayed()
    }

    private fun assertChipFullyVisible(dayIndex: Int) {
        val chip = composeRule.onNodeWithTag("hourly_day_chip_$dayIndex")
            .assertIsSelected().assertIsDisplayed().fetchSemanticsNode()
        val strip = composeRule.onNodeWithTag("hourly_day_strip").fetchSemanticsNode().boundsInRoot
        val bounds = chip.boundsInRoot
        assertTrue("Selected chip must be fully visible", bounds.width >= chip.size.width - 1f)
        assertTrue(bounds.left >= strip.left - 1f && bounds.right <= strip.right + 1f)
    }

    private fun assertHourlyHeadersDoNotOverlap() {
        composeRule.onAllNodesWithTag("hourly_day_strip").assertCountEquals(1)
        composeRule.onAllNodesWithTag("hourly_column_header").assertCountEquals(1)
        composeRule.onNodeWithTag("hourly_day_strip").assertIsDisplayed()
        composeRule.onNodeWithTag("hourly_column_header").assertIsDisplayed()

        val dayStrip = composeRule.onNodeWithTag("hourly_day_strip")
            .fetchSemanticsNode().boundsInRoot
        val columnHeader = composeRule.onNodeWithTag("hourly_column_header")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Date strip bottom ${dayStrip.bottom} must not overlap column header top ${columnHeader.top}",
            dayStrip.bottom <= columnHeader.top
        )
    }

    private fun renderWeather(
        baseTime: Instant,
        hourlyTableColumns: Set<HourlyTableColumn> = defaultHourlyTableColumns
    ): WeatherViewModel {
        return renderWeather(
            WeatherViewModel(MockWeatherRepository(baseTime = baseTime), Svilajnac),
            now = baseTime,
            hourlyTableColumns = hourlyTableColumns
        )
    }

    private fun renderWeather(viewModel: WeatherViewModel): WeatherViewModel {
        return renderWeather(
            viewModel,
            now = viewModel.state.value.forecast?.hourly?.firstOrNull()?.time
        )
    }

    private fun renderWeather(
        viewModel: WeatherViewModel,
        now: Instant?,
        hourlyTableColumns: Set<HourlyTableColumn> = defaultHourlyTableColumns,
        precipitationUnit: PrecipitationUnit = PrecipitationUnit.MM
    ): WeatherViewModel {

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                WeatherScreen(
                    viewModel = viewModel,
                    onSettingsClick = {},
                    hourlyTableColumns = hourlyTableColumns,
                    precipitationUnit = precipitationUnit,
                    now = now
                )
            }
        }

        return viewModel
    }

    private class EmptyLocationRepository : LocationRepository {
        override val locations = MutableStateFlow(emptyList<WeatherLocation>())
        override val activeLocation = MutableStateFlow<ActiveLocation?>(null)
        override val locationMode = MutableStateFlow(LocationMode.SAVED_LOCATION)

        override suspend fun add(location: WeatherLocation) = Unit

        override suspend fun update(location: WeatherLocation) = Unit

        override suspend fun delete(id: String) = Unit

        override suspend fun selectSaved(id: String) = Unit

        override suspend fun setTrackMe(enabled: Boolean) = Unit
    }

    private class TrackingRadarRepository(initial: RadarMetadataState) : RadarRepository {
        private val updates = MutableStateFlow(initial)
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var activeCollectors = 0
            private set

        override val state = updates
            .onStart { activeCollectors++ }
            .onCompletion { activeCollectors-- }
            .stateIn(
                scope,
                SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0),
                initial
            )

        fun publish(metadata: RadarMetadata) {
            updates.value = RadarMetadataState(metadata = metadata, isLoading = false)
        }

        fun close() = scope.cancel()
    }

    private class MutableTestLifecycleOwner(initialState: Lifecycle.State) : LifecycleOwner {
        override val lifecycle = LifecycleRegistry(this).apply { currentState = initialState }
    }

    private fun testRadarFrame(provider: RadarProvider, timestamp: Long) = RadarFrame(
        timestampSeconds = timestamp,
        path = "/v2/radar/$timestamp",
        type = RadarFrameType.OBSERVED,
        provider = provider
    )

    private fun ImageBitmap.hasLightForeground(): Boolean {
        val pixels = IntArray(width * height)
        readPixels(pixels)
        return pixels.any {
            val color = Color(it)
            color.red > 0.6f && color.green > 0.6f && color.blue > 0.6f
        }
    }

    private class DeferredWeatherRepository(
        private vararg val networkResults: CompletableDeferred<ForecastLoad>
    ) : WeatherRepository {
        var networkRequests = 0

        override suspend fun getForecast(
            provider: WeatherProvider,
            location: ActiveLocation
        ): ForecastLoad = networkResults[networkRequests++].await()

        override suspend fun clearCache() = Unit
    }
}
