package dev.prestwich.autv.provider

import android.content.Context
import dev.prestwich.autv.data.provider.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.UUID

internal fun validateSbsRequest(method: String, address: String, bearer: String?) {
    val uri = URI(address)
    require(uri.scheme == "https" && uri.port == -1 && uri.rawUserInfo == null && uri.fragment == null)
    val auth = uri.host == "auth.sbs.com.au" && uri.path in setOf("/device/link", "/device/poll", "/refresh") && method == "POST" && uri.query == null
    val epg = uri.host == "tv-epg.pr.sbsod.com" && uri.path == "/guides-v2" && method == "GET" && uri.query == "limit=0"
    val playback = uri.host == "playback.pr.sbsod.com" && uri.path.startsWith("/stream/") && method == "POST" && uri.query == null
    require(auth || epg || playback)
    require(bearer == null || epg || playback)
}

/** No cookies, redirects, request/body logging, CDN credentials or official-device impersonation. */
internal class HttpSbsTransport : SbsTransport {
    override fun request(method: String, url: String, body: JSONObject?, accessToken: String?): JSONObject {
        validateSbsRequest(method, url, accessToken)
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 10000; connection.readTimeout = 15000
            connection.instanceFollowRedirects = false; connection.requestMethod = method
            connection.setRequestProperty("User-Agent", "AUTV/0.5 (Android TV)")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("accept-language", "en")
            // Distributed-client public API configuration, not an account credential or permission grant.
            connection.setRequestProperty("x-api-key", "ce3f270a-31fb-486e-855e-4f608a62640e")
            accessToken?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            if (method == "POST") {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write((body ?: JSONObject()).toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val poll = URI(url).path == "/device/poll"
            if (status == 429 || status in 500..599) throw ProviderException(ProviderFailure.RETRY_LATER, status)
            if (status == 401 && !poll) throw ProviderException(
                if (URI(url).path == "/device/link") ProviderFailure.ACTIVATION_DENIED else ProviderFailure.SESSION_EXPIRED, status)
            if (status == 403) throw ProviderException(
                if (URI(url).host == "auth.sbs.com.au") ProviderFailure.ACTIVATION_DENIED else ProviderFailure.REGION_DENIED, status)
            if (status !in 200..299 && !(poll && status in setOf(400, 401))) throw ProviderException(ProviderFailure.UNAVAILABLE, status)
            val input = if (status >= 400) connection.errorStream else connection.inputStream
            val bytes = java.io.ByteArrayOutputStream()
            input?.use {
                val buffer = ByteArray(8192)
                while (true) {
                    val count = it.read(buffer)
                    if (count < 0) break
                    if (bytes.size() + count > 2 * 1024 * 1024) throw ProviderException(ProviderFailure.UNAVAILABLE)
                    bytes.write(buffer, 0, count)
                }
            } ?: throw ProviderException(ProviderFailure.UNAVAILABLE)
            JSONObject(bytes.toString("UTF-8"))
        } catch (error: ProviderException) { throw error }
        catch (_: java.io.IOException) { throw ProviderException(ProviderFailure.OFFLINE) }
        catch (_: Exception) { throw ProviderException(ProviderFailure.UNAVAILABLE) }
        finally { connection.disconnect() }
    }
}
internal fun createSbsProvider(context: Context): ProviderAdapter {
    val store = EncryptedProviderSessionStore(context, "autv.sbs.session.v1", "sbs-session-v1")
    val file = android.util.AtomicFile(java.io.File(context.noBackupFilesDir, "sbs-device-v1"))
    val id = runCatching { String(file.readFully(), Charsets.UTF_8).also { UUID.fromString(it) } }.getOrElse {
        UUID.randomUUID().toString().also { value ->
            val output = file.startWrite()
            try { output.write(value.toByteArray(Charsets.UTF_8)); file.finishWrite(output) }
            catch (error: Exception) { file.failWrite(output); throw error }
        }
    }
    return SbsAdapter(HttpSbsTransport(), store, store, id)
}
