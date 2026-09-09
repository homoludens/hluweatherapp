package net.droopia.hluweather.data

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val hourFormatter =
    DateTimeFormatter.ofPattern("HH'h'", java.util.Locale.US)

private val dateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE, MMM d, yyyy • HH:mm", java.util.Locale.US)

private val dayFormatter =
    DateTimeFormatter.ofPattern("EEE, MMM d", java.util.Locale.US)

fun Double?.temperatureText(): String =
    this?.roundToInt()?.let { "$it°" } ?: "—"

fun Int?.percentText(): String =
    this?.let { "$it%" } ?: "—"

fun Double?.precipitationText(): String =
    this?.let { "${it.roundToInt()} mm" } ?: "—"

fun Instant.hourText(zone: ZoneId = ZoneId.systemDefault()): String =
    toJavaInstant().atZone(zone).format(hourFormatter)

fun Instant.dateTimeText(zone: ZoneId = ZoneId.systemDefault()): String =
    toJavaInstant().atZone(zone).format(dateTimeFormatter)

fun LocalDate.dayText(zone: ZoneId = ZoneId.systemDefault()): String =
    toJavaDate().atStartOfDay(zone).format(dayFormatter)

fun Instant.toAppLocalDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    toLocalDateTime(TimeZone.of(zone.id)).date

private fun Instant.toJavaInstant(): java.time.Instant =
    java.time.Instant.ofEpochMilli(toEpochMilliseconds())

private fun LocalDate.toJavaDate(): java.time.LocalDate =
    java.time.LocalDate.of(year, monthNumber, dayOfMonth)
