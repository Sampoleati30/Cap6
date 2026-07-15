package fr.cap6.app

import kotlin.math.*

class HeadingFilter(
    private val minimumSpeedKnots: Double = 1.2,
    private val alpha: Double = 0.24,
    private val maximumRotationDegreesPerSecond: Double = 45.0
) {
    private var heading: Double? = null
    private var lastTimestampMs: Long? = null

    fun reset() { heading = null; lastTimestampMs = null }

    fun update(courseDegrees: Double?, speedKnots: Double, timestampMs: Long): Double? {
        if (courseDegrees == null || !courseDegrees.isFinite() || speedKnots < minimumSpeedKnots) {
            lastTimestampMs = timestampMs
            return heading
        }
        val target = GeoCore.normalizeDegrees(courseDegrees)
        val current = heading
        if (current == null) {
            heading = target
            lastTimestampMs = timestampMs
            return target
        }
        val dt = ((timestampMs - (lastTimestampMs ?: timestampMs)).coerceAtLeast(1)) / 1000.0
        val rawDelta = GeoCore.shortestAngleDelta(current, target)
        val smoothedDelta = rawDelta * alpha
        val limited = smoothedDelta.coerceIn(-maximumRotationDegreesPerSecond * dt, maximumRotationDegreesPerSecond * dt)
        heading = GeoCore.normalizeDegrees(current + limited)
        lastTimestampMs = timestampMs
        return heading
    }
}
