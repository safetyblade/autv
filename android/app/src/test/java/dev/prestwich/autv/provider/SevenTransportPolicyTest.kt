package dev.prestwich.autv.provider

import dev.prestwich.autv.data.provider.SevenPlusAdapter
import org.junit.Assert.fail
import org.junit.Test

class SevenTransportPolicyTest {
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
