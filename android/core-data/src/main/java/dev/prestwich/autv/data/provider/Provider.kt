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
    ACTIVATION_EXPIRED, ACTIVATION_DENIED, AUTH_RESPONSE_UNSUPPORTED, AD_SESSION_UNSUPPORTED
}
/** Fixed diagnostic codes only; no provider URLs, IDs or account values. */
enum class ProviderPlaybackStage(val code: String) {
    ABC_ACTIVATION("ABC-ACTIVATION"), ABC_POLL("ABC-POLL"), ABC_TOKEN("ABC-TOKEN"),
    ABC_CATALOGUE("ABC-CATALOGUE"), ABC_PROGRAMME("ABC-PROGRAMME"), ABC_AUTHORISATION("ABC-AUTHORISATION"), ABC_SIGN_OUT("ABC-SIGN-OUT"),
    TEN_CONFIG("10-CONFIG"), TEN_ACTIVATION("10-ACTIVATION"), TEN_POLL("10-POLL"), TEN_REFRESH("10-REFRESH"),
    TEN_CATALOGUE("10-CATALOGUE"), TEN_DESCRIPTOR("10-DESCRIPTOR"),
    SEVEN_CATALOGUE("7-CATALOGUE"), SEVEN_CHANNEL("7-CHANNEL"), SEVEN_LINK("7-LINK"),
    SEVEN_PLAYER("7-PLAYER-METADATA"), SEVEN_IDENTITY("7-IDENTITY"),
    SEVEN_IDENTITY_TYPE("7-IDENTITY-TYPE"), SEVEN_IDENTITY_QUERY("7-IDENTITY-QUERY"),
    SEVEN_IDENTITY_ACCOUNT("7-IDENTITY-ACCOUNT"), SEVEN_IDENTITY_REFERENCE("7-IDENTITY-REFERENCE"),
    SEVEN_IDENTITY_AMBIGUOUS("7-IDENTITY-AMBIGUOUS"), SEVEN_IDENTITY_VALUE("7-IDENTITY-VALUE"),
    SEVEN_DESCRIPTOR("7-DESCRIPTOR"), SEVEN_SOURCE("7-SOURCE"),
    SEVEN_MANIFEST("7-MANIFEST"), SEVEN_DRM("7-DRM"), SEVEN_ADS("7-ADS"), SEVEN_RENDERER("7-PLAYER")
}
/** Never propagate raw transport errors, URLs, server bodies or credentials to UI/logs. */
class ProviderException(val failure: ProviderFailure, val httpStatus: Int? = null, val stage: ProviderPlaybackStage? = null, val sevenRejection: SevenDescriptorRejection? = null) : Exception(failure.name)

/** Sensitive values deliberately have no data-class toString/copy/serialization. */
class ProviderSession(val accessToken: String, val refreshToken: String, val expiresAt: Long, privateContext: Map<String,String> = emptyMap()) {
    val privateContext = privateContext.toMap().also(::validatePrivateContext)
    override fun toString() = "ProviderSession(redacted)"
}
class DeviceActivation(
    val userCode: String, val deviceCode: String,
    val verificationUrl: String, val expiresAt: Long, val pollIntervalMillis: Long,
    privateContext: Map<String,String> = emptyMap(),
) {
    val privateContext = privateContext.toMap().also(::validatePrivateContext)
    override fun toString() = "DeviceActivation(redacted)"
}
class ProviderPlaybackRequest(
    val channelId: ProviderChannelId,
    val uri: String,
    val mimeType: String,
    headers: Map<String, String> = emptyMap(),
    val options: ProviderMediaOptions = ProviderMediaOptions(),
) {
    val headers = headers.toMap().also(ProviderOriginHeaders::validateHeaders)
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

private fun validatePrivateContext(context: Map<String,String>) {
    require(context.size <= 16 && context.all { (key,value) -> key.matches(Regex("[A-Za-z0-9_-]{1,64}")) && value.length <= 4096 && value.none { it.isISOControl() } })
}
