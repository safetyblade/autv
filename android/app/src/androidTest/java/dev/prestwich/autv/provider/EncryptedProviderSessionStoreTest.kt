package dev.prestwich.autv.provider

import androidx.test.platform.app.InstrumentationRegistry
import dev.prestwich.autv.data.provider.ProviderSession
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.KeyStore

class EncryptedProviderSessionStoreTest {
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
