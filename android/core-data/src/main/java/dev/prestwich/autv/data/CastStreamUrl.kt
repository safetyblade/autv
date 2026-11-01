package dev.prestwich.autv.data

import java.net.URI
import java.net.URLDecoder

/** Public receiver URL only. Preserve every query byte, including signed/ad/session parameters. */
object CastStreamUrl {
    private val macro=Regex("\\[[A-Za-z_][A-Za-z0-9_]*\\]|\\{[A-Za-z_][A-Za-z0-9_]*\\}")
    fun resolve(raw: String): String {
        val result=raw.trim().substringBefore('#')
        require(result.length<=16384 && result.none(Char::isISOControl))
        val uri=URI(result)
        require(uri.scheme in listOf("https","http") && !uri.host.isNullOrBlank() && uri.rawUserInfo==null) {
            "Cast requires an HTTP(S) media URL without user credentials"
        }
        return result
    }
    fun hasUnresolvedParameters(url: String): Boolean {
        val query=url.substringBefore('#').substringAfter('?',"")
        if(query.isEmpty()) return false
        return query.split('&').any { parameter ->
            val value=parameter.substringAfter('=',"")
            macro.containsMatchIn(runCatching { URLDecoder.decode(value,"UTF-8") }.getOrDefault(value))
        }
    }
}
