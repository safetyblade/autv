package dev.prestwich.autv.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer

class AuTvPlayer(context: Context) {
    val player: Player = ExoPlayer.Builder(context).build()
    fun play(url: String?, channelNumber: Int? = null) {
        if (url.isNullOrBlank()) return
        // Provider HLS endpoints (including Plex) need not end in .m3u8.
        player.setMediaItem(MediaItem.Builder().setMediaId(channelNumber?.toString() ?: url).setUri(url).setMimeType(MimeTypes.APPLICATION_M3U8).build())
        player.prepare()
        player.playWhenReady = true
    }
    fun release() = player.release()
}
