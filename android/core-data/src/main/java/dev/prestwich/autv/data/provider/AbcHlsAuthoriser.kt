package dev.prestwich.autv.data.provider

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder

/** AUTV-approved ABC device name and signer. No upstream private key/default client identity. */
class AbcHlsSigningRegistration(val deviceType: String, val sign: (String)->String) {
    init { require(deviceType.matches(Regex("[A-Za-z0-9_-]{1,64}"))) }
    override fun toString()="AbcHlsSigningRegistration(redacted)"
}
/** Concrete /auth/hls/sign -> hdnea transport, supplied to AbcClientRegistration.mediaAuthoriser. */
class AbcHlsAuthoriser internal constructor(private val registration: AbcHlsSigningRegistration,
    private val open: (URL)->HttpURLConnection, private val now: ()->Long): (String,String,String?)->String {
    constructor(registration: AbcHlsSigningRegistration): this(registration,{it.openConnection() as HttpURLConnection},System::currentTimeMillis)
    override fun invoke(house: String, original: String, accountToken: String?): String {
        try {
            require(house.matches(Regex("[A-Za-z0-9_-]{1,128}")))
            ProviderOriginHeaders.origin(original)
            require(URI(original).rawQuery?.split('&')?.none { java.net.URLDecoder.decode(it.substringBefore('='),"UTF-8")=="hdnea" }!=false)
            val path="/auth/hls/sign?ts=${now()/1000}&hn=${URLEncoder.encode(house,"UTF-8")}&d=${URLEncoder.encode(registration.deviceType,"UTF-8")}"
            val signature=registration.sign(path)
            require(signature.matches(Regex("[0-9a-f]{64}")))
            val connection=open(URL("https://api.iview.abc.net.au$path&sig=$signature"))
            val token=try {
                connection.instanceFollowRedirects=false;connection.connectTimeout=10000;connection.readTimeout=15000
                connection.requestMethod="GET";connection.setRequestProperty("Accept","text/plain")
                connection.setRequestProperty("User-Agent","AUTV (Android)")
                // This protocol has no account bearer/profile key header; do not forward one.
                val status=connection.responseCode
                if(status !in 200..299) throw ProviderException(if(status==429 || status>=500) ProviderFailure.RETRY_LATER else ProviderFailure.UNAVAILABLE,status)
                val bytes=connection.inputStream.use { input ->
                    val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(1024)
                    while(true) { val count=input.read(buffer);if(count<0)break
                        if(out.size()+count>8192) throw ProviderException(ProviderFailure.AUTH_RESPONSE_UNSUPPORTED,status)
                        out.write(buffer,0,count)
                    };out.toByteArray()
                }
                String(bytes,Charsets.UTF_8).trim().also {
                    require(it.isNotBlank() && it.none { character -> character.isISOControl() || character.isWhitespace() } && !it.trimStart().startsWith("<") && !it.trimStart().startsWith("{"))
                }
            } finally { connection.disconnect() }
            val separator=if(URI(original).rawQuery==null) "?" else if(original.endsWith('?') || original.endsWith('&')) "" else "&"
            return original+separator+"hdnea="+URLEncoder.encode(token,"UTF-8")
        } catch(error: ProviderException) { throw ProviderException(error.failure,error.httpStatus,error.stage ?: ProviderPlaybackStage.ABC_AUTHORISATION) }
        catch(_: java.io.IOException) { throw ProviderException(ProviderFailure.OFFLINE,stage=ProviderPlaybackStage.ABC_AUTHORISATION) }
        catch(_: Exception) { throw ProviderException(ProviderFailure.AUTH_RESPONSE_UNSUPPORTED,stage=ProviderPlaybackStage.ABC_AUTHORISATION) }
    }
    override fun toString()="AbcHlsAuthoriser(redacted)"
}
