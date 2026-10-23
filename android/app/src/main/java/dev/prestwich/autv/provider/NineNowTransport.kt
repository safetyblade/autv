package dev.prestwich.autv.provider

import dev.prestwich.autv.data.provider.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** No request/response logging, redirects, cookies, scraping or TLS overrides. */
internal class HttpNineNowTransport : NineNowTransport {
    override var lastHttpStatus: Int? = null
        private set
    override fun request(method: String, url: String, accessToken: String?): JSONObject {
        lastHttpStatus = null
        val uri = URI(url)
        require(uri.scheme == "https" && uri.host in setOf("login.nine.com.au", "api.9now.com.au"))
        require(accessToken == null || uri.host == "api.9now.com.au")
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = false
            connection.requestMethod = method
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "AUTV/0.4 (Android)")
            accessToken?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            if (method == "POST") {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write("{}".toByteArray()) }
            }
            val status = connection.responseCode
            lastHttpStatus = status
            val authHost = uri.host == "login.nine.com.au"
            val devicePoll = authHost && uri.path == "/api/device/token"
            if (status == 429 || status in 500..599) throw ProviderException(ProviderFailure.RETRY_LATER, status)
            if (status == 401 && !devicePoll) throw ProviderException(
                if (authHost) ProviderFailure.ACTIVATION_DENIED else ProviderFailure.SESSION_EXPIRED, status)
            if (status == 403) throw ProviderException(
                if (authHost) ProviderFailure.ACTIVATION_DENIED else ProviderFailure.REGION_DENIED, status)
            // Device approval may return a JSON pending error with 400.
            if (status !in 200..299 && !(authHost && (status == 400 || (devicePoll && status == 401))))
                throw ProviderException(ProviderFailure.UNAVAILABLE, status)
            val input = if (status >= 400) connection.errorStream else connection.inputStream
            val body = input?.use { stream ->
                val bytes = stream.readBytesLimited(2 * 1024 * 1024)
                String(bytes, Charsets.UTF_8)
            } ?: throw ProviderException(ProviderFailure.UNAVAILABLE)
            JSONObject(body)
        } catch (error: ProviderException) { throw error }
        catch (_: java.io.IOException) { throw ProviderException(ProviderFailure.OFFLINE) }
        catch (_: Exception) { throw ProviderException(ProviderFailure.UNAVAILABLE) }
        finally { connection.disconnect() }
    }
}
private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        if (output.size() + count > limit) throw ProviderException(ProviderFailure.UNAVAILABLE)
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
