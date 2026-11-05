package dev.prestwich.autv.provider

import dev.prestwich.autv.data.provider.AbcLiveProtocol
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Owner-initiated, debug-build-only live service check on the TV's Australian connection.
 * This never authenticates, collects passwords, publishes guide rows or claims a linked account.
 * Native device-code linking continues through the separate registered provider adapters.
 */
internal object NativeBroadcasterTrial {
    internal data class Result(val stage: String, val summary: String, val channels: List<String> = emptyList())
    private const val ABC_HOME = "https://api.iview.abc.net.au/v2/home"
    private const val ABC_COLLECTION = "https://api.iview.abc.net.au/v2/collection/"
    private const val TEN_CONFIG = "https://10.com.au/api/v1/config"

    // A known unofficial candidate, deliberately limited to an explicit user-initiated debug test.
    // It is NOT a registered Network 10 account session or an approved production channel feed.
    const val TEN_VIC_EXPERIMENTAL_HLS = "https://i.mjh.nz/.r/10-vic.m3u8"

    /**
     * Debug-only candidate linear channels, not a 10 subscription or authorised 10 API.
     * Only the 10 VIC entry displayed video in owner's 5 November physical test.
     * Other three old public redirect aliases have not been confirmed to match 2026
     * broadcaster identities: never include in production builds.
     */
    internal data class TenLinearTrial(val number: Int, val name: String, val source: String,
                                       val physicallyObserved: Boolean)
    internal val TEN_LINEAR_TRIALS = listOf(
        // Always-on owner-approved channel, but external source is not a registered 10 login.
        TenLinearTrial(10, "10 VIC", TEN_VIC_EXPERIMENTAL_HLS, true),
        // Corrected 6 November 2026 URLs: public HLS manifests returned HTTP 200.
        // Native video/audio and broadcaster distribution approval are not verified.
        TenLinearTrial(-98422, "10 Drama — Source trial", "https://i.mjh.nz/.r/10bold-vic.m3u8", false),
        TenLinearTrial(-98423, "10 Comedy — Source trial", "https://i.mjh.nz/.r/10peach-vic.m3u8", false),
    )

    // Independent ABC public-web live candidates. Never mark these official
    // authorised AUTV media sessions: the URLs are maintained by a third party.
    internal data class AbcLiveTrial(val number: Int, val name: String, val source: String)
    internal val ABC_LIVE_TRIALS = listOf(
        AbcLiveTrial(-98431, "ABC TV VIC — Direct HLS trial", "https://c.mjh.nz/abc-vic.m3u8"),
        AbcLiveTrial(-98432, "ABC NEWS — Direct HLS trial", "https://c.mjh.nz/abc-news.m3u8"),
        AbcLiveTrial(-98433, "ABC Kids — Direct HLS trial", "https://c.mjh.nz/abc-kids.m3u8"),
    )

    fun inspect(provider: String, fetch: (String) -> JSONObject = ::getJson): Result = try {
        when (provider) {
            "abc" -> {
                val collection = AbcLiveProtocol.liveCollectionId(fetch(ABC_HOME))
                val rows = AbcLiveProtocol.channels(fetch(ABC_COLLECTION + collection))
                Result("ABC-CATALOGUE",
                    "ABC responded with ${rows.size} live catalogue channels. Account linking and playback are not yet verified.",
                    rows.map { it.title }.take(24))
            }
            "ten" -> {
                val json = fetch(TEN_CONFIG)
                val data = json.optJSONObject("data") ?: json
                val auth = data.optJSONObject("authConfig")
                Result("10-CONFIG",
                    if (auth != null) "10's configuration service responded with device-link endpoints. AUTV still needs a registered client to request a real activation code."
                    else "10's configuration service responded, but did not expose a recognised device-link contract. No activation code was issued.")
            }
            else -> Result("NOT-SUPPORTED", "This diagnostic supports ABC and 10 only.")
        }
    } catch (failure: HttpStatus) {
        Result(if (provider == "abc") "ABC-CATALOGUE" else "10-CONFIG",
            "Service returned HTTP ${failure.status}. Account linking was not attempted.")
    } catch (_: java.io.IOException) {
        Result(if (provider == "abc") "ABC-CATALOGUE" else "10-CONFIG",
            "Could not reach the live service from this TV. Check its Australian network connection.")
    } catch (_: Exception) {
        Result(if (provider == "abc") "ABC-CATALOGUE" else "10-CONFIG",
            "Service replied, but its current response could not be understood. No connection was made.")
    }

    private class HttpStatus(val status: Int) : Exception()

    private fun getJson(address: String): JSONObject {
        // Only AUTV-authored official metadata URLs are fetched; no arbitrary URL from the service.
        require(address == ABC_HOME || address == TEN_CONFIG ||
            (address.startsWith(ABC_COLLECTION) && address.substring(ABC_COLLECTION.length)
                .matches(Regex("[A-Za-z0-9_-]{1,128}"))))
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "AUTV Android TV")
            val status = connection.responseCode
            if (status !in 200..299) throw HttpStatus(status)
            val bytes = connection.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (out.size() + count > 1_048_576) error("Response too large")
                    out.write(buffer, 0, count)
                }
                out.toByteArray()
            }
            return JSONObject(String(bytes, Charsets.UTF_8))
        } finally {
            connection.disconnect()
        }
    }
}
