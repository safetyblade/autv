package dev.prestwich.autv.tifprobe

import android.media.tv.TvInputService
import android.media.tv.TvInputManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import dev.prestwich.autv.data.GuideRepository
import dev.prestwich.autv.guide.ChannelTuning
import dev.prestwich.autv.player.AuTvPlayer
import java.util.concurrent.Executors

/** Separate experimental APK. A system-owned TIF surface requires its own session player. */
class ProbeInputService : TvInputService() {
    private fun record(key: String, value: String) { getSharedPreferences("probe", 0).edit().putString(key, value).apply() }
    override fun onCreateSession(inputId: String): Session {
        record("session", "Created for $inputId")
        return object : Session(this) {
            val holder = AuTvPlayer(applicationContext)
            val executor = Executors.newSingleThreadExecutor()
            val handler = Handler(Looper.getMainLooper())
            var generation = 0
            var released = false
            var hasSurface = false
            init {
                holder.player.addListener(object : Player.Listener {
                    override fun onRenderedFirstFrame() { record("video", "First frame rendered"); notifyVideoAvailable() }
                    override fun onPlayerError(error: PlaybackException) {
                        record("error", "Media3: ${error.errorCodeName}"); notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN)
                    }
                })
            }
            override fun onSetSurface(surface: Surface?): Boolean {
                hasSurface = surface != null
                holder.player.setVideoSurface(surface)
                if (!hasSurface) holder.player.pause() else if (holder.player.mediaItemCount > 0) holder.player.play()
                return true
            }
            override fun onSetStreamVolume(volume: Float) { holder.player.volume = volume.coerceIn(0f, 1f) }
            override fun onSetCaptionEnabled(enabled: Boolean) { /* Probe does not publish subtitle tracks. */ }
            override fun onTune(channelUri: Uri): Boolean {
                val request = ++generation
                record("tune", channelUri.toString())
                notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING)
                executor.execute {
                    val result = runCatching {
                        val target = ProbeCatalogue.target(applicationContext, channelUri) ?: error("Not an owned probe channel")
                        ChannelTuning.resolve(GuideRepository().load().channels, target) ?: error("Stream absent from current catalogue")
                    }
                    handler.post {
                        if (!released && generation == request) result.fold(onSuccess = {
                            record("channel", "${it.number} ${it.name}"); notifyContentAllowed(); holder.play(it.streamUrl, it.number)
                            if (!hasSurface) holder.player.pause()
                        }, onFailure = {
                            record("error", "${it.javaClass.simpleName}: ${it.message}"); notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN)
                        })
                    }
                }
                return true
            }
            override fun onRelease() { released = true; generation++; holder.release(); executor.shutdownNow(); record("session", "Released") }
        }
    }
}
