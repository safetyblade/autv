package dev.prestwich.autv.provider

import androidx.test.platform.app.InstrumentationRegistry
import dev.prestwich.autv.data.provider.ProviderSession
import dev.prestwich.autv.data.provider.DeviceActivation
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.KeyStore

class EncryptedProviderSessionStoreTest {
    @Test fun sevenChallengeAndSessionSurviveRecreationAndLogoutKeepsOtherStores() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val seven = EncryptedProviderSessionStore(context, "autv.fixture.seven.persistence", "seven-persistence-fixture")
        val nine = EncryptedProviderSessionStore(context, "autv.fixture.nine.seven", "nine-seven-fixture")
        val sbs = EncryptedProviderSessionStore(context, "autv.fixture.sbs.seven", "sbs-seven-fixture")
        try {
            seven.clear()
            nine.write(ProviderSession("nine-access", "nine-refresh", 999999))
            sbs.write(ProviderSession("sbs-access", "sbs-refresh", 999999))
            var requests = 0
            val transport = dev.prestwich.autv.data.provider.SevenPlusTransport { _, url, _ ->
                requests++
                if (url.endsWith("/authorize")) org.json.JSONObject("""{"user_code":"ABCD","device_code":"private-seven","verification_uri":"https://7plus.com.au/connect","expires_in":300,"interval":5}""")
                else org.json.JSONObject("""{"access_token":"seven-access","refresh_token":"seven-refresh","expires_in":3600}""")
            }
            val first = dev.prestwich.autv.data.provider.SevenPlusAdapter(transport, seven, seven, "id")
            val challenge = first.beginSignIn()
            val restored = EncryptedProviderSessionStore(context, "autv.fixture.seven.persistence", "seven-persistence-fixture")
            val adapter = dev.prestwich.autv.data.provider.SevenPlusAdapter(transport, restored, restored, "id")
            assertEquals(challenge.deviceCode, adapter.beginSignIn().deviceCode)
            assertEquals(1, requests)
            assertTrue(adapter.pollSignIn(adapter.pendingActivation!!))
            assertEquals("seven-refresh", seven.read()!!.refreshToken)
            assertNull(seven.readActivation())
            val reopened = dev.prestwich.autv.data.provider.SevenPlusAdapter(transport, seven, seven, "id")
            assertEquals(dev.prestwich.autv.data.provider.ProviderConnection.CONNECTED, reopened.connection)
            reopened.signOut()
            assertNull(seven.read())
            assertEquals("nine-refresh", nine.read()!!.refreshToken)
            assertEquals("sbs-refresh", sbs.read()!!.refreshToken)
        } finally { seven.clear(); nine.clear(); sbs.clear() }
    }

    @Test fun sbsSignOutDeletesOnlyItsOwnEncryptedIdentity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val nine = EncryptedProviderSessionStore(context, "autv.fixture.nine.isolation", "nine-isolation-fixture")
        val sbs = EncryptedProviderSessionStore(context, "autv.fixture.sbs.isolation", "sbs-isolation-fixture")
        try {
            nine.write(ProviderSession("nine-access-fixture", "nine-refresh-fixture", 999999))
            sbs.write(ProviderSession("sbs-access-fixture", "sbs-refresh-fixture", 999999))
            sbs.clear()
            assertNull(sbs.read())
            assertEquals("nine-refresh-fixture", nine.read()!!.refreshToken)
        } finally { nine.clear(); sbs.clear() }
    }
    @Test fun sbsApprovalParsesAndroidJwtAndPersistsTheSession() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = EncryptedProviderSessionStore(context, "autv.fixture.sbs.jwt", "sbs-jwt-fixture")
        val payload = org.json.JSONObject().put("exp", System.currentTimeMillis() / 1000 + 3600).toString()
        val token = "fixture." + android.util.Base64.encodeToString(payload.toByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP) + ".fixture"
        try {
            val adapter = dev.prestwich.autv.data.provider.SbsAdapter(dev.prestwich.autv.data.provider.SbsTransport { _, url, _, _ ->
                if (url.endsWith("/link")) org.json.JSONObject("""{"userCode":"1234","deviceCode":"fixture","expiresIn":300,"interval":1,"verifyURI":"sbs.com.au/connect"}""")
                else org.json.JSONObject().put("accessToken", token).put("refreshToken", "refresh-fixture")
            }, store, store, "device-fixture")
            assertTrue(adapter.pollSignIn(adapter.beginSignIn()))
            assertEquals(token, store.read()!!.accessToken)
            assertNull(store.readActivation())
        } finally { store.clear() }
    }
    @Test fun pendingChallengeIsEncryptedAndSurvivesReadingAnAbsentSession() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val alias = "autv.9now.fixture.activation"
        val name = "9now-activation-fixture"
        val first = EncryptedProviderSessionStore(context, alias, name)
        first.clear()
        try {
            first.writeActivation(DeviceActivation("fixture-user-code", "fixture-device-code",
                "https://www.9now.com.au/activate", 999999L, 5000))
            val ciphertext = File(context.noBackupFilesDir, "$name-activation").readBytes()
            assertFalse(String(ciphertext, Charsets.ISO_8859_1).contains("fixture-device-code"))
            val restored = EncryptedProviderSessionStore(context, alias, name)
            assertNull(restored.read())
            assertEquals("fixture-device-code", restored.readActivation()!!.deviceCode)
            restored.clearActivation()
            assertNull(restored.readActivation())
        } finally { first.clear() }
    }
    @Test fun ciphertextSurvivesRecreationAndSignOutDeletesKeyAndRecord() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val alias = "autv.9now.fixture.session"
        val fileName = "9now-session-fixture"
        val store = EncryptedProviderSessionStore(context, alias, fileName)
        store.clear()
        try {
            store.write(ProviderSession("fixture-access-value", "fixture-refresh-value", 123456L))
            val ciphertext = File(context.noBackupFilesDir, fileName).readBytes()
            assertFalse(String(ciphertext, Charsets.ISO_8859_1).contains("fixture-access-value"))
            val restored = EncryptedProviderSessionStore(context, alias, fileName).read()!!
            assertEquals("fixture-access-value", restored.accessToken)
            assertEquals("fixture-refresh-value", restored.refreshToken)
            assertEquals(123456L, restored.expiresAt)
            store.clear()
            assertFalse(File(context.noBackupFilesDir, fileName).exists())
            val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            assertFalse(keys.containsAlias(alias))
            assertNull(store.read())
        } finally { store.clear() }
    }
}
