package dev.prestwich.autv.data.provider

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NineNowAdapterTest {
    private class MemoryStore : ProviderSessionStore {
        var value: ProviderSession? = null
        override fun read() = value
        override fun write(session: ProviderSession) { value = session }
        override fun clear() { value = null }
    }
    private val catalogue = """{"data":{"getLivestream":{"channels":[
        {"slug":"seinfeld","referenceId":"official-reference","name":"Seinfeld 24/7",
         "stream":{"url":"https://example.invalid/ssai/live.m3u8?session=fixture"}},
        {"slug":"channel-9","referenceId":"other","name":"Nine"}
    ]}}}"""
    private fun signedInStore() = MemoryStore().apply { value = ProviderSession("access-fixture", "refresh-fixture", 100_000) }

    @Test fun activationIsUserInitiatedAndHonoursProviderInterval() {
        val store = MemoryStore()
        var calls = 0
        val adapter = NineNowAdapter(NineNowTransport { _, url, token ->
            calls++
            assertNull(token)
            when {
                url.contains("/code?") -> JSONObject("""{"auth_code":"ABC123","device_code":"device-fixture","expires_in":300,"interval":7}""")
                else -> JSONObject("""{"accessToken":"access-fixture","refresh_token":"refresh-fixture","expiresIn":3600}""")
            }
        }, store, { 1000 })
        assertEquals(0, calls)
        assertEquals(ProviderConnection.NOT_CONNECTED, adapter.connection)
        val activation = adapter.beginSignIn()
        assertEquals(7000, activation.pollIntervalMillis)
        assertEquals(301000, activation.expiresAt)
        assertNull(store.value)
        assertTrue(adapter.pollSignIn(activation))
        assertEquals(ProviderConnection.CONNECTED, adapter.connection)
        assertNotNull(store.value)
        assertEquals("DeviceActivation(redacted)", activation.toString())
        assertEquals("ProviderSession(redacted)", store.value.toString())
    }

    @Test fun discoveryOmitsPlaybackUrlsAndResolutionPreservesAdRouting() {
        val adapter = NineNowAdapter(NineNowTransport { _, _, token ->
            assertEquals("access-fixture", token)
            JSONObject(catalogue)
        }, signedInStore(), { 1000 })
        val channels = adapter.channels()
        assertEquals(1, channels.size)
        assertEquals("official-reference", channels.single().reference)
        assertEquals(NineNowAdapter.SEINFELD, channels.single().id)
        assertFalse(channels.toString().contains("ssai"))
        val request = adapter.resolve(channels.single().id)
        assertEquals("https://example.invalid/ssai/live.m3u8?session=fixture", request.uri)
        assertTrue(request.headers.isEmpty()) // Never leak account bearer to a media host.
        assertEquals("ProviderPlaybackRequest(redacted)", request.toString())
    }

    @Test fun expiredSessionRefreshesBeforeDiscoveryAndSignOutClearsIt() {
        val store = signedInStore()
        var refreshes = 0
        val adapter = NineNowAdapter(NineNowTransport { method, url, token ->
            if (url.contains("refresh-token")) {
                assertEquals("POST", method); assertNull(token); refreshes++
                JSONObject("""{"accessToken":"renewed-fixture","expiresIn":3600}""")
            } else {
                assertEquals("renewed-fixture", token)
                JSONObject(catalogue)
            }
        }, store, { 200_000 })
        adapter.channels()
        assertEquals(1, refreshes)
        adapter.signOut()
        assertNull(store.value)
        assertEquals(ProviderConnection.NOT_CONNECTED, adapter.connection)
        expect(ProviderFailure.AUTH_REQUIRED) { adapter.channels() }
    }

    @Test fun refreshDenialClearsCredentialsButOfflineFailureRetainsThem() {
        for (failure in listOf(ProviderFailure.SESSION_EXPIRED, ProviderFailure.OFFLINE)) {
            val store = signedInStore()
            val adapter = NineNowAdapter(NineNowTransport { _, _, _ -> throw ProviderException(failure) }, store, { 200_000 })
            expect(failure) { adapter.channels() }
            if (failure == ProviderFailure.OFFLINE) assertNotNull(store.value)
            else { assertNull(store.value); assertEquals(ProviderConnection.SESSION_EXPIRED, adapter.connection) }
        }
    }

    @Test fun activationExpiryAndPendingApprovalDoNotCreateSession() {
        val store = MemoryStore()
        val adapter = NineNowAdapter(NineNowTransport { _, _, _ -> JSONObject("""{"error":"authorization_pending"}""") }, store, { 1000 })
        assertFalse(adapter.pollSignIn(DeviceActivation("code", "device", "https://www.9now.com.au/activate", 2000, 5000)))
        expect(ProviderFailure.AUTH_REQUIRED) {
            adapter.pollSignIn(DeviceActivation("code", "device", "https://www.9now.com.au/activate", 1000, 5000))
        }
        assertNull(store.value)
    }

    @Test fun deniedRegionUnavailableChannelAndDeclaredDrmAreExplicitFailures() {
        val store = signedInStore()
        val denied = NineNowAdapter(NineNowTransport { _, _, _ -> throw ProviderException(ProviderFailure.REGION_DENIED) }, store, { 1000 })
        expect(ProviderFailure.REGION_DENIED) { denied.channels() }
        val missing = NineNowAdapter(NineNowTransport { _, _, _ -> JSONObject("""{"data":{"getLivestream":{"channels":[]}}}""") }, store, { 1000 })
        expect(ProviderFailure.UNAVAILABLE) { missing.resolve(NineNowAdapter.SEINFELD) }
        val protected = NineNowAdapter(NineNowTransport { _, _, _ ->
            JSONObject(catalogue.replace("\"stream\":{", "\"stream\":{\"drm\":true,"))
        }, store, { 1000 })
        expect(ProviderFailure.DRM_UNSUPPORTED) { protected.resolve(NineNowAdapter.SEINFELD) }
    }
    @Test fun titleAndSlugVariantsUseTheSameSelectionForDiscoveryAndTune() {
        for (identity in listOf(
            "\"slug\":\"seinfeld-247\",\"name\":\"Seinfeld 24/7\"",
            "\"slug\":\"seinfeld-24-7\",\"name\":\"Seinfeld\"",
            "\"slug\":\"provider-channel-id\",\"displayName\":\"Seinfeld 24 / 7\""
        )) {
            val json = """{"data":{"getLivestream":{"channels":[{$identity,
                "referenceId":"actual-provider-id","stream":{"url":"https://example.invalid/ssai/variant.m3u8"}}]}}}"""
            val adapter = NineNowAdapter(NineNowTransport { _, _, _ -> JSONObject(json) }, signedInStore(), { 1000 })
            assertEquals("actual-provider-id", adapter.channels().single().reference)
            assertEquals("https://example.invalid/ssai/variant.m3u8", adapter.resolve(NineNowAdapter.SEINFELD).uri)
            assertEquals(ProviderDiscovery(1, 0, 1), adapter.discovery)
        }
    }

    @Test fun liveEventCatalogueEntriesAreDiscoveredAndDuplicateReferencesAreCollapsed() {
        val row = """{"type":"live-event","displayName":"Seinfeld 24/7","referenceId":"live-reference",
            "stream":{"url":"https://example.invalid/ssai/event.m3u8"}}"""
        for (channels in listOf("[]", "[$row]", """[{"name":"Seinfeld","referenceId":"live-reference"}]""")) {
            val json = """{"data":{"getLivestream":{"channels":$channels,"events":[$row]}}}"""
            val adapter = NineNowAdapter(NineNowTransport { _, _, _ -> JSONObject(json) }, signedInStore(), { 1000 })
            assertEquals(1, adapter.channels().size)
            assertEquals(1, adapter.discovery!!.matches)
            assertEquals("https://example.invalid/ssai/event.m3u8", adapter.resolve(NineNowAdapter.SEINFELD).uri)
        }
    }

    @Test fun ambiguousChannelsAndUnrelatedTitlesAreNotSelectedArbitrarily() {
        val ambiguous = """{"channels":[
            {"name":"Seinfeld 24/7","referenceId":"first"},
            {"name":"Seinfeld 24/7","referenceId":"second"}]}"""
        assertTrue(NineNowAdapter.parseChannels(JSONObject(ambiguous)).isEmpty())
        val unrelated = """{"channels":[{"name":"Best of Seinfeld","slug":"seinfeld-clips","referenceId":"clip"}],
            "events":[{"type":"episode","name":"Seinfeld","referenceId":"episode"}]}"""
        assertTrue(NineNowAdapter.parseChannels(JSONObject(unrelated)).isEmpty())
    }

    @Test fun listedChannelWithoutNativeStreamIsDistinctFromMissingChannel() {
        val json = """{"data":{"getLivestream":{"events":[{"type":"live-event",
            "displayName":"Seinfeld 24/7","referenceId":"event-reference"}]}}}"""
        val adapter = NineNowAdapter(NineNowTransport { _, _, _ -> JSONObject(json) }, signedInStore(), { 1000 })
        assertEquals(1, adapter.channels().size)
        expect(ProviderFailure.PLAYBACK_UNSUPPORTED) { adapter.resolve(NineNowAdapter.SEINFELD) }
    }

    private fun expect(failure: ProviderFailure, action: () -> Unit) {
        try { action(); fail("Expected provider failure") }
        catch (error: ProviderException) { assertEquals(failure, error.failure) }
    }
}
