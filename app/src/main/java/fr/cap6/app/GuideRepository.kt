package fr.cap6.app

import android.content.Context
import org.json.JSONObject

data class GuideCard(
    val id: String,
    val title: String,
    val meaning: String,
    val action: String,
    val colours: String,
    val topmark: String,
    val light: String
)

class GuideRepository(private val context: Context) {
    fun cards(): List<GuideCard> {
        val root = JSONObject(context.assets.open("data/code_balisage_fr.json").bufferedReader().use { it.readText() })
        val array = root.getJSONArray("cards")
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(GuideCard(
                    item.getString("id"), item.getString("title"), item.optString("meaning"), item.optString("action"),
                    item.optString("colours"), item.optString("topmark"), item.optString("light")
                ))
            }
        }
    }
}
