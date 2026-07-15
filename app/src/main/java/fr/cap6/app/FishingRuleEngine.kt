package fr.cap6.app

import java.time.LocalDate

enum class FishingStatus { ALLOWED, REGULATED, FORBIDDEN, INFORMATIONAL, INSUFFICIENT }
enum class VerificationLevel { OFFICIAL, COLLABORATIVE, PROTOTYPE, UNKNOWN }

data class FishingRuleZone(
    val id: String,
    val name: String,
    val polygon: List<GeoPoint>,
    val status: FishingStatus,
    val ruleText: String,
    val species: String? = null,
    val gear: String? = null,
    val periodText: String? = null,
    val legalReference: String? = null,
    val sourceName: String,
    val sourceDate: LocalDate? = null,
    val validFrom: LocalDate? = null,
    val validUntil: LocalDate? = null,
    val verification: VerificationLevel,
    val enabled: Boolean = true
)

data class FishingCoverage(
    val id: String,
    val polygon: List<GeoPoint>,
    val completeForDate: Boolean,
    val sourceName: String,
    val sourceDate: LocalDate? = null
)

data class FishingEvaluation(
    val status: FishingStatus,
    val matchingRules: List<FishingRuleZone>,
    val coverage: FishingCoverage?,
    val message: String
)

class FishingRuleEngine(
    private val zones: List<FishingRuleZone>,
    private val coverages: List<FishingCoverage>
) {
    fun evaluate(point: GeoPoint, date: LocalDate): FishingEvaluation {
        val coverage = coverages.firstOrNull { PolygonMath.contains(point, it.polygon) }
        val matches = zones.asSequence()
            .filter { it.enabled }
            .filter { isActive(it, date) }
            .filter { PolygonMath.contains(point, it.polygon) }
            .sortedByDescending { priority(it.status) }
            .toList()

        val strongest = matches.firstOrNull()
        if (strongest != null) {
            val status = when {
                strongest.verification != VerificationLevel.OFFICIAL -> FishingStatus.INSUFFICIENT
                else -> strongest.status
            }
            val message = when (status) {
                FishingStatus.FORBIDDEN -> "Pêche interdite selon la règle officielle active la plus contraignante."
                FishingStatus.REGULATED -> "Pêche réglementée. Consultez les espèces, engins, périodes et l'arrêté cité."
                FishingStatus.ALLOWED -> "Aucune interdiction particulière n'est recensée dans la couverture officielle chargée."
                FishingStatus.INFORMATIONAL -> "Zone protégée informative : elle ne suffit pas, à elle seule, à conclure sur la pêche."
                FishingStatus.INSUFFICIENT -> "Une zone correspond, mais sa source ou sa validation ne permet pas une conclusion réglementaire."
            }
            return FishingEvaluation(status, matches, coverage, message)
        }

        if (coverage == null || !coverage.completeForDate) {
            return FishingEvaluation(
                FishingStatus.INSUFFICIENT,
                emptyList(),
                coverage,
                "Information insuffisante : aucune couverture réglementaire officielle complète n'est installée pour ce point et cette date."
            )
        }

        return FishingEvaluation(
            FishingStatus.ALLOWED,
            emptyList(),
            coverage,
            "Aucune restriction particulière n'est recensée dans la couverture officielle complète chargée. Vérifiez néanmoins l'arrêté en vigueur."
        )
    }

    private fun isActive(zone: FishingRuleZone, date: LocalDate): Boolean =
        (zone.validFrom == null || !date.isBefore(zone.validFrom)) &&
            (zone.validUntil == null || !date.isAfter(zone.validUntil))

    private fun priority(status: FishingStatus): Int = when (status) {
        FishingStatus.FORBIDDEN -> 5
        FishingStatus.REGULATED -> 4
        FishingStatus.INFORMATIONAL -> 3
        FishingStatus.ALLOWED -> 2
        FishingStatus.INSUFFICIENT -> 1
    }
}
