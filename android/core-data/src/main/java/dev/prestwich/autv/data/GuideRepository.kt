package dev.prestwich.autv.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Channel(
    val number: Int,
    val name: String,
    val genre: String,
    val subgenre: String,
    val description: String,
    val epg: String,
    val available: Boolean,
    val streamUrl: String?,
    val tvgId: String?,
    val logoUrl: String?,
)

data class Guide(val activeCount: Int, val availableCount: Int, val channels: List<Channel>)

class GuideRepository(private val endpoint: String = GUIDE_ENDPOINT) {
    fun load(): Guide {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("Accept", "application/json")
        try {
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(body)
            val array = root.getJSONArray("channels")
            val channels = buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(Channel(
                        number = item.getInt("number"),
                        name = item.getString("name"),
                        genre = item.getString("genre"),
                        subgenre = item.getString("subgenre"),
                        description = item.optString("description"),
                        epg = item.optString("epg"),
                        available = item.optBoolean("available"),
                        streamUrl = item.optString("streamUrl").takeIf { it.isNotBlank() && it != "null" },
                        tvgId = item.optString("tvgId").takeIf { it.isNotBlank() && it != "null" },
                        logoUrl = item.optString("logoUrl").takeIf { it.isNotBlank() && it != "null" },
                    ))
                }
            }
            return Guide(root.optInt("activeCount", channels.size), root.optInt("availableCount", channels.count { it.available }), channels.sortedBy { it.number })
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val GUIDE_ENDPOINT = "https://raw.githubusercontent.com/safetyblade/autv/main/guide.json"
    }
}
