package net.droopia.hluweather.data.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.zip.GZIPInputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import net.droopia.hluweather.data.model.WeatherLocation

interface VisualCrossingApi {
    suspend fun forecast(location: WeatherLocation): VisualCrossingTimelineResponse

    /** Optional air-quality products must not make an otherwise valid weather forecast fail. */
    suspend fun airQuality(location: WeatherLocation): VisualCrossingTimelineResponse? = null
}

class VisualCrossingApiException(message: String) : IOException(message)

class KtorVisualCrossingApi(
    private val client: HttpClient,
    private val baseUrl: String = "https://weather.visualcrossing.com/VisualCrossingWebServices/rest/services",
    private val clock: Clock = Clock.System,
    private val json: Json = JSON
) : VisualCrossingApi {

    override suspend fun forecast(location: WeatherLocation): VisualCrossingTimelineResponse {
        if (!location.isValidForWeatherRequest()) {
            throw VisualCrossingApiException("Invalid weather location")
        }

        val response = try {
            client.get(timelinePath(location)) {
                parameter("unitGroup", "metric")
                parameter("include", "days,hours,current")
                parameter("elements", ELEMENTS)
                parameter("contentType", "json")
                // OkHttp only transparently decodes gzip when it adds this header itself.
                // Since the anonymous endpoint requires explicit negotiation, decode it below.
                header(HttpHeaders.AcceptEncoding, "gzip")
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            throw VisualCrossingApiException("Weather request failed")
        }

        if (response.status.value !in 200..299) {
            throw VisualCrossingApiException("Weather service returned HTTP ${response.status.value}")
        }

        return try {
            val bytes = response.body<ByteArray>()
            val decodedBytes = when (response.headers[HttpHeaders.ContentEncoding]?.trim()?.lowercase()) {
                null, "", "identity" -> bytes
                "gzip" -> GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
                else -> throw VisualCrossingApiException("Unsupported weather response encoding")
            }
            json.decodeFromString(decodedBytes.decodeToString())
        } catch (error: CancellationException) {
            throw error
        } catch (error: VisualCrossingApiException) {
            throw error
        } catch (_: Throwable) {
            throw VisualCrossingApiException("Unable to decode weather response")
        }
    }

    override suspend fun airQuality(location: WeatherLocation): VisualCrossingTimelineResponse? {
        if (!location.isValidForWeatherRequest()) return null

        val response = try {
            client.get(timelinePath(location)) {
                parameter("unitGroup", "metric")
                parameter("include", "days,hours")
                parameter("elements", AIR_QUALITY_ELEMENTS)
                parameter("contentType", "json")
                header(HttpHeaders.AcceptEncoding, "gzip")
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            return null
        }

        // AQI and particulate matter are optional Timeline products. Authorization,
        // rate-limit, server, and other product failures must not discard core weather.
        if (response.status.value !in 200..299) return null

        return try {
            val bytes = response.body<ByteArray>()
            val decodedBytes = when (response.headers[HttpHeaders.ContentEncoding]?.trim()?.lowercase()) {
                null, "", "identity" -> bytes
                "gzip" -> GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
                else -> return null
            }
            json.decodeFromString(decodedBytes.decodeToString())
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            null
        }
    }

    private fun timelinePath(location: WeatherLocation): String {
        val utcDate = clock.now().toLocalDateTime(TimeZone.UTC).date
        // Timeline dates are civil dates at the location. The one-day past and
        // seven-day future buffers cover the largest UTC offsets; the mapper trims
        // the result to the seven local forecast dates.
        val startDate = utcDate.minus(1, DateTimeUnit.DAY)
        val endDate = utcDate.plus(7, DateTimeUnit.DAY)
        return "$baseUrl/timeline/${location.latitude},${location.longitude}/$startDate/$endDate"
    }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }

        // Keep the anonymous weather request to core fields; et0 is restricted and
        // AQ fields are optional products requested separately below.
        const val ELEMENTS =
            "datetime,datetimeEpoch,temp,tempmin,tempmax,feelslike,humidity,dew,precip,precipprob," +
                "windspeed,winddir,conditions,icon,sunrise,sunriseEpoch,sunset,sunsetEpoch,moonphase"

        const val AIR_QUALITY_ELEMENTS = "datetime,datetimeEpoch,aqieur,pm2p5,pm10"
    }
}

private fun WeatherLocation.isValidForWeatherRequest(): Boolean =
    latitude.isFinite() && latitude in -90.0..90.0 &&
        longitude.isFinite() && longitude in -180.0..180.0
