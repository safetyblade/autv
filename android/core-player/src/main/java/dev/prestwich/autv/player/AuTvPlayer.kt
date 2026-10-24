package dev.prestwich.autv.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
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
    private var view: PlayerView? = null
    private var ima: ImaServerSideAdInsertionMediaSource.AdsLoader? = null
    private var interstitials: HlsInterstitialsAdsLoader? = null
    private val handler = Handler(Looper.getMainLooper())
    private var expiry: Runnable? = null
    private var generation = 0L
    private val adViews = object : AdViewProvider {
        override fun getAdViewGroup(): ViewGroup? = view?.adViewGroup
        override fun getAdOverlayInfos(): List<AdOverlayInfo> = view?.adOverlayInfos.orEmpty()
    }
    fun attachPlayerView(view: PlayerView?) { this.view = view }
    private fun endProviderSession() {
        generation++
        expiry?.let(handler::removeCallbacks); expiry = null
        // Detach old media before releasing its session/SDK. Never retain a prior account's ad state.
        player.stop(); player.clearMediaItems()
        ima?.release(); ima = null
        interstitials?.setPlayer(null); interstitials?.release(); interstitials = null
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
                    val settings = ImaSdkFactory.getInstance().createImaSdkSettings()
                    ImaSdkFactory.getInstance().initialize(context, settings)
                    val loader = ImaServerSideAdInsertionMediaSource.AdsLoader.Builder(context, adViews)
                        .setImaSdkSettings(settings)
                        .setAdEventListener { event -> if (generation == activeGeneration) onAdEvent?.invoke(event.type.name) }.build()
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
                    loader.setPlayer(player); interstitials = loader
                    HlsInterstitialsAdsLoader.AdsMediaSourceFactory(loader, adViews, base)
                        .createMediaSource(ProviderMediaSourceFactory.item(request, channelNumber, uri))
                }
                else -> base.createMediaSource(ProviderMediaSourceFactory.item(request, channelNumber, uri))
            }
            (player as ExoPlayer).setMediaSource(source)
            player.prepare(); player.play()
            request.options.expiresAt?.let { deadline ->
                expiry = Runnable { endProviderSession(); onProviderSessionExpired?.invoke() }
                    .also { handler.postDelayed(it, (deadline - System.currentTimeMillis()).coerceAtLeast(0)) }
            }
        } catch (error: Exception) { endProviderSession(); throw error }
    }
    fun release() { endProviderSession(); view = null; player.release() }
}
