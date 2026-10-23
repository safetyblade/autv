package dev.prestwich.autv.data.provider

import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder

/** Consumer endpoints observed in the current provider client flow; experimental, not a public SDK.
 * No upstream implementation is copied. URLs returned by the service are never unwrapped.
 */
class NineNowAdapter(
    private val transport: NineNowTransport,
    private val store: ProviderSessionStore,
    private val now: () -> Long = System::currentTimeMillis,
    private val region: String = "nsw",
) : ProviderAdapter {
    private var session = store.read()
    override var connection = if (session == null) ProviderConnection.NOT_CONNECTED else ProviderConnection.CONNECTED
        private set

    override fun beginSignIn(): DeviceActivation {
        connection = ProviderConnection.CONNECTING
        val json = transport.request("POST", "$AUTH/code?client_id=9nowdevice")
        val expires = json.optLong("expires_in", 0).coerceIn(0, 1800)
        val code = json.optString("auth_code")
        val device = json.optString("device_code")
        if (code.isBlank() || device.isBlank() || expires == 0L) {
            connection = ProviderConnection.UNSUPPORTED
            throw ProviderException(ProviderFailure.UNSUPPORTED)
        }
        return DeviceActivation(code, device, "https://www.9now.com.au/activate",
            now() + expires * 1000, json.optLong("interval", 5).coerceIn(1, 60) * 1000)
    }

    override fun pollSignIn(activation: DeviceActivation): Boolean {
        if (now() >= activation.expiresAt) {
            connection = ProviderConnection.NOT_CONNECTED
            throw ProviderException(ProviderFailure.AUTH_REQUIRED)
        }
        val json = transport.request("GET", "$AUTH/token?client_id=9nowdevice&response_types=id_token" +
            "&auth_code=" + encode(activation.userCode) + "&device_code=" + encode(activation.deviceCode))
        if (!json.has("accessToken")) {
            when (json.optString("error")) {
                "", "authorization_pending", "pending" -> return false
                else -> {
                    connection = ProviderConnection.NOT_CONNECTED
                    throw ProviderException(ProviderFailure.AUTH_REQUIRED)
                }
            }
        }
        save(json)
        return true
    }

    private fun save(json: JSONObject) {
        val access = json.optString("accessToken")
        val refresh = json.optString("refresh_token").ifBlank { session?.refreshToken.orEmpty() }
        val lifetime = json.optLong("expiresIn", 0)
        if (access.isBlank() || refresh.isBlank() || lifetime <= 0) throw ProviderException(ProviderFailure.AUTH_REQUIRED)
        val updated = ProviderSession(access, refresh, now() + lifetime.coerceAtMost(86400 * 30) * 1000 - 30_000)
        store.write(updated)
        session = updated
        connection = ProviderConnection.CONNECTED
    }

    private fun accessToken(): String {
        val current = session ?: throw ProviderException(ProviderFailure.AUTH_REQUIRED)
        if (current.expiresAt > now()) return current.accessToken
        try {
            save(transport.request("POST", "$AUTH/refresh-token?client_id=9nowdevice&response_types=id_token&refresh_token=" +
                encode(current.refreshToken)))
        } catch (error: ProviderException) {
            if (error.failure == ProviderFailure.OFFLINE) throw error
            expire()
            throw ProviderException(ProviderFailure.SESSION_EXPIRED)
        }
        return session!!.accessToken
    }

    private fun catalogue(): JSONObject {
        val token = accessToken()
        return try {
            val json = transport.request("GET", "$LIVE?device=web&streamParams=web%2Cchrome%2Cwindows&offset=0&region=" +
                encode(region), token)
            if (json.has("errors")) throw ProviderException(ProviderFailure.UNAVAILABLE)
            json.getJSONObject("data").getJSONObject("getLivestream")
        } catch (error: ProviderException) {
            if (error.failure == ProviderFailure.SESSION_EXPIRED) expire()
            throw error
        } catch (_: Exception) { throw ProviderException(ProviderFailure.UNAVAILABLE) }
    }

    override fun channels(): List<ProviderChannel> = parseChannels(catalogue())

    override fun resolve(channel: ProviderChannelId): ProviderPlaybackRequest {
        if (channel != SEINFELD) throw ProviderException(ProviderFailure.UNAVAILABLE)
        val data = catalogue()
        val row = rows(data).singleOrNull { it.optString("slug") == channel.channel }
            ?: throw ProviderException(ProviderFailure.UNAVAILABLE)
        val stream = row.optJSONObject("stream") ?: throw ProviderException(ProviderFailure.UNAVAILABLE)
        // Do not infer or bypass protection. This proof only supports provider-returned HLS.
        if (stream.optBoolean("drm", false) || stream.has("key_systems"))
            throw ProviderException(ProviderFailure.DRM_UNSUPPORTED)
        val uri = stream.optString("url")
        if (!isHttps(uri)) throw ProviderException(ProviderFailure.UNAVAILABLE)
        return ProviderPlaybackRequest(channel, uri, "application/x-mpegURL")
    }

    private fun expire() {
        session = null
        store.clear()
        connection = ProviderConnection.SESSION_EXPIRED
    }
    override fun signOut() {
        session = null
        store.clear()
        connection = ProviderConnection.NOT_CONNECTED
    }

    companion object {
        val SEINFELD = ProviderChannelId("9now", "seinfeld")
        private const val AUTH = "https://login.nine.com.au/api/device"
        private const val LIVE = "https://api.9now.com.au/ctv/livestreams"
        private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
        internal fun isHttps(value: String) = runCatching {
            val uri = URI(value)
            uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.rawUserInfo == null
        }.getOrDefault(false)
        private fun rows(data: JSONObject): List<JSONObject> {
            val array = data.optJSONArray("channels") ?: return emptyList()
            return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
        }
        /** Catalogue records intentionally omit stream/session URLs. Only the one proof channel. */
        internal fun parseChannels(data: JSONObject): List<ProviderChannel> = rows(data).mapNotNull { row ->
            if (row.optString("slug") != SEINFELD.channel) return@mapNotNull null
            val reference = row.optString("referenceId")
            if (reference.isBlank()) return@mapNotNull null
            val name = row.optString("name").ifBlank { "Seinfeld 24/7" }
            val logo = row.optJSONObject("image")?.optJSONObject("sizes")?.optString("w768")
                ?.takeIf(::isHttps)
            ProviderChannel(SEINFELD, reference, name, logo)
        }.distinctBy { it.id }
    }
}

/** token applies only to the catalogue request, never to playback/CDN hosts. */
fun interface NineNowTransport {
    fun request(method: String, url: String, accessToken: String?): JSONObject
}
/** Convenience preserves a small fakeable transport while keeping token optional for auth calls. */
private fun NineNowTransport.request(method: String, url: String): JSONObject = request(method, url, null)
