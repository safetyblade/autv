package dev.prestwich.autv.data.provider

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Synthetic source/documentation-derived schemas, not a Ten playback capture. */
class TenLiveProtocolTest {
    private fun descriptor() = JSONObject("""{"dai":{"assetKey":"provider-live-fixture"}}""")
    private val manifest = "https://media.invalid/live/master.m3u8?session=private-fixture&ads=normal"
    private fun response() = JSONObject().put("stream_manifest", manifest)
        .put("metadata_url", "https://dai.invalid/metadata?session=private-fixture")
        .put("media_verification_url", "https://dai.invalid/verify/session/")
        .put("session_update_url", "https://dai.invalid/update/session")
    @Test fun providerOriginalMediaAndMeasurementStayTogether() {
        val request = TenLiveProtocol.liveRequest(descriptor(), "provider-auth-fixture")
        assertEquals("provider-live-fixture", request.assetKey)
        assertEquals("provider-auth-fixture", request.authToken)
        assertFalse(request.toString().contains("provider-auth"))
        val session = TenLiveProtocol.liveSession(response(), TenLiveProtocol.Format.HLS)
        assertEquals(manifest, session.manifest)
        assertTrue(session.metadata.contains("private-fixture"))
        assertEquals(10L, session.pollingSeconds)
        assertFalse(session.toString().contains("private-fixture"))
    }
    @Test fun vodMissingAuthenticationAndDrmNeverPretendToBeClearLive() {
        for ((data, token) in listOf(descriptor().put("dai", JSONObject().put("videoId", "vod")) to "auth",
            descriptor() to "", descriptor().put("drm", true) to "auth")) {
            try { TenLiveProtocol.liveRequest(data, token); fail() } catch (_: ProviderException) { }
        }
    }
    @Test fun missingManifestOrMeasurementCannotUseDerivedOrAdFreeFallback() {
        for (key in listOf("stream_manifest", "metadata_url", "media_verification_url")) {
            val data = response(); data.remove(key)
            data.put("id3_events_url", "https://dai.invalid/id3-events.json")
            try { TenLiveProtocol.liveSession(data, TenLiveProtocol.Format.HLS); fail() } catch (_: ProviderException) { }
        }
        try { TenLiveProtocol.liveSession(response().put("polling_frequency", 0), TenLiveProtocol.Format.HLS); fail() }
        catch (_: ProviderException) { }
        try { TenLiveProtocol.liveSession(response().put("metadata_url", "http://unsafe.invalid/"), TenLiveProtocol.Format.HLS); fail() }
        catch (_: ProviderException) { }
    }
    @Test fun catalogueKeepsDescriptorDistinctFromStableGuideIdentity() {
        val url = "https://10.com.au/api/fixture/playback/main?session=private-fixture"
        val row = TenLiveProtocol.catalogue(JSONObject().put("channels", org.json.JSONArray().put(
            JSONObject().put("name", "Main VIC fixture").put("streamUrl", url)))).single()
        assertEquals(url, row.descriptorUrl)
        assertFalse(row.toString().contains("private-fixture"))
        try { TenLiveProtocol.catalogue(JSONObject("""{"channels":[{"name":"invalid","streamUrl":"http://unsafe.invalid/"}]}""")); fail() }
        catch (_: ProviderException) { }
    }
}
