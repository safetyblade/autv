package dev.prestwich.autv.creator

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Explicit on-demand metadata only. No polling, stream URLs, token minting or stored credentials. */
internal class CreatorAvailability(
    private val get: (String, Map<String, String>) -> String = ::download,
) {
    fun twitch(login: String, clientId: String, userToken: String): Availability {
        if (clientId.isBlank() || userToken.isBlank()) return Availability(message = "Twitch status needs an OAuth token; open Twitch to check")
        return safely {
            val rows = JSONObject(get("https://api.twitch.tv/helix/streams?user_login=${encode(login)}",
                mapOf("Client-Id" to clientId, "Authorization" to "Bearer $userToken"))).getJSONArray("data")
            val live = (0 until rows.length()).any { rows.getJSONObject(it).getString("user_login").equals(login, true) }
            Availability(if (live) LiveStatus.LIVE else LiveStatus.OFFLINE, if (live) "Twitch reports live" else "Twitch reports offline")
        }
    }

    fun youtube(channelId: String, apiKey: String): Availability {
        if (apiKey.isBlank()) return Availability(message = "YouTube discovery needs a Data API key; open YouTube to check")
        return safely {
            val search = JSONObject(get("https://www.googleapis.com/youtube/v3/search?part=id&type=video&eventType=live&maxResults=5&channelId=${encode(channelId)}&key=${encode(apiKey)}", emptyMap()))
            val rows = search.getJSONArray("items")
            if (rows.length() == 0) return@safely Availability(LiveStatus.OFFLINE, "No current public live broadcast found")
            val ids = (0 until rows.length()).map { rows.getJSONObject(it).getJSONObject("id").getString("videoId") }
            require(ids.all { it.matches(Regex("[A-Za-z0-9_-]{11}")) })
            val videos = JSONObject(get("https://www.googleapis.com/youtube/v3/videos?part=snippet,status,liveStreamingDetails&id=${ids.joinToString(",")}&key=${encode(apiKey)}", emptyMap())).getJSONArray("items")
            val live = (0 until videos.length()).map { videos.getJSONObject(it) }.filter {
                val snippet = it.getJSONObject("snippet")
                snippet.getString("channelId") == channelId && snippet.optString("liveBroadcastContent") == "live" &&
                    !it.getJSONObject("liveStreamingDetails").has("actualEndTime")
            }
            if (live.isEmpty()) return@safely Availability(LiveStatus.UNKNOWN, "Broadcast changed during discovery; retry when ready")
            val playable = live.firstOrNull { it.getJSONObject("status").optBoolean("embeddable", false) }
                ?: return@safely Availability(LiveStatus.UNAVAILABLE, "Live broadcast does not permit embedding; open YouTube")
            Availability(LiveStatus.LIVE, "Public live broadcast found; player still checks region, age and account restrictions", playable.getString("id"))
        }
    }

    private fun safely(block: () -> Availability): Availability = try { block() }
    catch (_: Exception) { Availability(message = "Status could not be checked (network, credentials, quota or response). Retry or open the platform") }

    companion object {
        private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
        private fun download(url: String, headers: Map<String, String>): String {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 8_000; connection.readTimeout = 8_000
            connection.instanceFollowRedirects = false
            headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
            return try {
                check(connection.responseCode == 200)
                connection.inputStream.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    val deadline = System.nanoTime() + 15_000_000_000L
                    while (true) {
                        check(System.nanoTime() < deadline)
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= 512 * 1024)
                        output.write(buffer, 0, count)
                    }
                    output.toString("UTF-8")
                }
            } finally { connection.disconnect() }
        }
    }
}
