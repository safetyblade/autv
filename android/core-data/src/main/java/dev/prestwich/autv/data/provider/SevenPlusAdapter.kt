package dev.prestwich.autv.data.provider

import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder

/** Experimental account/catalogue and one original clear SSAI HLS proof; no DRM or CSAI bypass. */
class SevenPlusAdapter(
    private val transport: SevenPlusTransport,
    private val store: ProviderSessionStore,
    private val activationStore: ProviderActivationStore,
    private val deviceId: String,
    private val now: () -> Long = System::currentTimeMillis,
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
    private fun form() = linkedMapOf("platformId" to "androidtv", "regSource" to "7plus")
    override fun beginSignIn(): DeviceActivation {
        pendingActivation?.takeIf { it.expiresAt > now() }?.let { return it }
        val response = transport.request("POST", "$AUTH/account/device/authorize", form().apply {
            put("deviceId", deviceId)
            // Do not disable the broadcaster's location verification.
            put("locationVerificationRequired", "true")
        })
        val code = text(response, "user_code", 32)
        val device = text(response, "device_code", 4096)
        val expiry = response.optLong("expires_in").coerceIn(0, 1800)
        // Only review the destination, never copy the complete URL or private query into a QR.
        val address = text(response, "verification_uri") ?: text(response, "verification_uri_complete")
        val destination = runCatching { URI(address ?: "") }.getOrNull()
        if (code == null || device == null || expiry == 0L || destination?.scheme != "https" ||
            destination.host != "7plus.com.au" || destination.path != "/connect" || destination.port != -1 ||
            destination.rawUserInfo != null || destination.fragment != null)
            throw ProviderException(ProviderFailure.AUTH_RESPONSE_UNSUPPORTED)
        val challenge = DeviceActivation(code, device, ACTIVATION, now() + expiry * 1000,
            response.optLong("interval", 5).coerceIn(1, 60) * 1000)
        activationStore.writeActivation(challenge); pendingActivation = challenge
        connection = ProviderConnection.CONNECTING
        return challenge
    }
    override fun pollSignIn(activation: DeviceActivation): Boolean {
        if (activation !== pendingActivation) throw ProviderException(ProviderFailure.ACTIVATION_DENIED)
        if (now() >= activation.expiresAt) terminal(ProviderFailure.ACTIVATION_EXPIRED)
        val response = transport.request("POST", "$AUTH/connect/token", form().apply { put("deviceCode", activation.deviceCode) })
        if (text(response, "access_token") != null) { save(response); return true }
        when (response.optString("error")) {
            "authorization_pending" -> return false
            "slow_down", "temporarily_unavailable" -> throw ProviderException(ProviderFailure.RETRY_LATER)
            "expired_token" -> terminal(ProviderFailure.ACTIVATION_EXPIRED)
            "access_denied", "authorization_declined", "invalid_grant" -> terminal(ProviderFailure.ACTIVATION_DENIED)
            // Unknown and empty responses are not evidence of pending approval.
            else -> throw ProviderException(ProviderFailure.AUTH_RESPONSE_UNSUPPORTED)
        }
    }
    private fun save(response: JSONObject) {
        val token = text(response, "access_token") ?: throw ProviderException(ProviderFailure.AUTH_RESPONSE_UNSUPPORTED)
        val refresh = text(response, "refresh_token") ?: session?.refreshToken
            ?: throw ProviderException(ProviderFailure.AUTH_RESPONSE_UNSUPPORTED)
        val seconds = response.optLong("expires_in")
        if (seconds !in 31..2592000) throw ProviderException(ProviderFailure.AUTH_RESPONSE_UNSUPPORTED)
        val updated = ProviderSession(token, refresh, now() + seconds * 1000 - 30000)
        store.write(updated); session = updated; activationStore.clearActivation(); pendingActivation = null
        connection = ProviderConnection.CONNECTED
    }
    private fun requireSession() {
        val current = session ?: throw ProviderException(ProviderFailure.AUTH_REQUIRED)
        if (current.expiresAt > now()) return
        try { save(transport.request("POST", "$AUTH/connect/token", form().apply { put("refreshToken", current.refreshToken) })) }
        catch (failure: ProviderException) {
            if (failure.failure in setOf(ProviderFailure.OFFLINE, ProviderFailure.RETRY_LATER)) throw failure
            store.clear(); session = null; connection = ProviderConnection.SESSION_EXPIRED
            throw ProviderException(ProviderFailure.SESSION_EXPIRED)
        }
    }
    private data class Catalogue(val market: String, val rows: List<JSONObject>)
    private fun catalogue(): Catalogue {
        requireSession()
        val market = text(transport.request("GET", MARKET), "_id", 128)
            ?: throw ProviderException(ProviderFailure.REGION_DENIED)
        val sections = transport.request("GET", "$CONTENT?${contentQuery(market)}").optJSONArray("items")
            ?: throw ProviderException(ProviderFailure.UNAVAILABLE)
        val rows = linkedMapOf<String, JSONObject>()
        for (index in 0 until sections.length()) {
            val section = sections.optJSONObject(index) ?: continue
            if (section.optString("childType") != "channelItem") continue
            val items = section.optJSONArray("mediaItems") ?: continue
            for (n in 0 until items.length()) {
                val row = items.optJSONObject(n) ?: continue
                if (row.optString("type") != "ChannelItem") continue
                val id = text(row, "channelId", 128) ?: continue
                if (text(row, "name", 256) != null) rows.putIfAbsent(id, row)
            }
        }
        return Catalogue(market, rows.values.toList())
    }
    override fun channels(): List<ProviderChannel> = catalogue().rows.map { row ->
        val id = row.optString("channelId")
        ProviderChannel(ProviderChannelId("seven", id), id, row.getString("name"))
    }
    override fun resolve(channel: ProviderChannelId): ProviderPlaybackRequest {
        if (channel.provider != "seven") throw ProviderException(ProviderFailure.UNAVAILABLE)
        val catalogue = step(ProviderPlaybackStage.SEVEN_CATALOGUE) { catalogue() }
        val row = step(ProviderPlaybackStage.SEVEN_CHANNEL) {
            val selected = catalogue.rows.singleOrNull { it.optString("channelId") == channel.channel }
                ?: throw ProviderException(ProviderFailure.UNAVAILABLE)
            // One primary-network proof. Official IDs are never invented or replaced with IPTV slugs.
            if (selected.optString("name").trim() !in setOf("7", "Seven", "Channel 7", "7HD"))
                throw ProviderException(ProviderFailure.PLAYBACK_UNSUPPORTED)
            selected
        }
        val slug = step(ProviderPlaybackStage.SEVEN_LINK) {
            val link = text(row.optJSONObject("contentLink") ?: JSONObject(), "url")
                ?: throw ProviderException(ProviderFailure.PLAYBACK_UNSUPPORTED)
            channelSlug(link)
        }
        val video = step(ProviderPlaybackStage.SEVEN_PLAYER) {
            val player = transport.request("GET", "$PLAYER/$slug?${contentQuery(catalogue.market)}&signedUp=True")
                .optJSONObject("videoPlayer") ?: throw ProviderException(ProviderFailure.PLAYBACK_UNSUPPORTED)
            player.opt("videoUrl")
        }
        val identity = step(ProviderPlaybackStage.SEVEN_IDENTITY) { SevenPlaybackIdentity.read(video) }
        val parameters = linkedMapOf(
            "appId" to "7plus", "deviceType" to "androidtv", "platformType" to "tv",
            "deviceId" to deviceId, "accountId" to identity.account, "referenceId" to identity.reference,
            "marketId" to catalogue.market, "videoType" to "live",
        )
        // No invented postcode, tracking identity, SDK attestation or ad-delivery override.
        val descriptor = try { step(ProviderPlaybackStage.SEVEN_DESCRIPTOR) { transport.playback(parameters, session!!.accessToken) } }
        catch (error: ProviderException) {
            if (error.failure == ProviderFailure.SESSION_EXPIRED) {
                store.clear(); session = null; connection = ProviderConnection.SESSION_EXPIRED
            }
            throw error
        }
        return step(ProviderPlaybackStage.SEVEN_SOURCE) { SevenLiveSource.select(channel, descriptor) }
    }
    private inline fun <T> step(stage: ProviderPlaybackStage, action: () -> T): T = try { action() }
    catch (error: ProviderException) { throw ProviderException(error.failure, error.httpStatus, error.stage ?: stage) }
    catch (_: Exception) { throw ProviderException(ProviderFailure.PLAYBACK_UNSUPPORTED, stage = stage) }
    private fun contentQuery(market: String) = "platform-id=androidtv&market-id=${URLEncoder.encode(market, "UTF-8")}&platform-version=5.25.0.0&api-version=5.9.0.0"
    private fun channelSlug(link: String): String {
        val returned = runCatching { URI(link) }.getOrNull()
            ?: throw ProviderException(ProviderFailure.PLAYBACK_UNSUPPORTED)
        // SlyGuy extracts a slug from both relative and absolute content links.
        // Resolve metadata against the official origin; never follow this link over the network.
        if (returned.scheme == null && returned.rawAuthority != null)
            throw ProviderException(ProviderFailure.PLAYBACK_UNSUPPORTED)
        val uri = URI("https://7plus.com.au/").resolve(returned)
        if (uri.scheme != "https" || uri.host != "7plus.com.au" || uri.port != -1 || uri.rawUserInfo != null || uri.fragment != null)
            throw ProviderException(ProviderFailure.PLAYBACK_UNSUPPORTED)
        val query = query(uri)
        val slug = query["channel-id"] ?: uri.path.trimEnd('/').substringAfterLast('/')
        if (!slug.matches(Regex("[A-Za-z0-9_-]{1,128}"))) throw ProviderException(ProviderFailure.PLAYBACK_UNSUPPORTED)
        return slug
    }
    private fun query(uri: URI): Map<String, String> = try {
        val pairs = uri.rawQuery?.split('&')?.map {
            val parts = it.split('=', limit = 2)
            require(parts.size == 2)
            java.net.URLDecoder.decode(parts[0], "UTF-8") to java.net.URLDecoder.decode(parts[1], "UTF-8")
        }.orEmpty()
        require(pairs.map { it.first }.distinct().size == pairs.size)
        pairs.toMap()
    } catch (_: Exception) { throw ProviderException(ProviderFailure.PLAYBACK_UNSUPPORTED) }
    private fun terminal(failure: ProviderFailure): Nothing {
        activationStore.clearActivation(); pendingActivation = null; connection = ProviderConnection.NOT_CONNECTED
        throw ProviderException(failure)
    }
    override fun signOut() {
        activationStore.clearActivation(); pendingActivation = null; session = null; store.clear()
        connection = ProviderConnection.NOT_CONNECTED
    }
    companion object {
        const val ACTIVATION = "https://7plus.com.au/connect"
        const val AUTH = "https://auth2.swm.digital"
        const val MARKET = "https://market-cdn.swm.digital/v1/market/ip/?apikey=web"
        const val CONTENT = "https://component-cdn.swm.digital/content/live-tv"
        const val PLAYER = "https://component.swm.digital/player/live"
        const val PLAYBACK = "https://videoservice.swm.digital/playback"
        private fun text(json: JSONObject, key: String, limit: Int = 16384) = json.optString(key).takeUnless {
            it.isBlank() || it == "null" || it.length > limit || it.any { c -> c.isISOControl() }
        }
    }
}

/** No bearer is needed for catalogue calls; credentials remain on the account token endpoint. */
fun interface SevenPlusTransport {
    fun request(method: String, url: String, form: Map<String, String>): JSONObject
    fun playback(parameters: Map<String, String>, accessToken: String): JSONObject =
        throw ProviderException(ProviderFailure.PLAYBACK_UNSUPPORTED)
}
private fun SevenPlusTransport.request(method: String, url: String) = request(method, url, emptyMap())
