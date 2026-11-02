package dev.prestwich.autv.data.provider

/**
 * Live videoservice contract, traced through SlyGuy 7plus 0.5.8 api.play.
 * deliveryId selects the broadcaster delivery contract; it is not a capability
 * or an entitlement. Keep CSAI advertising metadata and never request an ad-free
 * variant. IPTV AU's mjh_master is a third-party redirect, not a Seven session API.
 *
 * Do not copy the reference's postcode, ozid, SDK verification assertion or
 * official-app user agent. Those are not metadata issued to this AUTV session.
 */
object SevenLiveContract {
    const val DELIVERY = "csai"
    val clientParameters: Map<String, String> = mapOf(
        "appId" to "7plus", "deviceType" to "androidtv", "platformType" to "tv",
        "videoType" to "live", "deliveryId" to DELIVERY,
        // No advertising identifier collected by AUTV; the reference uses this sentinel.
        "advertid" to "null",
    )

    fun parameters(identity: SevenPlaybackIdentity, market: String, device: String): Map<String, String> =
        linkedMapOf("deviceId" to device, "accountId" to identity.account,
            "referenceId" to identity.reference, "marketId" to market).apply {
            putAll(clientParameters)
            putAll(SevenPlaybackCapabilities.parameters)
        }
}
