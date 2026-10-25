package dev.prestwich.autv.data.provider

import java.net.URLDecoder

/** Provider-supplied IDs, not a destination. Never fetch videoUrl or expose its values. */
class SevenPlaybackIdentity private constructor(val account: String, val reference: String) {
    override fun toString() = "SevenPlaybackIdentity(redacted)"
    companion object {
        fun read(value: Any?): SevenPlaybackIdentity {
            val address = value as? String ?: fail(ProviderPlaybackStage.SEVEN_IDENTITY_TYPE)
            if (address.length > 16384 || address.any { it.isISOControl() }) fail(ProviderPlaybackStage.SEVEN_IDENTITY_VALUE)
            // Mirrors standalone SlyGuy's query extraction. The URL is metadata only;
            // its origin/path/fragment are not used for any network request.
            if (!address.contains('?')) fail(ProviderPlaybackStage.SEVEN_IDENTITY_QUERY)
            val query = address.substringAfter('?').substringBefore('#')
            val fields = mutableMapOf<String, MutableList<String>>()
            for (part in query.split('&')) {
                val pieces = part.split('=', limit = 2)
                val key = runCatching { URLDecoder.decode(pieces[0], "UTF-8") }.getOrNull() ?: continue
                if (key !in setOf("accountId", "referenceId")) continue
                val decoded = runCatching { URLDecoder.decode(pieces.getOrElse(1) { "" }, "UTF-8") }
                    .getOrElse { fail(ProviderPlaybackStage.SEVEN_IDENTITY_VALUE) }
                fields.getOrPut(key) { mutableListOf() }.add(decoded)
            }
            fun required(key: String, missing: ProviderPlaybackStage, limit: Int): String {
                val values = fields[key]?.distinct().orEmpty()
                if (values.isEmpty() || values.all { it.isBlank() }) fail(missing)
                if (values.size != 1) fail(ProviderPlaybackStage.SEVEN_IDENTITY_AMBIGUOUS)
                val result = values.single()
                // Brightcove reference IDs are not confined to AUTV's former ASCII regex.
                // Query encoding on the fixed Seven endpoint safely preserves punctuation.
                if (result.length > limit || result.any { it.isISOControl() || it == '\uFFFD' }) fail(ProviderPlaybackStage.SEVEN_IDENTITY_VALUE)
                return result
            }
            return SevenPlaybackIdentity(required("accountId", ProviderPlaybackStage.SEVEN_IDENTITY_ACCOUNT, 128),
                required("referenceId", ProviderPlaybackStage.SEVEN_IDENTITY_REFERENCE, 512))
        }
        private fun fail(stage: ProviderPlaybackStage): Nothing =
            throw ProviderException(ProviderFailure.PLAYBACK_UNSUPPORTED, stage = stage)
    }
}
