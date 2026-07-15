package fr.cap6.app

import java.util.PriorityQueue
import kotlin.math.*

data class BoatProfile(
    val draftMeters: Double,
    val airDraftMeters: Double? = null,
    val cruiseSpeedKnots: Double,
    val coastalPermit: Boolean = true
)

data class HazardCircle(val id: String, val center: GeoPoint, val clearanceMeters: Double)

data class RouteConstraints(
    val landPolygons: List<List<GeoPoint>>,
    val forbiddenPolygons: List<ForbiddenZone>,
    val hazards: List<HazardCircle>,
    val coastDistanceMeters: (GeoPoint) -> Double?,
    val depthMeters: (GeoPoint) -> Double?,
    val shelterDistanceMeters: ((GeoPoint) -> Double?)? = null,
    val corridorNm: Double? = 5.5,
    val corridorCriterion: CorridorCriterion = CorridorCriterion.COASTLINE,
    val requireDepthData: Boolean = true,
    val minimumUnderKeelClearanceMeters: Double = 0.5
)

enum class CorridorCriterion { COASTLINE, ACCESSIBLE_SHELTER, NONE }

enum class RouteFailureReason {
    START_BLOCKED,
    DESTINATION_BLOCKED,
    NO_PASSAGE,
    DATA_INSUFFICIENT,
    ITERATION_LIMIT
}

data class PlannedRoute(
    val points: List<GeoPoint>,
    val distanceNm: Double,
    val initialBearingDegrees: Double,
    val estimatedDurationMinutes: Double,
    val maximumCoastDistanceNm: Double?,
    val confidence: VerificationLevel,
    val warnings: List<String>
)

sealed interface RoutePlanResult {
    data class Success(val route: PlannedRoute): RoutePlanResult
    data class Failure(val reason: RouteFailureReason, val details: List<String>): RoutePlanResult
}

class GridRoutePlanner(
    private val resolutionNm: Double = 0.35,
    private val marginNm: Double = 8.0,
    private val sampleStepMeters: Double = 120.0,
    private val maxIterations: Int = 160_000
) {
    private data class Node(val x: Int, val y: Int)
    private data class QueueNode(val node: Node, val f: Double): Comparable<QueueNode> {
        override fun compareTo(other: QueueNode): Int = f.compareTo(other.f)
    }

    fun plan(start: GeoPoint, end: GeoPoint, boat: BoatProfile, constraints: RouteConstraints): RoutePlanResult {
        val startCheck = pointBlockReasons(start, boat, constraints)
        if (startCheck.isNotEmpty()) return RoutePlanResult.Failure(RouteFailureReason.START_BLOCKED, startCheck)
        val endCheck = pointBlockReasons(end, boat, constraints)
        if (endCheck.isNotEmpty()) return RoutePlanResult.Failure(RouteFailureReason.DESTINATION_BLOCKED, endCheck)

        val directIssues = segmentBlockReasons(start, end, boat, constraints)
        if (directIssues.isEmpty()) return RoutePlanResult.Success(buildResult(listOf(start, end), boat, constraints))

        val latRef = Math.toRadians((start.latitude + end.latitude) / 2.0)
        val lonNmFactor = 60.0 * cos(latRef)
        fun toXY(p: GeoPoint): Pair<Double, Double> =
            (p.longitude - start.longitude) * lonNmFactor to (p.latitude - start.latitude) * 60.0
        fun toPoint(x: Double, y: Double): GeoPoint =
            GeoPoint(start.latitude + y / 60.0, start.longitude + x / lonNmFactor)

        val (ex, ey) = toXY(end)
        val minX = min(0.0, ex) - marginNm
        val maxX = max(0.0, ex) + marginNm
        val minY = min(0.0, ey) - marginNm
        val maxY = max(0.0, ey) + marginNm
        val nx = ceil((maxX - minX) / resolutionNm).toInt() + 1
        val ny = ceil((maxY - minY) / resolutionNm).toInt() + 1
        if (nx.toLong() * ny.toLong() > 450_000L) {
            return RoutePlanResult.Failure(RouteFailureReason.ITERATION_LIMIT, listOf("Zone de calcul trop grande pour la résolution choisie."))
        }

        fun nodePoint(n: Node): GeoPoint = toPoint(minX + n.x * resolutionNm, minY + n.y * resolutionNm)
        fun nodeFor(x: Double, y: Double) = Node(
            ((x - minX) / resolutionNm).roundToInt().coerceIn(0, nx - 1),
            ((y - minY) / resolutionNm).roundToInt().coerceIn(0, ny - 1)
        )

        val startNode = nodeFor(0.0, 0.0)
        val endNode = nodeFor(ex, ey)
        val queue = PriorityQueue<QueueNode>()
        val g = mutableMapOf(startNode to 0.0)
        val parent = mutableMapOf<Node, Node>()
        val closed = mutableSetOf<Node>()
        queue += QueueNode(startNode, 0.0)
        val directions = listOf(-1 to -1, -1 to 0, -1 to 1, 0 to -1, 0 to 1, 1 to -1, 1 to 0, 1 to 1)
        var iterations = 0
        var found = false

        while (queue.isNotEmpty() && iterations++ < maxIterations) {
            val current = queue.poll().node
            if (!closed.add(current)) continue
            if (current == endNode) { found = true; break }
            val currentPoint = nodePoint(current)
            val currentG = g.getValue(current)
            for ((dx, dy) in directions) {
                val next = Node(current.x + dx, current.y + dy)
                if (next.x !in 0 until nx || next.y !in 0 until ny || next in closed) continue
                val nextPoint = nodePoint(next)
                if (pointBlockReasons(nextPoint, boat, constraints).isNotEmpty()) continue
                if (segmentBlockReasons(currentPoint, nextPoint, boat, constraints).isNotEmpty()) continue
                val step = hypot(dx.toDouble(), dy.toDouble()) * resolutionNm
                val proximityPenalty = routePenalty(nextPoint, constraints)
                val nextG = currentG + step + proximityPenalty
                if (nextG < (g[next] ?: Double.POSITIVE_INFINITY)) {
                    g[next] = nextG
                    parent[next] = current
                    val h = hypot((next.x - endNode.x).toDouble(), (next.y - endNode.y).toDouble()) * resolutionNm
                    queue += QueueNode(next, nextG + h)
                }
            }
        }

        if (!found) {
            val reason = if (iterations >= maxIterations) RouteFailureReason.ITERATION_LIMIT else RouteFailureReason.NO_PASSAGE
            return RoutePlanResult.Failure(reason, directIssues.distinct())
        }

        val reversed = mutableListOf<GeoPoint>()
        var cursor = endNode
        while (cursor != startNode) {
            reversed += nodePoint(cursor)
            cursor = parent[cursor] ?: return RoutePlanResult.Failure(RouteFailureReason.NO_PASSAGE, listOf("Chaîne de route incomplète."))
        }
        val raw = listOf(start) + reversed.asReversed().dropLast(1) + end
        val simplified = simplify(raw, boat, constraints)
        return RoutePlanResult.Success(buildResult(simplified, boat, constraints))
    }

    private fun simplify(points: List<GeoPoint>, boat: BoatProfile, constraints: RouteConstraints): List<GeoPoint> {
        if (points.size <= 2) return points
        val result = mutableListOf(points.first())
        var i = 0
        while (i < points.lastIndex) {
            var j = points.lastIndex
            while (j > i + 1 && segmentBlockReasons(points[i], points[j], boat, constraints).isNotEmpty()) j--
            result += points[j]
            i = j
        }
        return result
    }

    private fun buildResult(points: List<GeoPoint>, boat: BoatProfile, constraints: RouteConstraints): PlannedRoute {
        var meters = 0.0
        var maxCoast: Double? = null
        val warnings = linkedSetOf<String>()
        for (i in 1 until points.size) {
            meters += GeoCore.haversineMeters(points[i - 1], points[i])
            val coast = constraints.coastDistanceMeters(points[i])
            if (coast == null) warnings += "Distance à la côte inconnue sur une partie du parcours."
            else maxCoast = maxOf(maxCoast ?: 0.0, GeoCore.metersToNauticalMiles(coast))
            if (constraints.depthMeters(points[i]) == null) warnings += "Bathymétrie absente ou incomplète."
        }
        if (constraints.corridorCriterion == CorridorCriterion.COASTLINE) {
            warnings += "Corridor contrôlé par rapport au trait de côte, pas par rapport à un abri accessible."
        }
        val distanceNm = GeoCore.metersToNauticalMiles(meters)
        return PlannedRoute(
            points = points,
            distanceNm = distanceNm,
            initialBearingDegrees = GeoCore.initialBearingDegrees(points.first(), points.last()),
            estimatedDurationMinutes = if (boat.cruiseSpeedKnots > 0) distanceNm / boat.cruiseSpeedKnots * 60.0 else Double.POSITIVE_INFINITY,
            maximumCoastDistanceNm = maxCoast,
            confidence = if (warnings.isEmpty()) VerificationLevel.OFFICIAL else VerificationLevel.PROTOTYPE,
            warnings = warnings.toList()
        )
    }

    private fun segmentBlockReasons(a: GeoPoint, b: GeoPoint, boat: BoatProfile, constraints: RouteConstraints): List<String> {
        val meters = GeoCore.haversineMeters(a, b)
        val steps = max(1, ceil(meters / sampleStepMeters).toInt())
        val reasons = linkedSetOf<String>()
        for (i in 0..steps) {
            reasons += pointBlockReasons(GeoCore.interpolate(a, b, i.toDouble() / steps), boat, constraints)
            if (reasons.size >= 4) break
        }
        return reasons.toList()
    }

    private fun pointBlockReasons(point: GeoPoint, boat: BoatProfile, constraints: RouteConstraints): List<String> {
        val reasons = mutableListOf<String>()
        if (PolygonMath.containsAny(point, constraints.landPolygons)) reasons += "Terre détectée"
        if (constraints.forbiddenPolygons.any { PolygonMath.contains(point, it.polygon) }) reasons += "Zone interdite"
        if (constraints.hazards.any { GeoCore.haversineMeters(point, it.center) < it.clearanceMeters }) reasons += "Marge de danger insuffisante"
        val depth = constraints.depthMeters(point)
        if (depth == null && constraints.requireDepthData) reasons += "Profondeur inconnue"
        if (depth != null && depth < boat.draftMeters + constraints.minimumUnderKeelClearanceMeters) reasons += "Profondeur insuffisante"
        val corridorNm = constraints.corridorNm
        if (corridorNm != null) {
            val distance = when (constraints.corridorCriterion) {
                CorridorCriterion.COASTLINE -> constraints.coastDistanceMeters(point)
                CorridorCriterion.ACCESSIBLE_SHELTER -> constraints.shelterDistanceMeters?.invoke(point)
                CorridorCriterion.NONE -> 0.0
            }
            if (distance == null) reasons += "Critère du corridor inconnu"
            else if (GeoCore.metersToNauticalMiles(distance) > corridorNm) reasons += "Corridor de ${"%.1f".format(corridorNm)} NM dépassé"
        }
        return reasons
    }

    private fun routePenalty(point: GeoPoint, constraints: RouteConstraints): Double {
        val coast = constraints.coastDistanceMeters(point) ?: return 3.0
        val coastNm = GeoCore.metersToNauticalMiles(coast)
        return when {
            coastNm < 0.15 -> 4.0
            coastNm < 0.35 -> 1.5
            else -> 0.0
        }
    }
}
