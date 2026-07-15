package fr.cap6.app

import android.content.Context
import org.json.JSONObject

data class OfflineRegion(val id: String, val name: String, val minZoom: Int, val maxZoom: Int, val status: String)

class OfflineRegionRepository(private val context: Context) {
    fun regions(): List<OfflineRegion> {
        val root = JSONObject(context.assets.open("data/mediterranee/offline_regions_mediterranee.json").bufferedReader().use { it.readText() })
        val array = root.getJSONArray("regions")
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(OfflineRegion(item.getString("id"), item.getString("name"), item.getInt("min_zoom"), item.getInt("max_zoom"), item.getString("status")))
            }
        }
    }
}
