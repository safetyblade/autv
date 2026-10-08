package dev.prestwich.autv

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Resolves a current The Roku Channel live playback URL at tune time.
 *
 * Some Roku FAST channels (notably Wrestling Central) are present in Roku EPG
 * data but are not exposed by the static public M3U mirror used by AUTV. Their
 * playable URL must be resolved from Roku's current content/playback APIs.
 */
class RokuChannelResolver {
    private val origin = "https://therokuchannel.roku.com"
    private val contentBase = "https://content.sr.roku.com/content/v1/roku-trc/"

    fun resolve(channelId: String): String {
        require(channelId.isNotBlank()) { "Missing Roku channel id" }

        val csrf = JSONObject(request("$origin/api/v1/csrf")).optString("csrf")
        require(csrf.isNotBlank()) { "Roku CSRF token unavailable" }

        val encodedBase = URLEncoder.encode(contentBase, StandardCharsets.UTF_8.toString())
        val expand = "viewOptions.channelId,viewOptions.playId,next.viewOptions.channelId,next.viewOptions.playId"
        val encodedExpand = URLEncoder.encode(expand, StandardCharsets.UTF_8.toString())
            .replace("%2C", "%252C")
        val contentUrl = "$origin/api/v2/homescreen/content/$encodedBase$channelId?expand=$encodedExpand"
        val content = JSONObject(request(contentUrl, referer = "$origin/watch/$channelId"))
        val viewOptions = content.optJSONArray("viewOptions")
        require(viewOptions != null && viewOptions.length() > 0) { "Roku channel has no current play option" }
        val playId = viewOptions.getJSONObject(0).optString("playId")
        require(playId.isNotBlank()) { "Roku channel has no play id" }

        val body = JSONObject()
            .put("rokuId", channelId)
            .put("playId", playId)
            .put("mediaFormat", "m3u")
            .put("drmType", "widevine")
            .put("quality", "fhd")
            .put("bifUrl", JSONObject.NULL)
            .put("adPolicyId", "")
            .put("providerId", "rokuavod")
            .toString()

        val playback = JSONObject(
            request(
                "$origin/api/v3/playback",
                method = "POST",
                body = body,
                csrf = csrf,
                referer = "$origin/watch/$channelId"
            )
        )
        val url = playback.optString("url")
        require(url.isNotBlank()) { "Roku playback URL unavailable" }
        return normalise(url)
    }

    private fun normalise(value: String): String {
        var url = value
        val replacements = mapOf(
            "https://osm.sr.roku.com/osm/v1/hls/master/" to "https://aka-live1050.delivery.roku.com/",
            "https://osm-use1.sr.roku.com/osm/v1/hls/use1/master/" to "https://aka-live1050.delivery.roku.com/",
            "https://osm-use2.sr.roku.com/osm/v1/hls/use2/master/" to "https://aka-live1050.delivery.roku.com/",
            "https://osm-euw1.sr.roku.com/osm/v1/hls/euw1/master/" to "https://aka-live1050.delivery.roku.com/",
            "https://osm-aps1.sr.roku.com/osm/v1/hls/aps1/master/" to "https://aka-live1050.delivery.roku.com/",
            "https://osm.sr.roku.com/osm/v1/hls/" to "https://aka-live1050.delivery.roku.com/"
        )
        for ((from, to) in replacements) {
            if (url.contains(from)) {
                url = url.replace(from, to)
                break
            }
        }
        return url.replace("/live.m3u8", "/t2-origin/out/v1/live.m3u8")
    }

    private fun request(
        url: String,
        method: String = "GET",
        body: String? = null,
        csrf: String? = null,
        referer: String = origin
    ): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("Accept", "application/json, text/plain, */*")
        connection.setRequestProperty("Origin", origin)
        connection.setRequestProperty("Referer", referer)
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android TV; AUTV)")
        if (!csrf.isNullOrBlank()) connection.setRequestProperty("csrf-token", csrf)
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        require(status in 200..299) { "Roku request failed: HTTP $status" }
        return text
    }
}
