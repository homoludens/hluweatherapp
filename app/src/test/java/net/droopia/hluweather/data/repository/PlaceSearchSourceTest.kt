package net.droopia.hluweather.data.repository

import kotlinx.coroutines.test.runTest
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.network.OpenMeteoGeocodingApi
import net.droopia.hluweather.data.network.OpenMeteoGeocodingResponse
import net.droopia.hluweather.data.network.OpenMeteoGeocodingResult
import net.droopia.hluweather.data.network.PhotonApi
import net.droopia.hluweather.data.network.PhotonFeature
import net.droopia.hluweather.data.network.PhotonGeometry
import net.droopia.hluweather.data.network.PhotonProperties
import net.droopia.hluweather.data.network.PhotonResponse
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceSearchSourceTest {

    @Test
    fun photon_source_returns_empty_for_blank_query_without_requesting() = runTest {
        val api = RecordingPhotonApi()

        val results = PhotonPlaceSearchSource(api).search("  ")

        assertEquals(emptyList<PlaceSearchResult>(), results)
        assertEquals(emptyList<String>(), api.queries)
    }

    @Test
    fun photon_source_trims_query_normalizes_labels_and_deduplicates_coordinates() = runTest {
        val api = RecordingPhotonApi(
            PhotonResponse(
                features = listOf(
                    feature(
                        coordinates = listOf(13.7768, 45.6495),
                        name = "Trieste",
                        city = "Trieste",
                        state = "Friuli Venezia Giulia",
                        country = "Italy"
                    ),
                    feature(
                        coordinates = listOf(13.7768, 45.6495),
                        name = "Duplicate"
                    ),
                    feature(
                        coordinates = listOf(13.8, 45.7),
                        name = "  ",
                        city = "Muggia",
                        state = "Friuli Venezia Giulia",
                        country = "Italy"
                    ),
                    feature(
                        coordinates = listOf(13.9),
                        name = "Malformed"
                    ),
                    feature(
                        coordinates = listOf(181.0, 45.0),
                        name = "Out of range"
                    )
                )
            )
        )

        val results = PhotonPlaceSearchSource(api).search("  trieste ")

        assertEquals(listOf("trieste"), api.queries)
        assertEquals(
            listOf(
                PlaceSearchResult("Trieste, Friuli Venezia Giulia, Italy", GeoPoint(45.6495, 13.7768)),
                PlaceSearchResult("Muggia, Friuli Venezia Giulia, Italy", GeoPoint(45.7, 13.8))
            ),
            results
        )
    }

    @Test
    fun open_meteo_source_returns_empty_for_blank_query_without_requesting() = runTest {
        val api = RecordingOpenMeteoGeocodingApi()

        val results = OpenMeteoPlaceSearchSource(api).search(" \n\t")

        assertEquals(emptyList<PlaceSearchResult>(), results)
        assertEquals(emptyList<String>(), api.queries)
    }

    @Test
    fun open_meteo_source_maps_labels_discards_invalid_coordinates_and_deduplicates() = runTest {
        val api = RecordingOpenMeteoGeocodingApi(
            OpenMeteoGeocodingResponse(
                results = listOf(
                    OpenMeteoGeocodingResult(
                        name = "Trieste",
                        latitude = 45.6495,
                        longitude = 13.7768,
                        country = "Italy",
                        admin1 = "Friuli Venezia Giulia"
                    ),
                    OpenMeteoGeocodingResult(
                        name = "Duplicate",
                        latitude = 45.6495,
                        longitude = 13.7768,
                        country = "Italy"
                    ),
                    OpenMeteoGeocodingResult(
                        name = "No region",
                        latitude = 45.0,
                        longitude = 13.0,
                        country = "Italy",
                        admin1 = "Italy"
                    ),
                    OpenMeteoGeocodingResult(
                        name = "Invalid",
                        latitude = 91.0,
                        longitude = 13.0,
                        country = "Italy"
                    )
                )
            )
        )

        val results = OpenMeteoPlaceSearchSource(api).search(" trieste ")

        assertEquals(listOf("trieste"), api.queries)
        assertEquals(
            listOf(
                PlaceSearchResult("Trieste, Friuli Venezia Giulia, Italy", GeoPoint(45.6495, 13.7768)),
                PlaceSearchResult("No region, Italy", GeoPoint(45.0, 13.0))
            ),
            results
        )
    }

    @Test
    fun sources_expose_their_provider() {
        assertEquals(PlaceSearchProvider.PHOTON, PhotonPlaceSearchSource(RecordingPhotonApi()).provider)
        assertEquals(
            PlaceSearchProvider.OPEN_METEO,
            OpenMeteoPlaceSearchSource(RecordingOpenMeteoGeocodingApi()).provider
        )
    }

    private fun feature(
        coordinates: List<Double>,
        name: String? = null,
        city: String? = null,
        state: String? = null,
        country: String? = null
    ) = PhotonFeature(
        geometry = PhotonGeometry(coordinates),
        properties = PhotonProperties(name, city, state, country)
    )

    private class RecordingPhotonApi(
        private val response: PhotonResponse = PhotonResponse()
    ) : PhotonApi {
        val queries = mutableListOf<String>()

        override suspend fun search(query: String): PhotonResponse {
            queries += query
            return response
        }
    }

    private class RecordingOpenMeteoGeocodingApi(
        private val response: OpenMeteoGeocodingResponse = OpenMeteoGeocodingResponse()
    ) : OpenMeteoGeocodingApi {
        val queries = mutableListOf<String>()

        override suspend fun search(query: String): OpenMeteoGeocodingResponse {
            queries += query
            return response
        }
    }
}
