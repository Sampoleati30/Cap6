package fr.cap6.app

import org.junit.Assert.*
import org.junit.Test

class CoreTest {
    @Test fun sixNauticalMilesAreExact() { assertEquals(11112.0, GeoCore.nauticalMilesToMeters(6.0), 0.0) }
    @Test fun headingWrapIsStable() { assertEquals(2.0, GeoCore.shortestAngleDelta(359.0, 1.0), 0.0); assertEquals(-2.0, GeoCore.shortestAngleDelta(1.0, 359.0), 0.0) }
    @Test fun persistedRouteRoundTrips() {
        val route = PlannedRoute(listOf(GeoPoint(43.397, 3.696), GeoPoint(43.433, 3.773)),4.0,55.0,20.0,1.2,VerificationLevel.PROTOTYPE,listOf("Données de développement"))
        val restored = RoutePersistenceCodec.decode(RoutePersistenceCodec.encode("Sète", route))
        assertNotNull(restored); assertEquals("Sète", restored!!.name); assertEquals(route.points, restored.route.points); assertEquals(route.warnings, restored.route.warnings)
    }
    @Test fun invalidRoutePayloadIsRejected() { assertNull(RoutePersistenceCodec.decode("CAP6_ROUTE_V1\npoint=bad")) }
}
