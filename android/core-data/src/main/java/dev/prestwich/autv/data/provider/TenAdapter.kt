package dev.prestwich.autv.data.provider

import org.json.JSONObject
import java.net.URI

class TenPlaybackDescriptor(val data: JSONObject, val daiAuth: String, val expiresAt: Long? = null, val mimeType: String = "application/x-mpegURL") {
    init { require(mimeType in setOf("application/x-mpegURL","application/dash+xml")) }
    override fun toString() = "TenPlaybackDescriptor(redacted)"
}
/** Implement with registered client access; no private X-N10 signing key or platform disguise here. */
interface TenProviderAccess {
    fun generateCode(deviceId: String): JSONObject
    fun validateCode(code: String, deviceId: String, expirySeconds: Long): JSONObject
    fun refresh(alternativeToken: String, refreshToken: String): JSONObject
    fun liveChannels(region: String): JSONObject
    fun playback(officialDescriptorUrl: String, bearer: String): TenPlaybackDescriptor
}
/** Catalogue identities remain subject to explicit registered live-channel grants. */
class TenAdapter(private val access: TenProviderAccess, private val store: ProviderSessionStore,
    private val pendingStore: ProviderActivationStore, private val deviceId: String,
    private val proofChannelKey: String, private val daiNetworkCode: String? = null,
    private val now: ()->Long = System::currentTimeMillis,
    authorisedChannelKeys: Set<String> = setOf(proofChannelKey)) : ProviderAdapter {
    private val authorisedChannelKeys = authorisedChannelKeys.toSet()
    private var session=store.read()
    override var pendingActivation=pendingStore.readActivation()?.takeIf { it.verificationUrl==ACTIVATION && it.expiresAt>now() && it.deviceCode==deviceId }; private set
    override var connection=when { session!=null->ProviderConnection.CONNECTED;pendingActivation!=null->ProviderConnection.CONNECTING;else->ProviderConnection.NOT_CONNECTED }; private set
    init { require(proofChannelKey.isNotBlank() && proofChannelKey in this.authorisedChannelKeys);
        require(this.authorisedChannelKeys.all { it.isNotBlank() && it.length<=128 && it.none(Char::isISOControl) });if(pendingActivation==null) pendingStore.clearActivation() }
    override fun beginSignIn(): DeviceActivation {
        pendingActivation?.takeIf { it.expiresAt>now() }?.let { return it }
        val data=call(ProviderPlaybackStage.TEN_ACTIVATION) { access.generateCode(deviceId) }
        val code=text(data,"code") ?: unsupported()
        val expiry=(data.optDouble("expiry",0.0)*1000).toLong()
        if(code.length>32 || expiry<=now() || expiry>now()+1800000) unsupported()
        return DeviceActivation(code,deviceId,ACTIVATION,expiry,5000).also {
            pendingStore.writeActivation(it);pendingActivation=it;connection=ProviderConnection.CONNECTING
        }
    }
    override fun pollSignIn(activation: DeviceActivation): Boolean {
        if(pendingActivation?.userCode!=activation.userCode || pendingActivation?.expiresAt!=activation.expiresAt || activation.deviceCode!=deviceId) throw ProviderException(ProviderFailure.ACTIVATION_DENIED)
        if(now()>=activation.expiresAt) terminal(ProviderFailure.ACTIVATION_EXPIRED)
        val data=call(ProviderPlaybackStage.TEN_POLL) { access.validateCode(activation.userCode,deviceId,activation.expiresAt/1000) }
        when(data.optString("error").lowercase()) {
            "access_denied", "rejected"->terminal(ProviderFailure.ACTIVATION_DENIED)
            "expired_code", "expired_token"->terminal(ProviderFailure.ACTIVATION_EXPIRED)
        }
        val jwt=data.optJSONObject("jwt") ?: run {
            if (data.length()==0 || data.optString("error") in setOf("authorization_pending","pending")) return false
            throw ProviderException(ProviderFailure.AUTH_RESPONSE_UNSUPPORTED,stage=ProviderPlaybackStage.TEN_POLL)
        }
        save(jwt);pendingStore.clearActivation();pendingActivation=null
        return true
    }
    private fun save(jwt: JSONObject) {
        val token=text(jwt,"alternativeToken") ?: unsupported()
        val refresh=text(jwt,"refreshToken") ?: session?.refreshToken ?: unsupported()
        val lifetime=jwt.optLong("expiresIn")
        if(lifetime !in 31..2592000) unsupported()
        val next=ProviderSession(token,refresh,now()+lifetime*1000-30000)
        store.write(next);session=next;connection=ProviderConnection.CONNECTED
    }
    private fun bearer(): String {
        val current=session ?: throw ProviderException(ProviderFailure.AUTH_REQUIRED)
        if(current.expiresAt>now()) return current.accessToken
        try { save(call(ProviderPlaybackStage.TEN_REFRESH) { access.refresh(current.accessToken,current.refreshToken) }) } catch(error: ProviderException) {
            if(error.failure in setOf(ProviderFailure.AUTH_REQUIRED,ProviderFailure.SESSION_EXPIRED)) {
                store.clear();session=null;connection=ProviderConnection.SESSION_EXPIRED
            }
            throw error
        }
        return session!!.accessToken
    }
    private fun catalogue(): List<JSONObject> {
        bearer()
        val rows=call(ProviderPlaybackStage.TEN_CATALOGUE) { access.liveChannels("VIC") }.optJSONArray("channels") ?: throw ProviderException(ProviderFailure.UNAVAILABLE)
        return (0 until rows.length()).map { rows.getJSONObject(it) }.also { entries ->
            val keys=entries.map { text(it,"key") ?: unsupported() }
            if(keys.distinct().size!=keys.size) unsupported()
        }
    }
    override fun channels()=catalogue().filter { text(it,"key") in authorisedChannelKeys }.map { row ->
        val key=text(row,"key")!!
        ProviderChannel(ProviderChannelId("ten",key),key,text(row,"name") ?: unsupported(),logoUrl=text(row,"logoUrl"),programmes=schedule(row))
    }
    private fun schedule(row: JSONObject): List<ProviderProgramme> {
        val rows=row.optJSONArray("schedule") ?: return emptyList()
        return (0 until minOf(rows.length(),200)).mapNotNull { index ->
            val event=rows.optJSONObject(index) ?: return@mapNotNull null
            val title=text(event,"title")?.takeIf { it!="No Stream Available" } ?: return@mapNotNull null
            fun date(key: String)=runCatching { java.time.Instant.parse(event.getString(key)).toEpochMilli() }.getOrNull()
            val start=date("startTime") ?: return@mapNotNull null
            val end=date("endTime") ?: return@mapNotNull null
            if(end<=start) null else ProviderProgramme(title,start,end)
        }.sortedBy { it.start }
    }
    override fun resolve(channel: ProviderChannelId): ProviderPlaybackRequest {
        if(channel.provider!="ten" || channel.channel !in authorisedChannelKeys) throw ProviderException(ProviderFailure.PLAYBACK_UNSUPPORTED)
        val row=catalogue().singleOrNull { text(it,"key")==channel.channel } ?: throw ProviderException(ProviderFailure.UNAVAILABLE)
        val url=text(row,"streamUrl") ?: unsupported()
        val uri=runCatching { URI(url) }.getOrNull() ?: unsupported()
        val origin=runCatching { ProviderOriginHeaders.origin(url) }.getOrNull()
        val host=uri.host ?: unsupported()
        if(origin==null || !(host=="10.com.au" || host.endsWith(".10.com.au") || host=="tenplay.com.au" || host.endsWith(".tenplay.com.au")) || !uri.path.startsWith("/api/")) unsupported()
        // TenHttpAccess independently enforces the registration's exact allowedOrigins.
        val descriptor=call(ProviderPlaybackStage.TEN_DESCRIPTOR) { access.playback(url,bearer()) }
        if (descriptor.expiresAt?.let { it <= now() } == true)
            throw ProviderException(ProviderFailure.RETRY_LATER,stage=ProviderPlaybackStage.TEN_DESCRIPTOR)
        val live=try { TenLiveProtocol.liveRequest(descriptor.data,descriptor.daiAuth) }
            catch(error: ProviderException) { throw ProviderException(error.failure,error.httpStatus,error.stage ?: ProviderPlaybackStage.TEN_DESCRIPTOR) }
        return ProviderPlaybackRequest(channel,url,descriptor.mimeType,options=ProviderMediaOptions(
            imaLive=ProviderImaLive(live.assetKey,networkCode=daiNetworkCode,authToken=live.authToken),expiresAt=descriptor.expiresAt))
    }
    override fun signOut() { pendingStore.clearActivation();pendingActivation=null;session=null;store.clear();connection=ProviderConnection.NOT_CONNECTED }
    private inline fun <T> call(stage: ProviderPlaybackStage, action: ()->T): T = try { action() }
    catch(error: ProviderException) {
        if (error.failure==ProviderFailure.SESSION_EXPIRED && stage in setOf(ProviderPlaybackStage.TEN_REFRESH,ProviderPlaybackStage.TEN_DESCRIPTOR)) {
            store.clear();session=null;connection=ProviderConnection.SESSION_EXPIRED
        }
        throw ProviderException(error.failure,error.httpStatus,error.stage ?: stage)
    }
    catch(_: java.io.IOException) { throw ProviderException(ProviderFailure.OFFLINE,stage=stage) }
    catch(_: Exception) { throw ProviderException(ProviderFailure.UNAVAILABLE,stage=stage) }
    private fun terminal(failure: ProviderFailure): Nothing {
        pendingStore.clearActivation();pendingActivation=null;connection=if(session==null) ProviderConnection.NOT_CONNECTED else ProviderConnection.CONNECTED
        throw ProviderException(failure)
    }
    companion object {
        const val ACTIVATION="https://10.com.au/activate"
        private fun text(json: JSONObject,key: String)=(json.opt(key) as? String)?.takeIf { it.isNotBlank() && it.length<=16384 && it.none { c->c.isISOControl() } }
        private fun unsupported(): Nothing=throw ProviderException(ProviderFailure.AUTH_RESPONSE_UNSUPPORTED)
    }
}
