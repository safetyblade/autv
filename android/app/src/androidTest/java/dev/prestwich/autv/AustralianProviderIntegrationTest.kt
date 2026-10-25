package dev.prestwich.autv

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.prestwich.autv.data.provider.ProviderConnection
import dev.prestwich.autv.guide.FavouriteIdentity
import dev.prestwich.autv.guide.TuneTarget
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AustralianProviderIntegrationTest {
    @get:Rule val ui=createAndroidComposeRule<AustralianIntegrationTestActivity>()
    private fun account(id: String)=ui.activity.playback.providerAccounts.getValue(id)
    private fun ready()=ui.waitUntil(30000) { ui.activity.playback.guide?.channels?.any { it.providerChannel?.id?.provider=="abc" }==true && ui.activity.playback.playing }
    private fun channel(id: String)=ui.activity.playback.guide!!.channels.first { it.providerChannel?.id?.provider==id }
    private fun tune(id: String) {
        val number=channel(id).number
        ui.runOnIdle { ui.activity.playback.request(TuneTarget.Number(number)) }
        ui.waitUntil(30000) { ui.activity.playback.player.isPlaying && ui.activity.playback.player.currentMediaItem?.mediaId==number.toString() }
    }
    private fun connectTen() {
        ui.runOnIdle { ui.activity.ten.approved=true;account("ten").connect() }
        ui.waitUntil(30000) { !account("ten").busy && account("ten").connection==ProviderConnection.CONNECTED }
    }
    @Test fun tenSettingsQrNativeTuneGuideFavouritesAndScopedSignOut() {
        ready();val model=ui.activity.playback;val player=model.player
        ui.onNodeWithTag("settings-button").performClick()
        ui.onNodeWithTag("provider-ten").performScrollTo().performClick()
        ui.onNodeWithTag("ten-connect").performScrollTo().performClick()
        ui.waitUntil(10000) { account("ten").activation!=null }
        ui.onNodeWithTag("activation-address").performScrollTo().assertTextEquals("https://10.com.au/activate")
        ui.onNodeWithTag("activation-code").performScrollTo().assertTextEquals("TV123")
        ui.runOnIdle { ui.activity.ten.approved=true }
        ui.waitUntil(30000) { !account("ten").busy && account("ten").connection==ProviderConnection.CONNECTED }
        ui.onNodeWithTag("close-settings").performScrollTo().performClick()
        tune("ten")
        val selected=channel("ten");val identity=FavouriteIdentity.key(selected)!!
        ui.runOnIdle {
            assertEquals("10",selected.subgenre)
            assertEquals("ten current fixture",model.epg.at(selected,System.currentTimeMillis()).now?.title)
            val favourites=Favourites(ui.activity)
            if(identity in favourites.keys) favourites.toggle(selected)
            assertEquals(true,favourites.toggle(selected))
            ui.activity.ten.reverse=true;account("ten").refresh()
        }
        ui.waitUntil(30000) { !account("ten").busy }
        ui.runOnIdle {
            assertEquals(selected.number,model.guide!!.channels.single { it.providerChannel?.id==selected.providerChannel?.id }.number)
            assertTrue(identity in Favourites(ui.activity).keys)
            model.disconnectNineNow()
        }
        ui.waitUntil(30000) { !model.providerBusy }
        ui.runOnIdle { assertSame(player,model.player);assertEquals("ten",model.selected?.providerChannel?.id?.provider);assertTrue(player.isPlaying);account("ten").disconnect() }
        ui.waitUntil(30000) { !account("ten").busy && model.player.currentMediaItem?.mediaId=="100" && model.playing }
        ui.runOnIdle { assertTrue(model.guide!!.channels.any { it.providerChannel?.id?.provider=="abc" }) }
    }
    @Test fun anonymousAbcGuideTunesWithoutFakeConnectionAndDoesNotStopTen() {
        ready();val model=ui.activity.playback
        assertEquals(ProviderConnection.NOT_CONNECTED,account("abc").connection)
        assertNull(account("abc").activation)
        tune("abc");connectTen();tune("ten")
        ui.runOnIdle { account("abc").disconnect() }
        ui.waitUntil(30000) { !account("abc").busy }
        ui.runOnIdle {
            assertEquals("ten",model.selected?.providerChannel?.id?.provider);assertTrue(model.player.isPlaying)
            assertEquals(ProviderConnection.CONNECTED,account("ten").connection)
            account("abc").refresh()
        }
        ui.waitUntil(30000) { !account("abc").busy && account("abc").channels.isNotEmpty() }
        tune("abc")
        ui.runOnIdle { model.next() }
        ui.waitUntil(30000) { model.playing }
        ui.runOnIdle { assertTrue(model.selected!!.tunable) }
    }
    @Test fun cancelledTenResolutionCannotReplaceAbcOrExposeSignedFailure() {
        ready();connectTen();val model=ui.activity.playback;tune("abc")
        ui.runOnIdle { ui.activity.ten.denied=true;model.request(TuneTarget.Number(channel("ten").number)) }
        ui.waitUntil(30000) { account("ten").error!=null && !model.buffering }
        ui.runOnIdle {
            assertEquals("abc",model.selected?.providerChannel?.id?.provider)
            assertTrue(model.player.isPlaying)
            assertFalse(account("ten").error!!.contains("https://"))
            ui.activity.ten.denied=false
        }
        ui.runOnIdle { ui.activity.ten.blocked=true;model.request(TuneTarget.Number(channel("ten").number)) }
        assertTrue(ui.activity.ten.entered.await(5,java.util.concurrent.TimeUnit.SECONDS))
        ui.runOnIdle { account("ten").disconnect() };ui.activity.ten.release.countDown()
        ui.waitUntil(30000) { !account("ten").busy }
        ui.runOnIdle {
            assertEquals("abc",model.selected?.providerChannel?.id?.provider)
            assertTrue(model.player.isPlaying);assertFalse(model.buffering);assertEquals(ProviderConnection.CONNECTED,model.providerConnection)
            assertEquals(ProviderConnection.NOT_CONNECTED,account("abc").connection)
        }
    }
}
