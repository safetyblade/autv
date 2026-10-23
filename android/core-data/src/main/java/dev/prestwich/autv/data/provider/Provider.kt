package dev.prestwich.autv.data.provider

/** Identity is independent of account credentials, playback URLs and local guide numbers. */
data class ProviderChannelId(val provider: String, val channel: String) {
    init { require(provider.isNotBlank() && channel.isNotBlank()) }
    val favouriteKey get() = "provider:$provider:$channel"
}
data class ProviderChannel(
    val id: ProviderChannelId,
    val reference: String,
    val name: String,
    val logoUrl: String? = null,
    val programmes: List<ProviderProgramme> = emptyList(),
)
data class ProviderProgramme(val title: String, val start: Long, val end: Long)
enum class ProviderConnection { NOT_CONNECTED, CONNECTING, CONNECTED, SESSION_EXPIRED, UNSUPPORTED }
enum class ProviderFailure {
    AUTH_REQUIRED, SESSION_EXPIRED, REGION_DENIED, UNAVAILABLE, UNSUPPORTED, OFFLINE, DRM_UNSUPPORTED, PLAYBACK_UNSUPPORTED, RETRY_LATER,
    AD_SESSION_UNSUPPORTED, ACTIVATION_EXPIRED, ACTIVATION_DENIED, AUTH_RESPONSE_UNSUPPORTED
}
/** Never propagate raw transport errors, URLs, server bodies or credentials to UI/logs. */
class ProviderException(val failure: ProviderFailure, val httpStatus: Int? = null) : Exception(failure.name)

/** Sensitive values deliberately have no data-class toString/copy/serialization. */
class ProviderSession(val accessToken: String, val refreshToken: String, val expiresAt: Long) {
    override fun toString() = "ProviderSession(redacted)"
}
class DeviceActivation(
    val userCode: String, val deviceCode: String,
    val verificationUrl: String, val expiresAt: Long, val pollIntervalMillis: Long,
) {
    override fun toString() = "DeviceActivation(redacted)"
}
class ProviderPlaybackRequest(
    val channelId: ProviderChannelId,
    val uri: String,
    val mimeType: String,
    val headers: Map<String, String> = emptyMap(),
) {
    override fun toString() = "ProviderPlaybackRequest(redacted)"
}
interface ProviderSessionStore {
    fun read(): ProviderSession?
    fun write(session: ProviderSession)
    fun clear()
}
interface ProviderActivationStore {
    fun readActivation(): DeviceActivation?
    fun writeActivation(activation: DeviceActivation)
    fun clearActivation()
}
class MemoryProviderActivationStore : ProviderActivationStore {
    @Volatile private var value: DeviceActivation? = null
    override fun readActivation() = value
    override fun writeActivation(activation: DeviceActivation) { value = activation }
    override fun clearActivation() { value = null }
}
/** Counts only: no account identifiers, channel list, codes or signed playback URLs. */
data class ProviderDiscovery(val channels: Int, val events: Int, val matches: Int)
interface ProviderAdapter {
    val discovery: ProviderDiscovery? get() = null
    val pendingActivation: DeviceActivation? get() = null
    val connection: ProviderConnection
    fun beginSignIn(): DeviceActivation
    /** false means approval is still pending; provider interval/expiry must be respected. */
    fun pollSignIn(activation: DeviceActivation): Boolean
    fun channels(): List<ProviderChannel>
    fun resolve(channel: ProviderChannelId): ProviderPlaybackRequest
    fun signOut()
}
