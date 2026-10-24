package dev.prestwich.autv.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsInterstitialsAdsLoader
import androidx.media3.exoplayer.ima.ImaServerSideAdInsertionMediaSource
import androidx.media3.exoplayer.ima.ImaServerSideAdInsertionUriBuilder
import androidx.media3.ui.PlayerView
import com.google.ads.interactivemedia.v3.api.AdEvent
import com.google.ads.interactivemedia.v3.api.ImaSdkFactory
import dev.prestwich.autv.data.provider.*

/** Advanced routes opt in per host; existing FAST and provider-HLS entry points remain available. */
class AuTvPlayer(private val context: Context, private val advancedProviderPlayback: Boolean = false) {
    val player: Player = ExoPlayer.Builder(context).build()
    var onProviderSessionExpired: (() -> Unit)? = null
    var onAdEvent: ((String) -> Unit)? = null
    var onImaError: ((Int) -> Unit)? = null
    var onProviderSessionFailure: ((Int) -> Unit)? = null
    private var view: PlayerView? = null
    private val adOverlay = FrameLayout(context.applicationContext)
    private var ima: ImaServerSideAdInsertionMediaSource.AdsLoader? = null
    private var interstitials: HlsInterstitialsAdsLoader? = null
    private val handler = Handler(Looper.getMainLooper())
    private var expiry: Runnable? = null
    private var startup: Runnable? = null
    private var generation = 0L
    private val adViews = object : AdViewProvider {
        override fun getAdViewGroup(): ViewGroup? = if (view != null) adOverlay else null
        override fun getAdOverlayInfos(): List<AdOverlayInfo> = view?.adOverlayInfos.orEmpty()
    }
    init {
        if (advancedProviderPlayback) initialiseIma(context.applicationContext)
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) { startup?.let(handler::removeCallbacks); startup=null }
            }
        })
    }
    fun attachPlayerView(view: PlayerView?) {
        if (this.view === view) return
        (adOverlay.parent as? ViewGroup)?.removeView(adOverlay)
        this.view = view
        view?.adViewGroup?.addView(adOverlay, FrameLayout.LayoutParams(-1,-1))
    }
    fun detachPlayerView(view: PlayerView) { if (this.view === view) attachPlayerView(null) }
    private fun endProviderSession() {
        generation++
        startup?.let(handler::removeCallbacks); startup=null
        expiry?.let(handler::removeCallbacks); expiry = null
        // Detach old media before releasing its session/SDK. Never retain a prior account's ad state.
        player.stop(); player.clearMediaItems()
        ima?.release(); ima = null
        interstitials?.setPlayer(null); interstitials?.release(); interstitials = null
        adOverlay.removeAllViews()
    }
    fun play(url: String?, channelNumber: Int? = null) {
        if (url.isNullOrBlank()) return
        endProviderSession()
        player.setMediaItem(MediaItem.Builder().setMediaId(channelNumber?.toString() ?: url).setUri(url).setMimeType(MimeTypes.APPLICATION_M3U8).build(), true)
        player.prepare(); player.play()
    }
    /** Requests and provider-issued DRM/DAI values never become catalogue/cache records. */
    fun play(request: ProviderPlaybackRequest, channelNumber: Int) {
        ProviderMediaSourceFactory.validate(request, advancedProviderPlayback)
        if (request.options.imaLive != null || request.options.hlsInterstitials) check(view != null) { "Ad view is not attached" }
        val base = ProviderMediaSourceFactory.base(context, request)
        endProviderSession()
        val activeGeneration = generation
        try {
            var uri = Uri.parse(request.uri)
            val source = when {
                request.options.imaLive != null -> {
                    val config = request.options.imaLive!!
                    val settings = initialiseIma(context.applicationContext)
                    val loader = ImaServerSideAdInsertionMediaSource.AdsLoader.Builder(context.applicationContext, adViews)
                        .setImaSdkSettings(settings)
                        .setAdEventListener { event -> if (generation == activeGeneration) onAdEvent?.invoke(event.type.name) }
                        .setAdErrorListener { event -> if (generation == activeGeneration) onImaError?.invoke(event.error.errorCodeNumber) }.build()
                    loader.setPlayer(player); ima = loader
                    val address = ImaServerSideAdInsertionUriBuilder().setAssetKey(config.assetKey)
                        .setAdsId("${request.channelId.provider}:$channelNumber:${System.nanoTime()}")
                        .setFormat(if(request.mimeType == MimeTypes.APPLICATION_MPD) C.CONTENT_TYPE_DASH else C.CONTENT_TYPE_HLS)
                        .setAdTagParameters(config.adParameters)
                    config.networkCode?.let(address::setNetworkCode)
                    config.authToken?.let(address::setAuthToken)
                    config.apiKey?.let(address::setApiKey)
                    uri = address.build()
                    ImaServerSideAdInsertionMediaSource.Factory(loader, base)
                        .createMediaSource(ProviderMediaSourceFactory.item(request, channelNumber, uri))
                }
                request.options.hlsInterstitials -> {
                    val loader = HlsInterstitialsAdsLoader(ProviderMediaSourceFactory.data(context, request.options.originHeaders))
                    loader.addListener(object : HlsInterstitialsAdsLoader.Listener {
                        private fun failAd() { handler.post {
                            if (generation==activeGeneration) {
                                endProviderSession();onProviderSessionFailure?.invoke(PlaybackException.ERROR_CODE_IO_UNSPECIFIED)
                            }
                        } }
                        override fun onPrepareError(mediaItem: MediaItem, adsId: Any, adGroupIndex: Int, adIndexInAdGroup: Int, exception: java.io.IOException) { failAd() }
                        override fun onAssetListLoadFailed(mediaItem: MediaItem, adsId: Any, adGroupIndex: Int, adIndexInAdGroup: Int, ioException: java.io.IOException?, cancelled: Boolean) { if(!cancelled) failAd() }
                    })
                    loader.setPlayer(player); interstitials = loader
                    HlsInterstitialsAdsLoader.AdsMediaSourceFactory(loader, adViews, base)
                        .createMediaSource(ProviderMediaSourceFactory.item(request, channelNumber, uri))
                }
                else -> base.createMediaSource(ProviderMediaSourceFactory.item(request, channelNumber, uri))
            }
            (player as ExoPlayer).setMediaSource(source)
            player.prepare(); player.play()
            if (request.options.imaLive != null) {
                startup=Runnable {
                    if (generation==activeGeneration && player.playbackState!=Player.STATE_READY) {
                        endProviderSession();onProviderSessionFailure?.invoke(PlaybackException.ERROR_CODE_TIMEOUT)
                    }
                }.also { handler.postDelayed(it,45000) }
            }
            request.options.expiresAt?.let { deadline ->
                expiry = Runnable { endProviderSession(); onProviderSessionExpired?.invoke() }
                    .also { handler.postDelayed(it, (deadline - System.currentTimeMillis()).coerceAtLeast(0)) }
            }
        } catch (error: Exception) { endProviderSession(); throw error }
    }
    companion object {
        private val imaSettings by lazy { ImaSdkFactory.getInstance().createImaSdkSettings() }
        private var imaInitialised=false
        private fun initialiseIma(context: Context): com.google.ads.interactivemedia.v3.api.ImaSdkSettings {
            if (!imaInitialised) { ImaSdkFactory.getInstance().initialize(context.applicationContext,imaSettings);imaInitialised=true }
            return imaSettings
        }
    }
    fun release() { endProviderSession(); attachPlayerView(null); player.release() }
}
