package net.droopia.hluweather.ui.weather

import kotlinx.datetime.LocalDate
import java.time.ZoneId
import net.droopia.hluweather.data.model.HourForecast
import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.toAppLocalDate
import kotlin.math.abs

data class HourlyTableData(
    val items: List<HourlyTableItem>,
    val days: List<HourlyTableDay>
) {
    val hourItems: List<HourlyTableHour>
        get() = items.filterIsInstance<HourlyTableHour>()
}

sealed interface HourlyTableItem {
    val dayIndex: Int
    val key: String
}

data class HourlyTableDay(
    val dayIndex: Int,
    val date: LocalDate,
    val firstItemIndex: Int
)

data class HourlyTableBoundary(
    override val dayIndex: Int,
    val date: LocalDate,
    override val key: String
) : HourlyTableItem

data class HourlyTableHour(
    override val dayIndex: Int,
    val date: LocalDate,
    val hour: HourForecast,
    override val key: String
) : HourlyTableItem

fun WeatherForecast.toHourlyTableData(): HourlyTableData {
    val displayZone = ZoneId.of(timezone)
    val items = buildList {
        hourly.groupBy { it.time.toAppLocalDate(displayZone) }.forEach { (date, hours) ->
            val dayIndex = daily.indexOfFirst { it.date == date }
                .takeIf { it >= 0 }
                ?: (Int.MIN_VALUE + date.toEpochDays().toInt())

            add(
                HourlyTableBoundary(
                    dayIndex = dayIndex,
                    date = date,
                    key = "date-header-$date"
                )
            )
            hours.forEach { hour ->
                add(
                    HourlyTableHour(
                        dayIndex = dayIndex,
                        date = date,
                        hour = hour,
                        key = "hour-${hour.time}"
                    )
                )
            }
        }
    }

    return HourlyTableData(
        items = items,
        days = items.filterIsInstance<HourlyTableBoundary>().map { boundary ->
            HourlyTableDay(
                dayIndex = boundary.dayIndex,
                date = boundary.date,
                firstItemIndex = items.indexOf(boundary)
            )
        }
    )
}

fun HourlyTableData.firstItemIndexForDay(dayIndex: Int): Int? =
    days.firstOrNull { it.dayIndex == dayIndex }?.firstItemIndex

fun HourlyTableData.firstHourIndexForDay(dayIndex: Int): Int? =
    items.indexOfFirst { it.dayIndex == dayIndex && it is HourlyTableHour }
        .takeIf { it >= 0 }

fun HourlyTableData.dayIndexForDate(date: LocalDate): Int? =
    days.firstOrNull { it.date == date }?.dayIndex

fun HourlyTableData.nearestDayForIndex(dayIndex: Int): HourlyTableDay? =
    days.minByOrNull { abs(it.dayIndex.toLong() - dayIndex.toLong()) }
