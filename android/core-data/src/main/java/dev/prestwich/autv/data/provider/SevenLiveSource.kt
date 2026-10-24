package dev.prestwich.autv.data.provider

import org.json.JSONObject
import java.net.URI

/** Original provider HLS with all commercials/session parameters intact; no ad or DRM bypass. */
object SevenLiveSource {
    fun select(channel: ProviderChannelId, descriptor: JSONObject): ProviderPlaybackRequest {
        if (channel.provider != "seven") throw ProviderException(ProviderFailure.UNAVAILABLE)
        val error = descriptor.optString("error")
        if (error in setOf("location_verification_required", "video_not_in_market"))
            throw ProviderException(ProviderFailure.REGION_DENIED)
        if (error.isNotBlank() && error != "null") throw ProviderException(ProviderFailure.UNAVAILABLE)
        val media = descriptor.optJSONObject("media") ?: throw ProviderException(ProviderFailure.PLAYBACK_UNSUPPORTED)
        if (requiresAds(descriptor) || requiresAds(media)) throw ProviderException(ProviderFailure.AD_SESSION_UNSUPPORTED, stage = ProviderPlaybackStage.SEVEN_ADS)
        if (protected(descriptor) || protected(media)) throw ProviderException(ProviderFailure.DRM_UNSUPPORTED, stage = ProviderPlaybackStage.SEVEN_DRM)
        val sources = media.optJSONArray("sources") ?: throw ProviderException(ProviderFailure.PLAYBACK_UNSUPPORTED)
        var drm = false
        for (index in 0 until sources.length()) {
            val source = sources.optJSONObject(index) ?: continue
            if (protected(source)) { drm = true; continue }
            if (requiresAds(source)) throw ProviderException(ProviderFailure.AD_SESSION_UNSUPPORTED, stage = ProviderPlaybackStage.SEVEN_ADS)
            if (source.optString("type") !in setOf("application/x-mpegURL", "application/vnd.apple.mpegurl")) continue
            val address = source.optString("src")
            val uri = runCatching { URI(address) }.getOrNull() ?: continue
            // CDN hostname does not determine ad delivery. Use the provider's original
            // supported HLS source, in provider order, without changing any session field.
            if (uri.scheme != "https" || uri.host.isNullOrBlank() ||
                uri.port !in setOf(-1, 443) || uri.rawUserInfo != null || uri.fragment != null) continue
            return ProviderPlaybackRequest(channel, address, "application/x-mpegURL")
        }
        throw ProviderException(when {
            drm -> ProviderFailure.DRM_UNSUPPORTED
            else -> ProviderFailure.PLAYBACK_UNSUPPORTED
        }, stage = if (drm) ProviderPlaybackStage.SEVEN_DRM else ProviderPlaybackStage.SEVEN_SOURCE)
    }
    private fun protected(json: JSONObject): Boolean {
        val encryption = json.optString("encryption_type").lowercase()
        return (encryption !in setOf("", "null", "none", "clear")) ||
            listOf("key_systems", "drm", "license_url", "licenseUrl").any { declared(json.opt(it)) }
    }
    private fun requiresAds(json: JSONObject) = listOf("ad_sources", "adConfig", "ad_config", "adTagUrl", "ad_tag_url", "ads").any {
        declared(json.opt(it))
    }
    private fun declared(value: Any?): Boolean = when (value) {
        null, JSONObject.NULL, false -> false
        is JSONObject -> value.length() > 0
        is org.json.JSONArray -> value.length() > 0
        is String -> value.isNotBlank() && value != "null"
        else -> true
    }
}
