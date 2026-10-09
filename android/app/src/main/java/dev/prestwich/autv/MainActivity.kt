package dev.prestwich.autv

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.Player
import dev.prestwich.autv.guide.TuneTarget
import dev.prestwich.autv.guide.CastPhase

open class MainActivity : AppCompatActivity() {
    internal lateinit var playback: PlaybackModel; private set
    private var pipUi by mutableStateOf(false)
    private val pipListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) { updatePipParams() }
        override fun onPlaybackStateChanged(state: Int) { updatePipParams() }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        playback = ViewModelProvider(this, object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = modelClass.cast(createPlaybackModel())!!
        })[PlaybackModel::class.java]
        playback.player.addListener(pipListener)
        if (savedInstanceState == null && ChannelIntents.isTuneRequest(intent)) playback.request(ChannelIntents.target(intent))
        pipUi = Build.VERSION.SDK_INT >= 26 && isInPictureInPictureMode
        setContent {
            LaunchedEffect(playback.castPlayback) { updatePipParams() }
            AuTvScreen(playback.guide, playback.selected, playback.guideOpen, playback.loadError,
                playback.failed.toMap(), playback.buffering, playback.epg, playback.player,
                onSelect = { playback.request(TuneTarget.Number(it.number)) },
                onCloseGuide = { playback.guideOpen = false }, onOpenGuide = { playback.guideOpen = true },
                onChromeVisibilityChanged = { visible -> setPlayerSystemBars(window, visible) },
                onRetry = playback::reload, onNext = playback::next, onPrevious = playback::previous,
                onTuneNumber = { playback.request(TuneTarget.Number(it)) }, pictureInPicture = pipUi, notice = playback.notice,
                castPlayback = playback.castPlayback)
        }
        updatePipParams()
    }
    protected open fun createPlaybackModel() = PlaybackModel(application)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent)
        if (ChannelIntents.isTuneRequest(intent)) playback.request(ChannelIntents.target(intent))
    }
    internal fun mobilePipSupported(): Boolean = Build.VERSION.SDK_INT >= 26 &&
        resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK != Configuration.UI_MODE_TYPE_TELEVISION &&
        !packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) &&
        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    protected open fun automaticPipAllowed() = true
    private fun pipEligible() = automaticPipAllowed() && mobilePipSupported() && playback.castPlayback.phase == CastPhase.DISCONNECTED && playback.selected?.streamUrl != null && playback.player.isPlaying
    private fun pipParams(): PictureInPictureParams {
        val bounds = Rect(); window.decorView.getGlobalVisibleRect(bounds)
        val builder = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).setSourceRectHint(bounds)
        if (Build.VERSION.SDK_INT >= 31) builder.setAutoEnterEnabled(pipEligible()).setSeamlessResizeEnabled(true)
        return builder.build()
    }
    internal fun updatePipParams() {
        if (mobilePipSupported()) runCatching { setPictureInPictureParams(pipParams()) }
    }
    internal fun enterMobilePip(): Boolean {
        if (!pipEligible()) return false
        pipUi = true
        val entered = runCatching { enterPictureInPictureMode(pipParams()) }.getOrDefault(false)
        if (!entered) pipUi = false
        return entered
    }
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < 31) enterMobilePip()
        else if (pipEligible()) pipUi = true // Auto-entry: strip the chrome before the resize animation.
    }
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipUi = isInPictureInPictureMode
        if (!isInPictureInPictureMode && !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) playback.pause()
    }
    override fun onResume() {
        super.onResume()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        pipUi = Build.VERSION.SDK_INT >= 26 && isInPictureInPictureMode
        updatePipParams()
    }
    override fun onStop() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT < 26 || !isInPictureInPictureMode) playback.pause()
        super.onStop()
    }
    override fun onStart() { super.onStart(); if (::playback.isInitialized) playback.resume() }
    override fun onDestroy() { playback.player.removeListener(pipListener); super.onDestroy() }
}

internal fun setPlayerSystemBars(window: android.view.Window, visible: Boolean) {
    WindowCompat.getInsetsController(window, window.decorView).apply {
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (visible) show(WindowInsetsCompat.Type.systemBars()) else hide(WindowInsetsCompat.Type.systemBars())
    }
}
