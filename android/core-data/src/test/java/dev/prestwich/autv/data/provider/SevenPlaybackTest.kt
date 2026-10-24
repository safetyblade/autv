package dev.prestwich.autv.data.provider

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SevenPlaybackTest {
    private class Store : ProviderSessionStore {
        var session: ProviderSession? = ProviderSession("account-fixture", "refresh-fixture", 9999999)
        override fun read() = session
        override fun write(session: ProviderSession) { this.session = session }
        override fun clear() { session = null }
    }
    private val channel = ProviderChannelId("seven", "official-main-id")
    private val original = "https://csm-e-seven-fixture.yospace.com/live/master.m3u8?ads=session-fixture&quality=original&keep=1"
    private fun catalogue(name: String = "Seven", link: String = "https://7plus.com.au/live-tv?channel-id=MAIN_FROM_PROVIDER") = JSONObject().put("items", org.json.JSONArray().put(JSONObject()
        .put("childType", "channelItem").put("mediaItems", org.json.JSONArray().put(JSONObject().put("type", "ChannelItem")
            .put("channelId", channel.channel).put("name", name).put("contentLink", JSONObject().put("url", link))))))
    private fun descriptor(url: String = original) = JSONObject().put("media", JSONObject().put("sources", org.json.JSONArray().put(JSONObject()
        .put("type", "application/x-mpegURL").put("src", url))))
    private fun failWith(expected: ProviderFailure, action: () -> Unit) {
        try { action(); fail("Expected sanitized provider failure") } catch (e: ProviderException) { assertEquals(expected, e.failure) }
    }
    @Test fun freshOfficialIdentityResolvesOriginalSsaiWithoutLeakingAccountToMedia() {
        val calls = mutableListOf<String>()
        val transport = object : SevenPlusTransport {
            override fun request(method: String, url: String, form: Map<String, String>): JSONObject {
                assertEquals("GET", method); assertTrue(form.isEmpty()); calls.add(url)
                return when {
                    url == SevenPlusAdapter.MARKET -> JSONObject("""{"_id":"detected-market"}""")
                    url.startsWith(SevenPlusAdapter.CONTENT) -> catalogue()
                    url.startsWith(SevenPlusAdapter.PLAYER) -> {
                        assertTrue(url.contains("/MAIN_FROM_PROVIDER?")); assertTrue(url.contains("market-id=detected-market"))
                        JSONObject("""{"videoPlayer":{"videoUrl":"https://metadata.invalid/reference/v2?accountId=official-account&referenceId=ref%3Amain-live&unused=1&unused=2&flag&"}}""")
                    }
                    else -> throw AssertionError("Unexpected network destination")
                }
            }
            override fun playback(parameters: Map<String, String>, accessToken: String): JSONObject {
                assertEquals("account-fixture", accessToken)
                assertEquals("official-account", parameters["accountId"])
                assertEquals("ref:main-live", parameters["referenceId"])
                assertEquals("detected-market", parameters["marketId"])
                assertEquals("fixture-device", parameters["deviceId"])
                assertFalse(parameters.keys.any { it in setOf("pc", "ozid", "sdkverification", "deliveryId") })
                return descriptor()
            }
        }
        val adapter = SevenPlusAdapter(transport, Store(), MemoryProviderActivationStore(), "fixture-device") { 1000 }
        assertEquals(channel, adapter.channels().single().id)
        val request = adapter.resolve(channel)
        assertEquals(original, request.uri); assertTrue(request.headers.isEmpty())
        assertEquals("application/x-mpegURL", request.mimeType)
        assertEquals(2, calls.count { it.startsWith(SevenPlusAdapter.CONTENT) })
    }
    @Test fun officialRelativeContentLinksResolveWithoutFollowingAnExternalUrl() {
        for (link in listOf("/live-tv?channel-id=MAIN", "live-tv?channel-id=MAIN", "/MAIN")) {
            val adapter = SevenPlusAdapter(object : SevenPlusTransport {
                override fun request(method: String, url: String, form: Map<String, String>): JSONObject = when {
                    url == SevenPlusAdapter.MARKET -> JSONObject("""{"_id":"market"}""")
                    url.startsWith(SevenPlusAdapter.CONTENT) -> catalogue(link = link)
                    else -> {
                        assertTrue(url.startsWith("${SevenPlusAdapter.PLAYER}/MAIN?"))
                        JSONObject("""{"videoPlayer":{"videoUrl":"https://videoservice.swm.digital/playback?accountId=account&referenceId=ref"}}""")
                    }
                }
                override fun playback(parameters: Map<String, String>, accessToken: String) = descriptor()
            }, Store(), MemoryProviderActivationStore(), "id") { 1000 }
            assertEquals(original, adapter.resolve(channel).uri)
        }
    }
    @Test fun unsupportedResponsesExposeOnlyFixedResolutionStageAndFailure() {
        for (stage in listOf(ProviderPlaybackStage.SEVEN_LINK, ProviderPlaybackStage.SEVEN_PLAYER,
            ProviderPlaybackStage.SEVEN_IDENTITY_ACCOUNT, ProviderPlaybackStage.SEVEN_SOURCE)) {
            val adapter = SevenPlusAdapter(object : SevenPlusTransport {
                override fun request(method: String, url: String, form: Map<String, String>): JSONObject = when {
                    url == SevenPlusAdapter.MARKET -> JSONObject("""{"_id":"market"}""")
                    url.startsWith(SevenPlusAdapter.CONTENT) -> catalogue(link = if (stage == ProviderPlaybackStage.SEVEN_LINK) "//evil.invalid/MAIN" else "/MAIN")
                    stage == ProviderPlaybackStage.SEVEN_PLAYER -> JSONObject()
                    else -> JSONObject().put("videoPlayer", JSONObject().put("videoUrl", if (stage == ProviderPlaybackStage.SEVEN_IDENTITY_ACCOUNT)
                        "https://evil.invalid/private?secret=fixture" else "https://videoservice.swm.digital/playback?accountId=account&referenceId=ref"))
                }
                override fun playback(parameters: Map<String, String>, accessToken: String) = JSONObject()
            }, Store(), MemoryProviderActivationStore(), "id") { 1000 }
            try { adapter.resolve(channel); fail("Expected unsupported response") } catch (error: ProviderException) {
                assertEquals(stage, error.stage)
                assertEquals(ProviderFailure.PLAYBACK_UNSUPPORTED, error.failure)
                assertEquals("PLAYBACK_UNSUPPORTED", error.message)
                assertFalse(error.toString().contains("secret"))
                assertTrue(stage.code.startsWith("7-"))
            }
        }
    }
    @Test fun foreignMissingAndNonPrimaryChannelsCannotRequestPlayback() {
        var playRequests = 0
        val adapter = SevenPlusAdapter(object : SevenPlusTransport {
            override fun request(method: String, url: String, form: Map<String, String>) =
                if (url == SevenPlusAdapter.MARKET) JSONObject("""{"_id":"market"}""") else catalogue("7two")
            override fun playback(parameters: Map<String, String>, accessToken: String): JSONObject { playRequests++; return descriptor() }
        }, Store(), MemoryProviderActivationStore(), "id") { 1000 }
        failWith(ProviderFailure.UNAVAILABLE) { adapter.resolve(ProviderChannelId("9now", channel.channel)) }
        failWith(ProviderFailure.UNAVAILABLE) { adapter.resolve(ProviderChannelId("seven", "removed")) }
        failWith(ProviderFailure.PLAYBACK_UNSUPPORTED) { adapter.resolve(channel) }
        assertEquals(0, playRequests)
    }
    @Test fun unreviewedLinksAndAmbiguousReferenceQueriesNeverBecomeRequests() {
        val links = listOf("http://7plus.com.au/live-tv?channel-id=MAIN", "https://evil.invalid/MAIN", "//evil.invalid/MAIN", "https://7plus.com.au/live-tv?channel-id=ONE&channel-id=TWO")
        val videos = listOf("https://videoservice.swm.digital/playback?accountId=one&accountId=two&referenceId=three")
        for ((link, video) in links.map { it to "" } + videos.map { "https://7plus.com.au/MAIN" to it }) {
            val adapter = SevenPlusAdapter(SevenPlusTransport { _, url, _ ->
                when {
                    url == SevenPlusAdapter.MARKET -> JSONObject("""{"_id":"market"}""")
                    url.startsWith(SevenPlusAdapter.CONTENT) -> catalogue(link = link)
                    else -> JSONObject().put("videoPlayer", JSONObject().put("videoUrl", video))
                }
            }, Store(), MemoryProviderActivationStore(), "id") { 1000 }
            failWith(ProviderFailure.PLAYBACK_UNSUPPORTED) { adapter.resolve(channel) }
        }
    }
    @Test fun clearSsaiIsChosenWithoutRewritingOrChoosingUnprotectedFallbackForDrm() {
        val response = descriptor()
        val sources = response.getJSONObject("media").getJSONArray("sources")
        sources.put(JSONObject().put("type", "application/dash+xml").put("src", "https://fixture.invalid/media.mpd")
            .put("key_systems", JSONObject().put("com.widevine.alpha", JSONObject().put("license_url", "https://fixture.invalid/license"))))
        assertEquals(original, SevenLiveSource.select(channel, response).uri)
        failWith(ProviderFailure.DRM_UNSUPPORTED) { SevenLiveSource.select(channel, descriptor().put("drm", true)) }
        sources.remove(0)
        failWith(ProviderFailure.DRM_UNSUPPORTED) { SevenLiveSource.select(channel, response) }
    }
    @Test fun preservesBroadcasterHlsRegardlessOfCdnAndRequiresAdAndDrmContractsWhenDeclared() {
        val originalCdn = "https://broadcaster-cdn.invalid/channel7/master.m3u8?ads=normal&session=fixture"
        val source = descriptor(originalCdn)
        source.getJSONObject("media").put("ad_sources", org.json.JSONArray())
        source.getJSONObject("media").getJSONArray("sources").getJSONObject(0).put("key_systems", JSONObject()).put("encryption_type", "none")
        assertEquals(originalCdn, SevenLiveSource.select(channel, source).uri)
        for (url in listOf("http://csm.yospace.com/master.m3u8", "https://user@csm.yospace.com/master.m3u8"))
            failWith(ProviderFailure.PLAYBACK_UNSUPPORTED) { SevenLiveSource.select(channel, descriptor(url)) }
        val response = descriptor().put("adConfig", JSONObject().put("adTagUrl", "https://fixture.invalid/ads"))
        try { SevenLiveSource.select(channel, response); fail("Separate ad configuration needs integration") } catch (error: ProviderException) {
            assertEquals(ProviderFailure.AD_SESSION_UNSUPPORTED, error.failure)
            assertEquals(ProviderPlaybackStage.SEVEN_ADS, error.stage)
        }
        failWith(ProviderFailure.REGION_DENIED) { SevenLiveSource.select(channel, JSONObject("""{"error":"location_verification_required"}""")) }
    }
    @Test fun providerSessionFailureExpiresOnlySeven() {
        val store = Store()
        val adapter = SevenPlusAdapter(object : SevenPlusTransport {
            override fun request(method: String, url: String, form: Map<String, String>) = when {
                url == SevenPlusAdapter.MARKET -> JSONObject("""{"_id":"market"}""")
                url.startsWith(SevenPlusAdapter.CONTENT) -> catalogue()
                else -> JSONObject("""{"videoPlayer":{"videoUrl":"https://videoservice.swm.digital/playback?accountId=account&referenceId=ref"}}""")
            }
            override fun playback(parameters: Map<String, String>, accessToken: String): JSONObject = throw ProviderException(ProviderFailure.SESSION_EXPIRED)
        }, store, MemoryProviderActivationStore(), "id") { 1000 }
        failWith(ProviderFailure.SESSION_EXPIRED) { adapter.resolve(channel) }
        assertNull(store.session); assertEquals(ProviderConnection.SESSION_EXPIRED, adapter.connection)
    }
}
