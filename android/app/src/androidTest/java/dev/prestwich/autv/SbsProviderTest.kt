package dev.prestwich.autv

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.prestwich.autv.data.provider.ProviderConnection
import dev.prestwich.autv.guide.TuneTarget
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SbsProviderTest {
    @get:Rule val ui = createAndroidComposeRule<SbsTestActivity>()
    private fun ready() = ui.waitUntil(30000) { ui.activity.playback.guide?.channels?.any { it.providerChannel?.id?.provider == "9now" } == true && ui.activity.playback.playing }
    private fun connect() {
        ui.runOnIdle { ui.activity.sbs.approved = true; ui.activity.playback.sbsAccount.connect() }
        ui.waitUntil(30000) { ui.activity.playback.sbsAccount.connection == ProviderConnection.CONNECTED && !ui.activity.playback.sbsAccount.busy }
    }
    private fun sbsNumber() = ui.activity.playback.guide!!.channels.first { it.providerChannel?.id?.provider == "sbs" }.number
    @Test fun settingsQrAddsSbsAndNineSignOutKeepsSbsPlaying() {
        ready()
        ui.onNodeWithTag("settings-button").performClick()
        ui.onNodeWithTag("provider-sbs").performScrollTo().performClick()
        ui.onNodeWithTag("sbs-connect").performScrollTo().performClick()
        ui.waitUntil(10000) { ui.activity.playback.sbsAccount.activation != null }
        ui.onNodeWithTag("activation-address").performScrollTo().assertTextEquals("https://sbs.com.au/connect")
        ui.onNodeWithTag("activation-code").performScrollTo().assertTextEquals("1234")
        ui.runOnIdle { ui.activity.sbs.approved = true }
        ui.waitUntil(30000) { ui.activity.playback.sbsAccount.connection == ProviderConnection.CONNECTED && !ui.activity.playback.sbsAccount.busy }
        ui.onNodeWithTag("sbs-connection-state").performScrollTo().assertTextEquals("Connected")
        ui.onNodeWithTag("close-settings").performScrollTo().performClick()
        val player = ui.activity.playback.player
        val sbsChannels = ui.activity.playback.guide!!.channels.filter { it.providerChannel?.id?.provider == "sbs" }
        assertEquals(2, sbsChannels.size)
        for (channel in sbsChannels) {
            ui.runOnIdle { ui.activity.playback.request(TuneTarget.Number(channel.number)) }
            ui.waitUntil(30000) { player.isPlaying && player.currentMediaItem?.mediaId == channel.number.toString() }
        }
        ui.runOnIdle { ui.activity.playback.disconnectNineNow() }
        ui.waitUntil(30000) { !ui.activity.playback.providerBusy }
        ui.runOnIdle {
            assertSame(player, ui.activity.playback.player)
            assertEquals("sbs", ui.activity.playback.selected!!.providerChannel!!.id.provider)
            assertEquals(ProviderConnection.CONNECTED, ui.activity.playback.sbsAccount.connection)
            assertTrue(player.isPlaying)
            val numbers = ui.activity.playback.guide!!.channels.map { it.number }
            assertEquals(numbers.size, numbers.toSet().size)
            ui.activity.playback.sbsAccount.disconnect()
        }
        ui.waitUntil(30000) { !ui.activity.playback.sbsAccount.busy && ui.activity.playback.selected?.number == 100 && player.isPlaying }
    }
    @Test fun sbsSignOutDoesNotChangeNineAccountOrPlayer() {
        ready(); connect()
        ui.runOnIdle { ui.activity.playback.request(TuneTarget.Number(-9001)) }
        ui.waitUntil(30000) { ui.activity.playback.player.isPlaying && ui.activity.playback.player.currentMediaItem?.mediaId == "-9001" }
        ui.runOnIdle { ui.activity.playback.sbsAccount.disconnect() }
        ui.waitUntil(30000) { !ui.activity.playback.sbsAccount.busy }
        ui.runOnIdle {
            assertEquals(ProviderConnection.CONNECTED, ui.activity.playback.providerConnection)
            assertEquals("9now", ui.activity.playback.selected!!.providerChannel!!.id.provider)
            assertEquals("-9001", ui.activity.playback.player.currentMediaItem!!.mediaId)
            assertTrue(ui.activity.playback.guide!!.channels.none { it.providerChannel?.id?.provider == "sbs" })
        }
    }
    @Test fun lateSbsResolutionCannotPlayAfterSignOut() {
        ready(); connect()
        ui.runOnIdle { ui.activity.playback.request(TuneTarget.Number(100)) }
        ui.waitUntil(30000) { ui.activity.playback.player.isPlaying && ui.activity.playback.player.currentMediaItem?.mediaId == "100" }
        ui.runOnIdle { ui.activity.sbs.blocked = true; ui.activity.playback.request(TuneTarget.Number(sbsNumber())) }
        assertTrue(ui.activity.sbs.entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
        ui.runOnIdle { ui.activity.playback.sbsAccount.disconnect() }
        ui.activity.sbs.release.countDown()
        ui.waitUntil(30000) { !ui.activity.playback.sbsAccount.busy }
        ui.runOnIdle {
            assertEquals(100, ui.activity.playback.selected!!.number)
            assertEquals("100", ui.activity.playback.player.currentMediaItem!!.mediaId)
            assertEquals(ProviderConnection.CONNECTED, ui.activity.playback.providerConnection)
        }
    }
}
