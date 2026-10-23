package dev.prestwich.autv.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer

class AuTvPlayer(private val context: Context) {
    val player: Player = ExoPlayer.Builder(context).build()
    fun play(url: String?, channelNumber: Int? = null) {
        if (url.isNullOrBlank()) return
        // Provider HLS endpoints (including Plex) need not end in .m3u8.
        player.setMediaItem(MediaItem.Builder().setMediaId(channelNumber?.toString() ?: url).setUri(url).setMimeType(MimeTypes.APPLICATION_M3U8).build(), true)
        player.prepare()
        player.play()
    }
    /** Requests are ephemeral and never become catalogue/cache records. */
    fun play(request: dev.prestwich.autv.data.provider.ProviderPlaybackRequest, channelNumber: Int) {
        require(request.mimeType == MimeTypes.APPLICATION_M3U8)
        val http = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(request.headers)
        val source = androidx.media3.exoplayer.hls.HlsMediaSource.Factory(androidx.media3.datasource.DefaultDataSource.Factory(context, http))
            .createMediaSource(MediaItem.Builder().setMediaId(channelNumber.toString())
                .setUri(request.uri).setMimeType(request.mimeType).build())
        (player as ExoPlayer).setMediaSource(source)
        player.prepare()
        player.play()
    }
    fun release() = player.release()
}
