package net.droopia.hluweather.ui.weather

import java.util.TimeZone
import kotlinx.datetime.Instant
import net.droopia.hluweather.data.repository.Svilajnac
import net.droopia.hluweather.data.repository.buildMockForecast
import net.droopia.hluweather.data.toAppLocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HourlyTableDataTest {

    private lateinit var forecast: net.droopia.hluweather.data.model.WeatherForecast

    @Before
    fun setDefaultTimeZoneToUtc() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        forecast = buildMockForecast(
            location = Svilajnac,
            baseTime = Instant.fromEpochSeconds(0L)
        )
    }

    @Test
    fun includes_all_days_in_source_order() {
        val table = forecast.toHourlyTableData()

        assertEquals(7 * 24, table.hourItems.size)
        assertEquals(7, table.days.size)
        assertEquals(0, table.hourItems.first().dayIndex)
        assertEquals(6, table.hourItems.last().dayIndex)
        assertTrue(table.hourItems.zipWithNext().all { (a, b) -> a.hour.time <= b.hour.time })
    }

    @Test
    fun exposes_first_item_index_for_each_day_jump() {
        val table = forecast.toHourlyTableData()

        assertEquals(0, table.firstItemIndexForDay(0))
        assertEquals(25, table.firstItemIndexForDay(1))
        assertEquals(26, table.firstHourIndexForDay(1))
        assertEquals(4, table.dayIndexForDate(table.days[4].date))
    }

    @Test
    fun includes_date_boundaries_with_stable_keys() {
        val table = forecast.toHourlyTableData()

        assertEquals(7 * 25, table.items.size)
        assertEquals("date-header-1970-01-01", table.items.first().key)
        assertEquals("hour-1970-01-01T00:00:00Z", table.items[1].key)
        assertEquals(table.items.size, table.items.map { it.key }.toSet().size)
    }

    @Test
    fun omits_days_without_hourly_entries_and_keeps_remaining_rows_ordered() {
        val missingDate = forecast.daily[2].date
        val incompleteForecast = forecast.copy(
            hourly = forecast.hourly.filter { it.time.toAppLocalDate() != missingDate }
        )

        val table = incompleteForecast.toHourlyTableData()

        assertEquals(listOf(0, 1, 3, 4, 5, 6), table.days.map { it.dayIndex })
        assertEquals(6 * 24, table.hourItems.size)
        assertNull(table.firstItemIndexForDay(2))
        assertNull(table.firstHourIndexForDay(2))
        assertNull(table.dayIndexForDate(missingDate))
        assertTrue(table.hourItems.zipWithNext().all { (a, b) -> a.hour.time <= b.hour.time })
    }

    @Test
    fun finds_nearest_available_day_for_missing_daily_index() {
        val missingDate = forecast.daily[2].date
        val incompleteForecast = forecast.copy(
            hourly = forecast.hourly.filter { it.time.toAppLocalDate() != missingDate }
        )

        val table = incompleteForecast.toHourlyTableData()

        assertEquals(1, table.nearestDayForIndex(2)?.dayIndex)
    }
}
