package fr.cap6.app

object PolygonMath {
    fun contains(point: GeoPoint, polygon: List<GeoPoint>): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val yi = polygon[i].latitude
            val xi = polygon[i].longitude
            val yj = polygon[j].latitude
            val xj = polygon[j].longitude
            val crosses = (yi > point.latitude) != (yj > point.latitude)
            if (crosses) {
                val longitudeAtLatitude = (xj - xi) * (point.latitude - yi) / (yj - yi) + xi
                if (point.longitude < longitudeAtLatitude) inside = !inside
            }
            j = i
        }
        return inside
    }

    fun containsAny(point: GeoPoint, polygons: List<List<GeoPoint>>): Boolean =
        polygons.any { contains(point, it) }
}
