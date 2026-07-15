package fr.cap6.app

import kotlin.math.floor

class SegmentGridIndex(private val cellDegrees: Double = 0.05) {
    private val cells = mutableMapOf<Pair<Int, Int>, MutableList<Segment>>()

    fun insert(segment: Segment) {
        val minLat = minOf(segment.a.latitude, segment.b.latitude)
        val maxLat = maxOf(segment.a.latitude, segment.b.latitude)
        val minLon = minOf(segment.a.longitude, segment.b.longitude)
        val maxLon = maxOf(segment.a.longitude, segment.b.longitude)
        for (y in key(minLat)..key(maxLat)) for (x in key(minLon)..key(maxLon)) {
            cells.getOrPut(y to x) { mutableListOf() }.add(segment)
        }
    }

    fun nearestDistanceMeters(point: GeoPoint, maximumSearchRings: Int = 20): Double? {
        val cy = key(point.latitude); val cx = key(point.longitude)
        var best = Double.POSITIVE_INFINITY
        for (ring in 0..maximumSearchRings) {
            var found = false
            for (y in cy-ring..cy+ring) for (x in cx-ring..cx+ring) {
                if (ring > 0 && y in (cy-ring+1)..(cy+ring-1) && x in (cx-ring+1)..(cx+ring-1)) continue
                for (segment in cells[y to x].orEmpty()) {
                    found = true
                    best = minOf(best, GeoCore.pointToSegmentMeters(point, segment))
                }
            }
            if (found && best < (ring + 1) * cellDegrees * 111_320.0) return best
        }
        return best.takeIf { it.isFinite() }
    }

    private fun key(v: Double) = floor(v / cellDegrees).toInt()
}
