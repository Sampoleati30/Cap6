package fr.cap6.app

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class LocalAppDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE boat_profile (
                id INTEGER PRIMARY KEY CHECK(id=1),
                name TEXT NOT NULL,
                registration TEXT NOT NULL,
                call_sign TEXT NOT NULL,
                mmsi TEXT NOT NULL,
                length_m REAL NOT NULL,
                width_m REAL NOT NULL,
                draft_m REAL NOT NULL,
                air_draft_m REAL NOT NULL,
                cruise_speed_kn REAL NOT NULL,
                maximum_speed_kn REAL NOT NULL,
                fuel_capacity_l REAL NOT NULL,
                persons INTEGER NOT NULL,
                permit_type TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE track_points (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                latitude REAL NOT NULL CHECK(latitude BETWEEN -90 AND 90),
                longitude REAL NOT NULL CHECK(longitude BETWEEN -180 AND 180),
                timestamp_ms INTEGER NOT NULL UNIQUE,
                speed_kn REAL,
                accuracy_m REAL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_track_points_time ON track_points(timestamp_ms)")
        db.execSQL("CREATE TABLE app_state (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        db.execSQL("INSERT INTO app_state(key,value) VALUES('track_state','STOPPED')")
        createActiveRouteTable(db)
        createFishingSpotsTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createActiveRouteTable(db)
        if (oldVersion < 3) createFishingSpotsTable(db)
    }

    private fun createActiveRouteTable(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS active_route (
                id INTEGER PRIMARY KEY CHECK(id=1),
                payload TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            )
        """.trimIndent())
    }

    private fun createFishingSpotsTable(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS fishing_spots (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                latitude REAL NOT NULL CHECK(latitude BETWEEN -90 AND 90),
                longitude REAL NOT NULL CHECK(longitude BETWEEN -180 AND 180),
                notes TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                last_visited_at INTEGER,
                route_payload TEXT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_fishing_spots_recent ON fishing_spots(last_visited_at DESC, updated_at DESC)")
    }

    fun loadBoatProfile(): StoredBoatProfile? {
        readableDatabase.query("boat_profile", null, "id=1", null, null, null, null).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return StoredBoatProfile(
                name = cursor.text("name"), registration = cursor.text("registration"), callSign = cursor.text("call_sign"), mmsi = cursor.text("mmsi"),
                lengthMeters = cursor.double("length_m"), widthMeters = cursor.double("width_m"), draftMeters = cursor.double("draft_m"),
                airDraftMeters = cursor.double("air_draft_m"), cruiseSpeedKnots = cursor.double("cruise_speed_kn"), maximumSpeedKnots = cursor.double("maximum_speed_kn"),
                fuelCapacityLiters = cursor.double("fuel_capacity_l"), personsOnBoard = cursor.int("persons"), permitType = cursor.text("permit_type")
            )
        }
    }

    fun saveBoatProfile(profile: StoredBoatProfile) {
        val values = ContentValues().apply {
            put("id", 1); put("name", profile.name); put("registration", profile.registration); put("call_sign", profile.callSign); put("mmsi", profile.mmsi)
            put("length_m", profile.lengthMeters); put("width_m", profile.widthMeters); put("draft_m", profile.draftMeters); put("air_draft_m", profile.airDraftMeters)
            put("cruise_speed_kn", profile.cruiseSpeedKnots); put("maximum_speed_kn", profile.maximumSpeedKnots); put("fuel_capacity_l", profile.fuelCapacityLiters)
            put("persons", profile.personsOnBoard); put("permit_type", profile.permitType); put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict("boat_profile", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun trackState(): TrackState {
        readableDatabase.rawQuery("SELECT value FROM app_state WHERE key='track_state'", null).use { cursor ->
            val value = if (cursor.moveToFirst()) cursor.getString(0) else "STOPPED"
            return runCatching { TrackState.valueOf(value) }.getOrDefault(TrackState.STOPPED)
        }
    }

    fun setTrackState(state: TrackState) {
        val values = ContentValues().apply { put("key", "track_state"); put("value", state.name) }
        writableDatabase.insertWithOnConflict("app_state", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized fun appendTrackPoint(point: TrackPoint, minimumDistanceMeters: Double = 3.0): Boolean {
        val db = writableDatabase
        db.rawQuery("SELECT latitude,longitude,timestamp_ms FROM track_points ORDER BY timestamp_ms DESC LIMIT 1", null).use { cursor ->
            if (cursor.moveToFirst()) {
                val last = GeoPoint(cursor.getDouble(0), cursor.getDouble(1))
                val lastTime = cursor.getLong(2)
                if (point.timestampEpochMillis <= lastTime) return false
                val moved = GeoCore.haversineMeters(last, point.position)
                if (moved < minimumDistanceMeters && point.timestampEpochMillis - lastTime < 10_000L) return false
            }
        }
        val values = ContentValues().apply {
            put("latitude", point.position.latitude); put("longitude", point.position.longitude); put("timestamp_ms", point.timestampEpochMillis)
            point.speedKnots?.let { put("speed_kn", it) }; point.accuracyMeters?.let { put("accuracy_m", it) }
        }
        return db.insertWithOnConflict("track_points", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }

    fun loadTrackPoints(limit: Int = MAX_POINTS): List<TrackPoint> {
        val result = mutableListOf<TrackPoint>()
        readableDatabase.rawQuery(
            "SELECT latitude,longitude,timestamp_ms,speed_kn,accuracy_m FROM track_points ORDER BY timestamp_ms ASC LIMIT ?",
            arrayOf(limit.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += TrackPoint(
                    GeoPoint(cursor.getDouble(0), cursor.getDouble(1)), cursor.getLong(2),
                    if (cursor.isNull(3)) null else cursor.getDouble(3), if (cursor.isNull(4)) null else cursor.getDouble(4)
                )
            }
        }
        return result
    }

    fun clearTrack() {
        writableDatabase.transaction {
            delete("track_points", null, null)
            val values = ContentValues().apply { put("key", "track_state"); put("value", TrackState.STOPPED.name) }
            insertWithOnConflict("app_state", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    fun loadActiveRoute(): String? {
        readableDatabase.rawQuery("SELECT payload FROM active_route WHERE id=1", null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    fun saveActiveRoute(payload: String) {
        require(payload.length <= 2_000_000) { "Route payload too large" }
        val values = ContentValues().apply { put("id", 1); put("payload", payload); put("updated_at", System.currentTimeMillis()) }
        writableDatabase.insertWithOnConflict("active_route", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun clearActiveRoute() { writableDatabase.delete("active_route", "id=1", null) }

    fun insertFishingSpot(name: String, point: GeoPoint, notes: String): Long {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("name", name); put("latitude", point.latitude); put("longitude", point.longitude); put("notes", notes)
            put("created_at", now); put("updated_at", now)
        }
        return writableDatabase.insertOrThrow("fishing_spots", null, values)
    }

    fun loadFishingSpot(id: Long): FishingSpot? {
        readableDatabase.query("fishing_spots", null, "id=?", arrayOf(id.toString()), null, null, null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.toFishingSpot() else null
        }
    }

    fun loadFishingSpots(): List<FishingSpot> {
        val result = mutableListOf<FishingSpot>()
        readableDatabase.query(
            "fishing_spots", null, null, null, null, null,
            "COALESCE(last_visited_at, updated_at) DESC, id DESC"
        ).use { cursor -> while (cursor.moveToNext()) result += cursor.toFishingSpot() }
        return result
    }

    fun updateFishingSpot(id: Long, name: String, notes: String) {
        val values = ContentValues().apply {
            put("name", name); put("notes", notes); put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.update("fishing_spots", values, "id=?", arrayOf(id.toString()))
    }

    fun deleteFishingSpot(id: Long) {
        writableDatabase.delete("fishing_spots", "id=?", arrayOf(id.toString()))
    }

    fun saveFishingSpotRoute(id: Long, routePayload: String) {
        require(routePayload.length <= 2_000_000) { "Fishing route payload too large" }
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("route_payload", routePayload); put("last_visited_at", now); put("updated_at", now)
        }
        writableDatabase.update("fishing_spots", values, "id=?", arrayOf(id.toString()))
    }

    private fun Cursor.toFishingSpot(): FishingSpot {
        val payloadIndex = index("route_payload")
        val payload = if (isNull(payloadIndex)) null else getString(payloadIndex)
        val visitedIndex = index("last_visited_at")
        return FishingSpot(
            id = getLong(index("id")),
            name = text("name"),
            position = GeoPoint(double("latitude"), double("longitude")),
            notes = text("notes"),
            createdAtEpochMillis = getLong(index("created_at")),
            updatedAtEpochMillis = getLong(index("updated_at")),
            lastVisitedAtEpochMillis = if (isNull(visitedIndex)) null else getLong(visitedIndex),
            lastRoute = payload?.let { RoutePersistenceCodec.decode(it)?.route }
        )
    }

    private fun Cursor.index(name: String) = getColumnIndexOrThrow(name)
    private fun Cursor.text(name: String) = getString(index(name))
    private fun Cursor.double(name: String) = getDouble(index(name))
    private fun Cursor.int(name: String) = getInt(index(name))

    private inline fun SQLiteDatabase.transaction(block: SQLiteDatabase.() -> Unit) {
        beginTransaction()
        try { block(); setTransactionSuccessful() } finally { endTransaction() }
    }

    companion object {
        private const val DATABASE_NAME = "cap6_user.sqlite"
        private const val DATABASE_VERSION = 3
        private const val MAX_POINTS = 100_000
    }
}
