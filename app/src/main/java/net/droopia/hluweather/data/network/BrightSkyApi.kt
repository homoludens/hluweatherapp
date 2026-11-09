package net.droopia.hluweather.data.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import net.droopia.hluweather.data.model.WeatherLocation

private const val MAX_STATION_DISTANCE_METERS = 50_000

interface BrightSkyApi {
    /** Retrieves hourly records and forecasts for the seven local calendar days from today. */
    suspend fun weather(location: WeatherLocation, timeZone: TimeZone): BrightSkyWeatherResponse

    /** Retrieves Bright Sky's recent SYNOP-based current observation. */
    suspend fun currentWeather(location: WeatherLocation, timeZone: TimeZone): BrightSkyCurrentWeatherResponse
}

class BrightSkyApiException(message: String) : IOException(message)

class KtorBrightSkyApi(
    private val client: HttpClient,
    private val baseUrl: String = "https://api.brightsky.dev",
    private val clock: Clock = Clock.System,
    private val json: Json = JSON
) : BrightSkyApi {

    override suspend fun weather(
        location: WeatherLocation,
        timeZone: TimeZone
    ): BrightSkyWeatherResponse {
        val window = forecastWindow(timeZone)
        val body = request("/weather", location, timeZone) {
            parameter("date", window.first)
            parameter("last_date", window.last)
        }
        return decode<BrightSkyWeatherResponse>(body, "weather").withinStationDistanceLimit()
    }

    override suspend fun currentWeather(
        location: WeatherLocation,
        timeZone: TimeZone
    ): BrightSkyCurrentWeatherResponse {
        val body = request("/current_weather", location, timeZone)
        return decode<BrightSkyCurrentWeatherResponse>(body, "current weather").withinStationDistanceLimit()
    }

    private suspend fun request(
        path: String,
        location: WeatherLocation,
        timeZone: TimeZone,
        extraParameters: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {}
    ): String {
        if (!location.isValidForBrightSkyRequest()) {
            throw BrightSkyApiException("Invalid weather location")
        }

        val response = try {
            client.get("$baseUrl$path") {
                parameter("lat", location.latitude)
                parameter("lon", location.longitude)
                parameter("max_dist", MAX_STATION_DISTANCE_METERS)
                parameter("tz", timeZone.id)
                parameter("units", "dwd")
                extraParameters()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            throw BrightSkyApiException("Weather request failed")
        }

        if (response.status.value !in 200..299) {
            throw BrightSkyApiException("Weather service returned HTTP ${response.status.value}")
        }

        return try {
            response.body<ByteArray>().decodeToString()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            throw BrightSkyApiException("Unable to decode weather response")
        }
    }

    private inline fun <reified T> decode(body: String, responseName: String): T = try {
        json.decodeFromString<T>(body)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        throw BrightSkyApiException("Unable to decode $responseName response")
    }

    private fun forecastWindow(timeZone: TimeZone): ForecastWindow {
        val today = clock.now().toLocalDateTime(timeZone).date
        val start = today.atStartOfDayIn(timeZone)
        val endExclusive = today.plus(FORECAST_DAYS, DateTimeUnit.DAY).atStartOfDayIn(timeZone)
        return ForecastWindow(start.toString(), (endExclusive - 1.seconds).toString())
    }

    private data class ForecastWindow(val first: String, val last: String)

    private companion object {
        const val FORECAST_DAYS = 7
        val JSON = Json { ignoreUnknownKeys = true }
    }
}

private fun BrightSkyWeatherResponse.withinStationDistanceLimit(): BrightSkyWeatherResponse {
    val sourcesById = sources.groupBy { it.id }
    return copy(
        weather = weather.filter { record ->
            record.sourceId.isWithinStationDistanceLimit(sourcesById)
        }
    )
}

private fun BrightSkyCurrentWeatherResponse.withinStationDistanceLimit(): BrightSkyCurrentWeatherResponse {
    val sourcesById = sources.groupBy { it.id }
    return copy(
        weather = weather?.takeIf { record ->
            record.sourceId.isWithinStationDistanceLimit(sourcesById)
        }
    )
}

private fun Int?.isWithinStationDistanceLimit(sourcesById: Map<Int?, List<BrightSkySource>>): Boolean {
    val source = this?.let(sourcesById::get)?.singleOrNull() ?: return false
    return source.distance?.let { it in 0..MAX_STATION_DISTANCE_METERS } == true
}

private fun WeatherLocation.isValidForBrightSkyRequest(): Boolean =
    latitude.isFinite() && latitude in -90.0..90.0 &&
        longitude.isFinite() && longitude in -180.0..180.0
