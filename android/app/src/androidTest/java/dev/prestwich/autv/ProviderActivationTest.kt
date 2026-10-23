package dev.prestwich.autv

import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import dev.prestwich.autv.data.provider.ProviderConnection
import org.junit.Assert.*
import org.junit.Test

class ProviderActivationTest {
    private fun await(check: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 30_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (check()) return
            SystemClock.sleep(100)
        }
        fail("Activation fixture did not reach expected state")
    }
    @Test fun transientFailureAndNewActivityResumeSameChallenge() {
        ActivationFixture.reset()
        var first: ActivityScenario<ProviderActivationTestActivity>? = null
        var restored: ActivityScenario<ProviderActivationTestActivity>? = null
        try {
            first = ActivityScenario.launch(ProviderActivationTestActivity::class.java)
            first.onActivity { it.playback.connectNineNow() }
            await { ActivationFixture.polls.get() >= 1 }
            first.onActivity {
                assertNotNull(it.playback.providerActivation)
                assertTrue(it.playback.providerBusy)
            }
            first.moveToState(Lifecycle.State.CREATED) // Browser handoff/background lifecycle.
            first.close() // Discard the old ViewModel, like an app restart.
            first = null
            assertNotNull(ActivationFixture.challenges.readActivation())
            assertEquals(1, ActivationFixture.codeRequests.get())
            ActivationFixture.approved.set(true)
            restored = ActivityScenario.launch(ProviderActivationTestActivity::class.java)
            var connected = false
            await {
                restored!!.onActivity { connected = it.playback.providerConnection == ProviderConnection.CONNECTED && !it.playback.providerBusy }
                connected
            }
            assertEquals(1, ActivationFixture.codeRequests.get())
            assertNull(ActivationFixture.challenges.readActivation())
            assertNotNull(ActivationFixture.sessions.read())
        } finally { first?.close(); restored?.close(); ActivationFixture.reset() }
    }
    @Test fun explicitCancelRemovesPendingChallenge() {
        ActivationFixture.reset()
        val scenario = ActivityScenario.launch(ProviderActivationTestActivity::class.java)
        try {
            scenario.onActivity { it.playback.connectNineNow() }
            await { ActivationFixture.challenges.readActivation() != null }
            scenario.onActivity { it.playback.disconnectNineNow() }
            var cancelled = false
            await {
                scenario.onActivity { cancelled = it.playback.providerConnection == ProviderConnection.NOT_CONNECTED && !it.playback.providerBusy }
                cancelled
            }
            assertNull(ActivationFixture.challenges.readActivation())
            assertNull(ActivationFixture.sessions.read())
        } finally { scenario.close(); ActivationFixture.reset() }
    }
}
