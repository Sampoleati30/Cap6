package fr.cap6.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

data class PortRecord(val id: String, val name: String, val point: GeoPoint, val verified: Boolean)

data class MarineDataBundle(
    val coastlineIndex: SegmentGridIndex,
    val landPolygons: List<List<GeoPoint>>,
    val fishingEngine: FishingRuleEngine,
    val ports: List<PortRecord>
)

class GeoJsonRepository(private val context: Context) {
    fun load(): MarineDataBundle {
        val coast = JSONObject(read("data/coast_legacy.geojson"))
        val coastCoordinates = coast.getJSONArray("features").getJSONObject(0)
            .getJSONObject("geometry").getJSONArray("coordinates")
        val index = SegmentGridIndex(cellDegrees = 0.03)
        var previous: GeoPoint? = null
        for (i in 0 until coastCoordinates.length()) {
            val p = coordinate(coastCoordinates.getJSONArray(i))
            previous?.let { index.insert(Segment(it, p)) }
            previous = p
        }

        val landObject = JSONObject(read("data/mediterranee/land_mainland_legacy.geojson"))
        val landPolygons = parsePolygons(
            landObject.getJSONArray("features").getJSONObject(0).getJSONObject("geometry")
        )
        val fishingEngine = parseFishing(JSONObject(read("data/mediterranee/fishing_rules_mediterranee.json")))
        val ports = parsePorts(JSONObject(read("data/mediterranee/ports_mediterranee_unverified.json")))
        return MarineDataBundle(index, landPolygons, fishingEngine, ports)
    }

    private fun parseFishing(root: JSONObject): FishingRuleEngine {
        val coverages = mutableListOf<FishingCoverage>()
        val coverageArray = root.optJSONArray("coverages") ?: JSONArray()
        for (i in 0 until coverageArray.length()) {
            val item = coverageArray.getJSONObject(i)
            coverages += FishingCoverage(
                id = item.getString("id"),
                polygon = parseCoordinateRing(item.getJSONArray("polygon")),
                completeForDate = item.optBoolean("complete_for_date", false),
                sourceName = item.optString("source_name", "Source non renseignée"),
                sourceDate = date(item.optString("source_date"))
            )
        }
        val zones = mutableListOf<FishingRuleZone>()
        val zoneArray = root.optJSONArray("zones") ?: JSONArray()
        for (i in 0 until zoneArray.length()) {
            val item = zoneArray.getJSONObject(i)
            zones += FishingRuleZone(
                id = item.getString("id"), name = item.getString("name"),
                polygon = parseCoordinateRing(item.getJSONArray("polygon")),
                status = runCatching { FishingStatus.valueOf(item.getString("status")) }.getOrDefault(FishingStatus.INSUFFICIENT),
                ruleText = item.optString("rule_text", "Règle non renseignée"),
                species = item.optString("species").takeIf { it.isNotBlank() },
                gear = item.optString("gear").takeIf { it.isNotBlank() },
                periodText = item.optString("period_text").takeIf { it.isNotBlank() },
                legalReference = item.optString("legal_reference").takeIf { it.isNotBlank() },
                sourceName = item.optString("source_name", "Source non renseignée"),
                sourceDate = date(item.optString("source_date")),
                validFrom = date(item.optString("valid_from")),
                validUntil = date(item.optString("valid_until")),
                verification = runCatching { VerificationLevel.valueOf(item.optString("verification", "UNKNOWN")) }.getOrDefault(VerificationLevel.UNKNOWN),
                enabled = item.optBoolean("enabled", true)
            )
        }
        return FishingRuleEngine(zones, coverages)
    }

    private fun parsePorts(root: JSONObject): List<PortRecord> {
        val array = root.getJSONArray("ports")
        return buildList {
            for (i in 0 until array.length()) {
                val p = array.getJSONObject(i)
                add(PortRecord(
                    p.getString("id"), p.getString("name"),
                    GeoPoint(p.getDouble("latitude"), p.getDouble("longitude")),
                    p.optString("verification_status") == "verified"
                ))
            }
        }
    }

    private fun parsePolygons(geometry: JSONObject): List<List<GeoPoint>> = when (geometry.getString("type")) {
        "Polygon" -> listOf(parseCoordinateRing(geometry.getJSONArray("coordinates").getJSONArray(0)))
        "MultiPolygon" -> buildList {
            val polygons = geometry.getJSONArray("coordinates")
            for (i in 0 until polygons.length()) add(parseCoordinateRing(polygons.getJSONArray(i).getJSONArray(0)))
        }
        else -> emptyList()
    }

    private fun parseCoordinateRing(array: JSONArray): List<GeoPoint> = buildList {
        for (i in 0 until array.length()) add(coordinate(array.getJSONArray(i)))
    }

    private fun coordinate(array: JSONArray) = GeoPoint(array.getDouble(1), array.getDouble(0))
    private fun date(value: String?): LocalDate? = value?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    private fun read(path: String) = context.assets.open(path).bufferedReader().use { it.readText() }
}
