package dev.prestwich.autv.data.provider

import org.json.JSONObject
import java.net.URI

/** Source-derived protocol components. No client key, HMAC, network access or account state. */
object AbcLiveProtocol {
    class Selection internal constructor(val houseNumber: String, val programmeUrl: String, val title: String) {
        override fun toString() = "AbcLiveSelection(redacted)"
    }
    /** Not a refresh token or login substitute: the implementation must authorise this exact media. */
    fun interface MediaAuthoriser {
        fun authorise(houseNumber: String, originalHls: String): String
    }
    fun liveCollectionId(home: JSONObject): String {
        val rows = home.optJSONObject("_embedded")?.optJSONArray("collections") ?: unsupported()
        val ids = (0 until rows.length()).mapNotNull { rows.optJSONObject(it) }
            .filter { it.optString("title").contains("watch abc channels live", ignoreCase = true) }
            .map { identifier(it.optString("id")) }.distinct()
        return ids.singleOrNull() ?: unsupported()
    }
    fun channels(collection: JSONObject): List<Selection> {
        val rows = collection.optJSONArray("items") ?: unsupported()
        return (0 until rows.length()).mapNotNull { rows.optJSONObject(it) }
            .filter { it.optString("type") == "livestream" }.map { row ->
                val house = identifier(row.optString("houseNumber"))
                val title = row.optString("showTitle").ifBlank { row.optString("title") }
                if (title.isBlank() || title == "null" || title.length > 256 || title.any { it.isISOControl() }) unsupported()
                val href = row.optJSONObject("_links")?.optJSONObject("self")?.optString("href") ?: unsupported()
                Selection(house, programmeUrl(href), title)
            }.also { rowsOut ->
                // Conflicting metadata is not resolved by silently taking the first entry.
                if (rowsOut.map { it.houseNumber }.distinct().size != rowsOut.size) unsupported()
            }
    }
    fun resolve(channel: ProviderChannelId, selection: Selection, programme: JSONObject,
                authoriser: MediaAuthoriser): ProviderPlaybackRequest {
        if (channel.provider != "abc" || channel.channel != selection.houseNumber) unsupported()
        if (programme.optBoolean("playable", true) == false) throw ProviderException(ProviderFailure.UNAVAILABLE)
        if (identifier(programme.optString("houseNumber")) != selection.houseNumber) unsupported()
        protection(programme)
        val playlists = programme.optJSONObject("_embedded")?.optJSONArray("playlist") ?: unsupported()
        val live = (0 until playlists.length()).mapNotNull { playlists.optJSONObject(it) }
            .filter { it.optString("type") == "livestream" }
        val playlist = live.singleOrNull() ?: unsupported()
        protection(playlist)
        val streams = playlist.optJSONObject("streams") ?: unsupported()
        protection(streams)
        val hls = streams.optJSONObject("hls") ?: unsupported()
        protection(hls)
        // Pick an original provider variant; never derive, strip or upgrade a manifest.
        val original = listOf("1080", "720", "sd", "sd-low").mapNotNull { hls.opt(it) as? String }
            .firstOrNull { it.isNotBlank() } ?: unsupported()
        https(original)
        // No exception fallback to original: failed signing/approval is a playback failure.
        val authorised = try { authoriser.authorise(selection.houseNumber, original) }
            catch (error: ProviderException) { throw error }
            catch (_: Exception) { throw ProviderException(ProviderFailure.UNAVAILABLE) }
        https(authorised)
        return ProviderPlaybackRequest(channel, authorised, "application/x-mpegURL")
    }
    private fun protection(record: JSONObject) {
        if (listOf("drm", "key_systems", "licenseUrl").any { declared(record.opt(it)) })
            throw ProviderException(ProviderFailure.DRM_UNSUPPORTED)
        if (listOf("adConfig", "ad_sources", "adTagUrl").any { declared(record.opt(it)) }) unsupported()
    }
    private fun declared(value: Any?) = when(value) {
        null, JSONObject.NULL, false -> false
        is JSONObject -> value.length() > 0
        is org.json.JSONArray -> value.length() > 0
        is String -> value.isNotBlank()
        else -> true
    }
    private fun programmeUrl(value: String): String {
        val address = if (value.startsWith("/video/")) "https://api.iview.abc.net.au/v2$value" else value
        val uri = https(address)
        if (uri.host != "api.iview.abc.net.au" || uri.rawQuery != null ||
            !uri.rawPath.matches(Regex("/v2/video/[A-Za-z0-9_-]{1,128}"))) unsupported()
        return address
    }
    private fun identifier(value: String): String = value.takeIf { it != "null" && it.matches(Regex("[A-Za-z0-9_-]{1,128}")) } ?: unsupported()
    private fun https(value: String): URI {
        if (value.length > 16384 || value.any { it.isISOControl() }) unsupported()
        val uri = runCatching { URI(value) }.getOrNull() ?: unsupported()
        if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.port !in setOf(-1,443) ||
            uri.rawUserInfo != null || uri.rawFragment != null) unsupported()
        return uri
    }
    private fun unsupported(): Nothing = throw ProviderException(ProviderFailure.PLAYBACK_UNSUPPORTED)
}
