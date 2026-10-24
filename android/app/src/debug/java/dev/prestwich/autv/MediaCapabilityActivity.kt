package dev.prestwich.autv

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.*
import androidx.media3.common.util.Log
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.ui.PlayerView
import dev.prestwich.autv.data.provider.*
import dev.prestwich.autv.player.AuTvPlayer

/** Debug-only official sample lab. Never accepts arbitrary URLs, credentials or broadcaster IDs. */
class MediaCapabilityActivity : AppCompatActivity() {
    lateinit var holder: AuTvPlayer; private set
    private lateinit var status: TextView
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
    var expired = false; private set
    private var current = "none"
    private lateinit var layout: LinearLayout
    private lateinit var videoView: PlayerView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Public sample session URLs still do not belong in logs/errors.
        Log.setLogLevel(Log.LOG_LEVEL_OFF)
        android.util.Log.i("AUTV_MEDIA_QA", "environment sdk=${android.os.Build.VERSION.SDK_INT} webview=${if(android.os.Build.VERSION.SDK_INT>=26) android.webkit.WebView.getCurrentWebViewPackage()?.versionName else "pre26"}")
        holder = AuTvPlayer(this, advancedProviderPlayback = true)
        layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        status = TextView(this).apply { text = "AUTV official sample lab — no broadcaster entitlement"; textSize = 16f }
        layout.addView(status)
        val buttons = LinearLayout(this)
        for ((name,label) in listOf("hls" to "HLS", "dash" to "DASH", "widevine" to "Widevine", "ima-hls" to "Live IMA HLS", "ima-dash" to "Live IMA DASH", "fast" to "FAST fixture")) {
            buttons.addView(Button(this).apply { text = label; setOnClickListener { start(name) } })
        }
        layout.addView(buttons)
        videoView = PlayerView(this).apply { player = holder.player; useController = true }
        layout.addView(videoView, LinearLayout.LayoutParams(-1,0,1f)); setContentView(layout)
        holder.attachPlayerView(videoView)
        (holder.player as ExoPlayer).addAnalyticsListener(object : AnalyticsListener {
            override fun onRenderedFirstFrame(eventTime: AnalyticsListener.EventTime, output: Any, renderTimeMs: Long) { video = true; update() }
            override fun onAudioPositionAdvancing(eventTime: AnalyticsListener.EventTime, playoutStartSystemTimeMs: Long) { audio = true; update() }
            override fun onDrmKeysLoaded(eventTime: AnalyticsListener.EventTime) { drmKeys = true; update() }
        })
        holder.player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) { if(player.isPlayingAd) adMediaObserved=true }
            override fun onPlayerError(error: PlaybackException) { errorCode = error.errorCode; update() }
        })
        holder.onAdEvent = { event ->
            if(event=="COMPLETED") completedAds++
            if (event in setOf("AD_PERIOD_STARTED", "AD_BREAK_STARTED")) adStarted = true
            if (adStarted && event in setOf("AD_PERIOD_ENDED", "AD_BREAK_ENDED")) adEnded = true
            update()
        }
        holder.onImaError = { imaError=it;update() }
        holder.onProviderSessionFailure = { sessionFailure=it;errorCode=it;update() }
        holder.onProviderSessionExpired = { expired = true; holder.play("asset:///pip-test.m3u8",100); update() }
    }
    fun rebindVideoView() {
        videoView.player = null; layout.removeView(videoView)
        videoView = PlayerView(this).apply { player = holder.player; useController = true }
        layout.addView(videoView,LinearLayout.LayoutParams(-1,0,1f))
        holder.attachPlayerView(videoView)
    }
    fun start(sample: String) {
        current = sample; video = false; audio = false; drmKeys = false; errorCode = null; imaError = null; sessionFailure = null; adStarted = false; adEnded = false; completedAds = 0; adMediaObserved = false; expired = false
        val id = ProviderChannelId("public-sample",sample)
        val request = when(sample) {
            "hls" -> ProviderPlaybackRequest(id,"https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_16x9/bipbop_16x9_variant.m3u8",MimeTypes.APPLICATION_M3U8)
            "dash" -> ProviderPlaybackRequest(id,"https://storage.googleapis.com/wvmedia/clear/h264/tears/tears.mpd",MimeTypes.APPLICATION_MPD)
            "widevine" -> ProviderPlaybackRequest(id,"https://storage.googleapis.com/wvmedia/cenc/h264/tears/tears.mpd",MimeTypes.APPLICATION_MPD,
                options=ProviderMediaOptions(widevine=ProviderWidevine("https://proxy.uat.widevine.com/proxy?video_id=2015_tears&provider=widevine_test")))
            "ima-hls", "ima-dash" -> ProviderPlaybackRequest(id,"https://dai.google.com",if(sample=="ima-dash") MimeTypes.APPLICATION_MPD else MimeTypes.APPLICATION_M3U8,
                options=ProviderMediaOptions(imaLive=ProviderImaLive(if(sample=="ima-dash") "PSzZMzAkSXCmlJOWDmRj8Q" else "c-rArva4ShKVIAkNfy6HUQ",networkCode="21775744923")))
            "expiry" -> ProviderPlaybackRequest(id,"asset:///pip-test.m3u8",MimeTypes.APPLICATION_M3U8,options=ProviderMediaOptions(expiresAt=System.currentTimeMillis()+1500))
            "broken-interstitial" -> ProviderPlaybackRequest(id,"asset:///broken-interstitial-test.m3u8",MimeTypes.APPLICATION_M3U8,options=ProviderMediaOptions(hlsInterstitials=true))
            "interstitial" -> ProviderPlaybackRequest(id,"asset:///interstitial-test.m3u8",MimeTypes.APPLICATION_M3U8,options=ProviderMediaOptions(hlsInterstitials=true))
            "transition" -> ProviderPlaybackRequest(id,"asset:///ssai-transition-test.m3u8",MimeTypes.APPLICATION_M3U8)
            else -> { holder.play("asset:///pip-test.m3u8",100); update(); return }
        }
        try { holder.play(request,-999) } catch (_: Exception) { errorCode = PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK }
        update()
    }
    private fun update() { status.text = "$current  Video=$video  Audio-renderer=$audio  Keys=$drmKeys  Ad=$adStarted/$adEnded  Error=${errorCode ?: "none"} IMA=${imaError ?: "none"}" }
    override fun onPause() { holder.player.pause(); super.onPause() }
    override fun onResume() { super.onResume(); if (::holder.isInitialized && holder.player.mediaItemCount > 0) holder.player.play() }
    override fun onDestroy() { holder.release(); super.onDestroy() }
}
