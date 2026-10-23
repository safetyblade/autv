package dev.prestwich.autv.provider

import org.junit.Assert.*
import org.junit.Test

class SbsTransportPolicyTest {
    @Test fun restrictsCredentialsToSbsApiEndpoints() {
        validateSbsRequest("POST", "https://auth.sbs.com.au/device/link", null)
        validateSbsRequest("GET", "https://tv-epg.pr.sbsod.com/guides-v2?limit=0", "fixture")
        validateSbsRequest("POST", "https://playback.pr.sbsod.com/stream/fixture", "fixture")
        for ((method, url, bearer) in listOf(
            Triple("GET", "https://example.invalid/live.m3u8", "fixture"),
            Triple("POST", "https://auth.sbs.com.au/device/poll", "fixture"),
            Triple("GET", "http://tv-epg.pr.sbsod.com/guides-v2?limit=0", "fixture"),
            Triple("GET", "https://tv-epg.pr.sbsod.com.evil.invalid/guides-v2?limit=0", "fixture"),
            Triple("GET", "https://user@tv-epg.pr.sbsod.com/guides-v2?limit=0", "fixture"))) {
            try { validateSbsRequest(method, url, bearer); fail("Unsafe request accepted") } catch (_: IllegalArgumentException) { }
        }
    }
}
