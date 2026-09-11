package net.droopia.hluweather.data.repository

import java.io.IOException
import net.droopia.hluweather.data.model.DrivingRoute
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.network.OsrmApi

interface RoutingSource {
    val providerName: String

    suspend fun route(start: GeoPoint, end: GeoPoint): DrivingRoute
}

class RoutingException(message: String) : IOException(message)

class OsrmRoutingSource(
    private val api: OsrmApi
) : RoutingSource {
    override val providerName: String = "OSRM"

    override suspend fun route(start: GeoPoint, end: GeoPoint): DrivingRoute {
        val response = api.route(start, end)
        val route = response.routes.firstOrNull()
        if (response.code != "Ok" || route == null) {
            throw RoutingException("No driving route found")
        }

        val polyline = route.geometry.coordinates.mapNotNull { coordinate ->
            if (coordinate.size < 2) {
                return@mapNotNull null
            }

            val longitude = coordinate[0]
            val latitude = coordinate[1]
            if (!longitude.isFinite() || !latitude.isFinite() ||
                longitude !in -180.0..180.0 || latitude !in -90.0..90.0
            ) {
                null
            } else {
                GeoPoint(latitude = latitude, longitude = longitude)
            }
        }
        if (polyline.size < 2) {
            throw RoutingException("No driving route found")
        }

        return DrivingRoute(
            providerName = providerName,
            polyline = polyline,
            distanceMeters = route.distance,
            providerDurationSeconds = route.duration
        )
    }
}
