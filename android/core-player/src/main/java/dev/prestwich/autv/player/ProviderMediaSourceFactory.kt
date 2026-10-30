package dev.prestwich.autv.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.drm.DefaultDrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dev.prestwich.autv.data.provider.*
import okhttp3.OkHttpClient

/** Real Media3 factories, with an independent HTTP policy for media and licence traffic. */
internal object ProviderMediaSourceFactory {
    private val transport = OkHttpClient.Builder().followSslRedirects(false).build()
    fun scopedClient(scopes: Map<String, Map<String,String>>): OkHttpClient {
        val policy = ProviderOriginHeaders(scopes)
        val client = transport.newBuilder().addNetworkInterceptor { chain ->
            val builder = chain.request().newBuilder()
            policy.headerNames.forEach { builder.removeHeader(it) }
            policy.forAddress(chain.request().url.toString()).forEach { (key,value) -> builder.header(key,value) }
            chain.proceed(builder.build())
        }.build()
        return client
    }
    fun http(scopes: Map<String, Map<String,String>>) = OkHttpDataSource.Factory(scopedClient(scopes))
    fun data(context: Context, scopes: Map<String, Map<String,String>>) = DefaultDataSource.Factory(context, http(scopes))
    fun base(context: Context, request: ProviderPlaybackRequest): DefaultMediaSourceFactory {
        val scopes = request.options.originHeaders.toMutableMap()
        if (request.headers.isNotEmpty()) {
            val origin = ProviderOriginHeaders.origin(request.uri)
            scopes[origin] = scopes[origin].orEmpty() + request.headers
        }
        val factory = DefaultMediaSourceFactory(data(context, scopes))
        request.options.widevine?.let { config ->
            val drm = DefaultDrmSessionManagerProvider()
            drm.setDrmHttpDataSourceFactory(http(mapOf(ProviderOriginHeaders.origin(config.licenceUri) to config.headers)))
            factory.setDrmSessionManagerProvider(drm)
        }
        return factory
    }
    fun item(request: ProviderPlaybackRequest, channelNumber: Int, address: android.net.Uri): MediaItem {
        val builder = MediaItem.Builder().setMediaId(channelNumber.toString()).setUri(address).setMimeType(request.mimeType)
        request.options.widevine?.let {
            builder.setDrmConfiguration(MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID).setLicenseUri(it.licenceUri)
                .setForceDefaultLicenseUri(true).setMultiSession(it.multiSession).build())
        }
        if (request.options.hlsInterstitials) {
            builder.setAdsConfiguration(MediaItem.AdsConfiguration.Builder(android.net.Uri.parse("hls://interstitials"))
                .setAdsId("interstitial:$channelNumber:${System.nanoTime()}").build())
        }
        return builder.build()
    }
    fun validate(request: ProviderPlaybackRequest, advanced: Boolean, now: Long = System.currentTimeMillis()) {
        require(request.mimeType in setOf(MimeTypes.APPLICATION_M3U8, MimeTypes.APPLICATION_MPD))
        require(advanced || SevenPlaybackCapabilities.supports(request) || (request.mimeType == MimeTypes.APPLICATION_M3U8 && request.options.widevine == null &&
            request.options.imaLive == null && !request.options.hlsInterstitials)) { "Extended provider playback is disabled" }
        require(!request.options.hlsInterstitials || request.mimeType == MimeTypes.APPLICATION_M3U8)
        if (request.options.expiresAt?.let { it <= now } == true) throw ProviderException(ProviderFailure.SESSION_EXPIRED)
    }
}
