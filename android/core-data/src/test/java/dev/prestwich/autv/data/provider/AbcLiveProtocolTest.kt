package dev.prestwich.autv.data.provider

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Source-derived synthetic schemas, never labelled owner/provider-captured responses. */
class AbcLiveProtocolTest {
    private fun collection(href: String = "/video/LIVE_VIC") = JSONObject("""{"items":[{"type":"livestream","houseNumber":"LIVE_VIC","title":"ABC fixture VIC","_links":{"self":{"href":"$href"}}}]}""")
    private val channel = ProviderChannelId("abc", "LIVE_VIC")
    private val original = "https://original.invalid/live/master.m3u8?session=fixture&ads=normal"
    private fun programme(house: String = "LIVE_VIC") = JSONObject("""{"houseNumber":"$house","_embedded":{"playlist":[{"type":"livestream","streams":{"hls":{"720":"$original"}}}]}}""")
    @Test fun catalogueAndProgrammePreserveProviderIdentityAndOriginalSession() {
        assertEquals("live-collection", AbcLiveProtocol.liveCollectionId(JSONObject("""{"_embedded":{"collections":[{"id":"live-collection","title":"Watch ABC Channels Live"}]}}""")))
        val row = AbcLiveProtocol.channels(collection()).single()
        assertEquals("https://api.iview.abc.net.au/v2/video/LIVE_VIC", row.programmeUrl)
        val signed = "$original&hdnea=provider-issued-fixture"
        val request = AbcLiveProtocol.resolve(channel, row, programme()) { house, url ->
            assertEquals("LIVE_VIC", house); assertEquals(original, url); signed
        }
        assertEquals(signed, request.uri); assertTrue(request.headers.isEmpty())
        assertFalse(row.toString().contains("LIVE_VIC"))
        assertFalse(request.toString().contains("hdnea"))
    }
    @Test fun failedAuthorisationNeverFallsBackToUnsignedMedia() {
        val row = AbcLiveProtocol.channels(collection()).single()
        try {
            AbcLiveProtocol.resolve(channel, row, programme()) { _, _ -> throw IllegalStateException("private-url") }
            fail("An authorisation failure must not play")
        } catch (error: ProviderException) { assertEquals("UNAVAILABLE", error.message); assertFalse(error.toString().contains("private")) }
    }
    @Test fun wrongIdentityProtectedMediaAndForeignMetadataNeverReachAuthoriser() {
        val row = AbcLiveProtocol.channels(collection()).single()
        for (data in listOf(programme("OTHER"), programme().put("drm", true), programme().put("playable", false),
            programme().also { it.getJSONObject("_embedded").getJSONArray("playlist").getJSONObject(0).getJSONObject("streams").put("drm", true) })) {
            try { AbcLiveProtocol.resolve(channel, row, data) { _, _ -> throw AssertionError("Must not authorise") }; fail() }
            catch (_: ProviderException) { }
        }
        for (url in listOf("https://foreign.invalid/v2/video/LIVE_VIC", "//foreign.invalid/video/X", "/video/../X")) {
            try { AbcLiveProtocol.channels(collection(url)); fail() } catch (_: ProviderException) { }
        }
    }
    @Test fun ambiguousOrMissingLiveMetadataDoesNotInventChannelOrStream() {
        val data = collection(); data.getJSONArray("items").put(data.getJSONArray("items").getJSONObject(0))
        try { AbcLiveProtocol.channels(data); fail() } catch (_: ProviderException) { }
        val programme = programme(); programme.getJSONObject("_embedded").getJSONArray("playlist").getJSONObject(0).put("type", "program")
        try { AbcLiveProtocol.resolve(channel, AbcLiveProtocol.channels(collection()).single(), programme) { _, _ -> throw AssertionError() }; fail() }
        catch (_: ProviderException) { }
    }
}
