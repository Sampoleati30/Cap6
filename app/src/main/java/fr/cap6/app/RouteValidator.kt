package fr.cap6.app

sealed interface RouteIssue {
    data class CorridorExceeded(val distanceNm: Double, val allowedNm: Double): RouteIssue
    data class ForbiddenZoneEntered(val zoneId: String): RouteIssue
    data object CoastlineUnknown: RouteIssue
}

data class ForbiddenZone(val id: String, val polygon: List<GeoPoint>)
data class RouteValidation(val accepted: Boolean, val issues: List<RouteIssue>, val maximumCoastDistanceNm: Double?)

class RouteValidator(
    private val coastDistanceMeters: (GeoPoint) -> Double?,
    private val forbiddenZones: List<ForbiddenZone>,
    private val sampleStepMeters: Double = 50.0
) {
    fun validate(path: List<GeoPoint>, corridorNm: Double?): RouteValidation {
        require(path.size >= 2)
        val issues = mutableListOf<RouteIssue>()
        var maxCoast = 0.0
        for (i in 1 until path.size) {
            val a = path[i - 1]; val b = path[i]
            val distance = GeoCore.haversineMeters(a, b)
            val steps = maxOf(1, kotlin.math.ceil(distance / sampleStepMeters).toInt())
            for (j in 0..steps) {
                val p = GeoCore.interpolate(a, b, j.toDouble() / steps)
                val coast = coastDistanceMeters(p)
                if (coast == null) {
                    issues += RouteIssue.CoastlineUnknown
                } else {
                    val nm = GeoCore.metersToNauticalMiles(coast)
                    maxCoast = maxOf(maxCoast, nm)
                    if (corridorNm != null && nm > corridorNm) issues += RouteIssue.CorridorExceeded(nm, corridorNm)
                }
                forbiddenZones.firstOrNull { pointInPolygon(p, it.polygon) }?.let { issues += RouteIssue.ForbiddenZoneEntered(it.id) }
            }
        }
        val unique = issues.distinct()
        return RouteValidation(unique.isEmpty(), unique, maxCoast.takeIf { it > 0 })
    }

    private fun pointInPolygon(point: GeoPoint, polygon: List<GeoPoint>): Boolean {
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val yi = polygon[i].latitude; val xi = polygon[i].longitude
            val yj = polygon[j].latitude; val xj = polygon[j].longitude
            val crosses = (yi > point.latitude) != (yj > point.latitude)
            if (crosses && point.longitude < (xj - xi) * (point.latitude - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }
}
