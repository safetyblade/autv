package dev.prestwich.autv.cast

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.util.Log
import com.google.android.gms.cast.*
import com.google.android.gms.cast.framework.*
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage
import dev.prestwich.autv.data.*
import dev.prestwich.autv.guide.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.util.UUID

interface CastConnection {
    val connected: Boolean
    fun start()
    fun load(channel: Channel, reusePlayingMedia: Boolean = false)
    fun release()
}

/** One sender bound to the retained shared player. Never owns channel selection or another player. */
class CastSender(private val context: Context, private val scope: CoroutineScope,
    private val current: () -> Channel?, private val programme: (Channel) -> Programme?,
    private val changed: (CastPlayback) -> Unit) : CastConnection {
    private val transfer = CastTransfer()
    private var manager: SessionManager? = null
    private var client: RemoteMediaClient? = null
    private var timeout: Job? = null
    private val senderId = UUID.randomUUID().toString()
    private var expectedLoadId: String? = null
    override val connected get() = client != null
    private val callback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() = observe()
        override fun onMediaError(error: MediaError) {
            // Load results carry the request ticket. Uncorrelated errors during retuning may belong
            // to the superseded request; do not fail the new channel from those callbacks.
            Log.w(TAG, "Receiver error request=${error.requestId} type=${error.type} reason=${error.reason} code=${error.detailedErrorCode}")
            // This callback has an SDK request ID, not our load generation. Confirm any
            // failure through matching media status or the ticketed load result instead.
            observe()
        }
    }
    private val sessions = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, id: String) = attach(session)
        override fun onSessionResumed(session: CastSession, suspended: Boolean) = attach(session)
        override fun onSessionEnded(session: CastSession, error: Int) = detach()
        override fun onSessionSuspended(session: CastSession, reason: Int) { detach(); Log.w(TAG, "Cast suspended: $reason") }
        override fun onSessionStartFailed(session: CastSession, error: Int) { Log.w(TAG, "Cast connection failed: $error"); detach() }
        override fun onSessionResumeFailed(session: CastSession, error: Int) { Log.w(TAG, "Cast resume failed: $error"); detach() }
        override fun onSessionStarting(session: CastSession) = Unit
        override fun onSessionEnding(session: CastSession) = Unit
        override fun onSessionResuming(session: CastSession, id: String) = Unit
    }
    override fun start() {
        if (context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION) return
        try {
            manager = CastContext.getSharedInstance(context).sessionManager
            manager?.addSessionManagerListener(sessions, CastSession::class.java)
            manager?.currentCastSession?.takeIf { it.isConnected }?.let(::attach)
        } catch (error: Exception) {
            Log.i(TAG, "Cast sender unavailable on this device: ${error.javaClass.simpleName}")
        }
    }
    private fun attach(session: CastSession) {
        timeout?.cancel(); client?.unregisterCallback(callback)
        client = session.remoteMediaClient
        transfer.connected(session.castDevice?.friendlyName ?: "Cast receiver")
        Log.i(TAG, "Receiver connected; selected channel=${current()?.number}")
        emit()
        val remote = client ?: run {
            val ticket = transfer.request(current()?.number ?: -1, "")
            fail(ticket, "Receiver connected without a media client. Disconnect and reconnect Cast."); return
        }
        remote.registerCallback(callback)
        current()?.let { load(it, reusePlayingMedia = true) }
    }
    override fun load(channel: Channel, reusePlayingMedia: Boolean) {
        val remote = client ?: return
        timeout?.cancel()
        val url = try { CastStreamUrl.resolve(requireNotNull(channel.streamUrl)) }
        catch (error: Exception) {
            val ticket = transfer.request(channel.number, "")
            fail(ticket, "Invalid Cast stream URL; choose another channel or disconnect Cast."); return
        }
        val ticket = transfer.request(channel.number, url)
        expectedLoadId = "$senderId:$ticket"
        emit()
        if (reusePlayingMedia && remote.mediaInfo?.contentId == url && (remote.isPlaying || remote.isPaused)) {
            expectedLoadId = null // Adopt an existing matching session, including one from a previous sender process.
            observe(); return
        }
        // No browser cookies, sender-only headers, proxy, or local URL are required by this load.
        val media = mediaInfo(channel, url, programme(channel), expectedLoadId)
        Log.i(TAG, "Load requested channel=${channel.number} host=${Uri.parse(url).host} ticket=$ticket")
        timeout = scope.launch {
            delay(30_000)
            if (!transfer.state.ownsPlayback || transfer.state.phase == CastPhase.LOAD_REQUESTED || transfer.state.phase == CastPhase.LOAD_ACCEPTED)
                fail(ticket, "No receiver playback after 30s. Check receiver internet access, stream region/CORS/codec support; retry or disconnect Cast.")
        }
        try {
            remote.load(MediaLoadRequestData.Builder().setMediaInfo(media).setAutoplay(true).build()).setResultCallback { result ->
                if (ticket != transfer.generation || remote !== client) return@setResultCallback
                if (result.status.isSuccess) {
                    if (transfer.accepted(ticket)) { Log.i(TAG, "Load accepted channel=${channel.number} ticket=$ticket"); emit() }
                    observe()
                } else {
                    val error = result.mediaError
                    fail(ticket, "Cast load rejected (${result.status.statusCode}): ${error?.reason ?: error?.type ?: result.status.statusMessage ?: "receiver could not load stream"}. Retry or choose another channel.")
                }
            }
        } catch (error: Exception) {
            fail(ticket, "Cast load request failed: ${error.javaClass.simpleName}. Reconnect and retry.")
        }
    }
    private fun observe() {
        val remote = client ?: return
        val url = remote.mediaInfo?.contentId
        if (!matchesExpectedMedia()) return
        if (transfer.state.phase == CastPhase.FAILED) {
            if (remote.isPlaying || remote.isPaused || remote.isBuffering) runCatching { remote.stop() }
            return
        }
        when (remote.playerState) {
            MediaStatus.PLAYER_STATE_PLAYING -> {
                if (transfer.started(transfer.generation, url)) { timeout?.cancel(); Log.i(TAG, "Remote playback started channel=${transfer.state.channelNumber}"); emit() }
            }
            MediaStatus.PLAYER_STATE_PAUSED -> {
                if (transfer.started(transfer.generation, url, paused = true)) { timeout?.cancel(); emit() }
            }
            MediaStatus.PLAYER_STATE_IDLE -> if (remote.idleReason in listOf(MediaStatus.IDLE_REASON_ERROR, MediaStatus.IDLE_REASON_FINISHED, MediaStatus.IDLE_REASON_CANCELED)) {
                // CANCELED is normal for an old channel during retuning; only treat it as a
                // stop once the requested channel has actually taken ownership.
                if (transfer.state.phase in listOf(CastPhase.LOAD_ACCEPTED, CastPhase.PLAYING, CastPhase.PAUSED))
                    fail(transfer.generation, "Receiver stopped live playback (idle reason ${remote.idleReason}). Retry this channel or disconnect Cast for local playback.")
            }
        }
    }
    private fun matchesExpectedMedia(): Boolean {
        val info = client?.mediaInfo ?: return false
        if (info.contentId != transfer.expectedUrl) return false
        // A new load can use the same URL as an old one. The media payload carries a unique
        // generation so a late PLAYING/error status cannot confirm/fail the superseded tune.
        return expectedLoadId == null || info.customData?.optString("autvLoadId") == expectedLoadId
    }
    private fun fail(ticket: Long, reason: String) {
        if (transfer.failed(ticket, reason)) {
            timeout?.cancel()
            // An old stream or late load must not keep playing while local fallback resumes.
            runCatching { client?.stop() }
            Log.w(TAG, "Remote playback failed channel=${transfer.state.channelNumber} ticket=$ticket: $reason")
            emit()
        }
    }
    private fun emit() = changed(transfer.state)
    private fun detach() {
        timeout?.cancel(); client?.unregisterCallback(callback); client = null
        expectedLoadId = null; transfer.disconnected(); emit()
    }
    override fun release() {
        timeout?.cancel(); client?.unregisterCallback(callback)
        manager?.removeSessionManagerListener(sessions, CastSession::class.java)
        client = null; manager = null
    }
    companion object {
        const val TAG = "AUTV.Cast"
        internal fun mediaInfo(channel: Channel, url: String, programme: Programme?, loadId: String? = null): MediaInfo {
            val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_TV_SHOW).apply {
                putString(MediaMetadata.KEY_TITLE, "${channel.number} · ${channel.name}")
                putString(MediaMetadata.KEY_SUBTITLE, programme?.title ?: channel.genre)
                putString(MediaMetadata.KEY_SERIES_TITLE, channel.name)
                channel.logoUrl?.takeIf { it.startsWith("https://") || it.startsWith("http://") }?.let { runCatching { addImage(WebImage(Uri.parse(it))) } }
            }
            return MediaInfo.Builder(url).setContentUrl(url).setContentType("application/x-mpegURL")
                .setStreamType(MediaInfo.STREAM_TYPE_LIVE).setMetadata(metadata)
                .setCustomData(JSONObject().put("channelNumber", channel.number).put("channelId", channel.tvgId ?: channel.number.toString())
                    .apply { if (loadId != null) put("autvLoadId", loadId) })
                .build()
        }
    }
}
