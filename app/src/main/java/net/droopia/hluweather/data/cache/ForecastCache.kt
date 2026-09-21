package net.droopia.hluweather.data.cache

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.droopia.hluweather.data.model.CurrentWeather
import net.droopia.hluweather.data.model.DayForecast
import net.droopia.hluweather.data.model.HourForecast
import net.droopia.hluweather.data.model.WeatherCondition
import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherProvider
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

data class ForecastCacheKey(
    val provider: WeatherProvider,
    val locationKey: String
)

interface ForecastCacheStore {
    suspend fun get(key: ForecastCacheKey): WeatherForecast?
    suspend fun put(key: ForecastCacheKey, forecast: WeatherForecast)
    suspend fun entries(): List<ForecastCacheKey>
    suspend fun clear()
}

class ForecastCache(
    private val dataStore: DataStore<Preferences>,
    private val maxEntries: Int = 20
) : ForecastCacheStore {
    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
    }

    override suspend fun get(key: ForecastCacheKey): WeatherForecast? {
        var result: WeatherForecast? = null
        dataStore.edit { preferences ->
            val entries = preferences.decodeEntries()
            val index = entries.indexOfFirst { it.key == key }
            if (index >= 0) {
                result = entries[index].forecast.toWeatherForecastOrNull()
                if (result != null && index != entries.lastIndex) {
                    preferences[entriesKey] = json.encodeToString(
                        entries.toMutableList().apply {
                            add(removeAt(index))
                        }
                    )
                }
            }
        }
        return result
    }

    override suspend fun put(key: ForecastCacheKey, forecast: WeatherForecast) {
        dataStore.edit { preferences ->
            val entries = preferences.decodeEntries()
                .filterNot { it.key == key }
                .toMutableList()
                .apply { add(CacheEntry(key, forecast.toDto())) }
            while (entries.size > maxEntries) entries.removeAt(0)
            preferences[entriesKey] = json.encodeToString(entries)
        }
    }

    override suspend fun entries(): List<ForecastCacheKey> = dataStore.data.first()
        .decodeEntries()
        .mapNotNull(CacheEntry::keyOrNull)

    override suspend fun clear() {
        dataStore.edit { it.remove(entriesKey) }
    }

    private fun Preferences.decodeEntries(): List<CacheEntry> = this[entriesKey]
        ?.let { serialized -> runCatching { json.decodeFromString<List<CacheEntry>>(serialized) }.getOrDefault(emptyList()) }
        ?: emptyList()

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        val entriesKey = stringPreferencesKey("weather.forecast_cache")
    }
}

@Serializable
private data class CacheEntry(
    val provider: String,
    val locationKey: String,
    val forecast: ForecastDto
) {
    constructor(key: ForecastCacheKey, forecast: ForecastDto) : this(
        provider = key.provider.name,
        locationKey = key.locationKey,
        forecast = forecast
    )

    val key: ForecastCacheKey?
        get() = provider.toWeatherProvider()?.let { ForecastCacheKey(it, locationKey) }

    fun keyOrNull(): ForecastCacheKey? = key
}

@Serializable
private data class ForecastDto(
    val location: LocationDto,
    val provider: String,
    val fetchedAt: String,
    val current: CurrentDto,
    val hourly: List<HourDto>,
    val daily: List<DayDto>,
    val moonPhase: Double,
    val timezone: String
) {
    fun toWeatherForecastOrNull(): WeatherForecast? = runCatching {
        WeatherForecast(
            location = location.toWeatherLocation(),
            provider = provider.toWeatherProvider() ?: error("Unknown provider"),
            fetchedAt = Instant.parse(fetchedAt),
            current = current.toCurrentWeather(),
            hourly = hourly.map(HourDto::toHourForecast),
            daily = daily.map(DayDto::toDayForecast),
            moonPhase = moonPhase,
            timezone = timezone
        )
    }.getOrNull()
}

@Serializable
private data class LocationDto(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Int?
) {
    fun toWeatherLocation() = WeatherLocation(id, name, latitude, longitude, altitude)
}

@Serializable
private data class CurrentDto(
    val temperature: Double,
    val apparentTemperature: Double?,
    val humidity: Int?,
    val dewPoint: Double?,
    val precipitation: Double?,
    val condition: String,
    val isDay: Boolean?,
    val europeanAqi: Double? = null,
    val pm10: Double? = null,
    val pm2_5: Double? = null
) {
    fun toCurrentWeather() = CurrentWeather(
        temperature,
        apparentTemperature,
        humidity,
        dewPoint,
        precipitation,
        condition.toWeatherCondition(),
        isDay,
        europeanAqi,
        pm10,
        pm2_5
    )
}

@Serializable
private data class HourDto(
    val time: String,
    val temperature: Double,
    val apparentTemperature: Double?,
    val humidity: Int?,
    val dewPoint: Double?,
    val precipitation: Double?,
    val precipitationProbability: Int?,
    val condition: String,
    val isDay: Boolean?,
    val windSpeedKmh: Double? = null,
    val windDirectionDegrees: Double? = null,
    val evapotranspiration: Double? = null,
    val europeanAqi: Double? = null,
    val pm10: Double? = null,
    val pm2_5: Double? = null
) {
    fun toHourForecast() = HourForecast(
        time = Instant.parse(time),
        temperature = temperature,
        apparentTemperature = apparentTemperature,
        humidity = humidity,
        dewPoint = dewPoint,
        precipitation = precipitation,
        precipitationProbability = precipitationProbability,
        condition = condition.toWeatherCondition(),
        isDay = isDay,
        windSpeedKmh = windSpeedKmh,
        windDirectionDegrees = windDirectionDegrees,
        evapotranspiration = evapotranspiration,
        europeanAqi = europeanAqi,
        pm10 = pm10,
        pm2_5 = pm2_5
    )
}

@Serializable
private data class DayDto(
    val date: String,
    val condition: String,
    val temperatureMin: Double,
    val temperatureMax: Double,
    val precipitation: Double?,
    val sunrise: String?,
    val sunset: String?
) {
    fun toDayForecast() = DayForecast(
        date = LocalDate.parse(date),
        condition = condition.toWeatherCondition(),
        temperatureMin = temperatureMin,
        temperatureMax = temperatureMax,
        precipitation = precipitation,
        sunrise = sunrise?.let(Instant::parse),
        sunset = sunset?.let(Instant::parse)
    )
}

private fun WeatherForecast.toDto() = ForecastDto(
    location = LocationDto(
        location.id,
        location.name,
        location.latitude,
        location.longitude,
        location.altitude
    ),
    provider = provider.name,
    fetchedAt = fetchedAt.toString(),
    current = CurrentDto(
        current.temperature,
        current.apparentTemperature,
        current.humidity,
        current.dewPoint,
        current.precipitation,
        current.condition.name,
        current.isDay,
        current.europeanAqi,
        current.pm10,
        current.pm2_5
    ),
    hourly = hourly.map {
        HourDto(
            it.time.toString(),
            it.temperature,
            it.apparentTemperature,
            it.humidity,
            it.dewPoint,
            it.precipitation,
            it.precipitationProbability,
            it.condition.name,
            it.isDay,
            it.windSpeedKmh,
            it.windDirectionDegrees,
            it.evapotranspiration,
            it.europeanAqi,
            it.pm10,
            it.pm2_5
        )
    },
    daily = daily.map {
        DayDto(
            it.date.toString(),
            it.condition.name,
            it.temperatureMin,
            it.temperatureMax,
            it.precipitation,
            it.sunrise?.toString(),
            it.sunset?.toString()
        )
    },
    moonPhase = moonPhase,
    timezone = timezone
)

private fun String.toWeatherProvider(): WeatherProvider? =
    WeatherProvider.entries.firstOrNull { it.name == this }

private fun String.toWeatherCondition(): WeatherCondition =
    WeatherCondition.entries.firstOrNull { it.name == this } ?: WeatherCondition.UNKNOWN
