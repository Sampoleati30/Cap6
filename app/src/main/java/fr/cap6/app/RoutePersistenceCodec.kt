package fr.cap6.app

import java.nio.charset.StandardCharsets
import java.util.Base64

data class PersistedRoute(val name: String, val route: PlannedRoute)

object RoutePersistenceCodec {
    private const val HEADER = "CAP6_ROUTE_V1"

    fun encode(name: String, route: PlannedRoute): String = buildString {
        appendLine(HEADER)
        appendLine("name=${encodeText(name)}")
        appendLine("distanceNm=${route.distanceNm}")
        appendLine("initialBearing=${route.initialBearingDegrees}")
        appendLine("durationMinutes=${route.estimatedDurationMinutes}")
        appendLine("maximumCoastDistanceNm=${route.maximumCoastDistanceNm?.toString() ?: ""}")
        appendLine("confidence=${route.confidence.name}")
        route.warnings.forEach { appendLine("warning=${encodeText(it)}") }
        route.points.forEach { appendLine("point=${it.latitude},${it.longitude}") }
    }

    fun decode(payload: String): PersistedRoute? {
        val lines = payload.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        if (lines.firstOrNull() != HEADER) return null
        var name = "Route"
        var distanceNm: Double? = null
        var initialBearing: Double? = null
        var durationMinutes: Double? = null
        var maximumCoastDistanceNm: Double? = null
        var confidence = VerificationLevel.UNKNOWN
        val warnings = mutableListOf<String>()
        val points = mutableListOf<GeoPoint>()

        for (line in lines.drop(1)) {
            val key = line.substringBefore('=', "")
            val value = line.substringAfter('=', "")
            when (key) {
                "name" -> name = decodeText(value) ?: return null
                "distanceNm" -> distanceNm = value.toDoubleOrNull() ?: return null
                "initialBearing" -> initialBearing = value.toDoubleOrNull() ?: return null
                "durationMinutes" -> durationMinutes = value.toDoubleOrNull() ?: return null
                "maximumCoastDistanceNm" -> maximumCoastDistanceNm = value.takeIf(String::isNotBlank)?.toDoubleOrNull()
                "confidence" -> confidence = runCatching { VerificationLevel.valueOf(value) }.getOrDefault(VerificationLevel.UNKNOWN)
                "warning" -> warnings += decodeText(value) ?: return null
                "point" -> {
                    val parts = value.split(',')
                    if (parts.size != 2) return null
                    val lat = parts[0].toDoubleOrNull() ?: return null
                    val lon = parts[1].toDoubleOrNull() ?: return null
                    points += runCatching { GeoPoint(lat, lon) }.getOrNull() ?: return null
                }
            }
        }
        if (points.size < 2) return null
        return PersistedRoute(
            name,
            PlannedRoute(
                points = points,
                distanceNm = distanceNm ?: routeDistanceNm(points),
                initialBearingDegrees = initialBearing ?: GeoCore.initialBearingDegrees(points.first(), points[1]),
                estimatedDurationMinutes = durationMinutes ?: 0.0,
                maximumCoastDistanceNm = maximumCoastDistanceNm,
                confidence = confidence,
                warnings = warnings
            )
        )
    }

    private fun routeDistanceNm(points: List<GeoPoint>): Double =
        GeoCore.metersToNauticalMiles(points.zipWithNext().sumOf { (a, b) -> GeoCore.haversineMeters(a, b) })

    private fun encodeText(value: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodeText(value: String): String? = runCatching {
        String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
    }.getOrNull()
}
