package net.droopia.hluweather.data.repository

import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import net.droopia.hluweather.data.cache.ForecastCacheKey
import net.droopia.hluweather.data.cache.ForecastCacheStore
import net.droopia.hluweather.data.model.ActiveLocation
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherProvider

class CachingWeatherRepository(
    private val sources: Map<WeatherProvider, WeatherSource>,
    private val cache: ForecastCacheStore
) : WeatherRepository {

    constructor(
        openMeteo: WeatherSource,
        metNo: WeatherSource,
        cache: ForecastCacheStore
    ) : this(
        sources = mapOf(
            WeatherProvider.OPEN_METEO to openMeteo,
            WeatherProvider.MET_NO to metNo
        ),
        cache = cache
    )

    override suspend fun getForecast(
        provider: WeatherProvider,
        location: ActiveLocation
    ): ForecastLoad {
        val source = sources[provider]
            ?: throw WeatherRepositoryException("Weather provider ${provider.title} is not supported")
        val key = ForecastCacheKey(provider, location.cacheLocationKey())
        val forecast = try {
            source.getForecast(location.toWeatherLocation())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            cache.get(key)?.let { return ForecastLoad(it, isStale = true) }
                ?: throw error
        }
        try {
            cache.put(key, forecast)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // A live result is still usable when persistence is unavailable.
        }
        return ForecastLoad(forecast, isStale = false)
    }

    override suspend fun clearCache() {
        cache.clear()
    }
}

private fun ActiveLocation.cacheLocationKey(): String = when (this) {
    is ActiveLocation.Saved -> "saved:${location.id}"
    is ActiveLocation.Current -> currentCacheLocationKey(point)
}

private fun currentCacheLocationKey(point: GeoPoint): String =
    String.format(Locale.US, "current:%.3f:%.3f", point.latitude, point.longitude)

private fun ActiveLocation.toWeatherLocation(): WeatherLocation = when (this) {
    is ActiveLocation.Saved -> location
    is ActiveLocation.Current -> WeatherLocation(
        id = "current",
        name = "Current location",
        latitude = point.latitude,
        longitude = point.longitude,
        altitude = altitude
    )
}
