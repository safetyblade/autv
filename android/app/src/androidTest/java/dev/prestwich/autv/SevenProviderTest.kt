package dev.prestwich.autv

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.prestwich.autv.data.provider.ProviderConnection
import dev.prestwich.autv.guide.TuneTarget
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SevenProviderTest {
    @get:Rule val ui = createAndroidComposeRule<SevenTestActivity>()
    @Test fun qrApprovalDiscoversCatalogueWithoutClaimingPlaybackAndLogoutKeepsNine() {
        ui.onNodeWithTag("settings-button").performClick()
        ui.onNodeWithTag("provider-seven").performScrollTo().performClick()
        ui.onNodeWithTag("seven-connect").performScrollTo().performClick()
        ui.waitUntil(10000) { ui.activity.playback.sevenAccount.activation != null }
        ui.onNodeWithTag("activation-qr").performScrollTo().assertExists()
        ui.onNodeWithTag("activation-address").performScrollTo().assertTextEquals("https://7plus.com.au/connect")
        ui.onNodeWithTag("activation-code").performScrollTo().assertTextEquals("SEVEN")
        ui.runOnIdle { ui.activity.approved = true }
        ui.waitUntil(30000) { ui.activity.playback.sevenAccount.connection == ProviderConnection.CONNECTED && !ui.activity.playback.sevenAccount.busy }
        ui.onNodeWithTag("seven-connection-state").performScrollTo().assertTextEquals("Connected")
        ui.onNodeWithText("1 channels listed. Channel 7 native playback is experimental.").performScrollTo().assertExists()
        ui.onNodeWithTag("close-settings").performScrollTo().performClick()
        ui.runOnIdle {
            val channel = ui.activity.playback.guide!!.channels.first { it.providerChannel?.id?.provider == "seven" }
            ui.activity.playback.request(TuneTarget.Number(channel.number))
        }
        ui.waitUntil(10000) { ui.activity.playback.sevenAccount.error != null }
        ui.runOnIdle {
            assertTrue(ui.activity.playback.sevenAccount.error!!.contains("[7-CHANNEL]"))
            assertEquals(ProviderConnection.CONNECTED, ui.activity.playback.sevenAccount.connection)
            assertEquals(ProviderConnection.CONNECTED, ui.activity.playback.providerConnection)
            assertFalse(ui.activity.playback.player.currentMediaItem?.mediaId == ui.activity.playback.guide!!.channels.first { it.providerChannel?.id?.provider == "seven" }.number.toString())
            ui.activity.playback.sevenAccount.disconnect()
        }
        ui.waitUntil(10000) { !ui.activity.playback.sevenAccount.busy }
        ui.runOnIdle {
            assertEquals(ProviderConnection.CONNECTED, ui.activity.playback.providerConnection)
            assertEquals(ProviderConnection.NOT_CONNECTED, ui.activity.playback.sevenAccount.connection)
            assertFalse(ui.activity.playback.guide!!.channels.any { it.providerChannel?.id?.provider == "seven" })
        }
    }
}
