package dev.prestwich.autv.data.provider

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder

/** The one external dependency: Ten's AUTV registration, including approved request authorisation.
 * No defaults impersonate LG/NVIDIA/another app. Returned maps must contain only this request's headers.
 */
interface TenClientRegistration {
    val allowedOrigins: Set<String>
    val configParameters: Map<String,String>
    val deviceFields: Map<String,String>
    val playbackParameters: Map<String,String>
    val liveFormat: TenLiveProtocol.Format
    fun authorise(method: String, exactUrl: String, jsonBody: String?): Map<String,String>
}
/** Executable configuration -> device link/renewal -> VIC catalogue -> fresh live descriptor transport. */
class TenHttpAccess internal constructor(private val registration: TenClientRegistration,
    private val open: (URL)->HttpURLConnection) : TenProviderAccess {
    constructor(registration: TenClientRegistration): this(registration,{ it.openConnection() as HttpURLConnection })
    init {
        require(registration.allowedOrigins.isNotEmpty())
        registration.allowedOrigins.forEach { address ->
            require(ProviderOriginHeaders.origin(address)==address)
            val host=URI(address).host
            require(host=="10.com.au" || host.endsWith(".10.com.au") || host=="tenplay.com.au" || host.endsWith(".tenplay.com.au"))
        }
        require(registration.deviceFields.isNotEmpty() && registration.playbackParameters.isNotEmpty())
        require("deviceIdentifier" !in registration.deviceFields)
    }
    private fun config()=guarded(ProviderPlaybackStage.TEN_CONFIG) { send("GET",query("https://10.com.au/api/v1/config",registration.configParameters)).data }
    private fun auth(key: String)=config().getJSONObject("authConfig").getString(key)
    override fun generateCode(deviceId: String): JSONObject = guarded(ProviderPlaybackStage.TEN_ACTIVATION) {
        val payload=JSONObject(registration.deviceFields).put("deviceIdentifier",deviceId)
        send("POST",auth("generateCode"),payload).data
    }
    override fun validateCode(code: String, deviceId: String, expirySeconds: Long): JSONObject = guarded(ProviderPlaybackStage.TEN_POLL) {
        send("POST",auth("validateCode"),JSONObject().put("code",code).put("deviceIdentifier",deviceId).put("expiry",expirySeconds),pending=true).data
    }
    override fun refresh(alternativeToken: String, refreshToken: String): JSONObject = guarded(ProviderPlaybackStage.TEN_REFRESH) {
        send("POST",auth("refreshToken"),JSONObject().put("alternativeToken",alternativeToken).put("refreshToken",refreshToken)).data
    }
    override fun liveChannels(region: String): JSONObject = guarded(ProviderPlaybackStage.TEN_CATALOGUE) {
        require(region=="VIC") // Supported regional choice; never modifies IP/location verification.
        send("GET",query(config().getString("liveTvEndpoint").trimEnd('/')+"/$region",mapOf("limit" to "4"))).data
    }
    override fun playback(officialDescriptorUrl: String, bearer: String): TenPlaybackDescriptor = guarded(ProviderPlaybackStage.TEN_DESCRIPTOR) {
        val response=send("GET",query(officialDescriptorUrl,registration.playbackParameters),bearer=bearer)
        TenLiveProtocol.liveRequest(response.data,response.daiAuth.orEmpty()) // Refuse missing auth/VOD/protection before playback.
        TenPlaybackDescriptor(response.data,response.daiAuth!!,mimeType=when(registration.liveFormat) {
            TenLiveProtocol.Format.HLS->"application/x-mpegURL"
            TenLiveProtocol.Format.DASH->"application/dash+xml"
        })
    }
    private class Reply(val data: JSONObject,val daiAuth: String?)
    private fun send(method: String,address: String,body: JSONObject?=null,bearer: String?=null,pending: Boolean=false): Reply {
        val origin=ProviderOriginHeaders.origin(address)
        require(origin in registration.allowedOrigins)
        val uri=URI(address)
        require(uri.port in setOf(-1,443) && uri.rawPath.startsWith("/api/"))
        val encoded=body?.toString()
        val approved=registration.authorise(method,address,encoded).toMap()
        // Registration may override the public POST header with a provider-issued scheme.
        val defaults=TenPublicRequestContext.headers(method).filterKeys { name -> approved.keys.none { it.equals(name,true) } }
        val headers=defaults+approved
        ProviderOriginHeaders.validateHeaders(headers)
        require(headers.keys.none { it.equals("Authorization",true) || it.equals("Cookie",true) })
        val connection=open(URL(address))
        try {
            connection.instanceFollowRedirects=false;connection.connectTimeout=10000;connection.readTimeout=15000
            connection.requestMethod=method
            connection.setRequestProperty("Accept","application/json");connection.setRequestProperty("User-Agent","AUTV (Android)")
            headers.forEach(connection::setRequestProperty)
            bearer?.let { require(it.isNotBlank() && it.length<=16384 && it.none(Char::isISOControl));connection.setRequestProperty("Authorization","Bearer $it") }
            if(encoded!=null) {
                connection.doOutput=true;connection.setRequestProperty("Content-Type","application/json")
                connection.outputStream.use { it.write(encoded.toByteArray(Charsets.UTF_8)) }
            }
            val status=connection.responseCode
            if(pending && status in setOf(202,204)) return Reply(JSONObject(),null)
            if(status==401) throw ProviderException(ProviderFailure.SESSION_EXPIRED,status)
            // A bare 403 can be client, signature, entitlement or network policy; it is not proof of geography.
            if(status==403) throw ProviderException(ProviderFailure.UNAVAILABLE,status)
            if(status==429 || status in 500..599) throw ProviderException(ProviderFailure.RETRY_LATER,status)
            if(status !in 200..299 && !(pending && status==400)) throw ProviderException(ProviderFailure.UNAVAILABLE,status)
            val stream=if(status>=400) connection.errorStream else connection.inputStream
            val bytes=stream?.use { input ->
                val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(true) { val count=input.read(buffer);if(count<0) break
                    if(out.size()+count>2*1024*1024) throw ProviderException(ProviderFailure.UNAVAILABLE,status)
                    out.write(buffer,0,count)
                };out.toByteArray()
            } ?: throw ProviderException(ProviderFailure.UNAVAILABLE,status)
            if(bytes.size>2*1024*1024) throw ProviderException(ProviderFailure.UNAVAILABLE,status)
            return Reply(JSONObject(String(bytes,Charsets.UTF_8)),connection.getHeaderField("X-DAI-AUTH"))
        } finally { connection.disconnect() }
    }
    private inline fun <T> guarded(stage: ProviderPlaybackStage, action: ()->T): T = try { action() }
    catch(error: ProviderException) { throw ProviderException(error.failure,error.httpStatus,error.stage ?: stage) }
    catch(_: java.io.IOException) { throw ProviderException(ProviderFailure.OFFLINE,stage=stage) }
    catch(_: Exception) { throw ProviderException(ProviderFailure.AUTH_RESPONSE_UNSUPPORTED,stage=stage) }
    private fun query(address: String,values: Map<String,String>): String {
        require(values.keys.all { it.matches(Regex("[A-Za-z0-9_-]{1,64}")) })
        require(values.values.all { it.length<=4096 && it.none(Char::isISOControl) })
        // Requests.prepare_url in the reference preserves provider-issued query/session
        // fields. Preserve their exact bytes and sign the final URL, rather than rejecting them.
        val uri=URI(address)
        require(uri.rawFragment==null)
        val existing=linkedMapOf<String,String>()
        uri.rawQuery?.split('&')?.filter { it.isNotEmpty() }?.forEach { part ->
            val pair=part.split('=',limit=2)
            val key=java.net.URLDecoder.decode(pair[0],"UTF-8")
            val value=java.net.URLDecoder.decode(pair.getOrElse(1) { "" },"UTF-8")
            require(key !in existing);existing[key]=value
        }
        val additions=values.filter { (key,value) ->
            if(existing.containsKey(key)) { require(existing[key]==value);false } else true
        }
        if(additions.isEmpty()) return address
        val separator=if(uri.rawQuery==null) "?" else if(address.endsWith('?') || address.endsWith('&')) "" else "&"
        return address+separator+additions.entries.joinToString("&") { (key,value)->"${encode(key)}=${encode(value)}" }
    }
    private fun encode(value: String)=URLEncoder.encode(value,"UTF-8")
    override fun toString()="TenHttpAccess(redacted)"
}
