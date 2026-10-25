package dev.prestwich.autv

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import dev.prestwich.autv.data.*
import dev.prestwich.autv.guide.*
import dev.prestwich.autv.data.provider.*
import dev.prestwich.autv.provider.*
import dev.prestwich.autv.player.AuTvPlayer
import dev.prestwich.autv.cast.CastSender
import dev.prestwich.autv.cast.CastConnection
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Activity-retained state: UI transitions and PiP never create another playback engine. */
class PlaybackModel(application: Application, private val guideLoader: () -> Guide, private val epgLoader: () -> Epg,
    senderFactory: (((CastPlayback) -> Unit) -> CastConnection)? = null,
    guideCache: GuideCache? = null, playlistLoader: ((Guide) -> Guide)? = null, epgCacheLoader: (() -> Epg?)? = null,
    providerAdapter: ProviderAdapter? = null, sbsAdapter: ProviderAdapter? = null,
    providerOverrides: Map<String,ProviderAdapter> = emptyMap(),
    anonymousProviderIds: Set<String> = emptySet()) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, { GuideRepository().load() }, { EpgRepository(java.io.File(application.filesDir, "programme-guide-v1.xml.gz")).load() },
        guideCache = FileGuideCache(java.io.File(application.filesDir, "channel-guide-v1.json")),
        playlistLoader = { GuideRepository().reconcile(it) },
        epgCacheLoader = { EpgRepository(java.io.File(application.filesDir, "programme-guide-v1.xml.gz")).cached() })
    private val nineNow = providerAdapter ?: createNineNowProvider(application)
    private val configuration=(application as? AuTvApplication)?.australianProviders ?: AustralianProviderConfiguration()
    private val configuredAdapters = (configuredAustralianAdapters(application,configuration) +
        (sbsAdapter?.let { mapOf("sbs" to it) } ?: emptyMap()) + providerOverrides).also {
        require(it.keys.all { key -> key in setOf("sbs","ten","abc") })
        require(anonymousProviderIds.all { key -> key=="abc" })
    }
    internal val providerAccounts: Map<String,ProviderAccountController> = configuredAdapters.mapValues { (id,adapter) ->
        ProviderAccountController(providerName(id),id,adapter,viewModelScope,
            onChannelsChanged={ accountChannelsChanged(id) },onDisconnect={
                if(selected?.providerChannel?.id?.provider==id) recoverProvider()
                else if(providerTuneProvider==id) cancelProviderTune()
            },anonymousLiveApproved=id in anonymousProviderIds || (id=="abc" && configuration.abcAnonymousLiveApproved),
            accountLinkingEnabled=id!="abc" || configuration.abc?.accountLinkingEnabled!=false)
    }
    internal val sbsAccount get()=providerAccounts.getValue("sbs")
    val pendingActivationProvider: String? get()=if(providerActivation!=null || nineNow.pendingActivation!=null) "9now"
        else providerAccounts.entries.firstOrNull { it.value.activation!=null }?.key
    private fun accountChannelsChanged(id: String) {
        if(selected?.providerChannel?.id?.let { it.provider==id && providerAccounts[id]?.channels?.none { row -> row.id==it }==true }==true) recoverProvider()
        fastGuide?.let(::applyGuide)
    }
    internal fun providerUnavailableReason(id: String): String? = when {
        id=="9now" || providerAccounts.containsKey(id) -> null
        id=="ten" -> "10 connection requires AUTV's registered client configuration in this build."
        id=="abc" -> "ABC connection requires AUTV's registered profile and media-authorisation configuration in this build."
        id=="seven" -> "7plus playback remains paused at its recorded descriptor failure."
        else -> "This service is not enabled in this build."
    }
    private fun providerName(id: String)=when(id) { "sbs"->"SBS On Demand";"ten"->"10";"abc"->"ABC iview";else->"9Now" }
    private var providerTuneProvider: String? = null
    var providerConnection by mutableStateOf(nineNow.connection); private set
    var providerActivation by mutableStateOf<DeviceActivation?>(null); private set
    var providerError by mutableStateOf<String?>(null); private set
    var providerBusy by mutableStateOf(false); private set
    var providerAuthStatus by mutableStateOf<String?>(null); private set
    private var providerChannels = emptyList<ProviderChannel>()
    val providerChannelCount get() = providerChannels.size
    private val providerNumbers = application.getSharedPreferences("provider-channel-numbers-v1", 0)
    private fun providerNumber(channel: ProviderChannel): Int {
        if (channel.id == NineNowAdapter.SEINFELD) return -9001
        val key = channel.id.favouriteKey
        if (providerNumbers.contains(key)) return providerNumbers.getInt(key, -9002)
        val next = (providerNumbers.all.values.filterIsInstance<Int>().minOrNull() ?: -9001) - 1
        // Commit before publishing this identity; refresh/reordering must never renumber a channel.
        check(providerNumbers.edit().putInt(key, next).commit())
        return next
    }
    private var fastGuide: Guide? = null
    private var lastWorkingFast: Channel? = null
    private val providerMutex = Mutex()
    private var accountJob: Job? = null
    private var accountGeneration = 0L
    private var providerTuneJob: Job? = null
    private var providerTuneGeneration = 0L
    private suspend fun <T> providerCall(action: () -> T): T =
        providerMutex.withLock { withContext(Dispatchers.IO) { action() } }

    private val startup = GuideStartup(guideCache, guideLoader, playlistLoader)
    private val rokuResolver = RokuChannelResolver()
    var startupState by mutableStateOf(GuideStartupState.LOADING); private set
    var refreshFailed by mutableStateOf(false); private set
    val holder = AuTvPlayer(application, advancedProviderPlayback = configuredAdapters.keys.any { it in setOf("ten","abc") })
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
            if (selected?.providerChannel != null) { providerAccounts[selected?.providerChannel?.id?.provider]?.playbackFailed(error.errorCode); recoverProvider() }
            else restoreGuideTune(player.currentMediaItem?.mediaId?.toIntOrNull())
        }
        override fun onPlaybackStateChanged(state: Int) {
            buffering = state == Player.STATE_BUFFERING
            if (state == Player.STATE_READY && selected?.providerChannel == null &&
                player.currentMediaItem?.mediaId == selected?.number?.toString()) lastWorkingFast = selected
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
        holder.onProviderSessionFailure = { code -> if (selected?.providerChannel != null) { providerAccounts[selected?.providerChannel?.id?.provider]?.playbackFailed(code);recoverProvider() } }
        holder.onProviderSessionExpired = { if (selected?.providerChannel != null) { providerAccounts[selected?.providerChannel?.id?.provider]?.playbackFailed();recoverProvider() } }
        castSender.start()
        if (providerConnection == ProviderConnection.CONNECTED) refreshProviderChannels()
        else if (nineNow.pendingActivation != null) connectNineNow()
        providerAccounts.values.forEach { it.start() }
    }
    fun reload() {
        if (loading) return
        loading = true; loadError = false
        viewModelScope.launch {
            try {
                startup.refresh(fastGuide) { update ->
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
    private fun applyGuide(fastLoaded: Guide) {
        fastGuide = fastLoaded
        // Native-only rows are added after public-cache validation, never persisted in it.
        val local = (providerChannels + providerAccounts.values.flatMap { it.channels }).map { channel ->
            Channel(providerNumber(channel), channel.name, "Australian TV", providerName(channel.id.provider), "", "", true, null,
                channel.id.favouriteKey, channel.logoUrl, channel)
        }.filter { candidate -> fastLoaded.channels.none { it.number == candidate.number } }
        val loaded = Guide(fastLoaded.activeCount + local.size, fastLoaded.availableCount + local.size,
            fastLoaded.channels + local)
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
            if (refreshed == null || (!refreshed.tunable && refreshed.number != 297)) {
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
        if (channel.providerChannel != null && castSender.connected) {
            notice = "Provider Cast is unavailable. Disconnect Cast to watch on this device."
            return
        }
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
        cancelProviderTune()
        if (channel.providerChannel != null) {
            tuneProvider(channel)
            return
        }
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
        if (channel.providerChannel != null) { cancelProviderTune(); tuneProvider(channel); return }
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
        if (selected?.providerChannel != null && state.phase != CastPhase.DISCONNECTED) {
            recoverProvider()
            return
        }
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
        val catalogue = fastGuide ?: return
        val recent = selected?.number
        homeJob?.cancel()
        homeJob = viewModelScope.launch { homeMutex.withLock { withContext(Dispatchers.IO) { TvHomePublisher(getApplication()).publish(catalogue, recent) } } }
    }
    private fun cancelProviderTune() {
        providerTuneGeneration++
        providerTuneJob?.cancel()
        providerTuneProvider=null
    }
    private fun tuneProvider(channel: Channel) {
        if (castSender.connected) {
            notice = "Provider Cast is unavailable. Disconnect Cast to watch on this device."
            return
        }
        val id = channel.providerChannel?.id ?: return
        val generation = providerTuneGeneration
        providerTuneProvider=id.provider
        buffering = true
        providerTuneJob = viewModelScope.launch {
            try {
                val request = when(id.provider) {
                    "9now" -> providerCall { nineNow.resolve(id) }
                    else -> providerAccounts[id.provider]?.resolve(id) ?: throw ProviderException(ProviderFailure.UNAVAILABLE)
                }
                if(request.channelId!=id) throw ProviderException(ProviderFailure.UNAVAILABLE)
                if (generation != providerTuneGeneration) return@launch
                selected = channel
                failed.remove(channel.number)
                holder.play(request, channel.number)
                if (!foreground) player.pause()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (generation != providerTuneGeneration) return@launch
                buffering = false
                failed[channel.number] = "Playback unavailable"
                if(id.provider=="9now") { providerError=providerMessage(error);notice=providerError;syncProviderConnection() }
                else notice=providerAccounts[id.provider]?.error
                if (selected?.providerChannel != null) recoverProvider()
            }
        }
    }
    private fun recoverProvider() {
        cancelProviderTune()
        val fallback = fastGuide?.channels?.firstOrNull { it.tvgId != null && it.tvgId == lastWorkingFast?.tvgId }
            ?: fastGuide?.channels?.firstOrNull { it.number == lastWorkingFast?.number }
            ?: fastGuide?.channels?.firstOrNull { it.tunable }
        if (fallback != null) {
            request(TuneTarget.Number(fallback.number))
            notice = "Provider playback is unavailable. Returned to ${fallback.name}."
        } else {
            player.stop(); player.clearMediaItems(); selected = null; buffering = false; guideOpen = true
            notice = "Provider playback is unavailable. Choose another channel."
        }
    }
    private fun syncProviderConnection() {
        providerConnection = nineNow.connection
        if (providerConnection != ProviderConnection.CONNECTED) {
            providerChannels = emptyList()
            if (selected?.providerChannel?.id?.provider=="9now") recoverProvider()
            fastGuide?.let(::applyGuide)
        }
    }
    fun connectNineNow() {
        if (providerBusy) return
        providerBusy = true
        providerError = null
        providerAuthStatus = if (nineNow.pendingActivation != null) "Resuming your pending Nine activation…" else "Requesting an activation code…"
        providerActivation = nineNow.pendingActivation
        val generation = ++accountGeneration
        accountJob = viewModelScope.launch {
            var activationApproved = false
            try {
                val activation = providerCall { nineNow.beginSignIn() }
                providerActivation = activation
                providerConnection = ProviderConnection.CONNECTING
                providerAuthStatus = "Waiting for Nine approval. Return to AUTV after submitting the code."
                var interval = activation.pollIntervalMillis
                while (System.currentTimeMillis() < activation.expiresAt) {
                    delay(interval)
                    val approved = try { providerCall { nineNow.pollSignIn(activation) } }
                    catch (error: ProviderException) {
                        if (error.failure !in setOf(ProviderFailure.OFFLINE, ProviderFailure.RETRY_LATER)) throw error
                        interval = (interval + 5_000).coerceAtMost(60_000)
                        providerAuthStatus = "Activation connection interrupted; retrying the same code. Return to AUTV to continue."
                        continue
                    }
                    if (approved) {
                        activationApproved = true
                        providerAuthStatus = "Nine approved this device. Finding channels…"
                        providerActivation = null
                        providerChannels = providerCall { nineNow.channels() }
                        providerConnection = nineNow.connection
                        fastGuide?.let(::applyGuide)
                        if (providerChannels.isEmpty()) providerError = providerDiscoveryMessage()
                        providerAuthStatus = "Device activation complete"
                        return@launch
                    }
                    providerAuthStatus = "Waiting for Nine approval. Return to AUTV after submitting the code."
                }
                providerCall { nineNow.signOut() }
                providerError = "The sign-in code expired. Request a new code."
                providerAuthStatus = "Activation expired"
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                providerError = (if (activationApproved) "Channel discovery: " else "Device activation: ") + providerMessage(error)
                if (nineNow.connection == ProviderConnection.CONNECTING) providerCall { nineNow.signOut() }
                providerAuthStatus = if (activationApproved) "Device activation complete; channel discovery failed" else "Activation did not finish"
            } finally {
                // A destroyed Activity/process must not invalidate the saved provider challenge.
                // Only explicit cancel, expiry, denial or successful token exchange clears it.
                if (generation == accountGeneration) {
                    providerActivation = null
                    providerBusy = false
                    syncProviderConnection()
                }
            }
        }
    }
    fun refreshProviderChannels() {
        if (providerBusy) return
        providerBusy = true; providerError = null
        val generation = ++accountGeneration
        accountJob = viewModelScope.launch {
            try {
                providerChannels = providerCall { nineNow.channels() }
                if (providerChannels.isEmpty()) providerError = providerDiscoveryMessage()
                if (selected?.providerChannel?.id?.let { id -> id.provider=="9now" && providerChannels.none { it.id == id } } == true)
                    recoverProvider()
                fastGuide?.let(::applyGuide)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                providerError = providerMessage(error)
            } finally { if (generation == accountGeneration) { providerBusy = false; syncProviderConnection() } }
        }
    }
    fun disconnectNineNow() {
        // Wait for cancelled blocking IO to finish before clearing secrets, preventing late token writes.
        accountGeneration++
        val previous = accountJob
        previous?.cancel()
        if(selected?.providerChannel?.id?.provider=="9now" || providerTuneProvider=="9now") cancelProviderTune()
        providerActivation = null
        providerAuthStatus = null
        providerBusy = true
        providerChannels = emptyList()
        if (selected?.providerChannel?.id?.provider=="9now") recoverProvider()
        fastGuide?.let(::applyGuide)
        accountJob = viewModelScope.launch {
            previous?.join()
            providerCall { nineNow.signOut() }
            providerChannels = emptyList()
            providerError = null
            providerBusy = false
            syncProviderConnection()
        }
    }
    private fun providerDiscoveryMessage(): String {
        val counts = nineNow.discovery
        return if (counts == null) "Connected to 9Now, but no live channels were returned. Try refreshing channels."
        else "Connected to 9Now. Received ${counts.channels} channels and ${counts.events} event entries, " +
            "with ${counts.matches} usable live entries. Try refreshing channels."
    }
    private fun providerMessage(error: Exception): String = when ((error as? ProviderException)?.failure) {
        ProviderFailure.ACTIVATION_EXPIRED -> "The activation code expired. Request a new code."
        ProviderFailure.ACTIVATION_DENIED -> "Nine did not accept this activation. Request a fresh code and try again."
        ProviderFailure.AUTH_RESPONSE_UNSUPPORTED -> "Nine returned an unrecognised activation response."
        ProviderFailure.RETRY_LATER -> "Nine is temporarily busy. Try again shortly."
        ProviderFailure.AUTH_REQUIRED -> "Connect 9Now in Settings to watch."
        ProviderFailure.SESSION_EXPIRED -> "Your 9Now session expired. Connect again in Settings."
        ProviderFailure.REGION_DENIED -> "9Now denied this request. Check that you are in Australia."
        ProviderFailure.OFFLINE -> "Cannot reach 9Now. Check your connection and try again."
        ProviderFailure.PLAYBACK_UNSUPPORTED -> "9Now lists this channel, but did not supply a supported native playback stream."
        ProviderFailure.DRM_UNSUPPORTED -> "This 9Now stream needs playback protection that AUTV does not support yet."
        ProviderFailure.UNSUPPORTED -> "9Now sign-in is unavailable in this build."
        else -> "9Now is unavailable. Try again later."
    } + ((error as? ProviderException)?.httpStatus?.takeIf { it in 100..599 }?.let { " (HTTP $it)" } ?: "")
    override fun onCleared() { castSender.release(); player.removeListener(listener); holder.release() }
}
