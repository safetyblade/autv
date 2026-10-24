package dev.prestwich.autv.provider

import androidx.test.platform.app.InstrumentationRegistry
import dev.prestwich.autv.data.provider.ProviderSession
import dev.prestwich.autv.data.provider.DeviceActivation
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.KeyStore

class EncryptedProviderSessionStoreTest {
    @Test fun abcPrivateUidAndPendingRequestContextRemainEncryptedAcrossRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = EncryptedProviderSessionStore(context,"autv.abc.fixture.context","abc-context-fixture")
        store.clear()
        try {
            store.write(ProviderSession("fixture-token","",999999L,mapOf("uid" to "private-uid-fixture")))
            store.writeActivation(DeviceActivation("TEST","fixture-device","https://www.abc.net.au/linktv",999999L,3000,mapOf("requestId" to "private-request-fixture")))
            for(name in listOf("abc-context-fixture","abc-context-fixture-activation")) {
                val encrypted=File(context.noBackupFilesDir,name).readBytes().toString(Charsets.ISO_8859_1)
                assertFalse(encrypted.contains("private-uid-fixture"));assertFalse(encrypted.contains("private-request-fixture"))
            }
            val restored=EncryptedProviderSessionStore(context,"autv.abc.fixture.context","abc-context-fixture")
            assertEquals("private-uid-fixture",restored.read()!!.privateContext["uid"])
            assertEquals("private-request-fixture",restored.readActivation()!!.privateContext["requestId"])
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
