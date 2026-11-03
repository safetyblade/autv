package dev.prestwich.autv.data

import okhttp3.Call
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit

/** Ephemeral public media description; never include stream/session URLs in diagnostics. */
class CastMediaSource(val url: String,val mimeType: String,val live: Boolean,val fragmentedMp4: Boolean=false,
    internal val manifest: ByteArray=byteArrayOf()) {
    override fun toString()="CastMediaSource(redacted)"
}
enum class CastLoadFailure { URL_UNAVAILABLE, UNSUPPORTED_FORMAT, SENDER_REQUIREMENTS, RECEIVER_REJECTED, TIMEOUT, REMOTE_STOPPED }
class CastMediaException(val kind: CastLoadFailure,val status: Int?=null): Exception(kind.name)

/** Bounded, credential-free preflight. Receiver fetches the freshly resolved URL itself.
 * Redirect targets are refreshed on every load and are never saved in the guide/Favourites.
 */
class CastMediaResolver internal constructor(private val client: OkHttpClient,
    private val inspectHls: Boolean = false, private val report: (String) -> Unit = {}) {
    constructor(): this(transport)
    constructor(report: (String) -> Unit): this(transport, false, report)
    @Volatile private var active: Call?=null
    @Volatile private var cancelled=false
    fun cancel() { cancelled=true;active?.cancel() }
    fun resolve(raw: String): CastMediaSource {
        val original=try { CastStreamUrl.resolve(raw) } catch(_: Exception) { throw CastMediaException(if(CastStreamUrl.hasUnresolvedParameters(raw)) CastLoadFailure.SENDER_REQUIREMENTS else CastLoadFailure.URL_UNAVAILABLE) }
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
                    report("stage=manifest redirects=$hop host=${safeHost(address)} http=${response.code}")
                    if(response.code in setOf(301,302,303,307,308)) {
                        if(hop==5) throw CastMediaException(CastLoadFailure.URL_UNAVAILABLE,response.code)
                        val target=response.header("Location")?.let { URI(address).resolve(it).toString() }
                            ?: throw CastMediaException(CastLoadFailure.URL_UNAVAILABLE,response.code)
                        val next=CastStreamUrl.resolve(target)
                        if(URI(address).scheme=="https" && URI(next).scheme!="https") throw CastMediaException(CastLoadFailure.URL_UNAVAILABLE)
                        address=next
                    } else {
                        if(!response.isSuccessful) throw CastMediaException(CastLoadFailure.URL_UNAVAILABLE,response.code)
                        val body=response.body ?: throw CastMediaException(CastLoadFailure.UNSUPPORTED_FORMAT)
                        // A single InputStream.read can return only a partial tag/manifest.
                        val bytes=body.byteStream().takeBytes(65_536)
                        val description=describe(original,address,response.header("Content-Type"),bytes)
                        val source=CastMediaSource(description.url,description.mimeType,description.live,description.fragmentedMp4,bytes)
                        report("stage=format mime=${source.mimeType} container=${if(source.fragmentedMp4) "fmp4" else "unknown"}")
                        return if(inspectHls && source.mimeType=="application/x-mpegURL") inspect(source,bytes) else source
                    }
                }
            }
        } catch(error: CastMediaException) { throw error }
        catch(_: IOException) { throw CastMediaException(CastLoadFailure.URL_UNAVAILABLE) }
        catch(_: Exception) { throw CastMediaException(CastLoadFailure.URL_UNAVAILABLE) }
        finally { active=null }
        throw CastMediaException(CastLoadFailure.URL_UNAVAILABLE)
    }
    /** Run after submitting LOAD: slow diagnostic children must never delay receiver startup. */
    fun diagnose(source: CastMediaSource) {
        if(source.mimeType=="application/x-mpegURL" && !cancelled) inspect(source,source.manifest)
    }
    /** Child probes are evidence only: never replace a master, choose a rendition, or
     * reject a working source because a sender-side sample differs from receiver access. */
    private fun inspect(source: CastMediaSource, master: ByteArray): CastMediaSource {
        var text=String(master,Charsets.UTF_8)
        var base=source.url
        var fragmented=source.fragmentedMp4
        reportManifest(text)
        try {
            val lines=text.lineSequence().map { it.trim() }.toList()
            val variant=lines.indexOfFirst { it.startsWith("#EXT-X-STREAM-INF:") }
            if(variant>=0) {
                val child=lines.drop(variant+1).firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
                if(child!=null) {
                    val sample=sample(URI(base).resolve(child).toString(),"variant",65_536)
                    if(sample!=null) { base=sample.first;text=String(sample.second,Charsets.UTF_8);reportManifest(text) }
                }
            }
            fragmented=fragmented || text.contains("#EXT-X-MAP:")
            val segment=text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
            // Do not request an entire segment. This sample does not prove receiver playback.
            if(segment!=null && text.startsWith("#EXTM3U") && !text.contains("#EXT-X-STREAM-INF:")) {
                val bytes=sample(URI(base).resolve(segment).toString(),"segment",1024)?.second
                val container=when {
                    bytes==null -> "unobserved"
                    bytes.size>188 && bytes[0]==0x47.toByte() && bytes[188]==0x47.toByte() -> "mpegts"
                    bytes.size>=8 && String(bytes.copyOfRange(4,8),Charsets.US_ASCII) in setOf("ftyp","styp","moof","sidx") -> "fmp4"
                    else -> "unknown"
                }
                report("stage=segment container=$container")
            }
        } catch(_: Exception) { report("stage=child_probe result=unavailable") }
        return CastMediaSource(source.url,source.mimeType,source.live,fragmented)
    }
    private fun reportManifest(text: String) {
        // Only recognised codec prefixes and encryption labels escape untrusted manifests.
        val codecs=Regex("CODECS=\"([^\"]+)\"").findAll(text).flatMap { it.groupValues[1].split(',').asSequence() }
            .map { it.substringBefore('.').trim().lowercase() }.filter { it in setOf("avc1","avc3","hvc1","hev1","mp4a","ac-3","ec-3","opus","vp09","av01") }.distinct().toList()
        val methods=Regex("METHOD=([A-Z0-9-]+)").findAll(text).map { it.groupValues[1] }
            .map { if(it in setOf("NONE","AES-128","SAMPLE-AES","SAMPLE-AES-CTR")) it else "OTHER" }.distinct().toList()
        report("stage=hls codecs=${codecs.joinToString(",").ifEmpty { "unknown" }} encryption=${methods.joinToString(",").ifEmpty { "none_observed" }} map=${text.contains("#EXT-X-MAP:")}")
    }
    private fun sample(raw: String,stage: String,limit: Int): Pair<String,ByteArray>? {
        var address=CastStreamUrl.resolve(raw)
        for(hop in 0..5) {
            if(cancelled) return null
            val call=client.newCall(Request.Builder().url(address).header("User-Agent","AUTV Cast media probe")
                .apply { if(stage=="segment") header("Range","bytes=0-${limit-1}") }.build())
            active=call
            if(cancelled) call.cancel()
            call.execute().use { response ->
                report("stage=$stage redirects=$hop host=${safeHost(address)} http=${response.code}")
                if(response.code in setOf(301,302,303,307,308)) {
                    val next=CastStreamUrl.resolve(URI(address).resolve(response.header("Location") ?: return null).toString())
                    if(URI(address).scheme=="https" && URI(next).scheme!="https") return null
                    address=next
                } else {
                    if(!response.isSuccessful) return null
                    return address to (response.body?.byteStream()?.takeBytes(limit) ?: byteArrayOf())
                }
            }
        }
        return null
    }
    private fun java.io.InputStream.takeBytes(limit: Int): ByteArray {
        val output=java.io.ByteArrayOutputStream()
        val buffer=ByteArray(4096)
        while(output.size()<limit) {
            val count=read(buffer,0,minOf(buffer.size,limit-output.size()))
            if(count<0) break
            output.write(buffer,0,count)
        }
        return output.toByteArray()
    }
    private fun safeHost(url: String): String = URI(url).host?.takeIf { it.matches(Regex("[a-zA-Z0-9.:-]+")) } ?: "unknown"
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
