package dev.prestwich.autv.data

import java.net.URI
import java.net.URLDecoder

/** Receiver-side fetching cannot substitute IPTV advertising macros. Preserve real/auth query values. */
object CastStreamUrl {
    private val macro = Regex("\\[[A-Za-z_][A-Za-z0-9_]*\\]|\\{[A-Za-z_][A-Za-z0-9_]*\\}")
    fun resolve(raw: String): String {
        val value = raw.trim()
        val fragment = value.indexOf('#').let { if (it < 0) value.length else it }
        val query = value.indexOf('?').takeIf { it >= 0 && it < fragment }
        val result = if (query == null) value.substring(0, fragment) else {
            val kept = value.substring(query + 1, fragment).split('&').filterNot { parameter ->
                val encoded = parameter.substringAfter('=', "")
                val decoded = runCatching { URLDecoder.decode(encoded, "UTF-8") }.getOrDefault(encoded)
                macro.containsMatchIn(decoded)
            }
            value.substring(0, query) + if (kept.isEmpty()) "" else "?" + kept.joinToString("&")
        }
        val uri = URI(result)
        require(uri.scheme in listOf("https", "http") && !uri.host.isNullOrBlank()) { "Cast requires a public HTTP(S) stream URL" }
        return result
    }
}
