package fr.cap6.app

import java.time.Instant
import kotlin.math.max

enum class TrackState { STOPPED, RECORDING, PAUSED }

data class TrackPoint(
    val position: GeoPoint,
    val timestampEpochMillis: Long,
    val speedKnots: Double? = null,
    val accuracyMeters: Double? = null
)

data class TrackSummary(
    val pointCount: Int,
    val distanceNm: Double,
    val activeDurationMillis: Long,
    val averageSpeedKnots: Double,
    val maximumSpeedKnots: Double
)

class TrackRecorder(private val minimumDistanceMeters: Double = 3.0) {
    private val mutablePoints = mutableListOf<TrackPoint>()
    private var activeDurationMillis = 0L
    private var lastActiveTimestamp: Long? = null
    var state: TrackState = TrackState.STOPPED
        private set

    val points: List<TrackPoint> get() = mutablePoints.toList()

    fun start(clearExisting: Boolean = true) {
        if (clearExisting) clear()
        state = TrackState.RECORDING
        lastActiveTimestamp = null
    }

    fun pause() {
        if (state == TrackState.RECORDING) {
            state = TrackState.PAUSED
            lastActiveTimestamp = null
        }
    }

    fun resume() {
        if (state == TrackState.PAUSED) {
            state = TrackState.RECORDING
            lastActiveTimestamp = null
        }
    }

    fun stop() {
        state = TrackState.STOPPED
        lastActiveTimestamp = null
    }

    fun clear() {
        mutablePoints.clear()
        activeDurationMillis = 0L
        lastActiveTimestamp = null
        state = TrackState.STOPPED
    }

    fun add(point: TrackPoint): Boolean {
        if (state != TrackState.RECORDING) return false
        val last = mutablePoints.lastOrNull()
        if (last != null && point.timestampEpochMillis <= last.timestampEpochMillis) return false
        val moved = last?.let { GeoCore.haversineMeters(it.position, point.position) } ?: Double.POSITIVE_INFINITY
        val accepted = last == null || moved >= minimumDistanceMeters || point.timestampEpochMillis - last.timestampEpochMillis >= 10_000L
        if (!accepted) return false
        lastActiveTimestamp?.let { activeDurationMillis += max(0L, point.timestampEpochMillis - it) }
        lastActiveTimestamp = point.timestampEpochMillis
        mutablePoints += point
        return true
    }

    fun restore(points: List<TrackPoint>, state: TrackState = TrackState.STOPPED) {
        clear()
        mutablePoints += points.sortedBy { it.timestampEpochMillis }
        for (i in 1 until mutablePoints.size) {
            val gap = mutablePoints[i].timestampEpochMillis - mutablePoints[i - 1].timestampEpochMillis
            if (gap in 1..600_000L) activeDurationMillis += gap
        }
        this.state = state
        lastActiveTimestamp = null
    }

    fun summary(): TrackSummary {
        var distanceMeters = 0.0
        var maxSpeed = 0.0
        for (i in mutablePoints.indices) {
            maxSpeed = max(maxSpeed, mutablePoints[i].speedKnots ?: 0.0)
            if (i > 0) distanceMeters += GeoCore.haversineMeters(mutablePoints[i - 1].position, mutablePoints[i].position)
        }
        val distanceNm = GeoCore.metersToNauticalMiles(distanceMeters)
        val average = if (activeDurationMillis > 0) distanceNm / (activeDurationMillis / 3_600_000.0) else 0.0
        return TrackSummary(mutablePoints.size, distanceNm, activeDurationMillis, average, maxSpeed)
    }
}

object GpxCodec {
    fun exportTrack(name: String, points: List<TrackPoint>): String {
        val safeName = escapeXml(name)
        val trackPoints = points.joinToString("") { point ->
            val speed = point.speedKnots?.let { "<extensions><cap6:speedKnots xmlns:cap6=\"https://cap6.fr/gpx\">$it</cap6:speedKnots></extensions>" } ?: ""
            "<trkpt lat=\"${point.position.latitude}\" lon=\"${point.position.longitude}\"><time>${Instant.ofEpochMilli(point.timestampEpochMillis)}</time>$speed</trkpt>"
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><gpx version=\"1.1\" creator=\"CAP 6\" xmlns=\"http://www.topografix.com/GPX/1/1\"><metadata><name>$safeName</name></metadata><trk><name>$safeName</name><trkseg>$trackPoints</trkseg></trk></gpx>"
    }

    fun importTrack(gpx: String): List<TrackPoint> {
        val regex = Regex("""<trkpt\s+[^>]*lat=["']([^"']+)["'][^>]*lon=["']([^"']+)["'][^>]*>(.*?)</trkpt>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val timeRegex = Regex("""<time>([^<]+)</time>""", RegexOption.IGNORE_CASE)
        return regex.findAll(gpx).mapNotNull { match ->
            val lat = match.groupValues[1].toDoubleOrNull() ?: return@mapNotNull null
            val lon = match.groupValues[2].toDoubleOrNull() ?: return@mapNotNull null
            val time = timeRegex.find(match.groupValues[3])?.groupValues?.get(1)?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                ?: return@mapNotNull null
            runCatching { TrackPoint(GeoPoint(lat, lon), time) }.getOrNull()
        }.toList()
    }

    private fun escapeXml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
