package dev.prestwich.autv.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

class AuTvPlayer(context: Context) {
    val player: ExoPlayer = ExoPlayer.Builder(context).build()
    fun play(url: String?) {
        if (url.isNullOrBlank()) return
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = true
    }
    fun release() = player.release()
}
