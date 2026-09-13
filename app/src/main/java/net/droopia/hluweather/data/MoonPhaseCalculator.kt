package net.droopia.hluweather.data

import kotlin.time.Instant

object MoonPhaseCalculator {

    private val NEW_MOON = Instant.fromEpochSeconds(947_182_440L)
    private const val SYNODIC_MONTH_SECONDS = 29.53058867 * 86_400.0

    fun phase(now: Instant): Double {
        val deltaSeconds =
            (now.toEpochMilliseconds() - NEW_MOON.toEpochMilliseconds()) / 1000.0
        val shifted = (deltaSeconds % SYNODIC_MONTH_SECONDS + SYNODIC_MONTH_SECONDS) %
            SYNODIC_MONTH_SECONDS
        return shifted / SYNODIC_MONTH_SECONDS
    }
}
