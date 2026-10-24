package dev.prestwich.autv

import android.os.SystemClock
import android.util.Log
import androidx.media3.common.C
import android.media.MediaDrm
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Internet/official SDK integration tests. Rendering != audible physical-TV acceptance. */
class PublicMediaCapabilityTest {
    @get:Rule val activity = ActivityScenarioRule(MediaCapabilityActivity::class.java)
    private fun <T> main(block: (MediaCapabilityActivity)->T): T {
        var result: Result<T>? = null
        activity.scenario.onActivity { result = runCatching { block(it) } }
        return result!!.getOrThrow()
    }
    private fun waitFor(timeout: Long = 90000, predicate: (MediaCapabilityActivity)->Boolean) {
        val end = SystemClock.uptimeMillis()+timeout
        while (SystemClock.uptimeMillis()<end) {
            if (main(predicate)) return
            SystemClock.sleep(100)
        }
        fail("Public sample did not reach its required rendering/session state")
    }
    private fun report(sample: String) { main {
        Log.i("AUTV_MEDIA_QA","$sample video=${it.video} audio=${it.audio} keys=${it.drmKeys} ads=${it.adStarted}/${it.adEnded} error=${it.errorCode} live=${it.holder.player.isCurrentMediaItemLive}")
    } }
    private fun rendered(sample: String) {
        main { it.start(sample) }
        try { waitFor { (it.video && it.audio && it.holder.player.isPlaying) || it.errorCode != null } }
        finally { report(sample) }
        assertNull("Public sample renderer error",main { it.errorCode })
        assertTrue(main { it.video && it.audio })
    }
    @Test fun officialHlsRendersVideoAndAudio() { rendered("hls") }
    @Test fun officialDashRendersVideoAndAudio() { rendered("dash") }
    @Test fun officialImaHlsIsLiveAndReleasesToFast() {
        rendered("ima-hls")
        assertTrue(main { it.holder.player.isCurrentMediaItemLive })
        val retained = main { it.holder.player }
        main { it.start("fast") }
        waitFor { it.holder.player.isPlaying && it.video }
        assertSame(retained,main { it.holder.player })
    }
    @Test fun officialImaDashPlaysCompleteAdBreakAndContinues() {
        rendered("ima-dash")
        assertTrue(main { it.holder.player.isCurrentMediaItemLive })
        try { waitFor(360000) { (it.adStarted && it.adEnded && it.holder.player.isPlaying) || it.errorCode != null } }
        finally { report("ima-dash-ad-break") }
        assertNull(main { it.errorCode })
        assertTrue(main { it.adStarted && it.adEnded })
        val position = main { it.holder.player.currentPosition }
        waitFor(20000) { it.holder.player.currentPosition > position+2000 && it.holder.player.isPlaying }
    }
    @Test fun widevineReportsActualDeviceResultWithoutSecurityDowngrade() {
        if (!MediaDrm.isCryptoSchemeSupported(C.WIDEVINE_UUID)) {
            Log.i("AUTV_MEDIA_QA","widevine DEVICE_UNSUPPORTED — no protected playback claimed")
            return
        }
        main { it.start("widevine") }
        waitFor { (it.video && it.audio && it.drmKeys) || it.errorCode != null }
        report("widevine")
        val error = main { it.errorCode }
        if (error != null) {
            assertTrue("Only an explicit DRM platform failure is an accepted device limitation",error in 6000..6999)
            main { it.start("fast") }; waitFor { it.video && it.holder.player.isPlaying }
        } else assertTrue(main { it.video && it.audio && it.drmKeys })
    }
    @Test fun sdkPlaysSyntheticVodInterstitialAndResumesContent() {
        main { it.start("interstitial") }
        waitFor(30000) { it.errorCode != null || it.holder.player.isPlayingAd }
        assertNull(main { it.errorCode })
        assertTrue(main { it.holder.player.isPlayingAd })
        waitFor(35000) { it.errorCode != null || (!it.holder.player.isPlayingAd && it.holder.player.currentPosition > 9000) }
        assertNull(main { it.errorCode })
        report("synthetic-vod-interstitial")
    }
    @Test fun expiryReturnsToFastAndSyntheticDiscontinuityPlaysWithoutRemoval() {
        main { it.start("expiry") }
        waitFor { it.expired && it.holder.player.currentMediaItem?.mediaId == "100" && it.holder.player.isPlaying }
        main { it.start("transition") }
        waitFor(35000) { it.errorCode != null || (it.video && it.holder.player.currentPosition > 17000) }
        assertNull(main { it.errorCode })
        report("synthetic-discontinuity")
    }
}
