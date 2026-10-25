package dev.prestwich.autv.data.provider

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class SbsAdapterTest {
    private class Store : ProviderSessionStore {
        var value: ProviderSession? = null
        override fun read() = value
        override fun write(session: ProviderSession) { value = session }
        override fun clear() { value = null }
    }
    private fun token(expiry: Long = 3600) = "fixture." + Base64.getUrlEncoder().withoutPadding()
        .encodeToString("""{"exp":$expiry}""".toByteArray()) + ".fixture"
    private fun signedIn() = Store().apply { value = ProviderSession(token(), "refresh-fixture", 3500000) }
    private fun adapter(transport: SbsTransport, store: Store = signedIn(), pending: ProviderActivationStore = MemoryProviderActivationStore(), clock: () -> Long = { 1000 }) =
        SbsAdapter(transport, store, pending, "fixture-device-id", clock) { String(Base64.getUrlDecoder().decode(it)) }
    private val catalogue = """{"items":[{"channel":{"mpxMediaID":"sbs-official-id","title":"SBS NSW"}},
        {"channel":{"mpxMediaID":"viceland-official-id","title":"SBS VICELAND"}},
        {"channel":{"mpxMediaID":"sbs-official-id","title":"SBS duplicate"}}]}"""
    @Test fun deviceActivationRestoresSameCodeAndKeepsCredentialsSeparate() {
        val sessions = Store(); val pending = MemoryProviderActivationStore()
        var requests = 0
        var approved = false
        val transport = SbsTransport { _, url, body, bearer ->
            assertNull(bearer)
            requests++
            when {
                url.endsWith("/link") -> {
                    assertEquals("AUTV Android TV", body!!.getString("deviceName"))
                    JSONObject("""{"userCode":"1234","deviceCode":"private-fixture","verifyURI":"sbs.com.au/connect","qrCodeURI":"https://example.invalid/untrusted","expiresIn":300,"interval":3}""")
                }
                else -> {
                    assertEquals("private-fixture", body!!.getString("deviceCode"))
                    if (approved) JSONObject().put("accessToken", token()).put("refreshToken", "refresh-fixture")
                    else JSONObject("""{"error":"authorization_pending"}""")
                }
            }
        }
        val first = adapter(transport, sessions, pending)
        val challenge = first.beginSignIn()
        assertEquals(SbsAdapter.ACTIVATION, challenge.verificationUrl)
        assertEquals(3000, challenge.pollIntervalMillis)
        val restored = adapter(transport, sessions, pending)
        assertSame(challenge, restored.beginSignIn())
        assertEquals(1, requests)
        assertFalse(restored.pollSignIn(challenge))
        approved = true
        assertTrue(restored.pollSignIn(challenge))
        assertEquals(ProviderConnection.CONNECTED, restored.connection)
        assertNull(pending.readActivation())
        assertNotNull(sessions.value)
        restored.signOut()
        assertNull(sessions.value)
    }
    @Test fun discoveryDeduplicatesOfficialIdsAndEachTuneKeepsOriginalHls() {
        val address = "https://example.invalid/master_720.m3u8?originpath=fixture&ads=1"
        val provider = adapter(SbsTransport { _, url, body, bearer ->
            assertEquals(token(), bearer)
            if (url.contains("/guides-v2")) JSONObject(catalogue)
            else {
                assertTrue(url.endsWith("sbs-official-id"))
                assertEquals("fixture-device-id", body!!.getJSONObject("advertising").getString("deviceID"))
                JSONObject().put("streamProviders", org.json.JSONArray().put(JSONObject().put("type", "HLS").put("url", address)))
            }
        })
        val channels = provider.channels()
        assertEquals(2, channels.size)
        assertFalse(channels.toString().contains("m3u8"))
        val request = provider.resolve(channels.first().id)
        assertEquals(address, request.uri)
        assertEquals(channels.first().id, request.channelId)
        assertTrue(request.headers.isEmpty())
    }
    @Test fun unsupportedAdSessionAndDrmDoNotBecomeAlternateStreams() {
        for ((source, failure) in listOf(
            """{"type":"GoogleDAI","assetKey":"fixture"}""" to ProviderFailure.AD_SESSION_UNSUPPORTED,
            """{"type":"HLS","url":"https://example.invalid/drm.m3u8","drm":{"system":"widevine"}}""" to ProviderFailure.DRM_UNSUPPORTED,
            """{"type":"HLS","url":"http://example.invalid/live.m3u8"}""" to ProviderFailure.PLAYBACK_UNSUPPORTED)) {
            val provider = adapter(SbsTransport { _, url, _, _ ->
                if (url.contains("guides-v2")) JSONObject(catalogue) else JSONObject("""{"streamProviders":[$source]}""")
            })
            expect(failure) { provider.resolve(ProviderChannelId("sbs", "sbs-official-id")) }
        }
    }
    @Test fun unknownChannelAndOtherProviderCannotResolve() {
        var streams = 0
        val provider = adapter(SbsTransport { _, _, _, _ -> streams++; JSONObject(catalogue) })
        expect(ProviderFailure.UNAVAILABLE) { provider.resolve(NineNowAdapter.SEINFELD) }
        assertEquals(0, streams)
        expect(ProviderFailure.UNAVAILABLE) { provider.resolve(ProviderChannelId("sbs", "missing")) }
        assertEquals(1, streams)
    }
    @Test fun refreshRotatesTokensButTemporaryFailureRetainsSession() {
        for (failure in listOf(ProviderFailure.OFFLINE, ProviderFailure.RETRY_LATER, ProviderFailure.SESSION_EXPIRED)) {
            val store = signedIn()
            val provider = adapter(SbsTransport { _, _, _, _ -> throw ProviderException(failure) }, store, clock = { 4000000 })
            expect(if (failure == ProviderFailure.SESSION_EXPIRED) ProviderFailure.SESSION_EXPIRED else failure) { provider.channels() }
            if (failure == ProviderFailure.SESSION_EXPIRED) assertNull(store.value) else assertNotNull(store.value)
        }
        val store = signedIn()
        val provider = adapter(SbsTransport { _, url, body, bearer ->
            if (url.endsWith("/refresh")) {
                assertNull(bearer)
                assertEquals("refresh-fixture", body!!.getString("refreshToken"))
                JSONObject().put("accessToken", token(8000)).put("refreshToken", "rotated-refresh-fixture")
            } else { assertEquals(token(8000), bearer); JSONObject(catalogue) }
        }, store, clock = { 4000000 })
        provider.channels()
        assertEquals("rotated-refresh-fixture", store.value!!.refreshToken)
    }
    @Test fun expiredAndDeniedChallengesClearPendingState() {
        for ((response, failure) in listOf("access_denied" to ProviderFailure.ACTIVATION_DENIED, "expired_token" to ProviderFailure.ACTIVATION_EXPIRED)) {
            val pending = MemoryProviderActivationStore()
            val challenge = DeviceActivation("fixture", "fixture", SbsAdapter.ACTIVATION, 2000, 1000)
            pending.writeActivation(challenge)
            val provider = adapter(SbsTransport { _, _, _, _ -> JSONObject().put("error", response) }, Store(), pending)
            expect(failure) { provider.pollSignIn(challenge) }
            assertNull(pending.readActivation())
            assertEquals(ProviderConnection.NOT_CONNECTED, provider.connection)
        }
    }
    @Test fun staleSavedChallengeAndUnverifiedActivationAddressAreRejected() {
        val pending = MemoryProviderActivationStore().apply { writeActivation(DeviceActivation("f", "f", SbsAdapter.ACTIVATION, 500, 1000)) }
        val provider = adapter(SbsTransport { _, _, _, _ -> JSONObject("""{"userCode":"1234","deviceCode":"private","expiresIn":300,"verifyURI":"evil.invalid/connect"}""") }, Store(), pending)
        assertNull(pending.readActivation())
        expect(ProviderFailure.AUTH_RESPONSE_UNSUPPORTED) { provider.beginSignIn() }
    }
    private fun expect(failure: ProviderFailure, action: () -> Unit) {
        try { action(); fail("Expected $failure") } catch (error: ProviderException) { assertEquals(failure, error.failure) }
    }
}
