package dev.prestwich.autv

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.prestwich.autv.data.provider.ProviderConnection
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ProviderSettingsUiTest {
    @get:Rule val ui = createAndroidComposeRule<ProviderTestActivity>()
    @Test fun providerCardsOpenDetailsWithoutOfferingUnsupportedSignIn() {
        ui.onNodeWithTag("settings-button").performClick()
        ui.onNodeWithTag("provider-abc").performScrollTo().performClick()
        ui.onNodeWithText("ABC iview").assertExists()
        ui.onNodeWithTag("9now-connect").assertDoesNotExist()
        ui.onNodeWithTag("providers-back").performClick()
        ui.onNodeWithTag("provider-9now").performScrollTo().performClick()
        ui.onNodeWithTag("9now-connection-state").assertExists()
    }
    @Test fun abcAndTenFailClosedWithoutAuthorisedClientConfiguration() {
        ui.onNodeWithTag("settings-button").performClick()
        ui.onNodeWithTag("provider-abc").performScrollTo().performClick()
        ui.onNodeWithTag("abc-unavailable").assertIsDisplayed()
        ui.onNodeWithTag("abc-connect").assertDoesNotExist()
        ui.onNodeWithTag("providers-back").performClick()
        ui.onNodeWithTag("provider-ten").performScrollTo().performClick()
        ui.onNodeWithTag("ten-unavailable").assertIsDisplayed()
        ui.onNodeWithTag("ten-connect").assertDoesNotExist()
    }

    @Test fun settingsSignOutRemovesPrivateChannelsWithoutRecreatingPlayer() {
        ui.waitUntil(30_000) {
            ui.activity.playback.guide?.channels?.any { it.providerChannel != null } == true
        }
        val player = ui.activity.playback.player
        ui.onNodeWithTag("settings-button").performClick()
        ui.onNodeWithText("Australian services").assertIsDisplayed()
        ui.onNodeWithTag("provider-9now").performScrollTo().performClick()
        ui.onNodeWithTag("9now-connection-state").assertTextEquals("Connected")
        ui.onNodeWithTag("9now-disconnect").performScrollTo().performClick()
        ui.waitUntil(30_000) {
            ui.activity.playback.providerConnection == ProviderConnection.NOT_CONNECTED && !ui.activity.playback.providerBusy
        }
        ui.onNodeWithTag("9now-connect").assertIsDisplayed()
        ui.onNodeWithTag("close-settings").performClick()
        ui.runOnIdle {
            assertSame(player, ui.activity.playback.player)
            assertTrue(ui.activity.playback.guide!!.channels.none { it.providerChannel != null })
        }
    }
}
