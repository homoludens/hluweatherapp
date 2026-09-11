package net.droopia.hluweather.data.repository

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.runBlocking
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.network.OsrmApi
import net.droopia.hluweather.data.network.OsrmGeometry
import net.droopia.hluweather.data.network.OsrmResponse
import net.droopia.hluweather.data.network.OsrmRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.function.ThrowingRunnable

class OsrmRoutingSourceTest {

    @Test
    fun route_maps_osrm_longitude_latitude_pairs_and_summary_fields() = runTest {
        val source = OsrmRoutingSource(
            FakeOsrmApi(
                OsrmResponse(
                    code = "Ok",
                    routes = listOf(
                        OsrmRoute(
                            distance = 12345.6,
                            duration = 789.0,
                            geometry = OsrmGeometry(
                                coordinates = listOf(
                                    listOf(20.4, 44.8),
                                    listOf(17.0, 45.2),
                                    listOf(13.7, 45.6)
                                )
                            )
                        )
                    )
                )
            )
        )

        val route = source.route(GeoPoint(44.8, 20.4), GeoPoint(45.6, 13.7))

        assertEquals("OSRM", source.providerName)
        assertEquals(
            listOf(GeoPoint(44.8, 20.4), GeoPoint(45.2, 17.0), GeoPoint(45.6, 13.7)),
            route.polyline
        )
        assertEquals(12345.6, route.distanceMeters, 0.0)
        assertEquals(789.0, route.providerDurationSeconds, 0.0)
    }

    @Test
    fun route_rejects_an_osrm_no_route_response() = runTest {
        val source = OsrmRoutingSource(FakeOsrmApi(OsrmResponse(code = "NoRoute")))

        val exception = assertThrows(RoutingException::class.java, ThrowingRunnable {
            runBlocking {
                source.route(GeoPoint(44.8, 20.4), GeoPoint(45.6, 13.7))
            }
        })

        assertEquals("No driving route found", exception.message)
    }

    @Test
    fun route_rejects_a_route_with_malformed_geometry() = runTest {
        val source = OsrmRoutingSource(
            FakeOsrmApi(
                OsrmResponse(
                    code = "Ok",
                    routes = listOf(
                        OsrmRoute(
                            distance = 12345.6,
                            duration = 789.0,
                            geometry = OsrmGeometry(coordinates = listOf(listOf(20.4, 44.8)))
                        )
                    )
                )
            )
        )

        val exception = assertThrows(RoutingException::class.java, ThrowingRunnable {
            runBlocking {
                source.route(GeoPoint(44.8, 20.4), GeoPoint(45.6, 13.7))
            }
        })

        assertEquals("No driving route found", exception.message)
    }

    private class FakeOsrmApi(private val response: OsrmResponse) : OsrmApi {
        override suspend fun route(start: GeoPoint, end: GeoPoint): OsrmResponse = response
    }
}
