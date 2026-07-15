package fr.cap6.app

import kotlin.math.*

data class GeoPoint(val latitude: Double, val longitude: Double) {
    init {
        require(latitude in -90.0..90.0)
        require(longitude in -180.0..180.0)
    }
}

data class Segment(val a: GeoPoint, val b: GeoPoint)

object GeoCore {
    const val METERS_PER_NM = 1852.0
    private const val EARTH_RADIUS_M = 6371008.8

    fun nauticalMilesToMeters(nm: Double) = nm * METERS_PER_NM
    fun metersToNauticalMiles(meters: Double) = meters / METERS_PER_NM

    fun haversineMeters(a: GeoPoint, b: GeoPoint): Double {
        val p1 = Math.toRadians(a.latitude)
        val p2 = Math.toRadians(b.latitude)
        val dp = Math.toRadians(b.latitude - a.latitude)
        val dl = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return EARTH_RADIUS_M * 2 * atan2(sqrt(h), sqrt(1 - h))
    }

    fun initialBearingDegrees(a: GeoPoint, b: GeoPoint): Double {
        val p1 = Math.toRadians(a.latitude)
        val p2 = Math.toRadians(b.latitude)
        val dl = Math.toRadians(b.longitude - a.longitude)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return normalizeDegrees(Math.toDegrees(atan2(y, x)))
    }

    fun normalizeDegrees(value: Double): Double = ((value % 360.0) + 360.0) % 360.0

    fun shortestAngleDelta(from: Double, to: Double): Double {
        var d = normalizeDegrees(to) - normalizeDegrees(from)
        if (d > 180) d -= 360.0
        if (d < -180) d += 360.0
        return d
    }

    fun pointToSegmentMeters(point: GeoPoint, segment: Segment): Double {
        val refLat = Math.toRadians(point.latitude)
        fun xy(p: GeoPoint): Pair<Double, Double> {
            val x = Math.toRadians(p.longitude - point.longitude) * EARTH_RADIUS_M * cos(refLat)
            val y = Math.toRadians(p.latitude - point.latitude) * EARTH_RADIUS_M
            return x to y
        }
        val (ax, ay) = xy(segment.a)
        val (bx, by) = xy(segment.b)
        val dx = bx - ax
        val dy = by - ay
        val denom = dx * dx + dy * dy
        val t = if (denom == 0.0) 0.0 else (-(ax * dx + ay * dy) / denom).coerceIn(0.0, 1.0)
        return hypot(ax + t * dx, ay + t * dy)
    }

    fun interpolate(a: GeoPoint, b: GeoPoint, fraction: Double): GeoPoint {
        val t = fraction.coerceIn(0.0, 1.0)
        return GeoPoint(a.latitude + (b.latitude - a.latitude) * t, a.longitude + (b.longitude - a.longitude) * t)
    }
}
