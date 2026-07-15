package fr.cap6.app

import android.content.Context

data class StoredBoatProfile(
    val name: String = "Mon bateau",
    val registration: String = "",
    val callSign: String = "",
    val mmsi: String = "",
    val lengthMeters: Double = 8.0,
    val widthMeters: Double = 2.8,
    val draftMeters: Double = 1.0,
    val airDraftMeters: Double = 2.5,
    val cruiseSpeedKnots: Double = 12.0,
    val maximumSpeedKnots: Double = 25.0,
    val fuelCapacityLiters: Double = 200.0,
    val personsOnBoard: Int = 2,
    val permitType: String = "Côtier"
)

class BoatProfileStore(context: Context) {
    private val database = LocalAppDatabase(context.applicationContext)
    fun load(): StoredBoatProfile = database.loadBoatProfile() ?: StoredBoatProfile().also(database::saveBoatProfile)
    fun save(profile: StoredBoatProfile) = database.saveBoatProfile(profile)
}
