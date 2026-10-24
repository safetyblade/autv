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
        Log.i("AUTV_MEDIA_QA","$sample video=${it.video} audio=${it.audio} keys=${it.drmKeys} ads=${it.adStarted}/${it.adEnded} completed=${it.completedAds} adMedia=${it.adMediaObserved} error=${it.errorCode} ima=${it.imaError} stage=${it.session.stage} failureStage=${it.session.lastStartupStage} elapsedMs=${it.session.elapsed} fallback=${it.fallbackVideo}/${it.fallbackAudio} resumed=${it.session.returnedToProgramme} live=${it.holder.player.isCurrentMediaItemLive}")
    } }
    private fun rendered(sample: String) {
        main { it.start(sample) }
        try { waitFor { (it.video && it.audio && it.holder.player.isPlaying) || it.errorCode != null } }
        finally { report(sample) }
        assertNull("Public sample renderer error",main { it.errorCode })
        assertTrue(main { it.video && it.audio })
    }
    @Test fun officialImaBootstrapIsReachableFromAndroid() {
        val connection=java.net.URL("https://imasdk.googleapis.com/native/sdkloader/native_sdk_v3.html").openConnection() as java.net.HttpURLConnection
        try {
            connection.connectTimeout=15000;connection.readTimeout=15000
            val status=connection.responseCode
            Log.i("AUTV_MEDIA_QA","ima-bootstrap http=$status")
            assertEquals("Official IMA bootstrap HTTP status",200,status)
        } finally { connection.disconnect() }
    }
    @Test fun officialHlsRendersVideoAndAudio() { rendered("hls") }
    @Test fun officialDashRendersVideoAndAudio() { rendered("dash") }
    @Test fun officialImaHlsIsLiveAndReleasesToFast() {
        rendered("ima-hls")
        assertTrue(main { it.holder.player.isCurrentMediaItemLive })
        try { waitFor(360000) { it.session.returnedToProgramme || it.errorCode != null } }
        finally { report("ima-hls-ad-break") }
        assertNull(main { it.errorCode });assertTrue(main { it.session.returnedToProgramme })
        val retained = main { it.holder.player }
        val media = main { it.holder.player.currentMediaItem }
        val position = main { it.holder.player.currentPosition }
        main { it.rebindVideoView() }
        waitFor(20000) { it.holder.player.isPlaying && it.holder.player.currentPosition > position+1000 }
        assertEquals(media,main { it.holder.player.currentMediaItem })
        main { it.start("fast") }
        waitFor { it.holder.player.isPlaying && it.video }
        assertSame(retained,main { it.holder.player })
    }
    @Test fun officialImaDashPlaysCompleteAdBreakAndContinues() {
        rendered("ima-dash")
        assertTrue(main { it.holder.player.isCurrentMediaItemLive })
        try { waitFor(360000) { (it.adStarted && it.adEnded && it.completedAds>0 && it.adMediaObserved && it.holder.player.isPlaying) || it.errorCode != null } }
        finally { report("ima-dash-ad-break") }
        assertNull(main { it.errorCode })
        assertTrue(main { it.adStarted && it.adEnded && it.completedAds>0 && it.adMediaObserved })
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
    @Test fun failedInterstitialStopsInsteadOfPlayingUnadvertisedContent() {
        main { it.start("broken-interstitial") }
        waitFor(35000) { it.sessionFailure != null }
        assertEquals(androidx.media3.common.PlaybackException.ERROR_CODE_IO_UNSPECIFIED,main { it.sessionFailure })
        waitFor { it.fallbackVideo && it.fallbackAudio && it.holder.player.isPlaying }
        assertEquals("100",main { it.holder.player.currentMediaItem?.mediaId })
        main { it.start("fast") }; waitFor { it.video && it.audio && it.holder.player.isPlaying }
        report("failed-interstitial-fast-recovery")
    }
    @Test fun expiryReturnsToFastAndSyntheticDiscontinuityPlaysWithoutRemoval() {
        main { it.start("expiry") }
        waitFor { it.expired && it.fallbackVideo && it.fallbackAudio && it.holder.player.currentMediaItem?.mediaId == "100" && it.holder.player.isPlaying }
        main { it.start("transition") }
        waitFor(35000) { it.errorCode != null || (it.video && it.holder.player.currentPosition > 17000) }
        assertNull(main { it.errorCode })
        report("synthetic-discontinuity")
    }
}
