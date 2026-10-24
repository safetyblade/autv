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
/** Main-channel proof only. proofChannelKey must come from an approved current catalogue row.key. */
class TenAdapter(private val access: TenProviderAccess, private val store: ProviderSessionStore,
    private val pendingStore: ProviderActivationStore, private val deviceId: String,
    private val proofChannelKey: String, private val daiNetworkCode: String? = null,
    private val now: ()->Long = System::currentTimeMillis) : ProviderAdapter {
    private var session=store.read()
    override var pendingActivation=pendingStore.readActivation()?.takeIf { it.verificationUrl==ACTIVATION && it.expiresAt>now() && it.deviceCode==deviceId }; private set
    override var connection=when { session!=null->ProviderConnection.CONNECTED;pendingActivation!=null->ProviderConnection.CONNECTING;else->ProviderConnection.NOT_CONNECTED }; private set
    init { require(proofChannelKey.isNotBlank());if(pendingActivation==null) pendingStore.clearActivation() }
    override fun beginSignIn(): DeviceActivation {
        pendingActivation?.takeIf { it.expiresAt>now() }?.let { return it }
        val data=call { access.generateCode(deviceId) }
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
        val data=call { access.validateCode(activation.userCode,deviceId,activation.expiresAt/1000) }
        when(data.optString("error").lowercase()) {
            "access_denied", "rejected"->terminal(ProviderFailure.ACTIVATION_DENIED)
            "expired_code", "expired_token"->terminal(ProviderFailure.ACTIVATION_EXPIRED)
        }
        val jwt=data.optJSONObject("jwt") ?: return false
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
        try { save(call { access.refresh(current.accessToken,current.refreshToken) }) } catch(error: ProviderException) {
            if(error.failure in setOf(ProviderFailure.AUTH_REQUIRED,ProviderFailure.SESSION_EXPIRED)) {
                store.clear();session=null;connection=ProviderConnection.SESSION_EXPIRED
            }
            throw error
        }
        return session!!.accessToken
    }
    private fun catalogue(): List<JSONObject> {
        bearer()
        val rows=call { access.liveChannels("VIC") }.optJSONArray("channels") ?: throw ProviderException(ProviderFailure.UNAVAILABLE)
        return (0 until rows.length()).map { rows.getJSONObject(it) }.also { entries ->
            val keys=entries.map { text(it,"key") ?: unsupported() }
            if(keys.distinct().size!=keys.size) unsupported()
        }
    }
    override fun channels()=catalogue().map { row ->
        val key=text(row,"key")!!
        ProviderChannel(ProviderChannelId("ten",key),key,text(row,"name") ?: unsupported())
    }
    override fun resolve(channel: ProviderChannelId): ProviderPlaybackRequest {
        if(channel.provider!="ten" || channel.channel!=proofChannelKey) throw ProviderException(ProviderFailure.PLAYBACK_UNSUPPORTED)
        val row=catalogue().singleOrNull { text(it,"key")==channel.channel } ?: throw ProviderException(ProviderFailure.UNAVAILABLE)
        val url=text(row,"streamUrl") ?: unsupported()
        val uri=runCatching { URI(url) }.getOrNull() ?: unsupported()
        val origin=runCatching { ProviderOriginHeaders.origin(url) }.getOrNull()
        if(origin!="https://10.com.au" || !uri.path.startsWith("/api/")) unsupported()
        val descriptor=call { access.playback(url,bearer()) }
        val live=TenLiveProtocol.liveRequest(descriptor.data,descriptor.daiAuth)
        return ProviderPlaybackRequest(channel,url,descriptor.mimeType,options=ProviderMediaOptions(
            imaLive=ProviderImaLive(live.assetKey,networkCode=daiNetworkCode,authToken=live.authToken),expiresAt=descriptor.expiresAt))
    }
    override fun signOut() { pendingStore.clearActivation();pendingActivation=null;session=null;store.clear();connection=ProviderConnection.NOT_CONNECTED }
    private inline fun <T> call(action: ()->T): T = try { action() } catch(error: ProviderException) { throw error }
    catch(_: java.io.IOException) { throw ProviderException(ProviderFailure.OFFLINE) }
    catch(_: Exception) { throw ProviderException(ProviderFailure.UNAVAILABLE) }
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
