package net.droopia.hluweather.ui.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.ZoneId
import kotlinx.datetime.LocalDate
import kotlin.time.Instant
import net.droopia.hluweather.data.dayText
import net.droopia.hluweather.data.hourText
import net.droopia.hluweather.data.model.HourForecast
import net.droopia.hluweather.data.model.label
import net.droopia.hluweather.data.model.WeatherCondition
import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.percentText
import net.droopia.hluweather.data.precipitationText
import net.droopia.hluweather.data.temperatureValueText
import net.droopia.hluweather.data.windDirectionText
import net.droopia.hluweather.data.windSpeedText
import net.droopia.hluweather.ui.components.HluWeatherIcon
import net.droopia.hluweather.ui.settings.PrecipitationUnit
import net.droopia.hluweather.ui.settings.TemperatureUnit
import net.droopia.hluweather.ui.settings.HourlyTableColumn
import net.droopia.hluweather.ui.settings.defaultHourlyTableColumns
import net.droopia.hluweather.ui.settings.WindUnit
import net.droopia.hluweather.ui.settings.WindDirectionDisplay
import net.droopia.hluweather.ui.theme.LocalHluColors
import kotlin.time.Clock

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun HourlyForecast(
    forecast: WeatherForecast,
    selectedDayIndex: Int,
    onDaySelected: (Int) -> Unit,
    temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    precipitationUnit: PrecipitationUnit = PrecipitationUnit.MM,
    windUnit: WindUnit = WindUnit.KMH,
    windDirectionDisplay: WindDirectionDisplay = WindDirectionDisplay.ARROW,
    hourlyTableColumns: Set<HourlyTableColumn> = defaultHourlyTableColumns,
    now: Instant = Clock.System.now(),
    modifier: Modifier = Modifier
) {
    val displayZone = ZoneId.of(forecast.timezone)
    val tableData = remember(forecast, now) {
        forecast.toHourlyTableData(now = now)
    }
    val selectedDayDate = tableData.nearestDayForIndex(selectedDayIndex)?.date
    val listState = rememberLazyListState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("hourly_table"),
        state = listState
    ) {
        stickyHeader {
            HourlyDaySelector(
                tableData = tableData,
                selectedDayDate = selectedDayDate,
                displayZone = displayZone,
                onDaySelected = onDaySelected
            )
        }
        stickyHeader {
            ForecastColumnHeader(
                columns = hourlyTableColumns,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        itemsIndexed(
            items = tableData.items,
            key = { _, item -> item.key }
        ) { itemIndex, item ->
            when (item) {
                is HourlyTableBoundary -> {
                    HourlyDateBoundary(
                        item = item,
                        displayZone = displayZone,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                is HourlyTableHour -> {
                    val isFirstHour = tableData.firstHourIndexForDay(item.dayIndex) == itemIndex
                    ForecastRow(
                        weather = item.hour,
                        displayZone = displayZone,
                        temperatureUnit = temperatureUnit,
                        precipitationUnit = precipitationUnit,
                        columns = hourlyTableColumns,
                        windUnit = windUnit,
                        windDirectionDisplay = windDirectionDisplay,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .then(
                                if (isFirstHour) {
                                    Modifier.testTag("hourly_day_start_${item.dayIndex}")
                                } else {
                                    Modifier
                                }
                            ),
                        timeTestTag = if (isFirstHour && selectedDayDate == item.date) {
                            "hourly_selected_day_${item.dayIndex}"
                        } else {
                            null
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun HourlyDaySelector(
    tableData: HourlyTableData,
    selectedDayDate: LocalDate?,
    displayZone: ZoneId,
    onDaySelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("hourly_day_strip"),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tableData.days.forEach { day ->
                DayChip(
                    dayIndex = day.dayIndex,
                    text = day.date.dayText(displayZone),
                    selected = day.date == selectedDayDate,
                    onClick = { onDaySelected(day.dayIndex) }
                )
            }
        }
    }
}

@Composable
private fun DayChip(
    dayIndex: Int,
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalHluColors.current

    Surface(
        modifier = Modifier
            .testTag("hourly_day_chip_$dayIndex")
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick
            ),
        shape = RoundedCornerShape(26.dp),
        color = if (selected) colors.daySelected else Color.Transparent
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = 16.dp,
                vertical = 10.dp
            ),
            color = if (selected) {
                colors.daySelectedText
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
internal fun HourlyDateBoundary(
    item: HourlyTableBoundary,
    displayZone: ZoneId,
    modifier: Modifier = Modifier
) {
    Text(
        text = item.date.dayText(displayZone),
        modifier = modifier
            .fillMaxWidth()
            .testTag("hourly_day_boundary_${item.dayIndex}")
            .padding(top = 16.dp, bottom = 8.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
internal fun ForecastColumnHeader(
    columns: Set<HourlyTableColumn> = defaultHourlyTableColumns,
    modifier: Modifier = Modifier
) {
    val colors = LocalHluColors.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("hourly_column_header")
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(colors.tableHeader)
            .padding(
                horizontal = 16.dp,
                vertical = 13.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ForecastCell("Time", HourlyTableColumn.TIME.weight)
        HourlyTableColumn.entries
            .filter { it != HourlyTableColumn.TIME && it in columns }
            .forEach { column ->
            ForecastCell(column.header, column.weight)
            }
    }
}

@Composable
private fun RowScope.ForecastCell(
    text: String,
    weight: Float
) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp
    )
}

@Composable
internal fun ForecastRow(
    weather: HourForecast,
    displayZone: ZoneId,
    temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    precipitationUnit: PrecipitationUnit = PrecipitationUnit.MM,
    windUnit: WindUnit = WindUnit.KMH,
    windDirectionDisplay: WindDirectionDisplay = WindDirectionDisplay.ARROW,
    columns: Set<HourlyTableColumn> = defaultHourlyTableColumns,
    timeTestTag: String? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalHluColors.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.tableRow)
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
            .padding(
                horizontal = 16.dp,
                vertical = 9.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = weather.time.hourText(displayZone),
            modifier = Modifier
                .weight(HourlyTableColumn.TIME.weight)
                .then(timeTestTag?.let(Modifier::testTag) ?: Modifier),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )

        HourlyTableColumn.entries
            .filter { it != HourlyTableColumn.TIME && it in columns }
            .forEach { column ->
                when (column) {
                    HourlyTableColumn.WEATHER_ICON -> Box(
                        modifier = Modifier.weight(column.weight),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (weather.condition == WeatherCondition.UNKNOWN) {
                            Text("-", color = MaterialTheme.colorScheme.onSurface)
                        } else {
                            HluWeatherIcon(
                                condition = weather.condition,
                                isDay = weather.isDay,
                                contentDescription = weather.condition.label(),
                                modifier = Modifier
                                    .width(24.dp)
                                    .testTag("hourly_weather_icon")
                            )
                        }
                    }
                    HourlyTableColumn.WEATHER_TEXT -> ForecastValue(
                        text = if (weather.condition == WeatherCondition.UNKNOWN) {
                            "-"
                        } else {
                            weather.condition.label()
                        },
                        column = column
                    )
                    HourlyTableColumn.TEMPERATURE -> ForecastValue(
                        text = weather.temperature.temperatureValueText(temperatureUnit).tableValueText(),
                        column = column,
                        emphasized = true
                    )
                    HourlyTableColumn.DEW_POINT -> ForecastValue(
                        text = weather.dewPoint.temperatureValueText(temperatureUnit).tableValueText(),
                        column = column,
                        secondary = true
                    )
                    HourlyTableColumn.RELATIVE_HUMIDITY -> ForecastValue(
                        text = weather.humidity.percentText().tableValueText(),
                        column = column
                    )
                    HourlyTableColumn.PRECIPITATION -> ForecastValue(
                        text = weather.precipitation.precipitationText(precipitationUnit).tableValueText(),
                        column = column
                    )
                    HourlyTableColumn.WIND_SPEED -> ForecastValue(
                        text = weather.windSpeedKmh.windSpeedText(windUnit).tableValueText(),
                        column = column
                    )
                    HourlyTableColumn.WIND_DIRECTION -> {
                        val degrees = weather.windDirectionDegrees
                        if (degrees == null) {
                            ForecastValue(text = "-", column = column)
                        } else if (windDirectionDisplay == WindDirectionDisplay.ARROW) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Filled.Navigation,
                                contentDescription = null,
                                modifier = Modifier
                                    .weight(column.weight)
                                    .graphicsLayer { rotationZ = degrees.toFloat() }
                                    .testTag("hourly_wind_direction_arrow"),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        } else {
                            ForecastValue(
                                text = degrees.windDirectionText(windDirectionDisplay).tableValueText(),
                                column = column
                            )
                        }
                    }
                    HourlyTableColumn.EVAPOTRANSPIRATION -> ForecastValue(
                        text = weather.evapotranspiration.precipitationText(precipitationUnit).tableValueText(),
                        column = column
                    )
                    HourlyTableColumn.TIME -> Unit
                }
            }
    }
}

private fun String.tableValueText(): String = if (this == "—") "-" else this

@Composable
private fun RowScope.ForecastValue(
    text: String,
    column: HourlyTableColumn,
    emphasized: Boolean = false,
    secondary: Boolean = false
) {
    Text(
        text = text,
        modifier = Modifier.weight(column.weight),
        color = if (secondary) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal
    )
}

private val HourlyTableColumn.header: String
    get() = when (this) {
        HourlyTableColumn.TIME -> "Time"
        HourlyTableColumn.WEATHER_ICON -> "Icon"
        HourlyTableColumn.WEATHER_TEXT -> "Weather"
        HourlyTableColumn.TEMPERATURE -> "Temp."
        HourlyTableColumn.DEW_POINT -> "Dew point"
        HourlyTableColumn.RELATIVE_HUMIDITY -> "Hum."
        HourlyTableColumn.PRECIPITATION -> "Precip."
        HourlyTableColumn.WIND_SPEED -> "Wind"
        HourlyTableColumn.WIND_DIRECTION -> "Dir."
        HourlyTableColumn.EVAPOTRANSPIRATION -> "ET"
    }

private val HourlyTableColumn.weight: Float
    get() = when (this) {
        HourlyTableColumn.TIME -> 0.72f
        HourlyTableColumn.WEATHER_ICON -> 0.55f
        HourlyTableColumn.WEATHER_TEXT -> 1.35f
        HourlyTableColumn.TEMPERATURE -> 0.8f
        HourlyTableColumn.DEW_POINT -> 1f
        HourlyTableColumn.RELATIVE_HUMIDITY -> 0.8f
        HourlyTableColumn.PRECIPITATION -> 0.9f
        HourlyTableColumn.WIND_SPEED -> 0.95f
        HourlyTableColumn.WIND_DIRECTION -> 0.85f
        HourlyTableColumn.EVAPOTRANSPIRATION -> 0.9f
    }
