package dev.prestwich.autv.data.provider

/** Media capabilities actually implemented by Media3, never an entitlement/SDK attestation. */
object SevenPlaybackCapabilities {
    val parameters: Map<String,String> = mapOf(
        "cp.encryptionType" to "cenc", "cp.drmSystems" to "widevine",
        "cp.containerFormat" to "cmaf", "cp.supportedCodecs" to "avc")
    fun supports(request: ProviderPlaybackRequest): Boolean = request.channelId.provider=="seven" &&
        request.mimeType in setOf("application/x-mpegURL","application/dash+xml") &&
        request.options.imaLive==null && !request.options.hlsInterstitials
}
