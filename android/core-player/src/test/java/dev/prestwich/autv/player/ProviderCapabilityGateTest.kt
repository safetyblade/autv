package dev.prestwich.autv.player

import dev.prestwich.autv.data.provider.*
import org.junit.Assert.*
import org.junit.Test

class ProviderCapabilityGateTest {
    @Test fun sevenUsesIssuedWidevineWithoutEnablingAnUnconfiguredAdvertisingSession() {
        val id=ProviderChannelId("seven","official-fixture")
        val request=ProviderPlaybackRequest(id,"https://media.example/live.mpd","application/dash+xml",
            options=ProviderMediaOptions(widevine=ProviderWidevine("https://licence.example/issued")))
        ProviderMediaSourceFactory.validate(request,false)
        try {
            ProviderMediaSourceFactory.validate(ProviderPlaybackRequest(id,"https://media.example/live.mpd","application/dash+xml",
                options=ProviderMediaOptions(imaLive=ProviderImaLive("synthetic"))),false)
            fail("Advertising still requires its configured player path")
        } catch(_: IllegalArgumentException) { }
    }
    @Test fun legacyRemainsHlsOnlyAndExpiredSessionsNeverStart() {
        val id = ProviderChannelId("sample", "one")
        ProviderMediaSourceFactory.validate(ProviderPlaybackRequest(id,"https://media.invalid","application/x-mpegURL"),false)
        try { ProviderMediaSourceFactory.validate(ProviderPlaybackRequest(id,"https://media.invalid","application/dash+xml"),false);fail() }
        catch (_: IllegalArgumentException) { }
        ProviderMediaSourceFactory.validate(ProviderPlaybackRequest(id,"https://media.invalid","application/dash+xml"),true)
        try { ProviderMediaSourceFactory.validate(ProviderPlaybackRequest(id,"https://media.invalid","application/x-mpegURL",
            options=ProviderMediaOptions(expiresAt=100)),true,101); fail() }
        catch (error: ProviderException) { assertEquals(ProviderFailure.SESSION_EXPIRED,error.failure) }
    }
}
