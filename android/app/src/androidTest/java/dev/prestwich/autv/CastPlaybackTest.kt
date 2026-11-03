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
            assertEquals(CastPhase.LOADING, model.castPlayback.phase)
            assertTrue(player.isPlaying)
            remote.accept()
            assertEquals(CastPhase.LOADING, model.castPlayback.phase)
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
        compose.onNodeWithTag("cast-controller").assertExists()
        compose.onNodeWithTag("video").assertDoesNotExist()
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
    @Test fun failedInitialLoadKeepsTheExistingLocalMediaSession() {
        ready()
        compose.runOnUiThread {
            val item=model.player.currentMediaItem
            remote.connect(model.selected!!);remote.accept();remote.fail()
            assertTrue(model.player.isPlaying)
            assertSame(item,model.player.currentMediaItem)
            assertFalse(model.castPlayback.ownsPlayback)
        }
    }
    @Test fun failedCastGuidePreviewRestoresThePreviouslyWorkingChannelLocally() {
        ready()
        compose.runOnUiThread {
            remote.connect(model.selected!!);remote.playing()
            model.request(TuneTarget.Number(419),restoreOnFailure=true)
            remote.accept();remote.fail()
        }
        compose.waitUntil(30000) { compose.runOnUiThread { model.playing && model.selected?.number==100 && model.player.currentMediaItem?.mediaId=="100" } }
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
        compose.onNodeWithTag("cast-controller").assertExists()
        compose.onNodeWithTag("video").assertDoesNotExist()
    }
    @Test fun controllerRequiresConfirmationAndGuideShowsOneTimeoutWithLocalRecovery() {
        ready()
        compose.runOnUiThread { model.guideOpen=false; remote.connect(model.selected!!);remote.accept() }
        compose.onNodeWithTag("cast-controller").assertDoesNotExist()
        compose.onNodeWithTag("video").assertExists()
        compose.runOnUiThread { remote.playing() }
        compose.onNodeWithTag("cast-controller").assertExists()
        compose.onNodeWithTag("video").assertDoesNotExist()
        compose.onNodeWithTag("guide-toggle").performClick()
        compose.onNodeWithTag("guide-panel").assertIsDisplayed()
        compose.onNodeWithTag("channel-419").performClick()
        compose.runOnUiThread { remote.timeout() }
        compose.onAllNodesWithText(CastDiagnostics.failure(CastLoadFailure.TIMEOUT)).assertCountEquals(1)
        compose.onNodeWithTag("video").assertExists()
        compose.runOnUiThread { assertFalse(model.failed.containsKey(419)) }
    }
    @Test fun stopControllerRestoresLocalVideoAndBackgroundDoesNotPauseReceiver() {
        ready()
        compose.runOnUiThread { model.guideOpen=false;remote.connect(model.selected!!);remote.playing();model.pause();model.resume() }
        compose.onNodeWithTag("cast-controller").assertExists()
        compose.runOnUiThread { assertFalse(model.player.isPlaying); assertTrue(model.castPlayback.ownsPlayback) }
        compose.onNodeWithTag("stop-casting").performScrollTo().performClick()
        compose.onNodeWithTag("cast-controller").assertDoesNotExist()
        compose.onNodeWithTag("video").assertExists()
        compose.waitUntil(30000) { compose.runOnUiThread { model.playing } }
    }
    @Test fun controllerRemainsUsableInPortraitAndLandscape() {
        ready()
        compose.runOnUiThread { model.guideOpen=false;remote.connect(model.selected!!);remote.playing() }
        try {
            for ((name,orientation) in listOf("portrait" to android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                    "landscape" to android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)) {
                compose.runOnUiThread { compose.activity.requestedOrientation=orientation }
                compose.waitUntil(10_000) {
                    val expected=if(name=="portrait") android.content.res.Configuration.ORIENTATION_PORTRAIT else android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    compose.activity.resources.configuration.orientation==expected
                }
                compose.waitForIdle()
                compose.onNodeWithTag("cast-controller").assertExists()
                compose.onNodeWithTag("video").assertDoesNotExist()
                compose.onNodeWithTag("guide-toggle").performScrollTo().assertIsDisplayed()
                val instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                val target=java.io.File(instrumentation.targetContext.getExternalFilesDir(null),"cast-screenshots/$name.png")
                target.parentFile!!.mkdirs()
                instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
                    target.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
                }
                compose.onNodeWithTag("stop-casting").performScrollTo().assertIsDisplayed()
            }
        } finally { compose.runOnUiThread { compose.activity.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED } }
    }
    @Test fun supportedRemoteControlsReflectPausedMutedAndBufferingState() {
        ready()
        compose.runOnUiThread { model.guideOpen=false;remote.connect(model.selected!!);remote.playing() }
        compose.onNodeWithText("Pause").performScrollTo().performClick()
        compose.onNodeWithTag("cast-status").assertTextEquals("Paused on Fixture receiver")
        compose.onNodeWithText("Mute").performScrollTo().performClick()
        compose.onNodeWithText("Unmute").assertExists()
        compose.onNodeWithText("Play").performClick()
        compose.runOnUiThread { remote.buffering() }
        compose.onNodeWithTag("cast-controller").assertExists()
        compose.onNodeWithTag("video").assertDoesNotExist()
        compose.onNodeWithTag("cast-status").assertTextEquals("Buffering on Fixture receiver…")
    }
    @Test fun actualSdkEnvelopeUsesResolvedHlsLiveUrlAndChannelMetadata() {
        compose.runOnUiThread {
            assertEquals("https://example.test/live.m3u8", CastStreamUrl.resolve("https://example.test/live.m3u8"))
            val channel = Channel(419, "Ink Master", "Game Shows", "", "", "Full", true,
                "https://example.test/live.m3u8?ads=normal&token=a%2Bb", "provider-419", "https://example.test/logo.png")
            val url = CastStreamUrl.resolve(channel.streamUrl!!)
            val info = CastSender.mediaInfo(channel, url, Programme("The challenge", 0, Long.MAX_VALUE), "sender:123")
            assertEquals("https://example.test/live.m3u8?ads=normal&token=a%2Bb", info.contentId)
            assertEquals(info.contentId, info.contentUrl)
            assertEquals("application/x-mpegURL", info.contentType)
            assertEquals(MediaInfo.STREAM_TYPE_LIVE, info.streamType)
            assertEquals("419 · Ink Master", info.metadata!!.getString(MediaMetadata.KEY_TITLE))
            assertEquals("The challenge", info.metadata!!.getString(MediaMetadata.KEY_SUBTITLE))
            assertEquals(channel.logoUrl, info.metadata!!.images.single().url.toString())
            assertEquals(419, info.customData!!.getInt("channelNumber"))
            assertEquals("provider-419", info.customData!!.getString("channelId"))
            assertEquals("sender:123", info.customData!!.getString("autvLoadId"))
            val dash=CastSender.mediaInfo(channel,"https://example.test/live.mpd?session=1",null)
            assertEquals("application/dash+xml",dash.contentType)
            val cmaf=CastSender.mediaInfo(channel,url,null,mimeType="application/x-mpegURL",live=true,fragmentedMp4=true)
            assertEquals("fmp4",cmaf.hlsVideoSegmentFormat)
            val mp4=CastSender.mediaInfo(channel,"https://example.test/movie.mp4",null)
            assertEquals("video/mp4",mp4.contentType);assertEquals(MediaInfo.STREAM_TYPE_BUFFERED,mp4.streamType)
        }
    }
}
