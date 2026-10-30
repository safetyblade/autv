package dev.prestwich.autv.data.provider

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.io.encoding.Base64

/** The reference POST header is just a UTC timestamp encoding, not a private signing key.
 * GET X-N10-SIG still requires AUTV-authorised signing access; never import an app's key.
 */
object TenPublicRequestContext {
    private val format=DateTimeFormatter.ofPattern("yyyyMMddHHmmss",Locale.ROOT).withZone(ZoneOffset.UTC)
    fun headers(method: String, now: Long=System.currentTimeMillis()): Map<String,String> =
        if(method=="POST") mapOf("X-Network-Ten-Auth" to Base64.encode(format.format(Instant.ofEpochMilli(now)).toByteArray(Charsets.US_ASCII)))
        else emptyMap()
}
