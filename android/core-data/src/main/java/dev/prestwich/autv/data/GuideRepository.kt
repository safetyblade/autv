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

class GuideRepository(
    private val endpoint: String = GUIDE_ENDPOINT,
    private val fetch: (String) -> String = ::download,
) {
    /** JSON alone is the first usable catalogue. Playlist IO is explicitly separate. */
    fun load(): Guide = validated(fetch(endpoint))
    fun reconcile(guide: Guide): Guide = mergePlaylist(guide, fetch(PLAYLIST_ENDPOINT))

    internal fun validated(body: String): Guide {
        val rows = JSONObject(body).getJSONArray("channels")
        require(rows.length() > 0) { "The remote guide is empty" }
        val numbers = mutableSetOf<Int>()
        for (index in 0 until rows.length()) {
            val row = rows.getJSONObject(index)
            val number = row.get("number")
            require(number is Number && number.toDouble() == number.toInt().toDouble() && number.toInt() >= 0) { "Invalid channel number" }
            require(numbers.add(number.toInt())) { "Duplicate channel number" }
            require(row.get("name") is String && row.getString("name").trim().let { it.isNotEmpty() && it != "null" }) { "Missing channel name" }
        }
        // A missing/invalid stream is still a legitimate guide-only channel.
        return parse(body)
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

    companion object {
        const val REQUEST_TIMEOUT_MS = 30_000L
        private const val MAX_BYTES = 8 * 1024 * 1024
        private fun download(endpoint: String): String {
            val connection = URL(endpoint).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", "application/json, audio/x-mpegurl, text/plain")
            val deadline = System.nanoTime() + REQUEST_TIMEOUT_MS * 1_000_000
            return try {
                require(connection.responseCode in 200..299) { "Guide service HTTP ${connection.responseCode}" }
                connection.inputStream.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        check(System.nanoTime() < deadline) { "Guide service request timed out" }
                        val size = input.read(buffer)
                        if (size < 0) break
                        require(output.size() + size <= MAX_BYTES) { "Guide service response is too large" }
                        output.write(buffer, 0, size)
                    }
                    output.toString("UTF-8")
                }
            } finally { connection.disconnect() }
        }

        const val PLAYLIST_ENDPOINT = "https://raw.githubusercontent.com/safetyblade/autv/main/playlist.m3u"
        const val GUIDE_ENDPOINT = "https://raw.githubusercontent.com/safetyblade/autv/main/guide.json"
    }
}
