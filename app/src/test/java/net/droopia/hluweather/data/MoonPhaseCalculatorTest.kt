package net.droopia.hluweather.data

import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoonPhaseCalculatorTest {

    @Test
    fun known_new_moon_is_zero() {
        val newMoon = Instant.fromEpochSeconds(947_182_440L)

        assertEquals(0.0, MoonPhaseCalculator.phase(newMoon), 0.001)
    }

    @Test
    fun known_full_moon_is_about_one_half() {
        val fullMoon = Instant.fromEpochSeconds(948_472_200L)

        assertEquals(0.5, MoonPhaseCalculator.phase(fullMoon), 0.03)
    }

    @Test
    fun phase_stays_between_zero_and_one() {
        val start = Instant.fromEpochSeconds(0L)

        repeat(100) { index ->
            val instant = Instant.fromEpochSeconds(start.epochSeconds + index * 86_400L)
            val phase = MoonPhaseCalculator.phase(instant)
            assertTrue(phase >= 0.0)
            assertTrue(phase < 1.0)
        }
    }
}
