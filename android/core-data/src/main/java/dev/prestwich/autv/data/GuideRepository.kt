package dev.prestwich.autv.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URI

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
            val guide = parse(body)
            return runCatching { mergePlaylist(guide, download(PLAYLIST_ENDPOINT)) }.getOrDefault(guide)
        } finally {
            connection.disconnect()
        }
    }

    internal fun parse(body: String): Guide {
        val array = JSONObject(body).getJSONArray("channels")
        val channels = buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val number = item.optInt("number", -1)
                val name = item.optString("name").trim()
                if (number < 0 || name.isBlank() || name == "null") continue
                val streamUrl = item.optString("streamUrl").takeIf {
                    runCatching {
                        val uri = URI(it)
                        uri.scheme in listOf("https", "http") && !uri.host.isNullOrBlank()
                    }.getOrDefault(false)
                }
                add(Channel(
                    number = number,
                    name = name,
                    genre = item.optString("genre"),
                    subgenre = item.optString("subgenre"),
                    description = item.optString("description"),
                    epg = item.optString("epg"),
                    available = streamUrl != null,
                    streamUrl = streamUrl,
                    tvgId = item.optString("tvgId").takeIf { it.isNotBlank() && it != "null" },
                    logoUrl = item.optString("logoUrl").takeIf { it.isNotBlank() && it != "null" },
                ))
            }
        }.distinctBy { it.number }.sortedBy { it.number }
        return Guide(channels.size, channels.count { it.available }, channels)
    }

    internal fun mergePlaylist(guide: Guide, text: String): Guide {
        val entries = Playlist.parse(text)
        val channels = guide.channels.map { channel ->
            // Numbers in published snapshots can drift. Prefer a unique identity match.
            val match = entries.filter { Playlist.key(it.name) == Playlist.key(channel.name) }.singleOrNull()
                ?: entries.filter { channel.tvgId != null && it.id == channel.tvgId }.singleOrNull()
            if (match == null) channel else channel.copy(
                available = true, streamUrl = match.url,
                tvgId = match.id ?: channel.tvgId, logoUrl = match.logo ?: channel.logoUrl,
            )
        }
        return Guide(channels.size, channels.count { it.available }, channels)
    }

    private fun download(endpoint: String): String {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000
        return try { connection.inputStream.bufferedReader().use { it.readText() } }
        finally { connection.disconnect() }
    }

    companion object {
        const val PLAYLIST_ENDPOINT = "https://raw.githubusercontent.com/safetyblade/autv/main/playlist.m3u"
        const val GUIDE_ENDPOINT = "https://raw.githubusercontent.com/safetyblade/autv/main/guide.json"
    }
}
