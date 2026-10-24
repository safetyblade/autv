package dev.prestwich.autv

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import dev.prestwich.autv.data.provider.*
import dev.prestwich.autv.player.AuTvPlayer

/** Retained, debug-only public samples. No account data, arbitrary inputs or persisted media URLs. */
class MediaLabSession(app: Application) : AndroidViewModel(app) {
    val holder = AuTvPlayer(app, advancedProviderPlayback = true)
    var changed: (() -> Unit)? = null
    var current = "none"; private set
    var video = false; private set
    var audio = false; private set
    var drmKeys = false; private set
    var errorCode: Int? = null; private set
    var imaError: Int? = null; private set
    var sessionFailure: Int? = null; private set
    var adStarted = false; private set
    var adEnded = false; private set
    var completedAds = 0; private set
    var adMediaObserved = false; private set
    var returnedToProgramme = false; private set
    var expired = false; private set
    var fallbackVideo = false; private set
    var fallbackAudio = false; private set
    var stage = "IDLE"; private set
    var lastStartupStage = "IDLE"; private set
    private var startedAt = 0L
    private var startupDuration: Long? = null
    private var programmePosition: Long? = null
    private var recovering = false
    val elapsed get() = startupDuration ?: if(startedAt==0L) 0 else SystemClock.elapsedRealtime()-startedAt
    val state get() = when {
        errorCode!=null || imaError!=null -> "Failed"
        holder.player.isPlayingAd -> "Advertising"
        holder.player.playbackState==Player.STATE_ENDED -> "Completed"
        holder.player.isPlaying && (video || fallbackVideo) -> "Playing"
        current=="none" -> "Idle"
        else -> "Loading"
    }
    init {
        (holder.player as ExoPlayer).addAnalyticsListener(object : AnalyticsListener {
            override fun onRenderedFirstFrame(eventTime: AnalyticsListener.EventTime, output: Any, renderTimeMs: Long) {
                if(recovering) fallbackVideo=true else video=true
                refresh()
            }
            override fun onAudioPositionAdvancing(eventTime: AnalyticsListener.EventTime, playoutStartSystemTimeMs: Long) {
                if(recovering) fallbackAudio=true else audio=true
                refresh()
            }
            override fun onDrmKeysLoaded(eventTime: AnalyticsListener.EventTime) { drmKeys=true; refresh() }
            override fun onLoadStarted(eventTime: AnalyticsListener.EventTime, loadEventInfo: androidx.media3.exoplayer.source.LoadEventInfo, mediaLoadData: androidx.media3.exoplayer.source.MediaLoadData) {
                if(!recovering && mediaLoadData.dataType==C.DATA_TYPE_MANIFEST) stage="MANIFEST_LOADING"
                refresh()
            }
        })
        holder.player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if(player.isPlayingAd) { adMediaObserved=true; adStarted=true }
                refresh()
            }
            override fun onPlayerError(error: PlaybackException) {
                errorCode=error.errorCode;lastStartupStage=stage;recover();refresh()
            }
        })
        holder.onProviderStage = { stage=it;refresh() }
        holder.onAdEvent = {
            if(it=="STARTED") adStarted=true
            if(it=="COMPLETED") completedAds++
            if(adStarted && it in setOf("AD_PERIOD_ENDED","AD_BREAK_ENDED")) adEnded=true
            refresh()
        }
        holder.onImaError = { imaError=it;lastStartupStage=stage;refresh() }
        holder.onProviderSessionFailure = { sessionFailure=it;errorCode=it;lastStartupStage=stage;recover();refresh() }
        holder.onProviderSessionExpired = { expired=true;lastStartupStage=stage;recover();refresh() }
    }
    private fun recover() {
        if(recovering) return
        // Post out of player callbacks; never continue a source whose required ad has failed.
        recovering=true
        val tuneStartedAt=startedAt
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            if(!recovering || tuneStartedAt!=startedAt) return@post
            holder.play(LOCAL,100);stage="LOCAL_RECOVERY";refresh()
        }
    }
    fun refresh() {
        if(video && audio && startupDuration==null) { startupDuration=elapsed;stage="RENDERING" }
        if(adEnded && completedAds>0 && adMediaObserved && !holder.player.isPlayingAd && holder.player.isPlaying && !recovering) {
            val start=programmePosition
            if(start==null) programmePosition=holder.player.currentPosition
            else if(holder.player.currentPosition>start+1000) returnedToProgramme=true
        }
        changed?.invoke()
    }
    fun start(sample: String) {
        require(sample in SAMPLES.map { it.first })
        current=sample;video=false;audio=false;drmKeys=false;errorCode=null;imaError=null;sessionFailure=null
        adStarted=false;adEnded=false;completedAds=0;adMediaObserved=false;returnedToProgramme=false;expired=false
        fallbackVideo=false;fallbackAudio=false;recovering=false;programmePosition=null
        startedAt=SystemClock.elapsedRealtime();startupDuration=null;stage="SELECTED";lastStartupStage=stage
        val id=ProviderChannelId("public-sample",sample)
        val request=when(sample) {
            "hls" -> ProviderPlaybackRequest(id,"https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_16x9/bipbop_16x9_variant.m3u8",MimeTypes.APPLICATION_M3U8)
            "dash" -> ProviderPlaybackRequest(id,"https://storage.googleapis.com/wvmedia/clear/h264/tears/tears.mpd",MimeTypes.APPLICATION_MPD)
            "widevine" -> ProviderPlaybackRequest(id,"https://storage.googleapis.com/wvmedia/cenc/h264/tears/tears.mpd",MimeTypes.APPLICATION_MPD,
                options=ProviderMediaOptions(widevine=ProviderWidevine("https://proxy.uat.widevine.com/proxy?video_id=2015_tears&provider=widevine_test")))
            // Both HLS inputs are explicitly published by Google; the second uses Google's PUBLIC TEST key.
            // Working owner-tested DASH configuration is unchanged.
            "ima-hls","ima-hls-original","ima-dash" -> ProviderPlaybackRequest(id,"https://dai.google.com",if(sample=="ima-dash") MimeTypes.APPLICATION_MPD else MimeTypes.APPLICATION_M3U8,
                options=ProviderMediaOptions(imaLive=ProviderImaLive(when(sample) {
                    "ima-dash"->"PSzZMzAkSXCmlJOWDmRj8Q"
                    "ima-hls-original"->"c-rArva4ShKVIAkNfy6HUQ"
                    else->"XYrjlG09QTa8pxAo5Fzjww"
                },networkCode="21775744923",apiKey=if(sample=="ima-hls") PUBLIC_GOOGLE_TEST_KEY else null)))
            "expiry" -> ProviderPlaybackRequest(id,LOCAL,MimeTypes.APPLICATION_M3U8,options=ProviderMediaOptions(expiresAt=System.currentTimeMillis()+3000))
            "broken-interstitial" -> ProviderPlaybackRequest(id,"asset:///broken-interstitial-test.m3u8",MimeTypes.APPLICATION_M3U8,options=ProviderMediaOptions(hlsInterstitials=true))
            "interstitial" -> ProviderPlaybackRequest(id,"asset:///interstitial-test.m3u8",MimeTypes.APPLICATION_M3U8,options=ProviderMediaOptions(hlsInterstitials=true))
            "transition" -> ProviderPlaybackRequest(id,"asset:///ssai-transition-test.m3u8",MimeTypes.APPLICATION_M3U8)
            else -> { holder.play(LOCAL,100);refresh();return }
        }
        try { holder.play(request,-999) } catch(_: Exception) { errorCode=PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK;lastStartupStage=stage;recover() }
        refresh()
    }
    fun summary(): String {
        fun Boolean.yn()=if(this) "yes" else "no"
        return """Media Lab:
Device model: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}
Android version: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})
WebView: ${if(android.os.Build.VERSION.SDK_INT>=26) android.webkit.WebView.getCurrentWebViewPackage()?.versionName ?: "unavailable" else "pre26"}
Test: ${SAMPLES.firstOrNull { it.first==current }?.second ?: "None"}
State: $state
Startup stage: $stage; last failure stage: $lastStartupStage
Startup duration: ${elapsed} ms
Video: ${video.yn()}
Audio: ${audio.yn()}
DRM keys loaded: ${drmKeys.yn()}
Ad started: ${adStarted.yn()}
Ad completed: ${if(completedAds>0) "yes ($completedAds)" else "no"}
Ad media observed: ${adMediaObserved.yn()}
Returned to programme: ${returnedToProgramme.yn()}
Expired: ${expired.yn()}
Fallback video: ${fallbackVideo.yn()}; audio: ${fallbackAudio.yn()}
Error: ${errorCode ?: "none"}; IMA: ${imaError ?: "none"}"""
    }
    override fun onCleared() { changed=null;holder.release() }
    companion object {
        const val LOCAL="asset:///recovery/playlist.m3u8"
        // https://developers.google.com/ad-manager/dynamic-ad-insertion/streams (full-service live samples).
        private const val PUBLIC_GOOGLE_TEST_KEY="8B1DA006064BD04C5CD474456AB8C9BDA790628E3184E95074EC65E308331EBC"
        val SAMPLES=listOf("hls" to "HLS","dash" to "DASH","widevine" to "Widevine","ima-hls" to "Live IMA HLS",
            "ima-dash" to "Live IMA DASH","fast" to "Local FAST fixture","expiry" to "FAST recovery (expiry)",
            "broken-interstitial" to "FAST recovery (failed ad)","transition" to "HLS discontinuities", "interstitial" to "HLS interstitial",
            "ima-hls-original" to "Live IMA HLS (original)")
    }
}
