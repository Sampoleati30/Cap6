package fr.cap6.app

/** Point de pêche privé, enregistré uniquement dans la base locale du téléphone. */
data class FishingSpot(
    val id: Long,
    val name: String,
    val position: GeoPoint,
    val notes: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val lastVisitedAtEpochMillis: Long?,
    val lastRoute: PlannedRoute?
)

class FishingSpotStore(context: android.content.Context) {
    private val database = LocalAppDatabase(context.applicationContext)

    fun add(name: String, position: GeoPoint, notes: String = ""): FishingSpot {
        val safeName = name.trim().ifBlank { "Spot pêche" }
        val id = database.insertFishingSpot(safeName, position, notes.trim())
        return requireNotNull(database.loadFishingSpot(id))
    }

    fun all(): List<FishingSpot> = database.loadFishingSpots()

    fun recentRoutes(): List<FishingSpot> = all()
        .filter { it.lastRoute != null }
        .sortedByDescending { it.lastVisitedAtEpochMillis ?: it.updatedAtEpochMillis }

    fun rename(id: Long, name: String, notes: String) {
        database.updateFishingSpot(id, name.trim().ifBlank { "Spot pêche" }, notes.trim())
    }

    fun delete(id: Long) = database.deleteFishingSpot(id)

    fun saveRoute(id: Long, route: PlannedRoute) {
        database.saveFishingSpotRoute(id, RoutePersistenceCodec.encode("Spot pêche", route))
    }
}
