package dev.prestwich.autv.data.provider

import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.util.Locale

/** Experimental device activation + original provider-returned HLS. No password or DAI rewriting. */
class SbsAdapter(
    private val transport: SbsTransport,
    private val store: ProviderSessionStore,
    private val activationStore: ProviderActivationStore,
    private val deviceId: String,
    private val now: () -> Long = System::currentTimeMillis,
    private val decodePayload: (String) -> String = {
        String(android.util.Base64.decode(it, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP), Charsets.UTF_8)
    },
) : ProviderAdapter {
    private var session = store.read()
    override var pendingActivation = activationStore.readActivation()?.takeIf {
        session == null && it.expiresAt > now() && it.verificationUrl == ACTIVATION
    }; private set
    override var connection = when {
        session != null -> ProviderConnection.CONNECTED
        pendingActivation != null -> ProviderConnection.CONNECTING
        else -> ProviderConnection.NOT_CONNECTED
    }; private set
    init { if (pendingActivation == null) activationStore.clearActivation() }
    override fun beginSignIn(): DeviceActivation {
        pendingActivation?.takeIf { it.expiresAt > now() }?.let { return it }
        val response = transport.request("POST", "$AUTH/device/link", JSONObject().put("deviceName", DEVICE_NAME), null)
        val code = response.optString("userCode").takeUnless { it.isBlank() || it == "null" || it.length > 32 }
        val device = response.optString("deviceCode").takeUnless { it.isBlank() || it == "null" || it.length > 4096 }
        val lifetime = response.optLong("expiresIn").coerceIn(0, 1800)
        val returnedAddress = response.optString("verifyURI")
        val address = if (returnedAddress.startsWith("https://")) returnedAddress else "https://$returnedAddress"
        if (code == null || device == null || lifetime == 0L || address !in setOf(ACTIVATION, "https://www.sbs.com.au/connect"))
            throw ProviderException(ProviderFailure.AUTH_RESPONSE_UNSUPPORTED)
        // qrCodeURI is deliberately not trusted or encoded: only the reviewed official bare URL.
        val challenge = DeviceActivation(code, device, ACTIVATION, now() + lifetime * 1000,
            response.optLong("interval", 5).coerceIn(1, 60) * 1000)
        activationStore.writeActivation(challenge)
        pendingActivation = challenge
        connection = ProviderConnection.CONNECTING
        return challenge
    }
    override fun pollSignIn(activation: DeviceActivation): Boolean {
        if (now() >= activation.expiresAt) terminal(ProviderFailure.ACTIVATION_EXPIRED)
        val response = transport.request("POST", "$AUTH/device/poll", JSONObject()
            .put("deviceCode", activation.deviceCode).put("refreshTokenInBody", true), null)
        if (text(response, "accessToken") == null) {
            val error = when (val raw = response.opt("error")) {
                is JSONObject -> raw.optString("code")
                is String -> raw
                else -> ""
            }.lowercase(Locale.ROOT)
            when (error) {
                "", "pending", "authorization_pending" -> return false
                "slow_down", "temporarily_unavailable" -> throw ProviderException(ProviderFailure.RETRY_LATER)
                "expired_token", "expired_code" -> terminal(ProviderFailure.ACTIVATION_EXPIRED)
                "access_denied", "invalid_grant", "invalid_code", "authorization_declined" -> terminal(ProviderFailure.ACTIVATION_DENIED)
                else -> throw ProviderException(ProviderFailure.AUTH_RESPONSE_UNSUPPORTED)
            }
        }
        save(response)
        return true
    }
    private fun terminal(failure: ProviderFailure): Nothing {
        activationStore.clearActivation(); pendingActivation = null
        connection = ProviderConnection.NOT_CONNECTED
        throw ProviderException(failure)
    }
    private fun save(response: JSONObject) {
        val token = text(response, "accessToken") ?: throw ProviderException(ProviderFailure.AUTH_RESPONSE_UNSUPPORTED)
        val refresh = text(response, "refreshToken") ?: session?.refreshToken
            ?: throw ProviderException(ProviderFailure.AUTH_RESPONSE_UNSUPPORTED)
        val expiry = runCatching {
            val payload = token.split('.').takeIf { it.size == 3 }?.get(1) ?: error("Invalid token")
            require(payload.length <= 16384)
            JSONObject(decodePayload(payload)).getLong("exp") * 1000
        }.getOrElse { throw ProviderException(ProviderFailure.AUTH_RESPONSE_UNSUPPORTED) }
        if (expiry <= now() + 30_000)
            throw ProviderException(ProviderFailure.AUTH_RESPONSE_UNSUPPORTED)
        val updated = ProviderSession(token, refresh, minOf(expiry, now() + 30L * 86400_000) - 30_000)
        store.write(updated); session = updated
        activationStore.clearActivation(); pendingActivation = null
        connection = ProviderConnection.CONNECTED
    }
    private fun access(): String {
        val current = session ?: throw ProviderException(ProviderFailure.AUTH_REQUIRED)
        if (current.expiresAt > now()) return current.accessToken
        try {
            save(transport.request("POST", "$AUTH/refresh", JSONObject().put("deviceName", DEVICE_NAME)
                .put("refreshToken", current.refreshToken).put("refreshTokenInBody", true), null))
        } catch (error: ProviderException) {
            if (error.failure in setOf(ProviderFailure.OFFLINE, ProviderFailure.RETRY_LATER)) throw error
            expire(); throw ProviderException(ProviderFailure.SESSION_EXPIRED)
        }
        return session!!.accessToken
    }
    private fun accountRequest(method: String, url: String, body: JSONObject? = null): JSONObject = try {
        transport.request(method, url, body, access())
    } catch (error: ProviderException) {
        if (error.failure == ProviderFailure.SESSION_EXPIRED) expire()
        throw error
    }
    private fun catalogue(): List<JSONObject> {
        val response = accountRequest("GET", "$EPG/guides-v2?limit=0")
        val items = response.optJSONArray("items") ?: throw ProviderException(ProviderFailure.UNAVAILABLE)
        return (0 until items.length()).mapNotNull { items.optJSONObject(it)?.optJSONObject("channel") }
            .filter { text(it, "mpxMediaID") != null && text(it, "title") != null }
            .distinctBy { it.optString("mpxMediaID") }
    }
    override fun channels(): List<ProviderChannel> = catalogue().map { row ->
        val reference = row.optString("mpxMediaID")
        ProviderChannel(ProviderChannelId("sbs", reference), reference, row.getString("title"))
    }
    override fun resolve(channel: ProviderChannelId): ProviderPlaybackRequest {
        if (channel.provider != "sbs" || catalogue().none { it.optString("mpxMediaID") == channel.channel })
            throw ProviderException(ProviderFailure.UNAVAILABLE)
        val body = JSONObject().put("advertising", JSONObject().put("deviceID", deviceId)
            .put("deviceTargetingOptOut", true).put("sbsTargetingOptOut", true).put("subtitle", "en").put("resume", false))
            .put("deviceClass", "androidtv").put("streamOptions", JSONObject().put("audio", "demuxed"))
        val response = accountRequest("POST", "$PLAYBACK/stream/${URLEncoder.encode(channel.channel, "UTF-8")}", body)
        if (protected(response)) throw ProviderException(ProviderFailure.DRM_UNSUPPORTED)
        val providers = response.optJSONArray("streamProviders") ?: throw ProviderException(ProviderFailure.PLAYBACK_UNSUPPORTED)
        var drm = false
        var dai = false
        for (index in 0 until providers.length()) {
            val provider = providers.optJSONObject(index) ?: continue
            if (protected(provider)) { drm = true; continue }
            when (provider.optString("type")) {
                "GoogleDAI" -> dai = true
                "HLS" -> text(provider, "url")?.takeIf(NineNowAdapter::isHttps)?.let {
                    // Original URL including quality, originpath and ad/session query is retained verbatim.
                    return ProviderPlaybackRequest(channel, it, "application/x-mpegURL")
                }
            }
        }
        throw ProviderException(when { drm -> ProviderFailure.DRM_UNSUPPORTED; dai -> ProviderFailure.AD_SESSION_UNSUPPORTED; else -> ProviderFailure.PLAYBACK_UNSUPPORTED })
    }
    private fun expire() { session = null; store.clear(); connection = ProviderConnection.SESSION_EXPIRED }
    override fun signOut() {
        // Local sign-out is deterministic even offline; revoke only this provider's stored identity.
        activationStore.clearActivation(); pendingActivation = null
        session = null; store.clear(); connection = ProviderConnection.NOT_CONNECTED
    }
    companion object {
        const val ACTIVATION = "https://sbs.com.au/connect"
        const val AUTH = "https://auth.sbs.com.au"
        const val EPG = "https://tv-epg.pr.sbsod.com"
        const val PLAYBACK = "https://playback.pr.sbsod.com"
        private const val DEVICE_NAME = "AUTV Android TV"
        private fun text(json: JSONObject, key: String) = json.optString(key).takeUnless { it.isBlank() || it == "null" }
        private fun protected(json: JSONObject): Boolean = listOf("drm", "key_systems", "drmType", "licenseUrl", "licenseServerUrl").any {
            json.has(it) && !json.isNull(it) && json.opt(it) != false && json.optString(it).isNotBlank()
        }
    }
}

/** JSON API only. An account bearer must never be attached to a playback/CDN request. */
fun interface SbsTransport {
    fun request(method: String, url: String, body: JSONObject?, accessToken: String?): JSONObject
}
