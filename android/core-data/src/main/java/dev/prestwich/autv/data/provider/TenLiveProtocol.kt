package dev.prestwich.autv.data.provider

import org.json.JSONObject
import java.net.URI

/** Network-free Ten/Google DAI boundary. No signing key, approved-device identity or ad rewrite. */
object TenLiveProtocol {
    class Selection internal constructor(val title: String, val descriptorUrl: String) {
        // Descriptor URLs are not verified stable channel IDs and never become favourite keys.
        override fun toString() = "TenLiveSelection(redacted)"
    }
    class LiveRequest internal constructor(val assetKey: String, val authToken: String) {
        override fun toString() = "TenDaiLiveRequest(redacted)"
    }
    enum class Format { HLS, DASH }
    /** Keep media and measurement together; deliberately no raw-HLS ProviderPlaybackRequest adapter. */
    class LiveSession internal constructor(val manifest: String, val metadata: String, val verification: String,
        val update: String?, val pollingSeconds: Long, val format: Format) {
        override fun toString() = "TenDaiLiveSession(redacted)"
    }
    fun catalogue(data: JSONObject): List<Selection> {
        val channels = data.optJSONArray("channels") ?: unsupported()
        return (0 until channels.length()).map { index ->
            val row = channels.optJSONObject(index) ?: unsupported()
            val title = text(row, "name", 256)
            val url = text(row, "streamUrl")
            https(url)
            Selection(title, url)
        }
    }
    fun liveRequest(descriptor: JSONObject, returnedDaiAuth: String): LiveRequest {
        if (listOf("drm", "key_systems", "licenseUrl").any { declared(descriptor.opt(it)) })
            throw ProviderException(ProviderFailure.DRM_UNSUPPORTED)
        val dai = descriptor.optJSONObject("dai") ?: unsupported()
        if (declared(dai.opt("contentSourceId")) || declared(dai.opt("videoId"))) unsupported()
        val asset = text(dai, "assetKey", 512)
        // Authentication comes from the provider response; it is never synthesised locally.
        if (returnedDaiAuth.isBlank() || returnedDaiAuth.length > 16384 || returnedDaiAuth.any { it.isISOControl() })
            throw ProviderException(ProviderFailure.AUTH_REQUIRED)
        return LiveRequest(asset, returnedDaiAuth)
    }
    fun liveSession(response: JSONObject, negotiatedFormat: Format): LiveSession {
        // Google's documented response returns these URLs. Never derive a master from events URL.
        val manifest = text(response, "stream_manifest").also { https(it) }
        val metadata = text(response, "metadata_url").also { https(it) }
        val verification = text(response, "media_verification_url").also { https(it) }
        val update = (response.opt("session_update_url") as? String)?.also { https(it) }
        val poll = if (response.has("polling_frequency")) response.optLong("polling_frequency", 0) else 10L
        if (poll !in 1..3600) unsupported()
        return LiveSession(manifest, metadata, verification, update, poll, negotiatedFormat)
    }
    private fun declared(value: Any?) = when(value) {
        null, JSONObject.NULL, false -> false
        is JSONObject -> value.length() > 0
        is org.json.JSONArray -> value.length() > 0
        is String -> value.isNotBlank()
        else -> true
    }
    private fun text(json: JSONObject, key: String, limit: Int = 16384): String {
        val value = json.opt(key) as? String ?: unsupported()
        if (value.isBlank() || value.length > limit || value.any { it.isISOControl() }) unsupported()
        return value
    }
    private fun https(value: String) {
        if (value.length > 16384 || value.any { it.isISOControl() }) unsupported()
        val uri = runCatching { URI(value) }.getOrNull() ?: unsupported()
        if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.rawUserInfo != null ||
            uri.rawFragment != null || uri.port !in setOf(-1,443)) unsupported()
    }
    private fun unsupported(): Nothing = throw ProviderException(ProviderFailure.PLAYBACK_UNSUPPORTED)
}
