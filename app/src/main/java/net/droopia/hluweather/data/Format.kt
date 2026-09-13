package net.droopia.hluweather.data

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import net.droopia.hluweather.ui.settings.DistanceUnit
import net.droopia.hluweather.ui.settings.PrecipitationUnit
import net.droopia.hluweather.ui.settings.TemperatureUnit
import net.droopia.hluweather.ui.settings.WindUnit

private val hourFormatter =
    DateTimeFormatter.ofPattern("HH'h'", java.util.Locale.US)

private val dateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE, MMM d, yyyy • HH:mm", java.util.Locale.US)

private val dayFormatter =
    DateTimeFormatter.ofPattern("EEE, MMM d", java.util.Locale.US)

fun Double?.temperatureText(): String =
    this?.roundToInt()?.let { "$it°" } ?: "—"

fun Double?.temperatureText(unit: TemperatureUnit): String =
    this?.let {
        "${it.convertedTemperature(unit).roundToInt()} degrees ${unit.symbol}"
    } ?: "—"

fun Double?.temperatureValueText(unit: TemperatureUnit): String =
    this?.let { "${it.convertedTemperature(unit).roundToInt()}°${unit.symbol}" } ?: "—"

fun Int?.percentText(): String =
    this?.let { "$it%" } ?: "—"

fun Double?.precipitationText(): String =
    this?.let { "${it.roundToInt()} mm" } ?: "—"

fun Double?.precipitationText(unit: PrecipitationUnit): String =
    this?.let {
        val precipitation = if (unit == PrecipitationUnit.INCH) it / 25.4 else it
        "${precipitation.decimalText()} ${unit.symbol}"
    } ?: "—"

fun Double?.windSpeedText(unit: WindUnit): String =
    this?.let {
        val speed = if (unit == WindUnit.MPH) it * 0.621371 else it
        "${speed.decimalText()} ${unit.symbol}"
    } ?: "—"

fun Double?.windDirectionText(): String =
    this?.let { "${it.roundToInt()}°" } ?: "—"

fun Double?.distanceText(unit: DistanceUnit): String =
    this?.let {
        val distance = if (unit == DistanceUnit.MILES) it * 0.621371 else it
        "${distance.decimalText()} ${unit.symbol}"
    } ?: "—"

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

private fun Double.decimalText(): String =
    String.format(Locale.US, "%.2f", this)
        .trimEnd('0')
        .trimEnd('.')

private fun Double.convertedTemperature(unit: TemperatureUnit): Double =
    if (unit == TemperatureUnit.FAHRENHEIT) this * 9 / 5 + 32 else this

private val TemperatureUnit.symbol: String
    get() = if (this == TemperatureUnit.FAHRENHEIT) "F" else "C"

private val PrecipitationUnit.symbol: String
    get() = if (this == PrecipitationUnit.INCH) "in" else "mm"

private val WindUnit.symbol: String
    get() = if (this == WindUnit.MPH) "mph" else "km/h"

private val DistanceUnit.symbol: String
    get() = if (this == DistanceUnit.MILES) "mi" else "km"
