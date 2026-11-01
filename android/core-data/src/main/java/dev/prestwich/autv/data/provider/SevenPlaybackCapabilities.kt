package dev.prestwich.autv.data.provider

/** Media capabilities actually implemented by Media3, never an entitlement/SDK attestation. */
object SevenPlaybackCapabilities {
    val parameters: Map<String,String> = mapOf(
        "cp.encryptionType" to "cenc", "cp.drmSystems" to "widevine",
        "cp.containerFormat" to "cmaf", "cp.supportedCodecs" to "avc",
        // SlyGuy 7plus 0.5.8 api.play requests authenticated DRM descriptors.
        // SevenLiveSource consumes the issued media token for media/licence requests;
        // this declares that support, not account entitlement or SDK verification.
        "cp.drmAuth" to "true")
    fun supports(request: ProviderPlaybackRequest): Boolean = request.channelId.provider=="seven" &&
        request.mimeType in setOf("application/x-mpegURL","application/dash+xml") &&
        request.options.imaLive==null && !request.options.hlsInterstitials
}
