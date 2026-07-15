package fr.cap6.app

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

enum class EmergencyKind { MAYDAY, PAN_PAN, SECURITE }

data class EmergencyBoatProfile(
    val boatName: String,
    val callSign: String? = null,
    val mmsi: String? = null,
    val personsOnBoard: Int? = null
)

object CoordinateFormatter {
    fun decimal(point: GeoPoint): String = String.format(Locale.US, "%.5f, %.5f", point.latitude, point.longitude)

    fun degreesMinutes(point: GeoPoint): String = "${one(point.latitude, true)}  ${one(point.longitude, false)}"

    private fun one(value: Double, latitude: Boolean): String {
        val absolute = abs(value)
        val degrees = floor(absolute).toInt()
        val minutes = (absolute - degrees) * 60.0
        val hemisphere = if (latitude) {
            if (value >= 0) "N" else "S"
        } else {
            if (value >= 0) "E" else "W"
        }
        val degreeWidth = if (latitude) 2 else 3
        return String.format(Locale.US, "%0${degreeWidth}d° %06.3f' %s", degrees, minutes, hemisphere)
    }
}

object EmergencyMessageBuilder {
    fun build(
        kind: EmergencyKind,
        boat: EmergencyBoatProfile,
        position: GeoPoint?,
        nature: String? = null,
        assistance: String? = null
    ): String {
        val positionText = position?.let { "${CoordinateFormatter.degreesMinutes(it)} (${CoordinateFormatter.decimal(it)})" } ?: "POSITION GPS INDISPONIBLE"
        val name = boat.boatName.ifBlank { "NOM DU BATEAU À COMPLÉTER" }
        val identity = buildString {
            append(name)
            boat.callSign?.takeIf { it.isNotBlank() }?.let { append(" — indicatif ").append(it) }
            boat.mmsi?.takeIf { it.isNotBlank() }?.let { append(" — MMSI ").append(it) }
        }
        val persons = boat.personsOnBoard?.toString() ?: "À COMPLÉTER"
        return when (kind) {
            EmergencyKind.MAYDAY -> """MAYDAY MAYDAY MAYDAY
ICI $identity, $identity, $identity
POSITION : $positionText
NATURE DE LA DÉTRESSE : ${nature ?: "À COMPLÉTER"}
PERSONNES À BORD : $persons
ASSISTANCE DEMANDÉE : ${assistance ?: "IMMÉDIATE — À PRÉCISER"}
J'ÉCOUTE"""
            EmergencyKind.PAN_PAN -> """PAN PAN PAN
ICI $identity, $identity, $identity
POSITION : $positionText
NATURE DU PROBLÈME : ${nature ?: "PANNE / DÉRIVE — À COMPLÉTER"}
PERSONNES À BORD : $persons
ASSISTANCE DEMANDÉE : ${assistance ?: "À PRÉCISER"}
J'ÉCOUTE"""
            EmergencyKind.SECURITE -> """SÉCURITÉ SÉCURITÉ SÉCURITÉ
ICI $identity
POSITION : $positionText
INFORMATION DE SÉCURITÉ : ${nature ?: "OBJET FLOTTANT / DANGER — À COMPLÉTER"}
J'ÉCOUTE"""
        }
    }
}
