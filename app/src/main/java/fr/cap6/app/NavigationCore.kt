package fr.cap6.app

import kotlin.math.*

enum class NavigationAlert {
    OFF_ROUTE,
    APPROACHING_WAYPOINT,
    ARRIVED,
    GPS_ACCURACY_LOW,
    CORRIDOR_EXCEEDED
}

data class NavigationProgress(
    val activeSegmentIndex: Int,
    val nextWaypointIndex: Int,
    val crossTrackErrorNm: Double,
    val distanceToNextWaypointNm: Double,
    val distanceRemainingNm: Double,
    val bearingToNextWaypointDegrees: Double,
    val etaEpochMillis: Long?,
    val alerts: Set<NavigationAlert>
)

object RouteFollower {
    fun evaluate(
        position: GeoPoint,
        route: List<GeoPoint>,
        speedKnots: Double,
        nowEpochMillis: Long,
        gpsAccuracyMeters: Double? = null,
        coastDistanceMeters: Double? = null,
        corridorNm: Double? = null,
        deviationAlertNm: Double = 0.15,
        waypointApproachNm: Double = 0.12,
        arrivalNm: Double = 0.05,
        fallbackSpeedKnots: Double? = null
    ): NavigationProgress? {
        if (route.size < 2) return null

        var bestSegmentIndex = 0
        var bestProjection = Projection(route[0], 0.0, Double.POSITIVE_INFINITY)
        for (i in 0 until route.lastIndex) {
            val projection = project(position, route[i], route[i + 1])
            if (projection.distanceMeters < bestProjection.distanceMeters) {
                bestProjection = projection
                bestSegmentIndex = i
            }
        }

        var nextWaypointIndex = bestSegmentIndex + 1
        var distanceToNextMeters = GeoCore.haversineMeters(position, route[nextWaypointIndex])
        if (distanceToNextMeters <= GeoCore.nauticalMilesToMeters(arrivalNm) && nextWaypointIndex < route.lastIndex) {
            nextWaypointIndex += 1
            distanceToNextMeters = GeoCore.haversineMeters(position, route[nextWaypointIndex])
        }

        var remainingMeters = if (nextWaypointIndex == bestSegmentIndex + 1) {
            val segmentMeters = GeoCore.haversineMeters(route[bestSegmentIndex], route[bestSegmentIndex + 1])
            segmentMeters * (1.0 - bestProjection.fraction)
        } else {
            distanceToNextMeters
        }
        for (i in nextWaypointIndex until route.lastIndex) {
            remainingMeters += GeoCore.haversineMeters(route[i], route[i + 1])
        }

        val xteNm = GeoCore.metersToNauticalMiles(bestProjection.distanceMeters)
        val remainingNm = GeoCore.metersToNauticalMiles(remainingMeters)
        val nextNm = GeoCore.metersToNauticalMiles(distanceToNextMeters)
        val effectiveSpeed = when {
            speedKnots >= 0.5 -> speedKnots
            fallbackSpeedKnots != null && fallbackSpeedKnots > 0 -> fallbackSpeedKnots
            else -> null
        }
        val eta = effectiveSpeed?.let { nowEpochMillis + (remainingNm / it * 3_600_000.0).roundToLong() }
        val alerts = linkedSetOf<NavigationAlert>()
        if (xteNm > deviationAlertNm) alerts += NavigationAlert.OFF_ROUTE
        if (nextNm <= waypointApproachNm && nextWaypointIndex < route.lastIndex) alerts += NavigationAlert.APPROACHING_WAYPOINT
        if (remainingNm <= arrivalNm && nextWaypointIndex == route.lastIndex) alerts += NavigationAlert.ARRIVED
        if (gpsAccuracyMeters != null && gpsAccuracyMeters > 30.0) alerts += NavigationAlert.GPS_ACCURACY_LOW
        if (corridorNm != null && coastDistanceMeters != null && GeoCore.metersToNauticalMiles(coastDistanceMeters) > corridorNm) {
            alerts += NavigationAlert.CORRIDOR_EXCEEDED
        }

        return NavigationProgress(
            activeSegmentIndex = bestSegmentIndex,
            nextWaypointIndex = nextWaypointIndex,
            crossTrackErrorNm = xteNm,
            distanceToNextWaypointNm = nextNm,
            distanceRemainingNm = remainingNm,
            bearingToNextWaypointDegrees = GeoCore.initialBearingDegrees(position, route[nextWaypointIndex]),
            etaEpochMillis = eta,
            alerts = alerts
        )
    }

    private data class Projection(val point: GeoPoint, val fraction: Double, val distanceMeters: Double)

    private fun project(point: GeoPoint, a: GeoPoint, b: GeoPoint): Projection {
        val earthRadius = 6_371_008.8
        val refLat = Math.toRadians(point.latitude)
        fun xy(p: GeoPoint): Pair<Double, Double> {
            val x = Math.toRadians(p.longitude - point.longitude) * earthRadius * cos(refLat)
            val y = Math.toRadians(p.latitude - point.latitude) * earthRadius
            return x to y
        }
        val (ax, ay) = xy(a)
        val (bx, by) = xy(b)
        val dx = bx - ax
        val dy = by - ay
        val denominator = dx * dx + dy * dy
        val fraction = if (denominator == 0.0) 0.0 else (-(ax * dx + ay * dy) / denominator).coerceIn(0.0, 1.0)
        val projected = GeoCore.interpolate(a, b, fraction)
        val distance = hypot(ax + fraction * dx, ay + fraction * dy)
        return Projection(projected, fraction, distance)
    }
}
