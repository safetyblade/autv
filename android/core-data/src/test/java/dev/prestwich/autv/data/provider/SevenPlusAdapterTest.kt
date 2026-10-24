package dev.prestwich.autv.data.provider

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SevenPlusAdapterTest {
    private class Store : ProviderSessionStore {
        var value: ProviderSession? = null
        override fun read() = value
        override fun write(session: ProviderSession) { value = session }
        override fun clear() { value = null }
    }
    private val challenge = """{"user_code":"ABCD","device_code":"private-fixture","verification_uri_complete":"https://7plus.com.au/connect?code=ABCD&locationRequired=True","expires_in":300,"interval":5}"""
    private val tokens = """{"access_token":"access-fixture","refresh_token":"refresh-fixture","expires_in":3600}"""
    private fun failure(expected: ProviderFailure, action: () -> Unit) {
        try { action(); fail("Expected sanitized provider failure") } catch (e: ProviderException) { assertEquals(expected, e.failure) }
    }
    @Test fun persistsSameChallengeAndUsesLocationVerificationWithoutEncodingPrivateQuery() {
        val sessions = Store(); val pending = MemoryProviderActivationStore(); var count = 0; var approved = false
        val transport = SevenPlusTransport { _, url, form ->
            if (url.endsWith("/authorize")) {
                count++; assertEquals("true", form["locationVerificationRequired"])
                assertEquals("androidtv", form["platformId"]); assertEquals("fixture-id", form["deviceId"])
                JSONObject(challenge)
            } else {
                assertEquals("private-fixture", form["deviceCode"])
                JSONObject(if (approved) tokens else """{"error":"authorization_pending"}""")
            }
        }
        val first = SevenPlusAdapter(transport, sessions, pending, "fixture-id") { 1000 }
        val activation = first.beginSignIn()
        val restored = SevenPlusAdapter(transport, sessions, pending, "fixture-id") { 1000 }
        assertSame(activation, restored.beginSignIn()); assertEquals(1, count)
        assertEquals(SevenPlusAdapter.ACTIVATION, activation.verificationUrl)
        assertFalse(restored.pollSignIn(activation)); approved = true
        assertTrue(restored.pollSignIn(activation)); assertNull(pending.readActivation())
        assertEquals(ProviderConnection.CONNECTED, restored.connection)
        restored.signOut(); assertNull(sessions.value)
        failure(ProviderFailure.ACTIVATION_DENIED) { restored.pollSignIn(activation) }
    }
    @Test fun terminalExpiryDenialAndUnknownResponseNeverConnect() {
        for ((response, expected) in listOf("expired_token" to ProviderFailure.ACTIVATION_EXPIRED,
            "access_denied" to ProviderFailure.ACTIVATION_DENIED, "unexpected" to ProviderFailure.AUTH_RESPONSE_UNSUPPORTED)) {
            val store = Store(); var clock = 1000L
            val adapter = SevenPlusAdapter(SevenPlusTransport { _, url, _ ->
                JSONObject(if (url.endsWith("/authorize")) challenge else """{"error":"$response"}""")
            }, store, MemoryProviderActivationStore(), "id") { clock }
            val activation = adapter.beginSignIn()
            failure(expected) { adapter.pollSignIn(activation) }
            assertNull(store.value); assertNotEquals(ProviderConnection.CONNECTED, adapter.connection)
            clock = activation.expiresAt
            if (adapter.pendingActivation != null) failure(ProviderFailure.ACTIVATION_EXPIRED) { adapter.pollSignIn(activation) }
        }
    }
    @Test fun rejectsLookalikeActivationDestinationAndIncompleteTokens() {
        for (address in listOf("http://7plus.com.au/connect", "https://7plus.com.au.evil.invalid/connect", "https://user@7plus.com.au/connect", "https://7plus.com.au:443/connect")) {
            val adapter = SevenPlusAdapter(SevenPlusTransport { _, _, _ -> JSONObject(challenge).put("verification_uri_complete", address) }, Store(), MemoryProviderActivationStore(), "id") { 1000 }
            failure(ProviderFailure.AUTH_RESPONSE_UNSUPPORTED) { adapter.beginSignIn() }
        }
        val store = Store()
        val adapter = SevenPlusAdapter(SevenPlusTransport { _, url, _ -> JSONObject(if (url.endsWith("/authorize")) challenge else """{"access_token":"incomplete"}""") }, store, MemoryProviderActivationStore(), "id") { 1000 }
        failure(ProviderFailure.AUTH_RESPONSE_UNSUPPORTED) { adapter.pollSignIn(adapter.beginSignIn()) }
        assertNull(store.value)
    }
    @Test fun catalogueUsesDetectedMarketAndStableDeduplicatedIds() {
        val store = Store().apply { value = ProviderSession("access-fixture", "refresh-fixture", 999999) }
        val adapter = SevenPlusAdapter(SevenPlusTransport { _, url, form ->
            assertTrue(form.isEmpty())
            if (url == SevenPlusAdapter.MARKET) JSONObject("""{"_id":"official-market"}""")
            else {
                assertTrue(url.contains("market-id=official-market"))
                JSONObject("""{"items":[{"childType":"channelItem","mediaItems":[{"type":"ChannelItem","channelId":7,"name":"Seven fixture"},{"type":"ChannelItem","channelId":7,"name":"Duplicate"},{"type":"ChannelItem","channelId":72,"name":"7two fixture"}]}]}""")
            }
        }, store, MemoryProviderActivationStore(), "id") { 1000 }
        val rows = adapter.channels()
        assertEquals(listOf("7", "72"), rows.map { it.id.channel })
        assertTrue(rows.all { it.id.provider == "seven" && it.logoUrl == null })
        failure(ProviderFailure.PLAYBACK_UNSUPPORTED) { adapter.resolve(rows.first().id) }
        failure(ProviderFailure.UNAVAILABLE) { adapter.resolve(ProviderChannelId("9now", "7")) }
    }
    @Test fun missingMarketDoesNotFallBackToSydney() {
        val store = Store().apply { value = ProviderSession("access", "refresh", 999999) }; var count = 0
        val adapter = SevenPlusAdapter(SevenPlusTransport { _, _, _ -> count++; JSONObject() }, store, MemoryProviderActivationStore(), "id") { 1000 }
        failure(ProviderFailure.REGION_DENIED) { adapter.channels() }; assertEquals(1, count)
    }
    @Test fun refreshRotatesTokensAndTransientFailureKeepsSession() {
        val store = Store().apply { value = ProviderSession("old", "refresh-old", 999) }; var offline = true
        val adapter = SevenPlusAdapter(SevenPlusTransport { _, url, form ->
            if (url.endsWith("/token")) {
                assertEquals("refresh-old", form["refreshToken"])
                if (offline) throw ProviderException(ProviderFailure.OFFLINE)
                JSONObject(tokens)
            } else JSONObject("""{"_id":"market"}""")
        }, store, MemoryProviderActivationStore(), "id") { 1000 }
        failure(ProviderFailure.OFFLINE) { adapter.resolve(ProviderChannelId("seven", "7")) }
        assertEquals("old", store.value!!.accessToken); offline = false
        failure(ProviderFailure.PLAYBACK_UNSUPPORTED) { adapter.resolve(ProviderChannelId("seven", "7")) }
        assertEquals("refresh-fixture", store.value!!.refreshToken)
        assertEquals(ProviderConnection.CONNECTED, adapter.connection)
    }
}
