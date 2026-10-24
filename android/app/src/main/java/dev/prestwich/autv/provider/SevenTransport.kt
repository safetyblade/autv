package dev.prestwich.autv.provider

import android.content.Context
import dev.prestwich.autv.data.provider.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.UUID

internal fun validateSevenRequest(method: String, address: String) {
    val uri = URI(address)
    require(uri.scheme == "https" && uri.port == -1 && uri.rawUserInfo == null && uri.fragment == null)
    val auth = uri.host == "auth2.swm.digital" && uri.path in setOf("/account/device/authorize", "/connect/token") && method == "POST" && uri.query == null
    val market = address == SevenPlusAdapter.MARKET && method == "GET"
    val content = uri.host == "component-cdn.swm.digital" && uri.path == "/content/live-tv" && method == "GET" &&
        uri.rawQuery?.matches(Regex("platform-id=androidtv&market-id=[A-Za-z0-9%+_.-]+&platform-version=5[.]25[.]0[.]0&api-version=5[.]9[.]0[.]0")) == true
    require(auth || market || content)
}

/** No cookies, redirects, request/body logging, CDN credentials or official-device impersonation. */
internal class HttpSevenTransport : SevenPlusTransport {
    override fun request(method: String, url: String, form: Map<String, String>): JSONObject {
        validateSevenRequest(method, url)
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 10000; connection.readTimeout = 15000
            connection.instanceFollowRedirects = false; connection.requestMethod = method
            connection.setRequestProperty("User-Agent", "AUTV/0.5 (Android TV)")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("accept-language", "en")
            // Distributed-client public API configuration, not an account credential or permission grant.
            connection.setRequestProperty("x-swm-apikey", "kGcrNnuPClrkynfnKwG8IA/NhVG6ut5nPEdWF2jscvE=")
            if (method == "POST") {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                val encoded = form.entries.joinToString("&") { java.net.URLEncoder.encode(it.key, "UTF-8") + "=" + java.net.URLEncoder.encode(it.value, "UTF-8") }
                connection.outputStream.use { it.write(encoded.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val token = URI(url).path == "/connect/token"
            if (status == 429 || status in 500..599) throw ProviderException(ProviderFailure.RETRY_LATER, status)
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
            JSONObject(bytes.toString("UTF-8"))
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
