package dev.prestwich.autv

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.prestwich.autv.data.provider.ProviderConnection
import org.junit.After
import org.junit.Rule
import org.junit.Test

/** Official activation protocol is simulated; no Nine account or external calls. */
class ProviderQrIntegrationTest {
    @get:Rule val ui = createAndroidComposeRule<ProviderActivationTestActivity>()
    @After fun clearFixture() { ActivationFixture.reset() }
    @Test fun existingNineFlowShowsQrThenConnectedEvenWhenCatalogueIsEmpty() {
        ui.onNodeWithTag("settings-button").performClick()
        ui.onNodeWithTag("provider-9now").performScrollTo().performClick()
        ui.onNodeWithTag("9now-connect").performScrollTo().performClick()
        ui.waitUntil(10000) { ui.activity.playback.providerActivation != null }
        ui.onNodeWithTag("activation-qr").performScrollTo().assertExists()
        ui.onNodeWithTag("activation-code").performScrollTo().assertTextEquals("FIXTURE")
        ActivationFixture.approved.set(true)
        ui.waitUntil(30000) { ui.activity.playback.providerConnection == ProviderConnection.CONNECTED && !ui.activity.playback.providerBusy }
        ui.onNodeWithTag("activation-qr").assertDoesNotExist()
        ui.onNodeWithTag("9now-connection-state").performScrollTo().assertTextEquals("Connected")
    }
}
