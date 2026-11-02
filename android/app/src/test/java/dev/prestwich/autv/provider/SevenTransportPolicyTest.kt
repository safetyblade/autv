package dev.prestwich.autv.provider

import dev.prestwich.autv.data.provider.SevenPlusAdapter
import dev.prestwich.autv.data.provider.SevenLiveContract
import dev.prestwich.autv.data.provider.SevenPlaybackCapabilities
import org.junit.Assert.fail
import org.junit.Test

class SevenTransportPolicyTest {
    private val fixedQuery = (SevenLiveContract.clientParameters + SevenPlaybackCapabilities.parameters)
        .entries.joinToString("&") { "${it.key}=${it.value}" }

    @Test fun bearerIsConfinedToExactPlaybackApiAndCannotReachMediaOrMetadata() {
        val query = "deviceId=fixture&accountId=account&referenceId=ref%3Amain&marketId=market&$fixedQuery"
        validateSevenRequest("GET", "${SevenPlusAdapter.PLAYBACK}?$query", "account-fixture")
        validateSevenRequest("GET", "${SevenPlusAdapter.PLAYER}/MAIN?platform-id=androidtv&market-id=market&platform-version=5.25.0.0&api-version=5.9.0.0&signedUp=True")
        for (url in listOf(SevenPlusAdapter.MARKET, "${SevenPlusAdapter.AUTH}/connect/token", "https://csm-fixture.yospace.com/master.m3u8",
            "${SevenPlusAdapter.PLAYBACK}?$query&accountId=duplicate", "${SevenPlusAdapter.PLAYBACK}?$query&pc=3181")) {
            try { validateSevenRequest("GET", url, "account-fixture"); fail("Bearer destination not scoped") } catch (_: IllegalArgumentException) { }
        }
        try { validateSevenRequest("GET", "${SevenPlusAdapter.PLAYBACK}?$query"); fail("Missing account authorization") } catch (_: IllegalArgumentException) { }
    }
    @Test fun codecNegotiationCannotAssertSdkVerificationOrInventAdvertisingContext() {
        val base="deviceId=fixture&accountId=account&referenceId=ref&marketId=market"
        validateSevenRequest("GET","${SevenPlusAdapter.PLAYBACK}?$base&$fixedQuery","fixture")
        for(extra in listOf("sdkverification=true","deliveryId=ssai","pc=3181")) {
            try { validateSevenRequest("GET","${SevenPlusAdapter.PLAYBACK}?$base&$fixedQuery&$extra","fixture");fail() }
            catch(_: IllegalArgumentException) { }
        }
    }
    @Test fun incompleteOrDifferentClientContractCannotReachPlayback() {
        val issued = "deviceId=fixture&accountId=account&referenceId=ref&marketId=market"
        for (query in listOf(
            "$issued&$fixedQuery".replace("&deliveryId=csai", ""),
            "$issued&$fixedQuery".replace("deliveryId=csai", "deliveryId=adfree"),
            "$issued&$fixedQuery".replace("deviceType=androidtv", "deviceType=web"),
            "$issued&$fixedQuery".replace("videoType=live", "videoType=vod"),
            "$issued&$fixedQuery".replace("advertid=null", "advertid=borrowed"),
        )) {
            try { validateSevenRequest("GET", "${SevenPlusAdapter.PLAYBACK}?$query", "fixture"); fail("Inconsistent client contract") }
            catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun onlyReviewedApiMethodsHostsAndPathsAreAllowed() {
        validateSevenRequest("POST", "${SevenPlusAdapter.AUTH}/account/device/authorize")
        validateSevenRequest("POST", "${SevenPlusAdapter.AUTH}/connect/token")
        validateSevenRequest("GET", SevenPlusAdapter.MARKET)
        validateSevenRequest("GET", "${SevenPlusAdapter.CONTENT}?platform-id=androidtv&market-id=market&platform-version=5.25.0.0&api-version=5.9.0.0")
        for (url in listOf("https://cdn.invalid/master.m3u8", "http://auth2.swm.digital/connect/token",
            "https://user@auth2.swm.digital/connect/token", "https://auth2.swm.digital/connect/token?leak=1",
            "https://auth2.swm.digital:443/connect/token", "https://auth2.swm.digital/connect/token#fragment",
            "https://auth2.swm.digital.evil.invalid/connect/token")) {
            try { validateSevenRequest("POST", url); fail("Unreviewed destination") } catch (_: IllegalArgumentException) { }
        }
    }
}
