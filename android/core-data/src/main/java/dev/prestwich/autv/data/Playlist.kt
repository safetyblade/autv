package dev.prestwich.autv.data

import java.net.URI
import java.util.Locale

data class PlaylistEntry(val name: String, val id: String?, val logo: String?, val url: String)

internal object Playlist {
    fun key(name: String) = name.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
    fun parse(text: String): List<PlaylistEntry> {
        var metadata: String? = null
        return buildList {
            for (line in text.lineSequence().map { it.trim() }) {
                if (line.startsWith("#EXTINF:")) metadata = line
                else if (line.isNotBlank() && !line.startsWith("#")) {
                    val info = metadata ?: continue
                    metadata = null
                    val valid = runCatching { val uri = URI(line); uri.scheme in listOf("https", "http") && !uri.host.isNullOrBlank() }.getOrDefault(false)
                    if (!valid) continue
                    fun attr(key: String) = Regex("""$key="([^"]*)"""").find(info)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
                    add(PlaylistEntry(attr("tvg-name") ?: info.substringAfterLast(','), attr("tvg-id"), attr("tvg-logo"), line))
                }
            }
        }
    }
}
