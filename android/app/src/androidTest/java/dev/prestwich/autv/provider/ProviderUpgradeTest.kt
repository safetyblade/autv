package dev.prestwich.autv.provider

import androidx.test.platform.app.InstrumentationRegistry
import dev.prestwich.autv.data.provider.ProviderSession
import org.junit.Assert.*
import org.junit.Test

/** Run the two methods separately, with adb install -r of a newer APK between them. */
class ProviderUpgradeTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun store() = EncryptedProviderSessionStore(context, "autv.fixture.upgrade", "upgrade-session-fixture")

    @Test fun beforeUpdate() {
        store().write(ProviderSession("upgrade-access-fixture", "upgrade-refresh-fixture", 99999999))
        assertTrue(context.getSharedPreferences("upgrade-fixture", 0).edit()
            .putString("favourite", "provider:9now:fixture")
            .putInt("old-version", context.packageManager.getPackageInfo(context.packageName, 0).versionCode).commit())
    }
    @Test fun afterUpdate() {
        val preferences = context.getSharedPreferences("upgrade-fixture", 0)
        try {
            val saved = store().read()
            assertNotNull("Encrypted session must survive an in-place APK update", saved)
            assertEquals("upgrade-refresh-fixture", saved!!.refreshToken)
            assertEquals("upgrade-access-fixture", saved.accessToken)
            assertEquals("provider:9now:fixture", preferences.getString("favourite", null))
            assertTrue(context.packageManager.getPackageInfo(context.packageName, 0).versionCode > preferences.getInt("old-version", Int.MAX_VALUE))
        } finally {
            store().clear()
            preferences.edit().clear().commit()
        }
    }
}
