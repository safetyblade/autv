package dev.prestwich.autv

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import dev.prestwich.autv.data.*
import dev.prestwich.autv.guide.GuideNavigator
import dev.prestwich.autv.player.AuTvPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var auPlayer: AuTvPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        auPlayer = AuTvPlayer(this)
        setContent {
            var guide by remember { mutableStateOf<Guide?>(null) }
            var selected by remember { mutableStateOf<Channel?>(null) }
            var guideOpen by remember { mutableStateOf(true) }
            var loadError by remember { mutableStateOf(false) }
            var attempt by remember { mutableIntStateOf(0) }
            var epg by remember { mutableStateOf(Epg(emptyMap())) }
            val failed = remember { mutableStateMapOf<Int, String>() }
            var buffering by remember { mutableStateOf(false) }

            fun tune(channel: Channel) {
                if (channel.streamUrl.isNullOrBlank()) return
                selected = channel
                failed.remove(channel.number)
                buffering = true
                auPlayer.play(channel.streamUrl, channel.number)
            }
            DisposableEffect(Unit) {
                val listener = object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        // Associate the result with the actual media item, not a newer selection.
                        auPlayer.player.currentMediaItem?.mediaId?.toIntOrNull()?.let {
                            failed[it] = "Playback failed · select to retry"
                        }
                        buffering = false
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        buffering = state == Player.STATE_BUFFERING
                        if (state == Player.STATE_READY) auPlayer.player.currentMediaItem?.mediaId?.toIntOrNull()?.let { failed.remove(it) }
                    }
                }
                auPlayer.player.addListener(listener)
                onDispose { auPlayer.player.removeListener(listener) }
            }
            LaunchedEffect(attempt) {
                loadError = false
                try {
                    val loaded = withContext(Dispatchers.IO) { GuideRepository().load() }
                    guide = loaded
                    loaded.channels.firstOrNull { !it.streamUrl.isNullOrBlank() }?.let { tune(it) }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    loadError = true
                }
            }
            LaunchedEffect(Unit) {
                try { epg = withContext(Dispatchers.IO) { EpgRepository().load() } }
                catch (error: Exception) { if (error is CancellationException) throw error }
            }
            AuTvScreen(
                guide, selected, guideOpen, loadError, failed.toMap(), buffering, epg, auPlayer.player,
                onSelect = { tune(it); guideOpen = false },
                onCloseGuide = { guideOpen = false }, onOpenGuide = { guideOpen = true },
                onChromeVisibilityChanged = { visible -> setPlayerSystemBars(window, visible) },
                onRetry = { attempt++ },
                onNext = { GuideNavigator(guide?.channels.orEmpty()).next(selected)?.let { tune(it); guideOpen = false } },
                onPrevious = { GuideNavigator(guide?.channels.orEmpty()).previous(selected)?.let { tune(it); guideOpen = false } },
            )
        }
    }
    override fun onStop() { auPlayer.player.pause(); super.onStop() }
    override fun onStart() { super.onStart(); if (auPlayer.player.mediaItemCount > 0) auPlayer.player.play() }
    override fun onDestroy() { auPlayer.release(); super.onDestroy() }
}

internal fun setPlayerSystemBars(window: android.view.Window, visible: Boolean) {
    WindowCompat.getInsetsController(window, window.decorView).apply {
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (visible) show(WindowInsetsCompat.Type.systemBars()) else hide(WindowInsetsCompat.Type.systemBars())
    }
}
