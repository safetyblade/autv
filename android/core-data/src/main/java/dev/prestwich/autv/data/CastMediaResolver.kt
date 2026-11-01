package dev.prestwich.autv.data

import okhttp3.Call
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit

/** Ephemeral public media description; never include stream/session URLs in diagnostics. */
class CastMediaSource(val url: String,val mimeType: String,val live: Boolean,val fragmentedMp4: Boolean=false) {
    override fun toString()="CastMediaSource(redacted)"
}
enum class CastLoadFailure { URL_UNAVAILABLE, UNSUPPORTED_FORMAT, SENDER_REQUIREMENTS, RECEIVER_REJECTED, TIMEOUT, REMOTE_STOPPED }
class CastMediaException(val kind: CastLoadFailure,val status: Int?=null): Exception(kind.name)

/** Bounded, credential-free preflight. Receiver fetches the freshly resolved URL itself.
 * Redirect targets are refreshed on every load and are never saved in the guide/Favourites.
 */
class CastMediaResolver internal constructor(private val client: OkHttpClient) {
    constructor(): this(transport)
    @Volatile private var active: Call?=null
    @Volatile private var cancelled=false
    fun cancel() { cancelled=true;active?.cancel() }
    fun resolve(raw: String): CastMediaSource {
        val original=try { CastStreamUrl.resolve(raw) } catch(_: Exception) { throw CastMediaException(CastLoadFailure.URL_UNAVAILABLE) }
        if(CastStreamUrl.hasUnresolvedParameters(original)) throw CastMediaException(CastLoadFailure.SENDER_REQUIREMENTS)
        var address=original
        val visited=mutableSetOf<String>()
        try {
            for(hop in 0..5) {
                if(cancelled) throw IOException("cancelled")
                if(!visited.add(address)) throw CastMediaException(CastLoadFailure.URL_UNAVAILABLE)
                val request=Request.Builder().url(address).header("User-Agent","AUTV Cast media probe")
                    .header("Accept","application/vnd.apple.mpegurl, application/x-mpegURL, application/dash+xml, video/*;q=0.9, */*;q=0.1").build()
                val call=client.newCall(request);active=call
                if(cancelled) call.cancel()
                call.execute().use { response ->
                    if(response.code in setOf(301,302,303,307,308)) {
                        if(hop==5) throw CastMediaException(CastLoadFailure.URL_UNAVAILABLE,response.code)
                        val target=response.header("Location")?.let { URI(address).resolve(it).toString() }
                            ?: throw CastMediaException(CastLoadFailure.URL_UNAVAILABLE,response.code)
                        val next=CastStreamUrl.resolve(target)
                        if(URI(address).scheme=="https" && URI(next).scheme!="https") throw CastMediaException(CastLoadFailure.URL_UNAVAILABLE)
                        if(CastStreamUrl.hasUnresolvedParameters(next)) throw CastMediaException(CastLoadFailure.SENDER_REQUIREMENTS)
                        address=next
                    } else {
                        if(!response.isSuccessful) throw CastMediaException(CastLoadFailure.URL_UNAVAILABLE,response.code)
                        val body=response.body ?: throw CastMediaException(CastLoadFailure.UNSUPPORTED_FORMAT)
                        val buffer=ByteArray(8192)
                        val count=body.byteStream().read(buffer)
                        val bytes=if(count>0) buffer.copyOf(count) else byteArrayOf()
                        return describe(original,address,response.header("Content-Type"),bytes)
                    }
                }
            }
        } catch(error: CastMediaException) { throw error }
        catch(_: IOException) { throw CastMediaException(CastLoadFailure.URL_UNAVAILABLE) }
        catch(_: Exception) { throw CastMediaException(CastLoadFailure.URL_UNAVAILABLE) }
        finally { active=null }
        throw CastMediaException(CastLoadFailure.URL_UNAVAILABLE)
    }
    companion object {
        private val transport=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .cookieJar(CookieJar.NO_COOKIES).connectTimeout(6,TimeUnit.SECONDS).readTimeout(6,TimeUnit.SECONDS)
            .callTimeout(10,TimeUnit.SECONDS).build()
        fun fromAddress(url: String): CastMediaSource = describe(url,url,null,byteArrayOf())
        internal fun describe(original: String,effective: String,header: String?,bytes: ByteArray): CastMediaSource {
            val text=String(bytes,Charsets.UTF_8).removePrefix("\uFEFF").trimStart()
            val type=header?.substringBefore(';')?.trim()?.lowercase().orEmpty()
            val hls=text.startsWith("#EXTM3U") && text.contains("#EXT-X-")
            val dash=Regex("<(?:[A-Za-z0-9_]+:)?MPD(?:\\s|>)").containsMatchIn(text)
            val mp4=bytes.size>=8 && String(bytes.copyOfRange(4,8),Charsets.US_ASCII)=="ftyp"
            if(!hls && !dash && !mp4 && (type in setOf("text/html","application/json") || type.endsWith("+json") || text.startsWith("{") || text.startsWith("<html",true) || text.startsWith("<!doctype html",true)))
                throw CastMediaException(CastLoadFailure.UNSUPPORTED_FORMAT)
            val path=URI(effective).path.orEmpty().lowercase()
            val mime=when {
                hls -> "application/x-mpegURL"
                dash -> "application/dash+xml"
                mp4 -> "video/mp4"
                type in setOf("application/x-mpegurl","application/vnd.apple.mpegurl") -> "application/x-mpegURL"
                type=="application/dash+xml" -> type
                type in setOf("video/mp4","video/webm","audio/mp4","audio/mpeg","audio/aac") -> type
                path.endsWith(".m3u8") && !text.startsWith("<") -> "application/x-mpegURL"
                path.endsWith(".mpd") && !text.startsWith("#") -> "application/dash+xml"
                path.endsWith(".mp4") -> "video/mp4"
                path.endsWith(".webm") -> "video/webm"
                else -> throw CastMediaException(CastLoadFailure.UNSUPPORTED_FORMAT)
            }
            val live=when(mime) {
                "application/x-mpegURL" -> !text.contains("#EXT-X-ENDLIST")
                "application/dash+xml" -> Regex("\\btype\\s*=\\s*['\"]dynamic['\"]").containsMatchIn(text)
                else -> false
            }
            return CastMediaSource(effective,mime,live,mime=="application/x-mpegURL" && text.contains("#EXT-X-MAP:"))
        }
    }
}
