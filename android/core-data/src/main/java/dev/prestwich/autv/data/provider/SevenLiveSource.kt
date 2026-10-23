package dev.prestwich.autv.data.provider

import org.json.JSONObject
import java.net.URI

/** Only original server-stitched clear HLS; never a guessed URL or a stripped ad/DRM variant. */
object SevenLiveSource {
    fun select(channel: ProviderChannelId, descriptor: JSONObject): ProviderPlaybackRequest {
        if (channel.provider != "seven") throw ProviderException(ProviderFailure.UNAVAILABLE)
        val error = descriptor.optString("error")
        if (error in setOf("location_verification_required", "video_not_in_market"))
            throw ProviderException(ProviderFailure.REGION_DENIED)
        if (error.isNotBlank() && error != "null") throw ProviderException(ProviderFailure.UNAVAILABLE)
        val media = descriptor.optJSONObject("media") ?: throw ProviderException(ProviderFailure.PLAYBACK_UNSUPPORTED)
        if (requiresAds(descriptor) || requiresAds(media)) throw ProviderException(ProviderFailure.AD_SESSION_UNSUPPORTED)
        if (protected(descriptor) || protected(media)) throw ProviderException(ProviderFailure.DRM_UNSUPPORTED)
        val sources = media.optJSONArray("sources") ?: throw ProviderException(ProviderFailure.PLAYBACK_UNSUPPORTED)
        var drm = false
        var otherHls = false
        for (index in 0 until sources.length()) {
            val source = sources.optJSONObject(index) ?: continue
            if (protected(source)) { drm = true; continue }
            if (requiresAds(source)) { otherHls = true; continue }
            if (source.optString("type") !in setOf("application/x-mpegURL", "application/vnd.apple.mpegurl")) continue
            otherHls = true
            val address = source.optString("src")
            val uri = runCatching { URI(address) }.getOrNull() ?: continue
            // The inspected current IPTV AU Channel 7 route uses Yospace SSAI. Do not
            // choose arbitrary clear/ad-free alternatives or rewrite a provider source.
            if (uri.scheme != "https" || uri.host?.endsWith(".yospace.com") != true ||
                uri.port != -1 || uri.rawUserInfo != null || uri.fragment != null) continue
            return ProviderPlaybackRequest(channel, address, "application/x-mpegURL")
        }
        throw ProviderException(when {
            drm -> ProviderFailure.DRM_UNSUPPORTED
            otherHls -> ProviderFailure.AD_SESSION_UNSUPPORTED
            else -> ProviderFailure.PLAYBACK_UNSUPPORTED
        })
    }
    private fun protected(json: JSONObject) = listOf("key_systems", "drm", "encryption_type", "license_url", "licenseUrl").any {
        json.has(it) && !json.isNull(it) && json.opt(it) != false && json.optString(it).isNotBlank()
    }
    private fun requiresAds(json: JSONObject) = listOf("ad_sources", "adConfig", "ad_config", "adTagUrl", "ad_tag_url", "ads").any {
        json.has(it) && !json.isNull(it) && json.opt(it) != false && json.optString(it).isNotBlank()
    }
}
