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
    senderFactory: (((CastPlayback) -> Unit) -> CastConnection)? = null,
    guideCache: GuideCache? = null, playlistLoader: ((Guide) -> Guide)? = null, epgCacheLoader: (() -> Epg?)? = null) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, { GuideRepository().load() }, { EpgRepository(java.io.File(application.filesDir, "programme-guide-v1.xml.gz")).load() },
        guideCache = FileGuideCache(java.io.File(application.filesDir, "channel-guide-v1.json")),
        playlistLoader = { GuideRepository().reconcile(it) },
        epgCacheLoader = { EpgRepository(java.io.File(application.filesDir, "programme-guide-v1.xml.gz")).cached() })
    private val startup = GuideStartup(guideCache, guideLoader, playlistLoader)
    private val rokuResolver = RokuChannelResolver()
    var startupState by mutableStateOf(GuideStartupState.LOADING); private set
    var refreshFailed by mutableStateOf(false); private set
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
    private var initialSelectionPending = true
    private val guideTuneRecovery = GuideTuneRecovery()
    private val castSender = senderFactory?.invoke(::onCastChanged) ?: CastSender(application, viewModelScope, { selected },
        { epg.at(it, System.currentTimeMillis()).now }, ::onCastChanged)
    private val listener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            player.currentMediaItem?.mediaId?.toIntOrNull()?.let { failed[it] = "Playback failed" }
            buffering = false
            restoreGuideTune(player.currentMediaItem?.mediaId?.toIntOrNull())
        }
        override fun onPlaybackStateChanged(state: Int) {
            buffering = state == Player.STATE_BUFFERING
            if (state == Player.STATE_READY) player.currentMediaItem?.mediaId?.toIntOrNull()?.let { failed.remove(it); guideTuneRecovery.ready(it) }
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
    }
    init {
        player.addListener(listener)
        reload()
        viewModelScope.launch {
            epgCacheLoader?.let { loader ->
                withContext(Dispatchers.IO) { runCatching { loader() }.getOrNull() }?.let { epg = it }
            }
            while (isActive) {
                try { epg = withContext(Dispatchers.IO) { epgLoader() } }
                catch (error: Exception) { if (error is CancellationException) throw error }
                delay(30 * 60_000L)
            }
        }
        castSender.start()
    }
    fun reload() {
        if (loading) return
        loading = true; loadError = false
        viewModelScope.launch {
            try {
                startup.refresh(guide) { update ->
                    startupState = update.state
                    refreshFailed = update.refreshFailed
                    loadError = update.state == GuideStartupState.ERROR
                    update.guide?.let { applyGuide(it) }
                    // A target absent from the cache may still exist in the fresh catalogue.
                    if (hasPending && (update.state == GuideStartupState.READY || update.refreshFailed)) {
                        hasPending = false; applyRequest(pending)
                    }
                }
            } finally { loading = false }
        }
    }
    private fun applyGuide(loaded: Guide) {
        if (guide == loaded) return
        val previous = selected
        guide = loaded
        if (hasPending) {
            pending?.let { ChannelTuning.resolve(loaded.channels, it) }?.let {
                hasPending = false; applyRequest(pending)
            }
        } else if (previous == null && initialSelectionPending) {
            val recent = ChannelTuning.resolve(loaded.channels, TuneTarget.Number(preferences.getInt("channel", -1)))
            (recent ?: loaded.channels.firstOrNull { !it.streamUrl.isNullOrBlank() })?.let { tune(it) }
        } else if (previous != null) {
            val refreshed = previous.tvgId?.let { id -> loaded.channels.singleOrNull { it.tvgId == id } }
                ?: loaded.channels.singleOrNull { it.number == previous.number && it.name == previous.name }
            if (refreshed == null || (refreshed.streamUrl.isNullOrBlank() && refreshed.number != 297)) {
                selected = refreshed
                player.stop(); player.clearMediaItems(); buffering = false; guideOpen = true
                notice = "That channel is no longer available. Choose another channel."
            } else if (refreshed.streamUrl != previous.streamUrl || refreshed.number != previous.number) {
                tune(refreshed)
            } else {
                // Metadata refresh does not replace media or restart a local/Cast session.
                selected = refreshed
            }
        }
        publishHome()
    }
    fun request(target: TuneTarget?, restoreOnFailure: Boolean = false) {
        if (guide == null) { pending = target; hasPending = true; guideOpen = true }
        else applyRequest(target, restoreOnFailure)
    }
    private fun applyRequest(target: TuneTarget?, restoreOnFailure: Boolean = false) {
        val channel = target?.let { ChannelTuning.resolve(guide?.channels.orEmpty(), it) }
        if (channel == null) { guideOpen = true; notice = "That channel is not currently available. Choose another channel."; return }
        if (restoreOnFailure) guideTuneRecovery.begin(selected, channel) else guideTuneRecovery.clear()
        try {
            tune(channel); guideOpen = false; notice = null; publishHome()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            failed[channel.number] = "Playback failed"
            if (!restoreGuideTune(channel.number)) throw error
        }
    }
    private fun restoreGuideTune(number: Int?): Boolean {
        val previous = guideTuneRecovery.failed(number) ?: return false
        // Use the same request/tune path as Quick Guide; never resolve or play a stream in the UI.
        applyRequest(TuneTarget.Number(previous.number))
        notice = "Could not play that channel. Returned to ${previous.name}."
        return true
    }
    private fun tune(channel: Channel) {
        initialSelectionPending = false
        val retry = failed.remove(channel.number) != null
        selected = channel
        preferences.edit().putInt("channel", channel.number).apply()

        // Channel 297 is an isolated native-only resolver experiment. Keep it out
        // of Cast and out of the generic playlist path so no other channel changes.
        if (channel.number == 297) {
            playLocal(channel, retry)
            return
        }

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
        if (channel.number == 297) {
            buffering = true
            viewModelScope.launch {
                try {
                    val resolved = withContext(Dispatchers.IO) {
                        rokuResolver.resolve(channel.tvgId ?: "439096119777e0f33894343b551bece4")
                    }
                    if (selected?.number != 297) return@launch
                    failed.remove(297)
                    holder.play(resolved, 297)
                    if (!foreground) player.pause()
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    if (selected?.number == 297) {
                        failed[297] = "Playback failed"
                        buffering = false
                        notice = "Wrestling Central Roku test failed to resolve. Other channels are unaffected."
                        restoreGuideTune(297)
                    }
                }
            }
            return
        }

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
            guideTuneRecovery.ready(selected?.number)
            player.stop() // Release local decoding/buffering only after receiver status confirms ownership.
            buffering = false
        } else if (state.phase == CastPhase.FAILED && restoreGuideTune(selected?.number)) {
            return
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
