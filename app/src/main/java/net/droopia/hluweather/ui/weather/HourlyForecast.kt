package net.droopia.hluweather.ui.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.ZoneId
import kotlinx.datetime.LocalDate
import net.droopia.hluweather.data.dayText
import net.droopia.hluweather.data.hourText
import net.droopia.hluweather.data.model.HourForecast
import net.droopia.hluweather.data.model.label
import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.percentText
import net.droopia.hluweather.data.precipitationText
import net.droopia.hluweather.data.temperatureValueText
import net.droopia.hluweather.ui.components.HluWeatherIcon
import net.droopia.hluweather.ui.settings.PrecipitationUnit
import net.droopia.hluweather.ui.settings.TemperatureUnit
import net.droopia.hluweather.ui.theme.LocalHluColors

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun HourlyForecast(
    forecast: WeatherForecast,
    selectedDayIndex: Int,
    onDaySelected: (Int) -> Unit,
    temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    precipitationUnit: PrecipitationUnit = PrecipitationUnit.MM,
    modifier: Modifier = Modifier
) {
    val displayZone = ZoneId.of(forecast.timezone)
    val tableData = remember(forecast) {
        forecast.toHourlyTableData()
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
        ForecastCell("Time", 0.72f)
        ForecastCell("Weather", 1.85f)
        ForecastCell("Temp.", 0.8f)
        ForecastCell("Dew point", 1f)
        ForecastCell("Hum.", 0.8f)
        ForecastCell("Precip.", 0.9f)
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
                .weight(0.72f)
                .then(timeTestTag?.let(Modifier::testTag) ?: Modifier),
            fontWeight = FontWeight.SemiBold
        )

        Row(
            modifier = Modifier.weight(1.85f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HluWeatherIcon(
                condition = weather.condition,
                isDay = weather.isDay,
                modifier = Modifier.width(24.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = weather.condition.label(),
                maxLines = 1
            )
        }

        Text(
            text = weather.temperature.temperatureValueText(temperatureUnit),
            modifier = Modifier.weight(0.8f),
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = weather.dewPoint.temperatureValueText(temperatureUnit),
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = weather.humidity.percentText(),
            modifier = Modifier.weight(0.8f)
        )

        Text(
            text = weather.precipitation.precipitationText(precipitationUnit),
            modifier = Modifier.weight(0.9f)
        )
    }
}
