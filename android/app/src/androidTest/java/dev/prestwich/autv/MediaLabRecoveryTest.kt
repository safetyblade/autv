package dev.prestwich.autv

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.SurfaceView
import androidx.lifecycle.Lifecycle
import androidx.media3.ui.PlayerView
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Offline visual/audio/lifecycle acceptance. A decoded flat colour is insufficient. */
class MediaLabRecoveryTest {
    @get:Rule val activity=ActivityScenarioRule(MediaCapabilityActivity::class.java)
    private fun <T> main(action: (MediaCapabilityActivity)->T): T {
        var value: T?=null;activity.scenario.onActivity { value=action(it) };@Suppress("UNCHECKED_CAST") return value as T
    }
    private fun waitFor(action: (MediaCapabilityActivity)->Boolean) {
        val end=SystemClock.uptimeMillis()+20000
        while(SystemClock.uptimeMillis()<end) { if(main(action)) return;SystemClock.sleep(100) }
        fail("Local sample did not reach required observed state")
    }
    private fun frame(): IntArray {
        val bitmap=Bitmap.createBitmap(120,68,Bitmap.Config.ARGB_8888)
        val ready=CountDownLatch(1);var result=-1
        main {
            val view=it.window.decorView.findViewWithTag<PlayerView>("media-video")
            PixelCopy.request(view.videoSurfaceView as SurfaceView,bitmap,{ code -> result=code;ready.countDown() },Handler(Looper.getMainLooper()))
        }
        assertTrue(ready.await(5,TimeUnit.SECONDS));assertEquals(PixelCopy.SUCCESS,result)
        return IntArray(120*68).also { bitmap.getPixels(it,0,120,0,0,120,68);bitmap.recycle() }
    }
    private fun visibleMovingVideo() {
        val first=frame();SystemClock.sleep(700);val second=frame()
        assertTrue("Recovery must contain visible image detail",first.toSet().size>50)
        assertTrue("Recovery must visibly move",first.indices.count { first[it]!=second[it] }>200)
    }
    @Test fun localVideoHasPictureAudioAndSurvivesViewAndActivityRecreation() {
        main { it.start("fast") };waitFor { it.video && it.audio && it.holder.player.isPlaying };visibleMovingVideo()
        val player=main { it.holder.player };val item=main { it.holder.player.currentMediaItem }
        main { it.rebindVideoView() };waitFor { it.holder.player.isPlaying };visibleMovingVideo()
        activity.scenario.moveToState(Lifecycle.State.CREATED);activity.scenario.moveToState(Lifecycle.State.RESUMED)
        activity.scenario.recreate();waitFor { it.holder.player.isPlaying }
        assertSame(player,main { it.holder.player });assertEquals(item,main { it.holder.player.currentMediaItem });visibleMovingVideo()
        assertTrue(main { it.session.summary().contains("Audio: yes") })
        main { it.holder.player.pause() }
        activity.scenario.moveToState(Lifecycle.State.CREATED);activity.scenario.moveToState(Lifecycle.State.RESUMED)
        assertFalse(main { it.holder.player.playWhenReady })
        main { it.holder.player.play() };waitFor { it.holder.player.isPlaying }
    }
    @Test fun expiredAndFailedAdSessionsRecoverToVisibleAudioVideoAndRetune() {
        main { it.start("expiry") };waitFor { it.expired && it.fallbackVideo && it.fallbackAudio && it.holder.player.isPlaying };visibleMovingVideo()
        main { it.start("broken-interstitial") };waitFor { it.sessionFailure!=null && it.fallbackVideo && it.fallbackAudio };visibleMovingVideo()
        main { it.start("fast") };waitFor { it.video && it.audio && it.holder.player.isPlaying }
        assertNull(main { it.errorCode });assertFalse(main { it.adStarted });assertFalse(main { it.session.returnedToProgramme })
    }
}
