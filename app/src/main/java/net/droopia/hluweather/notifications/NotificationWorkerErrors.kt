package net.droopia.hluweather.notifications

import net.droopia.hluweather.data.repository.WeatherRepositoryException
import java.io.IOException

internal fun Throwable.isRetryableNotificationFailure(): Boolean = when (this) {
    is WeatherRepositoryException -> !message.orEmpty().isPermanentWeatherFailure()
    is IOException -> true
    else -> false
}

private fun String.isPermanentWeatherFailure(): Boolean =
    startsWith("Unable to map") ||
        startsWith("Missing") ||
        startsWith("Mismatched") ||
        startsWith("Invalid") ||
        contains("not supported") ||
        contains("returned")
