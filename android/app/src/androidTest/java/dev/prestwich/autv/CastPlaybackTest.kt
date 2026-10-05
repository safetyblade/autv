package dev.prestwich.autv

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import dev.prestwich.autv.cast.CastSender
import dev.prestwich.autv.data.*
import dev.prestwich.autv.guide.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CastPlaybackTest {
    @get:Rule val compose = createAndroidComposeRule<PlatformTestActivity>()
    private val model get() = compose.activity.playback
    private val remote get() = compose.activity.castProbe
    private fun ready() {
        compose.waitUntil(30_000) { model.playing && model.guide != null }
        compose.runOnUiThread { model.player.volume = 0f; model.request(TuneTarget.Number(100)) }
        compose.waitUntil(30_000) { compose.runOnUiThread { model.playing && model.selected?.number == 100 } }
    }
    @Test fun sharedTuningTransfersOnlyAfterConfirmationAndRestoresOnDisconnect() {
        ready()
        val player = model.player
        compose.runOnUiThread {
            remote.connect(model.selected!!)
            assertEquals(CastPhase.LOAD_REQUESTED, model.castPlayback.phase)
            assertTrue(player.isPlaying)
            remote.accept()
            assertEquals(CastPhase.LOAD_ACCEPTED, model.castPlayback.phase)
            assertTrue(player.isPlaying)
            remote.playing()
            assertFalse(player.isPlaying)
            assertEquals(androidx.media3.common.Player.STATE_IDLE, player.playbackState)
            assertEquals(CastPhase.PLAYING, model.castPlayback.phase)
            model.pause(); model.resume()
            assertFalse(player.isPlaying)
            compose.activity.allowPip = true
            assertFalse(compose.activity.enterMobilePip())
            compose.activity.allowPip = false
            model.guideOpen = false
        }
        compose.onNodeWithTag("cast-status").assertTextEquals("Playing on Fixture receiver")
        compose.onNodeWithTag("ch-plus").performClick()
        compose.runOnUiThread {
            assertEquals(419, model.selected?.number)
            assertEquals(419, remote.loads.last().number)
            assertFalse(player.isPlaying)
            remote.accept(); remote.playing()
        }
        compose.onNodeWithTag("ch-minus").performClick()
        compose.runOnUiThread {
            assertEquals(100, remote.loads.last().number)
            remote.playing()
            // Guide/deep-link requests use the same shared path.
            model.request(TuneTarget.Id("fixture-419"))
            assertEquals(419, remote.loads.last().number)
            remote.playing(); remote.disconnect()
            assertSame(player, model.player)
            assertEquals(CastPhase.DISCONNECTED, model.castPlayback.phase)
        }
        compose.waitUntil(30_000) { compose.runOnUiThread { model.playing && model.player.currentMediaItem?.mediaId == "419" } }
    }
    @Test fun failedRemoteLoadFallsBackLocallyWithoutMarkingChannelUnavailable() {
        ready()
        compose.runOnUiThread {
            remote.connect(model.selected!!); remote.playing()
            model.next()
            assertFalse(model.player.isPlaying)
            remote.fail()
            assertEquals(CastPhase.FAILED, model.castPlayback.phase)
            assertFalse(model.failed.containsKey(419))
        }
        compose.waitUntil(30_000) { compose.runOnUiThread { model.playing && model.player.currentMediaItem?.mediaId == "419" } }
        compose.onNodeWithTag("cast-error").assertTextContains("2100", substring = true)
        compose.runOnUiThread { model.request(TuneTarget.Number(419)); remote.playing() }
        compose.onNodeWithTag("cast-status").assertTextEquals("Playing on Fixture receiver")
    }
    @Test fun actualSdkEnvelopeUsesResolvedHlsLiveUrlAndChannelMetadata() {
        compose.runOnUiThread {
            assertEquals("https://example.test/live.m3u8", CastStreamUrl.resolve("https://example.test/live.m3u8"))
            val channel = Channel(419, "Ink Master", "Game Shows", "", "", "Full", true,
                "https://example.test/live.m3u8?ads.ifa=[IFA]&token=a%2Bb", "provider-419", "https://example.test/logo.png")
            val url = CastStreamUrl.resolve(channel.streamUrl!!)
            val info = CastSender.mediaInfo(channel, url, Programme("The challenge", 0, Long.MAX_VALUE), "sender:123")
            assertEquals("https://example.test/live.m3u8?token=a%2Bb", info.contentId)
            assertEquals(info.contentId, info.contentUrl)
            assertEquals("application/x-mpegURL", info.contentType)
            assertEquals(MediaInfo.STREAM_TYPE_LIVE, info.streamType)
            assertEquals("419 · Ink Master", info.metadata!!.getString(MediaMetadata.KEY_TITLE))
            assertEquals("The challenge", info.metadata!!.getString(MediaMetadata.KEY_SUBTITLE))
            assertEquals(channel.logoUrl, info.metadata!!.images.single().url.toString())
            assertEquals(419, info.customData!!.getInt("channelNumber"))
            assertEquals("provider-419", info.customData!!.getString("channelId"))
            assertEquals("sender:123", info.customData!!.getString("autvLoadId"))
        }
    }
}
