package dev.prestwich.autv.provider

import androidx.compose.runtime.*
import dev.prestwich.autv.data.provider.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Retained account state with no player. IO and sign-out serialize per broadcaster. */
internal class ProviderAccountController(
    private val name: String,
    private val providerId: String,
    private val adapter: ProviderAdapter,
    private val scope: CoroutineScope,
    private val onChannelsChanged: () -> Unit,
    private val onDisconnect: () -> Unit,
    val anonymousLiveApproved: Boolean = false,
    val accountLinkingEnabled: Boolean = true,
) {
    var connection by mutableStateOf(adapter.connection); private set
    var activation by mutableStateOf(adapter.pendingActivation); private set
    var busy by mutableStateOf(false); private set
    var status by mutableStateOf<String?>(null); private set
    var error by mutableStateOf<String?>(null); private set
    var channels by mutableStateOf(emptyList<ProviderChannel>()); private set
    private val mutex = Mutex()
    private var job: Job? = null
    private var generation = 0L
    private suspend fun <T> call(block: () -> T): T = mutex.withLock { withContext(Dispatchers.IO) { block() } }
    val canBrowse get() = connection == ProviderConnection.CONNECTED || anonymousLiveApproved
    fun start() { if (canBrowse) refresh() else if (activation != null) connect() }
    private fun sync() {
        connection = adapter.connection
        if (!canBrowse) { channels = emptyList(); onChannelsChanged() }
    }
    fun connect() {
        if (!accountLinkingEnabled || busy || connection == ProviderConnection.CONNECTED) return
        busy = true; error = null; status = "Requesting $name activation…"
        val current = ++generation
        job = scope.launch {
            var approved = false
            try {
                val challenge = call { adapter.beginSignIn() }
                activation = challenge; connection = ProviderConnection.CONNECTING
                var interval = challenge.pollIntervalMillis
                status = "Waiting for $name approval. Return to AUTV after submitting the code."
                while (System.currentTimeMillis() < challenge.expiresAt) {
                    delay(interval)
                    val connected = try { call { adapter.pollSignIn(challenge) } }
                    catch (failure: ProviderException) {
                        if (failure.failure !in setOf(ProviderFailure.OFFLINE, ProviderFailure.RETRY_LATER)) throw failure
                        interval = (interval + 5000).coerceAtMost(60000)
                        status = "Connection interrupted; retrying the same unexpired code."
                        continue
                    }
                    if (connected) {
                        approved = true; activation = null; connection = adapter.connection
                        status = "Account connected. Finding channels…"
                        channels = catalogue(); onChannelsChanged()
                        status = "Device activation complete"
                        if (channels.isEmpty()) error = "$name returned no live channels. Try refreshing."
                        return@launch
                    }
                    status = "Waiting for $name approval. Return to AUTV after submitting the code."
                }
                call { adapter.signOut() }
                status = "Activation expired"; error = "The code expired. Connect again for a new code."
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                error = message(failure)
                if (adapter.connection == ProviderConnection.CONNECTING) call { adapter.signOut() }
                status = if (approved) "Account connected; channel discovery failed" else "Activation did not finish"
            } finally {
                // Destruction must leave an encrypted unexpired challenge available for recreation.
                if (current == generation) { activation = null; busy = false; sync() }
            }
        }
    }
    fun refresh() {
        if (busy) return
        busy = true; error = null
        val current = ++generation
        job = scope.launch {
            try { channels = catalogue(); onChannelsChanged() }
            catch (failure: Exception) { if (failure is CancellationException) throw failure; error = message(failure) }
            finally { if (current == generation) { busy = false; sync() } }
        }
    }
    private suspend fun catalogue(): List<ProviderChannel> = call { adapter.channels() }.also { rows ->
        if (rows.any { it.id.provider!=providerId } || rows.map { it.id }.distinct().size!=rows.size)
            throw ProviderException(ProviderFailure.AUTH_RESPONSE_UNSUPPORTED)
    }
    fun playbackFailed(code: Int? = null) { error=if(code==null) "$name playback session expired. Select the channel to retry." else "$name playback failed (code $code). Select the channel to retry." }
    suspend fun resolve(id: ProviderChannelId): ProviderPlaybackRequest = try {
        if(id.provider!=providerId) throw ProviderException(ProviderFailure.UNAVAILABLE)
        call { adapter.resolve(id) }.also { if(it.channelId!=id) throw ProviderException(ProviderFailure.UNAVAILABLE) }
    }
    catch (failure: Exception) {
        if (failure is CancellationException) throw failure
        error = message(failure); sync(); throw failure
    }
    fun disconnect() {
        generation++
        val previous = job; previous?.cancel()
        activation = null; status = null; channels = emptyList(); busy = true
        onDisconnect(); onChannelsChanged()
        job = scope.launch {
            previous?.join()
            try { call { adapter.signOut() }; channels=emptyList();error=null }
            catch(failure: Exception) { if(failure is CancellationException) throw failure;error=message(failure) }
            finally { busy=false;sync();onChannelsChanged() }
        }
    }
    private fun message(failure: Exception): String = when ((failure as? ProviderException)?.failure) {
        ProviderFailure.ACTIVATION_EXPIRED -> "The activation code expired. Connect again."
        ProviderFailure.ACTIVATION_DENIED -> "$name did not approve this activation. Request a fresh code."
        ProviderFailure.AUTH_RESPONSE_UNSUPPORTED -> if((failure as? ProviderException)?.stage in setOf(
            ProviderPlaybackStage.ABC_CATALOGUE,ProviderPlaybackStage.ABC_PROGRAMME,ProviderPlaybackStage.ABC_AUTHORISATION,
            ProviderPlaybackStage.TEN_CONFIG,ProviderPlaybackStage.TEN_CATALOGUE,ProviderPlaybackStage.TEN_DESCRIPTOR))
            "$name returned an unsupported service response." else "$name returned an unsupported sign-in response."
        ProviderFailure.AUTH_REQUIRED, ProviderFailure.SESSION_EXPIRED -> "Connect $name in Settings to watch."
        ProviderFailure.OFFLINE -> "Cannot reach $name. Check your connection and retry."
        ProviderFailure.RETRY_LATER -> "$name is temporarily busy. Try again shortly."
        ProviderFailure.DRM_UNSUPPORTED -> "This $name stream needs playback protection AUTV does not support yet."
        ProviderFailure.AD_SESSION_UNSUPPORTED -> "This $name channel requires an ad-session playback path AUTV does not support yet."
        ProviderFailure.PLAYBACK_UNSUPPORTED -> if (name == "7plus") "7plus did not supply a supported Channel 7 playback response." else "$name did not return a supported native stream."
        ProviderFailure.REGION_DENIED -> "$name denied access. Check your Australian connection and account."
        else -> "$name is unavailable. Try again later."
    } + ((failure as? ProviderException)?.stage?.let { " [${it.code}]" } ?: "") + ((failure as? ProviderException)?.httpStatus?.takeIf { it in 100..599 }?.let { " (HTTP $it)" } ?: "") + ((failure as? ProviderException)?.sevenRejection?.let { " ${it.summary}" } ?: "")
}
