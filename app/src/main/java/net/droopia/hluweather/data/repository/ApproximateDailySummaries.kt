package net.droopia.hluweather.data.repository

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import net.droopia.hluweather.data.model.DayForecast
import net.droopia.hluweather.data.model.HourForecast
import net.droopia.hluweather.data.model.WeatherCondition

/** Groups available hourly records by calendar date; totals may be partial or cross interval boundaries. */
internal fun List<HourForecast>.toApproximateDailySummaries(zone: TimeZone): List<DayForecast> =
    groupBy { it.time.toLocalDateTime(zone).date }
        .toSortedMap()
        .map { (date, hours) ->
            val condition = hours.asSequence()
                .map { it.condition }
                .filter { it != WeatherCondition.UNKNOWN }
                .groupingBy { it }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key ?: WeatherCondition.UNKNOWN
            val precipitation = hours.mapNotNull { it.precipitation }
                .takeIf { it.isNotEmpty() }
                ?.sum()

            DayForecast(
                date = date,
                condition = condition,
                temperatureMin = hours.minOf { it.temperature },
                temperatureMax = hours.maxOf { it.temperature },
                precipitation = precipitation,
                sunrise = null,
                sunset = null
            )
        }
