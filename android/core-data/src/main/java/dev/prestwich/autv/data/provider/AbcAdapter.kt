package dev.prestwich.autv.data.provider

import org.json.JSONObject
import java.time.Instant

/** No network identity/key is embedded. The supplied access implementation must be authorised. */
interface AbcProviderAccess {
    fun link(deviceId: String): JSONObject
    fun linkStatus(deviceId: String, requestId: String?): JSONObject
    fun accountToken(uid: String): JSONObject
    fun unlink(deviceId: String)
    fun home(): JSONObject
    fun collection(id: String): JSONObject
    fun programme(officialUrl: String): JSONObject
    fun authoriseMedia(houseNumber: String, originalHls: String, accountToken: String?): String
}
/** The website's anonymous-live exception is opt-in only after the native access contract is confirmed. */
class AbcAdapter(private val access: AbcProviderAccess, private val store: ProviderSessionStore,
    private val pendingStore: ProviderActivationStore, private val deviceId: String,
    private val proofChannelHouseNumber: String,
    private val anonymousLiveApproved: Boolean = false, private val now: ()->Long = System::currentTimeMillis) : ProviderAdapter {
    private var session = store.read()
    override var pendingActivation = pendingStore.readActivation()?.takeIf { it.verificationUrl == ACTIVATION && it.expiresAt > now() }; private set
    override var connection = when { session != null -> ProviderConnection.CONNECTED; pendingActivation != null -> ProviderConnection.CONNECTING; else -> ProviderConnection.NOT_CONNECTED }; private set
    init { if (pendingActivation == null) pendingStore.clearActivation() }
    override fun beginSignIn(): DeviceActivation {
        pendingActivation?.takeIf { it.expiresAt > now() }?.let { return it }
        val response = unwrap(call { access.link(deviceId) })
        val code = text(response,"code") ?: text(response,"linkCode") ?: unsupported()
        val returnedDevice = text(response,"deviceId") ?: unsupported()
        val expiry = when {
            response.has("expiresIn") -> now()+response.optLong("expiresIn")*1000
            response.has("expiryAt") -> runCatching { Instant.parse(response.getString("expiryAt")).toEpochMilli() }.getOrElse { unsupported() }
            else -> unsupported()
        }
        if (code.length > 32 || expiry <= now() || expiry > now()+1800000) unsupported()
        val interval = response.optLong("interval",3)
        if (interval !in 1..60) unsupported()
        val context = text(response,"requestId")?.let { mapOf("requestId" to it) }.orEmpty()
        return DeviceActivation(code,returnedDevice,ACTIVATION,expiry,interval*1000,context).also {
            pendingStore.writeActivation(it); pendingActivation=it; connection=ProviderConnection.CONNECTING
        }
    }
    override fun pollSignIn(activation: DeviceActivation): Boolean {
        if (pendingActivation?.let { it.deviceCode != activation.deviceCode || it.userCode != activation.userCode || it.expiresAt != activation.expiresAt || it.privateContext != activation.privateContext } != false) throw ProviderException(ProviderFailure.ACTIVATION_DENIED)
        if (now() >= activation.expiresAt) terminal(ProviderFailure.ACTIVATION_EXPIRED)
        val status = unwrap(call { access.linkStatus(activation.deviceCode,activation.privateContext["requestId"]) })
        when (status.optString("status").ifBlank { status.optString("state") }.lowercase()) {
            "denied", "rejected" -> terminal(ProviderFailure.ACTIVATION_DENIED)
            "expired" -> terminal(ProviderFailure.ACTIVATION_EXPIRED)
            "pending", "waiting", "unlinked", "not_linked" -> return false
        }
        val uid = text(status,"UID") ?: text(status,"uid") ?: return false
        saveToken(uid,call { access.accountToken(uid) },activation.deviceCode)
        pendingStore.clearActivation(); pendingActivation=null
        return true
    }
    private fun saveToken(uid: String, response: JSONObject, linkedDeviceId: String = session?.privateContext?.get("deviceId") ?: deviceId) {
        val auth = response.optJSONObject("auth") ?: response
        val token = text(auth,"access_token") ?: text(auth,"accessToken") ?: unsupported()
        val expiry = when {
            auth.has("expiry") -> auth.optLong("expiry")*1000
            auth.has("expires_at") -> auth.optLong("expires_at")*1000
            auth.has("expires_in") -> now()+auth.optLong("expires_in")*1000
            else -> unsupported()
        }
        if (expiry <= now()+30000 || expiry > now()+30L*86400000) unsupported()
        val saved = ProviderSession(token,text(auth,"refresh_token") ?: text(auth,"refreshToken").orEmpty(),expiry-30000,mapOf("uid" to uid,"deviceId" to linkedDeviceId))
        store.write(saved); session=saved; connection=ProviderConnection.CONNECTED
    }
    private fun token(): String? {
        val current=session ?: return if(anonymousLiveApproved) null else throw ProviderException(ProviderFailure.AUTH_REQUIRED)
        if(current.expiresAt > now()) return current.accessToken
        val uid=current.privateContext["uid"] ?: throw ProviderException(ProviderFailure.SESSION_EXPIRED)
        try { saveToken(uid,call { access.accountToken(uid) }) } catch(error: ProviderException) {
            if(error.failure in setOf(ProviderFailure.AUTH_REQUIRED,ProviderFailure.SESSION_EXPIRED)) {
                store.clear();session=null;connection=ProviderConnection.SESSION_EXPIRED
            }
            throw error
        }
        return session!!.accessToken
    }
    private fun catalogue(): List<AbcLiveProtocol.Selection> {
        token()
        return AbcLiveProtocol.channels(call { access.collection(AbcLiveProtocol.liveCollectionId(call { access.home() })) })
    }
    override fun channels() = catalogue().map { ProviderChannel(ProviderChannelId("abc",it.houseNumber),it.houseNumber,it.title) }
    override fun resolve(channel: ProviderChannelId): ProviderPlaybackRequest {
        if(channel.provider!="abc" || channel.channel!=proofChannelHouseNumber) throw ProviderException(ProviderFailure.UNAVAILABLE)
        val row=catalogue().singleOrNull { it.houseNumber==channel.channel } ?: throw ProviderException(ProviderFailure.UNAVAILABLE)
        return AbcLiveProtocol.resolve(channel,row,call { access.programme(row.programmeUrl) }) { house,url -> access.authoriseMedia(house,url,token()) }
    }
    override fun signOut() {
        try { access.unlink(pendingActivation?.deviceCode ?: session?.privateContext?.get("deviceId") ?: deviceId) } catch (_: Exception) { }
        finally { pendingStore.clearActivation(); pendingActivation=null;store.clear();session=null;connection=ProviderConnection.NOT_CONNECTED }
    }
    private inline fun <T> call(action: ()->T): T = try { action() } catch(error: ProviderException) { throw error }
    catch(_: java.io.IOException) { throw ProviderException(ProviderFailure.OFFLINE) }
    catch(_: Exception) { throw ProviderException(ProviderFailure.UNAVAILABLE) }
    private fun terminal(failure: ProviderFailure): Nothing {
        pendingStore.clearActivation();pendingActivation=null;connection=if(session==null) ProviderConnection.NOT_CONNECTED else ProviderConnection.CONNECTED
        throw ProviderException(failure)
    }
    companion object {
        const val ACTIVATION="https://www.abc.net.au/linktv"
        private fun unwrap(json: JSONObject)=json.optJSONObject("data") ?: json
        private fun text(json: JSONObject,key: String)=(json.opt(key) as? String)?.takeIf { it.isNotBlank() && it.length<=4096 && it.none { c->c.isISOControl() } }
        private fun unsupported(): Nothing=throw ProviderException(ProviderFailure.AUTH_RESPONSE_UNSUPPORTED)
    }
}
