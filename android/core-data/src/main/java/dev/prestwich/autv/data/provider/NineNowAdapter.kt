package dev.prestwich.autv.data.provider

import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.util.Locale

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

    override var discovery: ProviderDiscovery? = null
        private set
    override fun channels(): List<ProviderChannel> {
        val data = catalogue()
        discovery = ProviderDiscovery(data.optJSONArray("channels")?.length() ?: 0,
            data.optJSONArray("events")?.length() ?: 0, seinfeldRows(data).size)
        return parseChannels(data)
    }

    override fun resolve(channel: ProviderChannelId): ProviderPlaybackRequest {
        if (channel != SEINFELD) throw ProviderException(ProviderFailure.UNAVAILABLE)
        val data = catalogue()
        val row = seinfeldRows(data).singleOrNull()
            ?: throw ProviderException(ProviderFailure.UNAVAILABLE)
        val stream = row.optJSONObject("stream") ?: throw ProviderException(ProviderFailure.PLAYBACK_UNSUPPORTED)
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
        discovery = null
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
        private fun entries(data: JSONObject, key: String): List<JSONObject> {
            val array = data.optJSONArray(key) ?: return emptyList()
            return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
        }
        private fun normalized(value: String) = value.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
        private fun seinfeldRows(data: JSONObject): List<JSONObject> {
            // The upstream live catalogue includes some linear services as live events.
            val rows = entries(data, "channels") + entries(data, "events").filter {
                it.optString("type") == "live-event"
            }
            val names = setOf("seinfeld", "seinfeld247")
            return rows.filter { row ->
                row.optString("referenceId").isNotBlank() &&
                    listOf("slug", "name", "displayName").any { normalized(row.optString(it)) in names }
            }.groupBy { it.optString("referenceId") }.values.map { duplicates ->
                // A metadata-only row and its live-event row may describe the same identity.
                duplicates.firstOrNull { it.optJSONObject("stream")?.optString("url")?.let(::isHttps) == true }
                    ?: duplicates.first()
            }
        }
        /** Require one identity; never pick arbitrarily from ambiguous matching services. */
        internal fun parseChannels(data: JSONObject): List<ProviderChannel> {
            val row = seinfeldRows(data).singleOrNull() ?: return emptyList()
            val name = row.optString("name").ifBlank { row.optString("displayName") }.ifBlank { "Seinfeld 24/7" }
            val logo = row.optJSONObject("image")?.optJSONObject("sizes")?.optString("w768")
                ?.takeIf(::isHttps)
            return listOf(ProviderChannel(SEINFELD, row.optString("referenceId"), name, logo))
        }
    }
}

/** token applies only to the catalogue request, never to playback/CDN hosts. */
fun interface NineNowTransport {
    fun request(method: String, url: String, accessToken: String?): JSONObject
}
/** Convenience preserves a small fakeable transport while keeping token optional for auth calls. */
private fun NineNowTransport.request(method: String, url: String): JSONObject = request(method, url, null)
