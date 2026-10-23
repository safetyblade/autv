package dev.prestwich.autv

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.prestwich.autv.data.provider.ProviderConnection
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ProviderSettingsUiTest {
    @get:Rule val ui = createAndroidComposeRule<ProviderTestActivity>()
    @Test fun settingsSignOutRemovesPrivateChannelsWithoutRecreatingPlayer() {
        ui.waitUntil(30_000) {
            ui.activity.playback.guide?.channels?.any { it.providerChannel != null } == true
        }
        val player = ui.activity.playback.player
        ui.onNodeWithTag("settings-button").performClick()
        ui.onNodeWithText("Australian services").assertIsDisplayed()
        ui.onNodeWithTag("9now-connection-state").assertTextEquals("Connected")
        ui.onNodeWithTag("9now-disconnect").performScrollTo().performClick()
        ui.waitUntil(30_000) {
            ui.activity.playback.providerConnection == ProviderConnection.NOT_CONNECTED && !ui.activity.playback.providerBusy
        }
        ui.onNodeWithTag("9now-connect").assertIsDisplayed()
        ui.onNodeWithTag("close-settings").performScrollTo().performClick()
        ui.runOnIdle {
            assertSame(player, ui.activity.playback.player)
            assertTrue(ui.activity.playback.guide!!.channels.none { it.providerChannel != null })
        }
    }
}
