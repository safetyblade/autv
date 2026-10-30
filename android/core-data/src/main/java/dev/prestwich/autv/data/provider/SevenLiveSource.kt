package dev.prestwich.autv.data.provider

import org.json.JSONObject

/** Consume the original Brightcove descriptor, including issued Widevine configuration. */
object SevenLiveSource {
    fun select(channel: ProviderChannelId, descriptor: JSONObject): ProviderPlaybackRequest {
        if(channel.provider!="seven") throw ProviderException(ProviderFailure.UNAVAILABLE)
        val error=descriptor.optString("error")
        if(error in setOf("location_verification_required","video_not_in_market")) throw ProviderException(ProviderFailure.REGION_DENIED)
        if(error.isNotBlank() && error!="null") throw ProviderException(ProviderFailure.UNAVAILABLE)
        val media=descriptor.optJSONObject("media") ?: fail(ProviderFailure.PLAYBACK_UNSUPPORTED,ProviderPlaybackStage.SEVEN_SOURCE)
        if(requiresAds(descriptor) || requiresAds(media)) fail(ProviderFailure.AD_SESSION_UNSUPPORTED,ProviderPlaybackStage.SEVEN_ADS)
        // Unknown global contracts cannot be replaced by a clear fallback source.
        if(protected(descriptor) || protected(media)) fail(ProviderFailure.DRM_UNSUPPORTED,ProviderPlaybackStage.SEVEN_DRM)
        val sources=media.optJSONArray("sources") ?: fail(ProviderFailure.PLAYBACK_UNSUPPORTED,ProviderPlaybackStage.SEVEN_SOURCE)
        for(index in 0 until sources.length()) {
            val source=sources.optJSONObject(index) ?: continue
            if(requiresAds(source)) fail(ProviderFailure.AD_SESSION_UNSUPPORTED,ProviderPlaybackStage.SEVEN_ADS)
            val type=when(source.optString("type")) {
                "application/x-mpegURL","application/vnd.apple.mpegurl" -> "application/x-mpegURL"
                "application/dash+xml" -> "application/dash+xml"
                else -> continue
            }
            val address=source.optString("src")
            val origin=runCatching { ProviderOriginHeaders.origin(address) }.getOrNull() ?: continue
            if(protected(source)) {
                val systems=source.optJSONObject("key_systems")
                    ?: fail(ProviderFailure.DRM_UNSUPPORTED,ProviderPlaybackStage.SEVEN_DRM)
                val widevine=systems.optJSONObject("com.widevine.alpha")
                    ?: fail(ProviderFailure.DRM_UNSUPPORTED,ProviderPlaybackStage.SEVEN_DRM)
                if(source.optString("encryption_type","cenc")!="cenc") fail(ProviderFailure.DRM_UNSUPPORTED,ProviderPlaybackStage.SEVEN_DRM)
                val licence=widevine.opt("license_url") as? String
                    ?: fail(ProviderFailure.DRM_UNSUPPORTED,ProviderPlaybackStage.SEVEN_DRM)
                val token=systems.optJSONObject("authorization")?.opt("token") as? String
                if(token!=null && (token.isBlank() || token.length>16000 || token.any(Char::isISOControl))) fail(ProviderFailure.DRM_UNSUPPORTED,ProviderPlaybackStage.SEVEN_DRM)
                val headers=token?.let { mapOf("Authorization" to "Bearer $it") }.orEmpty()
                val options=try {
                    // This is the descriptor's media token, never the account bearer. Redirect
                    // hops receive it only on the selected media/licence origins, not other CDNs.
                    ProviderMediaOptions(widevine=ProviderWidevine(licence,headers),
                        originHeaders=if(headers.isEmpty()) emptyMap() else mapOf(origin to headers))
                } catch(_: IllegalArgumentException) { fail(ProviderFailure.DRM_UNSUPPORTED,ProviderPlaybackStage.SEVEN_DRM) }
                return ProviderPlaybackRequest(channel,address,type,options=options)
            }
            return ProviderPlaybackRequest(channel,address,type)
        }
        fail(ProviderFailure.PLAYBACK_UNSUPPORTED,ProviderPlaybackStage.SEVEN_SOURCE)
    }
    private fun protected(json: JSONObject): Boolean {
        val encryption=json.optString("encryption_type").lowercase()
        return encryption !in setOf("","null","none","clear") ||
            listOf("key_systems","drm","license_url","licenseUrl").any { declared(json.opt(it)) }
    }
    private fun requiresAds(json: JSONObject)=listOf("ad_sources","adConfig","ad_config","adTagUrl","ad_tag_url","ads").any { declared(json.opt(it)) }
    private fun declared(value: Any?): Boolean=when(value) {
        null,JSONObject.NULL,false->false
        is JSONObject->value.length()>0
        is org.json.JSONArray->value.length()>0
        is String->value.isNotBlank() && value!="null"
        else->true
    }
    private fun fail(failure: ProviderFailure, stage: ProviderPlaybackStage): Nothing=throw ProviderException(failure,stage=stage)
}
