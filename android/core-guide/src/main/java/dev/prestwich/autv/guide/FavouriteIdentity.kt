package dev.prestwich.autv.guide

import dev.prestwich.autv.data.Channel
import java.net.URI
import java.security.MessageDigest

/** Provider IDs are canonical: duplicate rows with that ID deliberately share membership. */
object FavouriteIdentity {
    fun key(channel: Channel): String? {
        channel.tvgId?.trim()?.takeIf { it.isNotEmpty() }?.let { return "id:$it" }
        // Never use a display name or number as identity. Retain routing query parameters,
        // dropping only known rotating credentials; the URL is never used for playback here.
        val url = channel.streamUrl?.takeIf { it.isNotBlank() } ?: return null
        val canonical = runCatching {
            val uri = URI(url)
            val query = uri.rawQuery?.split('&')?.filterNot {
                it.substringBefore('=').lowercase() in setOf("token", "jwt", "auth", "signature", "expires", "exp", "sessionid")
            }?.sorted()?.joinToString("&")?.takeIf { it.isNotEmpty() }
            "${uri.scheme?.lowercase()}://${uri.rawAuthority?.lowercase()}${uri.rawPath.orEmpty()}${query?.let { "?$it" }.orEmpty()}"
        }.getOrNull() ?: return null
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return "url:" + digest.joinToString("") { "%02x".format(it) }
    }

    fun contains(channel: Channel, keys: Set<String>) = key(channel)?.let { it in keys } == true
}
