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
    private val configurationFailures = configuration.configurationFailures.toMutableSet()
    private val configuredAdapters = (configuredAustralianAdapters(application,configuration) { configurationFailures.add(it) } +
        (sbsAdapter?.let { mapOf("sbs" to it) } ?: emptyMap()) + providerOverrides).also {
        require(it.keys.all { key -> key in setOf("sbs","seven","ten","abc") })
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
    internal val experimentalAbcLink: Boolean get() = BuildConfig.DEBUG && configuration.abc == null && providerAccounts.containsKey("abc")
    internal val sbsAccount get()=providerAccounts.getValue("sbs")
    val pendingActivationProvider: String? get()=if(providerActivation!=null || nineNow.pendingActivation!=null) "9now"
        else providerAccounts.entries.firstOrNull { it.value.activation!=null }?.key
    private fun accountChannelsChanged(id: String) {
        if(selected?.providerChannel?.id?.let { it.provider==id && providerAccounts[id]?.channels?.none { row -> row.id==it }==true }==true) recoverProvider()
        fastGuide?.let(::applyGuide)
    }
    internal fun providerUnavailableReason(id: String): String? = when {
        id=="9now" || providerAccounts.containsKey(id) -> null
        id in configurationFailures -> "${providerName(id)} configuration could not be loaded. An approved registration source is required."
        id=="ten" -> "10 connection requires AUTV's registered client configuration in this build."
        id=="abc" -> "ABC connection requires AUTV's registered profile and media-authorisation configuration in this build."
        else -> "This service is not enabled in this build."
    }
    private fun providerName(id: String)=when(id) { "sbs"->"SBS On Demand";"seven"->"7plus";"ten"->"10";"abc"->"ABC iview";else->"9Now" }
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
    private companion object { const val TEN_VIC_TRIAL_NUMBER = -98421 }
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
    internal val startupPreferences = StartupPreferences(preferences)
    private var startupJob: Job? = null
    private var startupAttempt = false
    private var startupFailed = false
    private var startupSucceeded = false

    private fun saveWatched() {
        val channel = selected ?: return
        if (BuildConfig.DEBUG && NativeBroadcasterTrial.TEN_LINEAR_TRIALS.any { it.number == channel.number }) return // Debug previews are never watched history.
        val identity = FavouriteIdentity.key(channel) ?: return
        preferences.edit().putString("last-watched-id", identity).putInt("channel", channel.number).apply()
    }
    fun openGuide() { cancelStartup(); guideOpen = true }
    private fun cancelStartup() {
        initialSelectionPending = false
        startupJob?.cancel(); startupJob = null
        if (startupAttempt) cancelProviderTune()
        startupAttempt = false
    }
    private fun startChannel() {
        if (startupJob != null) return
        initialSelectionPending = false
        startupJob = viewModelScope.launch {
            // Give connected providers their existing discovery path before resolving saved identities.
            delay(200) // Account discovery starts in init after the cached guide is published.
            var discoveryWaits = 0
            while ((providerBusy || providerAccounts.values.any { it.busy }) && discoveryWaits++ < 40) delay(200)
            val rows = guide?.channels.orEmpty().filterNot { candidate ->
                BuildConfig.DEBUG && NativeBroadcasterTrial.TEN_LINEAR_TRIALS.any { it.number == candidate.number }
            }
            val favourites = getApplication<Application>().getSharedPreferences("favourites-v1", 0)
                .getStringSet("channels", emptySet()).orEmpty()
            val recent = preferences.getString("last-watched-id", null)
            val legacy = if (recent == null && preferences.contains("channel")) preferences.getInt("channel", -1) else null
            val candidates = StartupChannels.candidates(rows, startupPreferences.mode, startupPreferences.identity,
                recent, legacy, favourites)
            val deadline = android.os.SystemClock.elapsedRealtime() + 60_000
            for (channel in candidates) {
                if (android.os.SystemClock.elapsedRealtime() >= deadline) break
                startupAttempt = true; startupFailed = false; startupSucceeded = false
                guideOpen = true
                try { tune(channel) } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    startupFailed = true
                }
                val end = minOf(deadline, android.os.SystemClock.elapsedRealtime() + 8_000)
                while (!startupFailed && !startupSucceeded && android.os.SystemClock.elapsedRealtime() < end) delay(100)
                if (startupSucceeded && !startupFailed) {
                    startupAttempt = false; guideOpen = false; notice = null
                    return@launch
                }
                failed[channel.number] = "Startup playback unavailable"
                cancelProviderTune(); player.stop(); player.clearMediaItems(); buffering = false
            }
            startupAttempt = false; selected = null; buffering = false; guideOpen = true
            notice = "No startup channel could play. Choose a channel from the guide."
        }
    }
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
            if (startupAttempt) { startupFailed = true; return }
            if (selected?.providerChannel != null) { providerAccounts[selected?.providerChannel?.id?.provider]?.playbackFailed(error.errorCode); recoverProvider() }
            else restoreGuideTune(player.currentMediaItem?.mediaId?.toIntOrNull())
        }
        override fun onPlaybackStateChanged(state: Int) {
            buffering = state == Player.STATE_BUFFERING
            if (state == Player.STATE_READY && (!BuildConfig.DEBUG || NativeBroadcasterTrial.TEN_LINEAR_TRIALS.none { it.number == selected?.number }) && selected?.providerChannel == null &&
                player.currentMediaItem?.mediaId == selected?.number?.toString()) lastWorkingFast = selected
            if (state == Player.STATE_READY) player.currentMediaItem?.mediaId?.toIntOrNull()?.let { failed.remove(it); guideTuneRecovery.ready(it) }
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            playing = isPlaying
            if (isPlaying && player.currentMediaItem?.mediaId == selected?.number?.toString()) {
                saveWatched()
                if (startupAttempt) startupSucceeded = true
            }
        }
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
        // Owner-verified picture on 5 Nov: publish only the VIC trial in DEBUG builds.
        // This is NOT 10 account linking or an approved distribution feed.
        // Never introduce unverified 10 Drama/Comedy/Nickelodeon URLs as playable rows.
        val tenLinearTrials = if (BuildConfig.DEBUG)
            NativeBroadcasterTrial.TEN_LINEAR_TRIALS.filter { trial ->
                fastLoaded.channels.none { it.number == trial.number }
            }.map { trial ->
                Channel(trial.number, trial.name, "Australian TV", "10 (experimental)",
                    if (trial.physicallyObserved) "Picture observed; audio/rights/EPG unverified"
                    else "Legacy source candidate; current playback and channel mapping unverified",
                    "", true, trial.source, "autv-debug-ten-${trial.number}", null)
            }
        else emptyList()
        val added = local + tenLinearTrials
        val loaded = Guide(fastLoaded.activeCount + added.size, fastLoaded.availableCount + added.size,
            fastLoaded.channels + added)
        // Migrate numeric history against the first available catalogue (including the old cache)
        // before a fresh guide can renumber News rows. This changes identity format, not history.
        if (!preferences.contains("last-watched-id") && preferences.contains("channel")) {
            loaded.channels.firstOrNull { it.number == preferences.getInt("channel", -1) }
                ?.let(FavouriteIdentity::key)?.let { identity ->
                    preferences.edit().putString("last-watched-id", identity).apply()
                }
        }
        if (guide == loaded) return
        val previous = selected
        guide = loaded
        if (hasPending) {
            pending?.let { ChannelTuning.resolve(loaded.channels, it) }?.let {
                hasPending = false; applyRequest(pending)
            }
        } else if (previous == null && initialSelectionPending) {
            startChannel()
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
        cancelStartup()
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
    /** Explicit owner-triggered debug trial: live HLS within AUTV, not a linked account. */
    internal fun playTenVicNativeTrial() {
        if (!BuildConfig.DEBUG) return
        if (castSender.connected) {
            notice = "Disconnect Chromecast before testing live playback on this TV."
            return
        }
        cancelStartup()
        cancelProviderTune()
        guideTuneRecovery.clear()
        val trial = Channel(TEN_VIC_TRIAL_NUMBER, "10 VIC — Native playback trial",
            "Australian TV", "10 (experimental)", "", "", true,
            dev.prestwich.autv.provider.NativeBroadcasterTrial.TEN_VIC_EXPERIMENTAL_HLS,
            "autv-debug-ten-vic-test", null)
        selected = trial
        guideOpen = false
        notice = "Experimental 10 VIC stream. This does not link your 10 account or verify production rights."
        failed.remove(trial.number)
        try {
            playLocal(trial, retry = true)
        } catch (_: Exception) {
            failed[trial.number] = "Playback failed"
            buffering = false
            guideOpen = true
            notice = "10 VIC trial could not start. Your connected broadcasters are unaffected."
        }
    }

    fun next() { GuideNavigator(guide?.channels.orEmpty()).next(selected)?.let { request(TuneTarget.Number(it.number)) } }
    fun previous() { GuideNavigator(guide?.channels.orEmpty()).previous(selected)?.let { request(TuneTarget.Number(it.number)) } }
    fun retryCasting() { selected?.takeIf { it.providerChannel == null }?.let { notice=null; castSender.load(it) } }
    fun stopCasting() = castSender.stopCasting()
    fun toggleCastPause() = castSender.togglePause()
    fun toggleCastMute() = castSender.toggleMute()
    fun setCastVolume(value: Float) = castSender.setVolume(value)
    fun resume() { foreground = true; if (!castPlayback.ownsPlayback && player.mediaItemCount > 0) player.play() }
    fun pause() { foreground = false; player.pause() } // Never pauses the receiver when the sender backgrounds.
    private fun onCastChanged(state: CastPlayback) {
        val wasRemote = castPlayback.ownsPlayback
        if (state.phase != CastPhase.FAILED && notice == castPlayback.error) notice = null
        castPlayback = state
        if (selected?.providerChannel != null && state.phase != CastPhase.DISCONNECTED) {
            notice="Provider Cast is unavailable. Continuing playback on this device."
            return
        }
        if (state.phase == CastPhase.PLAYING && state.channelNumber == selected?.number) saveWatched()
        if (startupAttempt && state.channelNumber == selected?.number) {
            if (state.phase == CastPhase.PLAYING) startupSucceeded = true
            // Cast failure alone says nothing about local startup availability.
            // Fall through to local recovery; only a local player error fails startup.
        }
        if (state.ownsPlayback) {
            if(state.phase in setOf(CastPhase.PLAYING,CastPhase.PAUSED) && state.channelNumber==selected?.number)
                guideTuneRecovery.ready(selected?.number)
            if (!wasRemote) player.stop() // Release decoding only on confirmed handoff.
            buffering = false
        } else if (state.phase == CastPhase.FAILED && restoreGuideTune(selected?.number)) {
            return
        } else if (state.phase == CastPhase.FAILED || (state.phase == CastPhase.DISCONNECTED && wasRemote)) {
            selected?.let { playLocal(it, retry = wasRemote || player.playerError!=null) }
            state.error?.let { notice=it }
        }
    }
    private fun publishHome() {
        val catalogue = fastGuide ?: return
        val recent = selected?.number
        homeJob?.cancel()
        homeJob = viewModelScope.launch { homeMutex.withLock { withContext(Dispatchers.IO) { TvHomePublisher(getApplication()).publish(catalogue, recent) } } }
    }
    private fun cancelProviderTune() {
        val resolving=providerTuneJob?.isActive==true
        providerTuneGeneration++
        providerTuneJob?.cancel()
        providerTuneProvider=null
        if(resolving) buffering=player.playbackState==Player.STATE_BUFFERING
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
                val explanation=if(id.provider=="9now") { providerError=providerMessage(error);syncProviderConnection();providerError }
                    else providerAccounts[id.provider]?.error
                if (startupAttempt) { startupFailed = true; notice = explanation; return@launch }
                if (selected?.providerChannel?.id==id) recoverProvider()
                notice=explanation ?: notice
            }
        }
    }
    private fun recoverProvider() {
        if (startupAttempt) { startupFailed = true; return }
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
