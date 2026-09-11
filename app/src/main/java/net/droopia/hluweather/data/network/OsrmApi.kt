package net.droopia.hluweather.data.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import java.io.IOException
import kotlinx.serialization.Serializable
import net.droopia.hluweather.data.model.GeoPoint

interface OsrmApi {
    suspend fun route(start: GeoPoint, end: GeoPoint): OsrmResponse
}

class OsrmApiException(message: String) : IOException(message)

@Serializable
data class OsrmResponse(
    val code: String,
    val message: String? = null,
    val routes: List<OsrmRoute> = emptyList()
)

@Serializable
data class OsrmRoute(
    val distance: Double,
    val duration: Double,
    val geometry: OsrmGeometry
)

@Serializable
data class OsrmGeometry(
    val coordinates: List<List<Double>>
)

class KtorOsrmApi(
    private val client: HttpClient,
    baseUrl: String = "https://router.project-osrm.org"
) : OsrmApi {

    private val baseUrl = baseUrl.trimEnd('/')

    override suspend fun route(start: GeoPoint, end: GeoPoint): OsrmResponse {
        val response = client.get(
            "$baseUrl/route/v1/driving/" +
                "${start.longitude},${start.latitude};${end.longitude},${end.latitude}"
        ) {
            parameter("geometries", "geojson")
            parameter("overview", "full")
            parameter("steps", false)
        }

        if (response.status.value !in 200..299) {
            throw OsrmApiException("Routing service returned HTTP ${response.status.value}")
        }

        return response.body()
    }
}
