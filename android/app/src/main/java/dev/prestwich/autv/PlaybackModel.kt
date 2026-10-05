package dev.prestwich.autv

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import dev.prestwich.autv.data.*
import dev.prestwich.autv.guide.*
import dev.prestwich.autv.player.AuTvPlayer
import dev.prestwich.autv.cast.CastSender
import dev.prestwich.autv.cast.CastConnection
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Activity-retained state: UI transitions and PiP never create another playback engine. */
class PlaybackModel(application: Application, private val guideLoader: () -> Guide, private val epgLoader: () -> Epg,
    senderFactory: (((CastPlayback) -> Unit) -> CastConnection)? = null) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, { GuideRepository().load() }, { EpgRepository().load() })
    val holder = AuTvPlayer(application)
    val player = holder.player
    var guide by mutableStateOf<Guide?>(null); private set
    var selected by mutableStateOf<Channel?>(null); private set
    var guideOpen by mutableStateOf(true)
    var loadError by mutableStateOf(false); private set
    var epg by mutableStateOf(Epg(emptyMap())); private set
    var buffering by mutableStateOf(false); private set
    var playing by mutableStateOf(false); private set
    var notice by mutableStateOf<String?>(null); private set
    var castPlayback by mutableStateOf(CastPlayback()); private set
    val failed = mutableStateMapOf<Int, String>()
    private val preferences = application.getSharedPreferences("playback", 0)
    private var pending: TuneTarget? = null
    private var hasPending = false
    private var loading = false
    private var homeJob: Job? = null
    private val homeMutex = Mutex()
    private var foreground = true
    private val castSender = senderFactory?.invoke(::onCastChanged) ?: CastSender(application, viewModelScope, { selected },
        { epg.at(it, System.currentTimeMillis()).now }, ::onCastChanged)
    private val listener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            player.currentMediaItem?.mediaId?.toIntOrNull()?.let { failed[it] = "Playback failed" }
            buffering = false
        }
        override fun onPlaybackStateChanged(state: Int) {
            buffering = state == Player.STATE_BUFFERING
            if (state == Player.STATE_READY) player.currentMediaItem?.mediaId?.toIntOrNull()?.let { failed.remove(it) }
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
    }
    init {
        player.addListener(listener)
        reload()
        viewModelScope.launch {
            try { epg = withContext(Dispatchers.IO) { epgLoader() } }
            catch (error: Exception) { if (error is CancellationException) throw error }
        }
        castSender.start()
    }
    fun reload() {
        if (loading) return
        loading = true; loadError = false
        viewModelScope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) { guideLoader() }
                guide = loaded
                if (hasPending) { hasPending = false; applyRequest(pending) }
                else if (selected == null) {
                    val recent = ChannelTuning.resolve(loaded.channels, TuneTarget.Number(preferences.getInt("channel", -1)))
                    (recent ?: loaded.channels.firstOrNull { !it.streamUrl.isNullOrBlank() })?.let { tune(it) }
                }
                publishHome()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                loadError = true
            } finally { loading = false }
        }
    }
    fun request(target: TuneTarget?) {
        if (guide == null) { pending = target; hasPending = true; guideOpen = true }
        else applyRequest(target)
    }
    private fun applyRequest(target: TuneTarget?) {
        val channel = target?.let { ChannelTuning.resolve(guide?.channels.orEmpty(), it) }
        if (channel == null) { guideOpen = true; notice = "That channel is not currently available. Choose another channel."; return }
        tune(channel); guideOpen = false; notice = null; publishHome()
    }
    private fun tune(channel: Channel) {
        val retry = failed.remove(channel.number) != null
        selected = channel
        preferences.edit().putInt("channel", channel.number).apply()
        if (castSender.connected) {
            // Same selection path for guide, number/deep link and CH keys. Do not start the
            // new channel locally while the receiver is already responsible for playback.
            if (!castPlayback.ownsPlayback) playLocal(channel)
            castSender.load(channel)
            return
        }
        playLocal(channel, retry)
    }
    private fun playLocal(channel: Channel, retry: Boolean = false) {
        // Reuse only the same identity AND stream. A refreshed catalogue may replace a URL.
        if (!retry && player.currentMediaItem?.mediaId == channel.number.toString() &&
            player.currentMediaItem?.localConfiguration?.uri?.toString() == channel.streamUrl && player.playerError == null &&
            player.playbackState in listOf(Player.STATE_READY, Player.STATE_BUFFERING)) {
            if (foreground) player.play(); return
        }
        buffering = true
        holder.play(channel.streamUrl, channel.number)
        if (!foreground) player.pause()
    }
    fun next() { GuideNavigator(guide?.channels.orEmpty()).next(selected)?.let { request(TuneTarget.Number(it.number)) } }
    fun previous() { GuideNavigator(guide?.channels.orEmpty()).previous(selected)?.let { request(TuneTarget.Number(it.number)) } }
    fun resume() { foreground = true; if (!castPlayback.ownsPlayback && player.mediaItemCount > 0) player.play() }
    fun pause() { foreground = false; player.pause() } // Never pauses the receiver when the sender backgrounds.
    private fun onCastChanged(state: CastPlayback) {
        val wasRemote = castPlayback.ownsPlayback
        castPlayback = state
        if (state.ownsPlayback) {
            player.stop() // Release local decoding/buffering only after receiver status confirms ownership.
            buffering = false
        } else if (state.phase == CastPhase.FAILED || (state.phase == CastPhase.DISCONNECTED && wasRemote)) {
            selected?.let { playLocal(it, retry = true) }
        }
    }
    private fun publishHome() {
        val catalogue = guide ?: return
        val recent = selected?.number
        homeJob?.cancel()
        homeJob = viewModelScope.launch { homeMutex.withLock { withContext(Dispatchers.IO) { TvHomePublisher(getApplication()).publish(catalogue, recent) } } }
    }
    override fun onCleared() { castSender.release(); player.removeListener(listener); holder.release() }
}
