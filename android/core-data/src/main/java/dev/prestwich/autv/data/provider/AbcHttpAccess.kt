package dev.prestwich.autv.data.provider

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder

/** ABC must supply AUTV's profile registration and exact media-authorisation implementation.
 * No imported profile/HMAC key, browser Origin or tablet identity is provided by AUTV.
 */
class AbcClientRegistration(val productId: String, val profileApiKey: String,
    val region: String, val mainHouseNumber: String,
    val mediaAuthoriser: (String,String,String?)->String, val accountLinkingEnabled: Boolean = true) {
    init {
        require((!accountLinkingEnabled || productId.isNotBlank()) && productId.length<=256 && productId.none(Char::isISOControl))
        require((!accountLinkingEnabled || profileApiKey.isNotBlank()) && profileApiKey.length<=4096 && profileApiKey.none(Char::isISOControl))
        require(region=="VIC" && mainHouseNumber.matches(Regex("[A-Za-z0-9_-]{1,128}")))
    }
    override fun toString()="AbcClientRegistration(redacted)"
}
/** Concrete native HTTP request construction. Media authorisation is deliberately a distinct grant. */
class AbcHttpAccess internal constructor(private val registration: AbcClientRegistration,
    private val open: (URL)->HttpURLConnection): AbcProviderAccess {
    constructor(registration: AbcClientRegistration): this(registration,{ it.openConnection() as HttpURLConnection })
    override fun link(deviceId: String): JSONObject = guarded {
        if (!registration.accountLinkingEnabled) throw ProviderException(ProviderFailure.UNSUPPORTED)
        send("POST","$PROFILE/ctv/link",JSONObject().put("productId",registration.productId).put("deviceId",deviceId).put("deviceName","AUTV"),profileKey=true)
    }
    override fun linkStatus(deviceId: String,requestId: String?): JSONObject = guarded {
        if (!registration.accountLinkingEnabled) throw ProviderException(ProviderFailure.UNSUPPORTED)
        val values=linkedMapOf("productId" to registration.productId,"deviceId" to deviceId)
        requestId?.let { values["requestId"]=it }
        send("GET",query("$PROFILE/ctv/status",values),pending=true)
    }
    override fun accountToken(uid: String): JSONObject = guarded {
        if (!registration.accountLinkingEnabled) throw ProviderException(ProviderFailure.UNSUPPORTED)
        // UID renewal deliberately has no old bearer/profile key or shared cookie jar.
        send("GET",query("https://api.seesaw.abc.net.au/v2/token",mapOf("UID" to uid,"source" to "iview")))
    }
    override fun unlink(deviceId: String) { guarded {
        if (!registration.accountLinkingEnabled) throw ProviderException(ProviderFailure.UNSUPPORTED)
        send("POST","$PROFILE/ctv/unlink",JSONObject().put("productId",registration.productId).put("deviceId",deviceId),profileKey=true)
    } }
    override fun home(): JSONObject = guarded { send("GET","$IVIEW/home") }
    override fun collection(id: String): JSONObject = guarded {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,128}")))
        send("GET","$IVIEW/collection/$id")
    }
    override fun programme(officialUrl: String): JSONObject = guarded {
        val uri=URI(officialUrl)
        require(ProviderOriginHeaders.origin(officialUrl)=="https://api.iview.abc.net.au" && uri.rawQuery==null && uri.rawPath.matches(Regex("/v2/video/[A-Za-z0-9_-]{1,128}")))
        send("GET",officialUrl)
    }
    override fun authoriseMedia(houseNumber: String,originalHls: String,accountToken: String?): String = guarded {
        // Region/main-feed identity must be supplied from approved discovery, never guessed from title.
        if(houseNumber!=registration.mainHouseNumber) throw ProviderException(ProviderFailure.UNAVAILABLE)
        ProviderOriginHeaders.origin(originalHls)
        registration.mediaAuthoriser(houseNumber,originalHls,accountToken).also { ProviderOriginHeaders.origin(it) }
    }
    private fun send(method: String,address: String,body: JSONObject?=null,profileKey: Boolean=false,pending: Boolean=false): JSONObject {
        if (!registration.accountLinkingEnabled && (profileKey || address.startsWith(PROFILE) || address.startsWith("https://api.seesaw.abc.net.au/"))) throw ProviderException(ProviderFailure.UNSUPPORTED)
        val origin=ProviderOriginHeaders.origin(address)
        require(origin in setOf("https://mylogin-api.abc.net.au","https://api.seesaw.abc.net.au","https://api.iview.abc.net.au"))
        require(!profileKey || (origin=="https://mylogin-api.abc.net.au" && method=="POST"))
        val connection=open(URL(address))
        try {
            connection.instanceFollowRedirects=false;connection.connectTimeout=10000;connection.readTimeout=15000
            connection.requestMethod=method;connection.setRequestProperty("Accept","application/json")
            connection.setRequestProperty("User-Agent","AUTV (Android)")
            if(profileKey) connection.setRequestProperty("X-API-KEY",registration.profileApiKey)
            if(body!=null) {
                connection.doOutput=true;connection.setRequestProperty("Content-Type","application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status=connection.responseCode
            if(pending && status in setOf(204,404)) return JSONObject()
            if(status==401) throw ProviderException(ProviderFailure.SESSION_EXPIRED,status)
            if(status==403) throw ProviderException(ProviderFailure.REGION_DENIED,status)
            if(status==429 || status in 500..599) throw ProviderException(ProviderFailure.RETRY_LATER,status)
            if(status !in 200..299) throw ProviderException(ProviderFailure.UNAVAILABLE,status)
            if(status==204) return JSONObject()
            val bytes=connection.inputStream.use { input ->
                val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(true) { val count=input.read(buffer);if(count<0) break
                    if(out.size()+count>2*1024*1024) throw ProviderException(ProviderFailure.UNAVAILABLE,status)
                    out.write(buffer,0,count)
                };out.toByteArray()
            }
            return JSONObject(String(bytes,Charsets.UTF_8))
        } finally { connection.disconnect() }
    }
    private inline fun <T> guarded(action: ()->T): T = try { action() }
    catch(error: ProviderException) { throw error }
    catch(_: java.io.IOException) { throw ProviderException(ProviderFailure.OFFLINE) }
    catch(_: Exception) { throw ProviderException(ProviderFailure.AUTH_RESPONSE_UNSUPPORTED) }
    private fun query(address: String,values: Map<String,String>): String {
        require(values.values.all { it.isNotBlank() && it.length<=4096 && it.none(Char::isISOControl) })
        return address+"?"+values.entries.joinToString("&") { (key,value)->"$key=${URLEncoder.encode(value,"UTF-8")}" }
    }
    override fun toString()="AbcHttpAccess(redacted)"
    companion object {
        private const val PROFILE="https://mylogin-api.abc.net.au/latest"
        private const val IVIEW="https://api.iview.abc.net.au/v2"
    }
}
