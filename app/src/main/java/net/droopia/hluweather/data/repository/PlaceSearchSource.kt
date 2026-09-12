package net.droopia.hluweather.data.repository

import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.network.OpenMeteoGeocodingApi
import net.droopia.hluweather.data.network.PhotonApi

enum class PlaceSearchProvider { PHOTON, OPEN_METEO }

data class PlaceSearchResult(val label: String, val point: GeoPoint)

interface PlaceSearchSource {
    val provider: PlaceSearchProvider

    suspend fun search(query: String): List<PlaceSearchResult>
}

class PhotonPlaceSearchSource(
    private val api: PhotonApi
) : PlaceSearchSource {
    override val provider: PlaceSearchProvider = PlaceSearchProvider.PHOTON

    override suspend fun search(query: String): List<PlaceSearchResult> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            return emptyList()
        }

        return api.search(normalizedQuery).features.mapNotNull { feature ->
            val coordinates = feature.geometry.coordinates
            if (coordinates.size < 2) {
                return@mapNotNull null
            }

            val longitude = coordinates[0] ?: return@mapNotNull null
            val latitude = coordinates[1] ?: return@mapNotNull null
            if (!latitude.isFinite() || !longitude.isFinite() ||
                latitude !in -90.0..90.0 || longitude !in -180.0..180.0
            ) {
                return@mapNotNull null
            }

            val label = normalizedLabel(
                feature.properties.name,
                feature.properties.city,
                feature.properties.state,
                feature.properties.country
            ) ?: return@mapNotNull null

            PlaceSearchResult(label, GeoPoint(latitude, longitude))
        }.distinctBy { it.point }
    }
}

class OpenMeteoPlaceSearchSource(
    private val api: OpenMeteoGeocodingApi
) : PlaceSearchSource {
    override val provider: PlaceSearchProvider = PlaceSearchProvider.OPEN_METEO

    override suspend fun search(query: String): List<PlaceSearchResult> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            return emptyList()
        }

        return api.search(normalizedQuery).results.mapNotNull { result ->
            val latitude = result.latitude ?: return@mapNotNull null
            val longitude = result.longitude ?: return@mapNotNull null
            if (!latitude.isFinite() || !longitude.isFinite() ||
                latitude !in -90.0..90.0 || longitude !in -180.0..180.0
            ) {
                return@mapNotNull null
            }

            val label = normalizedLabel(result.name, result.admin1, result.country)
                ?: return@mapNotNull null
            PlaceSearchResult(label, GeoPoint(latitude, longitude))
        }.distinctBy { it.point }
    }
}

private fun normalizedLabel(vararg parts: String?): String? = parts
    .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
    .distinct()
    .joinToString(", ")
    .takeIf(String::isNotBlank)
