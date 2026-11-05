package dev.prestwich.autv.provider

import dev.prestwich.autv.data.provider.*
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder

/**
 * Experimental AUTV-identified ABC TV device-link flow, no borrowed profile key.
 *
 * A real unregistered POST /latest/ctv/link returned HTTP 200 and a four-character
 * challenge in the 5 November 2026 AUTV network probe. This implementation tests
 * approval and UID token issuance on the owner's TV. It MUST NOT be enabled in
 * release builds. It cannot authorise HLS media and never reports playback success.
 */
internal class AbcExperimentalLinkAccess : AbcProviderAccess {
    private val catalogue = AbcHttpAccess()
    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    override fun link(deviceId: String): JSONObject = request("POST", "$PROFILE/ctv/link",
        JSONObject().put("productId", "autv").put("deviceId", deviceId).put("deviceName", "AUTV"))

    override fun linkStatus(deviceId: String, requestId: String?): JSONObject {
        val query = "productId=autv&deviceId=${encode(deviceId)}" +
            (requestId?.let { "&requestId=${encode(it)}" } ?: "")
        return request("GET", "$PROFILE/ctv/status?$query", pending404 = true)
    }

    override fun accountToken(uid: String): JSONObject =
        request("GET", "$SEESAW/token?UID=${encode(uid)}&source=iview")

    override fun unlink(deviceId: String) {
        request("POST", "$PROFILE/ctv/unlink",
            JSONObject().put("productId", "autv").put("deviceId", deviceId))
    }

    override fun home(): JSONObject = catalogue.home()
    override fun collection(id: String): JSONObject = catalogue.collection(id)
    override fun programme(officialUrl: String): JSONObject = catalogue.programme(officialUrl)

    // Account approval is not broadcaster authorisation to play an unsigned HLS URL.
    override fun authoriseMedia(houseNumber: String, originalHls: String, accountToken: String?): String =
        throw ProviderException(ProviderFailure.PLAYBACK_UNSUPPORTED, stage = ProviderPlaybackStage.ABC_AUTHORISATION)

    private fun request(method: String, address: String, body: JSONObject? = null, pending404: Boolean = false): JSONObject {
        try {
            val uri = URI(address)
            require(uri.scheme == "https" && uri.port in setOf(-1, 443) &&
                uri.rawUserInfo == null && uri.rawFragment == null)
            val path = uri.rawPath
            require(when(uri.host) {
                "mylogin-api.abc.net.au" -> path in setOf("/latest/ctv/link","/latest/ctv/status","/latest/ctv/unlink")
                "api.seesaw.abc.net.au" -> path == "/v2/token"
                else -> false
            })
            require((body == null) == (method == "GET"))
            val connection = URL(address).openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.requestMethod = method
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "AUTV Android TV")
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                }
                val status = connection.responseCode
                if (status == 204 || (pending404 && status == 404)) return JSONObject()
                if (status == 401) throw ProviderException(ProviderFailure.SESSION_EXPIRED, status)
                if (status == 403) throw ProviderException(ProviderFailure.UNAVAILABLE, status)
                if (status == 429 || status in 500..599) throw ProviderException(ProviderFailure.RETRY_LATER, status)
                if (status !in 200..299) throw ProviderException(ProviderFailure.UNAVAILABLE, status)
                val bytes = connection.inputStream.use { input ->
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (out.size() + count > 131_072) throw ProviderException(ProviderFailure.AUTH_RESPONSE_UNSUPPORTED, status)
                        out.write(buffer, 0, count)
                    }
                    out.toByteArray()
                }
                return JSONObject(String(bytes, Charsets.UTF_8))
            } finally { connection.disconnect() }
        } catch (error: ProviderException) { throw error }
        catch (_: java.io.IOException) { throw ProviderException(ProviderFailure.OFFLINE) }
        catch (_: Exception) { throw ProviderException(ProviderFailure.AUTH_RESPONSE_UNSUPPORTED) }
    }
    private companion object {
        const val PROFILE = "https://mylogin-api.abc.net.au/latest"
        const val SEESAW = "https://api.seesaw.abc.net.au/v2"
    }
}
