package net.droopia.hluweather.data.model

import kotlinx.datetime.Instant

data class RouteEndpoint(val label: String, val point: GeoPoint)

data class DrivingRoute(
    val providerName: String,
    val polyline: List<GeoPoint>,
    val distanceMeters: Double,
    val providerDurationSeconds: Double
)

data class RouteWeatherSample(
    val point: GeoPoint,
    val distanceMeters: Double,
    val arrivalTime: Instant,
    val condition: WeatherCondition? = null,
    val temperatureCelsius: Double? = null,
    val windSpeedKmh: Double? = null,
    val precipitationProbability: Int? = null,
    val precipitationMm: Double? = null,
    val humidityPercent: Int? = null,
    val isDay: Boolean? = null,
    val placeLabel: String? = null,
    val elevationMeters: Double? = null
)

data class WeatherRouteResult(
    val start: RouteEndpoint,
    val end: RouteEndpoint,
    val route: DrivingRoute,
    val departure: Instant,
    val averageSpeedKmh: Int,
    val samples: List<RouteWeatherSample>
)
