package net.droopia.hluweather.data.repository

import java.util.Locale
import net.droopia.hluweather.data.model.GeoPoint

/** Matches the coordinate rounding used by the current-location forecast cache key. */
internal fun GeoPoint.currentLocationCacheKey(): String =
    String.format(Locale.US, "current:%.3f:%.3f", latitude, longitude)
