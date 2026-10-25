package dev.prestwich.autv.provider

import android.content.Context
import dev.prestwich.autv.data.provider.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.UUID

internal fun validateSevenRequest(method: String, address: String, bearer: String? = null) {
    val uri = URI(address)
    require(uri.scheme == "https" && uri.port == -1 && uri.rawUserInfo == null && uri.fragment == null)
    val auth = uri.host == "auth2.swm.digital" && uri.path in setOf("/account/device/authorize", "/connect/token") && method == "POST" && uri.query == null
    val market = address == SevenPlusAdapter.MARKET && method == "GET"
    val content = uri.host == "component-cdn.swm.digital" && uri.path == "/content/live-tv" && method == "GET" &&
        uri.rawQuery?.matches(Regex("platform-id=androidtv&market-id=[A-Za-z0-9%+_.*-]+&platform-version=5[.]25[.]0[.]0&api-version=5[.]9[.]0[.]0")) == true
    val player = uri.host == "component.swm.digital" && uri.path.matches(Regex("/player/live/[A-Za-z0-9_-]{1,128}")) && method == "GET" &&
        uri.rawQuery?.matches(Regex("platform-id=androidtv&market-id=[A-Za-z0-9%+_.*-]+&platform-version=5[.]25[.]0[.]0&api-version=5[.]9[.]0[.]0&signedUp=True")) == true
    val playback = uri.host == "videoservice.swm.digital" && uri.path == "/playback" && method == "GET"
    if (playback) {
        val pairs = uri.rawQuery?.split('&')?.map { it.split('=', limit = 2) }.orEmpty()
        val keys = setOf("appId", "deviceType", "platformType", "deviceId", "accountId", "referenceId", "marketId", "videoType")
        require(pairs.size == keys.size && pairs.all { it.size == 2 && it[0] in keys && it[1].matches(Regex("[A-Za-z0-9%+_.*-]+")) } && pairs.map { it[0] }.toSet() == keys)
    }
    require(auth || market || content || player || playback)
    require((bearer != null) == playback)
}

/** No cookies, redirects, request/body logging, CDN credentials or official-device impersonation. */
internal class HttpSevenTransport(private val open: (String) -> HttpURLConnection = { URL(it).openConnection() as HttpURLConnection }) : SevenPlusTransport {
    override fun request(method: String, url: String, form: Map<String, String>): JSONObject = execute(method, url, form, null)
    override fun playback(parameters: Map<String, String>, accessToken: String): JSONObject {
        val query = parameters.entries.joinToString("&") { java.net.URLEncoder.encode(it.key, "UTF-8") + "=" + java.net.URLEncoder.encode(it.value, "UTF-8") }
        return execute("GET", "${SevenPlusAdapter.PLAYBACK}?$query", emptyMap(), accessToken)
    }
    private fun execute(method: String, url: String, form: Map<String, String>, bearer: String?): JSONObject {
        validateSevenRequest(method, url, bearer)
        val connection = open(url)
        return try {
            connection.connectTimeout = 10000; connection.readTimeout = 15000
            connection.instanceFollowRedirects = false; connection.requestMethod = method
            connection.setRequestProperty("User-Agent", "AUTV/0.5 (Android TV)")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("accept-language", "en")
            // Distributed-client public API configuration, not an account credential or permission grant.
            connection.setRequestProperty("x-swm-apikey", "kGcrNnuPClrkynfnKwG8IA/NhVG6ut5nPEdWF2jscvE=")
            bearer?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            if (method == "POST") {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                val encoded = form.entries.joinToString("&") { java.net.URLEncoder.encode(it.key, "UTF-8") + "=" + java.net.URLEncoder.encode(it.value, "UTF-8") }
                connection.outputStream.use { it.write(encoded.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val token = URI(url).path == "/connect/token"
            if (status == 400 && bearer != null) {
                // Inspect only a bounded response in memory; retain enum-only diagnostics.
                val rejection = try {
                    val bytes = connection.errorStream?.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(1024)
                        while (output.size() <= SevenDescriptorRejection.MAX_BYTES) {
                            val count = input.read(buffer, 0, minOf(buffer.size, SevenDescriptorRejection.MAX_BYTES + 1 - output.size()))
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray() }
                    when {
                        bytes == null -> SevenDescriptorRejection.unavailable()
                        bytes.size > SevenDescriptorRejection.MAX_BYTES -> SevenDescriptorRejection.oversized()
                        else -> SevenDescriptorRejection.read(bytes.toString(Charsets.UTF_8),connection.contentType)
                    }
                } catch (_: Exception) { SevenDescriptorRejection.unavailable() }
                throw ProviderException(ProviderFailure.UNAVAILABLE, status, ProviderPlaybackStage.SEVEN_DESCRIPTOR, rejection)
            }
            if (status == 429 || status in 500..599) throw ProviderException(ProviderFailure.RETRY_LATER, status)
            if (status == 401 && bearer != null) throw ProviderException(ProviderFailure.SESSION_EXPIRED, status)
            if (status == 403) throw ProviderException(
                if (URI(url).host == "auth2.swm.digital") ProviderFailure.ACTIVATION_DENIED else ProviderFailure.REGION_DENIED, status)
            if (status !in 200..299 && !(token && status in setOf(400, 401))) throw ProviderException(ProviderFailure.UNAVAILABLE, status)
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
            try { JSONObject(bytes.toString("UTF-8")) }
            catch (_: org.json.JSONException) {
                // A successful edge response can still be HTML, not a media descriptor.
                if(bearer!=null) throw ProviderException(ProviderFailure.UNAVAILABLE,status,ProviderPlaybackStage.SEVEN_DESCRIPTOR,
                    SevenDescriptorRejection.read(bytes.toString("UTF-8"),connection.contentType))
                throw ProviderException(ProviderFailure.AUTH_RESPONSE_UNSUPPORTED,status)
            }
        } catch (error: ProviderException) { throw error }
        catch (_: java.io.IOException) { throw ProviderException(ProviderFailure.OFFLINE) }
        catch (_: Exception) { throw ProviderException(ProviderFailure.UNAVAILABLE) }
        finally { connection.disconnect() }
    }
}
internal fun createSevenProvider(context: Context): ProviderAdapter {
    val store = EncryptedProviderSessionStore(context, "autv.seven.session.v1", "seven-session-v1")
    val file = android.util.AtomicFile(java.io.File(context.noBackupFilesDir, "seven-device-v1"))
    val id = runCatching { String(file.readFully(), Charsets.UTF_8).also { UUID.fromString(it) } }.getOrElse {
        UUID.randomUUID().toString().also { value ->
            val output = file.startWrite()
            try { output.write(value.toByteArray(Charsets.UTF_8)); file.finishWrite(output) }
            catch (error: Exception) { file.failWrite(output); throw error }
        }
    }
    return SevenPlusAdapter(HttpSevenTransport(), store, store, id)
}
