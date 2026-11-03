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
    fun load(channel: Channel,reusePlayingMedia: Boolean=false)
    fun togglePause() = Unit
    fun toggleMute() = Unit
    fun setVolume(value: Float) = Unit
    fun stopCasting() = Unit
    fun release()
}

/** Existing session discovery, with ticketed media preparation and receiver-confirmed handoff. */
class CastSender(private val context: Context,private val scope: CoroutineScope,
    private val current: ()->Channel?,private val programme: (Channel)->Programme?,
    private val changed: (CastPlayback)->Unit): CastConnection {
    private val transfer=CastTransfer()
    private var manager: SessionManager?=null
    private var client: RemoteMediaClient?=null
    private val watchdog=CastWatchdog(scope) { ticket -> fail(ticket,CastDiagnostics.failure(CastLoadFailure.TIMEOUT)) }
    private var poll: Job?=null
    private var preparation: Job?=null
    private var resolver: CastMediaResolver?=null
    private val senderId=UUID.randomUUID().toString()
    private var expectedLoadId: String?=null
    private var acknowledged=false
    private var adopted=false
    private var loadStartedAt=0L
    private var previousSessionId: Long?=null
    private var ownedSessionId: Long?=null
    override val connected get()=client!=null
    private val callback=object: RemoteMediaClient.Callback() {
        override fun onStatusUpdated()=observe()
        override fun onMetadataUpdated()=observe()
        override fun onMediaError(error: MediaError) {
            // SDK request IDs are not our load tickets. Fail only an explicitly echoed
            // ticket; otherwise matching status/load results or the watchdog decide.
            Log.w(TAG,"Receiver media error code=${error.detailedErrorCode}")
            if(error.customData?.optString("autvLoadId")==expectedLoadId && expectedLoadId!=null)
                fail(transfer.generation,rejection(null,error))
            else observe()
        }
    }
    private val sessions=object: SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession,id: String)=attach(session)
        override fun onSessionResumed(session: CastSession,suspended: Boolean)=attach(session)
        override fun onSessionEnded(session: CastSession,error: Int)=detach(error.takeIf { it!=0 })
        override fun onSessionSuspended(session: CastSession,reason: Int)=detach(reason)
        override fun onSessionStartFailed(session: CastSession,error: Int)=detach(error)
        override fun onSessionResumeFailed(session: CastSession,error: Int)=detach(error)
        override fun onSessionStarting(session: CastSession) {
            transfer.connecting(session.castDevice?.friendlyName ?: "Cast receiver");emit()
        }
        override fun onSessionEnding(session: CastSession)=Unit
        override fun onSessionResuming(session: CastSession,id: String) {
            transfer.connecting(session.castDevice?.friendlyName ?: "Cast receiver");emit()
        }
    }
    override fun start() {
        if(context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK==Configuration.UI_MODE_TYPE_TELEVISION) return
        try {
            manager=CastContext.getSharedInstance(context).sessionManager
            manager?.addSessionManagerListener(sessions,CastSession::class.java)
            manager?.currentCastSession?.takeIf { it.isConnected }?.let(::attach)
        } catch(error: Exception) { Log.i(TAG,"Cast sender unavailable: ${error.javaClass.simpleName}") }
    }
    private fun cancelPending() {
        watchdog.cancel();poll?.cancel();poll=null
        resolver?.cancel();resolver=null;preparation?.cancel();preparation=null
    }
    private fun attach(session: CastSession) {
        cancelPending();client?.unregisterCallback(callback)
        client=session.remoteMediaClient;expectedLoadId=null;ownedSessionId=null;acknowledged=false
        transfer.connected(session.castDevice?.friendlyName ?: "Cast receiver");emit()
        val remote=client ?: run {
            fail(transfer.request(current()?.number ?: -1,""),CastDiagnostics.failure(CastLoadFailure.RECEIVER_REJECTED));return
        }
        remote.registerCallback(callback)
        current()?.let { load(it,reusePlayingMedia=true) }
    }
    override fun load(channel: Channel,reusePlayingMedia: Boolean) {
        if(channel.providerChannel!=null) return // Never export account-bound broadcaster media.
        val remote=client ?: return
        cancelPending();acknowledged=false;adopted=false
        val original=try { CastStreamUrl.resolve(requireNotNull(channel.streamUrl)) }
        catch(_: Exception) { fail(transfer.request(channel.number,""),CastDiagnostics.failure(CastLoadFailure.URL_UNAVAILABLE));return }
        val ticket=transfer.request(channel.number,original)
        loadStartedAt=android.os.SystemClock.elapsedRealtime()
        expectedLoadId="$senderId:$ticket";previousSessionId=remote.sessionId()
        emit()
        if(reusePlayingMedia && remote.isPlaying && (remote.mediaInfo?.contentId==original || remote.mediaInfo?.contentUrl==original)) {
            // A matching existing PLAYING session is actual playback, not a LOAD acknowledgement.
            adopted=true;acknowledged=true;ownedSessionId=remote.sessionId()
            if(transfer.started(ticket,original)) emit()
            return
        }
        armTimeout(ticket);startPolling(ticket,remote)
        val probe=CastMediaResolver { event ->
            Log.i(TAG,"probe channel=${channel.number} ticket=$ticket $event")
        };resolver=probe
        preparation=scope.launch {
            try {
                val source=withContext(Dispatchers.IO) { probe.resolve(original) }
                if(ticket!=transfer.generation || remote!==client) return@launch
                if(!transfer.prepared(ticket,source.url)) return@launch
                val media=mediaInfo(channel,source.url,programme(channel),expectedLoadId,source.mimeType,source.live,source.fragmentedMp4)
                val custom=JSONObject().put("autvLoadId",expectedLoadId)
                Log.i(TAG,"Media load channel=${channel.number} mime=${source.mimeType} ticket=$ticket")
                remote.load(MediaLoadRequestData.Builder().setMediaInfo(media).setAutoplay(true).setCustomData(custom).build())
                    .setResultCallback { result ->
                        if(ticket!=transfer.generation || remote!==client || transfer.state.phase==CastPhase.FAILED) return@setResultCallback
                        Log.i(TAG,"load_result channel=${channel.number} ticket=$ticket code=${result.status.statusCode} accepted=${result.status.isSuccess}")
                        if(result.status.isSuccess) {
                            acknowledged=true
                            if(transfer.accepted(ticket)) emit()
                            observe()
                            runCatching { remote.requestStatus() }
                        } else fail(ticket,rejection(result.status.statusCode,result.mediaError))
                    }
            } catch(error: CancellationException) { throw error }
            catch(error: CastMediaException) { fail(ticket,CastDiagnostics.failure(error.kind,error.status,http=error.status!=null)) }
            catch(_: Exception) { fail(ticket,CastDiagnostics.failure(CastLoadFailure.RECEIVER_REJECTED)) }
        }
    }
    private fun armTimeout(ticket: Long) = watchdog.arm(ticket)
    private fun startPolling(ticket: Long,remote: RemoteMediaClient) {
        if(poll?.isActive==true) return
        poll=scope.launch {
            while(ticket==transfer.generation && remote===client && transfer.state.phase!=CastPhase.FAILED) {
                delay(2000)
                if(acknowledged) runCatching { remote.requestStatus().setResultCallback { if(ticket==transfer.generation && remote===client) observe() } }
            }
        }
    }
    // The public SDK exposes the protocol session ID through MediaStatus.toJson().
    // Read only that number in memory; never log or export the status payload.
    private fun RemoteMediaClient.sessionId(): Long? = mediaStatus?.toJson()?.optLong("mediaSessionId",0)?.takeIf { it>0 }
    private fun matchesExpectedMedia(): Boolean {
        val remote=client ?: return false
        val info=remote.mediaInfo ?: return false
        if(adopted) return (info.contentId==transfer.expectedUrl || info.contentUrl==transfer.expectedUrl) && remote.sessionId()==ownedSessionId
        return CastStatusMatch.matches(transfer.expectedUrl,expectedLoadId,info.contentId,info.contentUrl,
            info.customData?.optString("autvLoadId"),acknowledged,remote.sessionId(),previousSessionId)
    }
    private fun observe() {
        val remote=client ?: return
        if(!matchesExpectedMedia()) {
            Log.i(TAG,"receiver channel=${transfer.state.channelNumber} ticket=${transfer.generation} state=${remote.playerState} matched=false")
            if(transfer.state.ownsPlayback) { armTimeout(transfer.generation);startPolling(transfer.generation,remote) }
            return
        }
        if(transfer.state.phase==CastPhase.FAILED) {
            if(remote.isPlaying || remote.isPaused || remote.isBuffering) runCatching { remote.stop() }
            return
        }
        Log.i(TAG,"receiver channel=${transfer.state.channelNumber} ticket=${transfer.generation} state=${remote.playerState} idle=${remote.idleReason} elapsedMs=${android.os.SystemClock.elapsedRealtime()-loadStartedAt}")
        val status=remote.mediaStatus
        if (transfer.controls(status?.isMediaCommandSupported(MediaStatus.COMMAND_PAUSE) == true,
                status?.isMediaCommandSupported(MediaStatus.COMMAND_SET_VOLUME) == true,
                status?.isMediaCommandSupported(MediaStatus.COMMAND_TOGGLE_MUTE) == true,
                (status?.streamVolume ?: 0.0).toFloat(),status?.isMute == true)) emit()
        when(remote.playerState) {
            MediaStatus.PLAYER_STATE_PLAYING -> {
                watchdog.cancel();poll?.cancel();poll=null
                ownedSessionId=remote.sessionId()
                if(transfer.started(transfer.generation,transfer.expectedUrl)) emit()
            }
            MediaStatus.PLAYER_STATE_PAUSED -> {
                val changed=transfer.started(transfer.generation,transfer.expectedUrl,paused=true)
                if(transfer.confirmed) { watchdog.cancel();poll?.cancel();poll=null }
                if(changed) emit()
            }
            MediaStatus.PLAYER_STATE_BUFFERING -> {
                if(transfer.buffering(transfer.generation,transfer.expectedUrl)) emit()
                armTimeout(transfer.generation);startPolling(transfer.generation,remote)
            }
            MediaStatus.PLAYER_STATE_IDLE -> {
                // CANCELED before new media playback can be a superseded LOAD transition.
                if(remote.idleReason==MediaStatus.IDLE_REASON_ERROR ||
                    (remote.idleReason in setOf(MediaStatus.IDLE_REASON_CANCELED,MediaStatus.IDLE_REASON_FINISHED) && transfer.state.ownsPlayback))
                    fail(transfer.generation,CastDiagnostics.failure(CastLoadFailure.REMOTE_STOPPED,remote.idleReason))
                else { armTimeout(transfer.generation);startPolling(transfer.generation,remote) }
            }
            else -> { armTimeout(transfer.generation);startPolling(transfer.generation,remote) }
        }
    }
    private fun fail(ticket: Long,reason: String) {
        if(!transfer.failed(ticket,reason)) return
        cancelPending()
        val remote=client
        // Do not stop unrelated media selected by a different sender/receiver user.
        if(remote!=null && (remote.mediaInfo==null || matchesExpectedMedia() || (ownedSessionId!=null && remote.sessionId()==ownedSessionId)))
            runCatching { remote?.stop() }
        Log.w(TAG,"Cast media failed channel=${transfer.state.channelNumber} ticket=$ticket: $reason")
        emit()
    }
    private fun rejection(status: Int?,error: MediaError?): String {
        val kind=when {
            error?.reason==MediaError.ERROR_REASON_NOT_SUPPORTED -> CastLoadFailure.UNSUPPORTED_FORMAT
            error?.reason==MediaError.ERROR_REASON_AUTHENTICATION_EXPIRED || status in setOf(CastStatusCodes.ERROR_URL_INSEURE,CastStatusCodes.ERROR_HOST_NOT_ALLOWED) -> CastLoadFailure.URL_UNAVAILABLE
            status==CastStatusCodes.TIMEOUT -> CastLoadFailure.TIMEOUT
            else -> CastLoadFailure.RECEIVER_REJECTED
        }
        return CastDiagnostics.failure(kind,status,error?.detailedErrorCode,reason=error?.reason)
    }
    private fun emit() {
        Log.i(TAG,"state channel=${transfer.state.channelNumber} ticket=${transfer.generation} phase=${transfer.state.phase} receiver=cast elapsedMs=${if(loadStartedAt>0) android.os.SystemClock.elapsedRealtime()-loadStartedAt else 0}")
        changed(transfer.state)
    }
    private fun detach(code: Int?=null) {
        cancelPending();client?.unregisterCallback(callback);client=null
        expectedLoadId=null;ownedSessionId=null;acknowledged=false
        transfer.disconnected(code?.let { "Cast disconnected (code $it). Playing on this device." });emit()
    }
    override fun togglePause() {
        val remote=client ?: return
        if (!transfer.state.ownsPlayback || !transfer.state.canPause) return
        runCatching { if(remote.isPaused) remote.play() else if(remote.isPlaying) remote.pause() }
    }
    override fun toggleMute() {
        val remote=client ?: return
        if(transfer.state.ownsPlayback && transfer.state.canMute) runCatching { remote.setStreamMute(remote.mediaStatus?.isMute != true) }
    }
    override fun setVolume(value: Float) {
        if(value.isFinite() && transfer.state.ownsPlayback && transfer.state.canVolume)
            runCatching { client?.setStreamVolume(value.coerceIn(0f,1f).toDouble()) }
    }
    override fun stopCasting() {
        runCatching { manager?.endCurrentSession(true) }
        detach()
    }
    override fun release() {
        cancelPending();client?.unregisterCallback(callback)
        manager?.removeSessionManagerListener(sessions,CastSession::class.java)
        client=null;manager=null
    }
    companion object {
        const val TAG="AUTV.Cast"
        internal fun mediaInfo(channel: Channel,url: String,programme: Programme?,loadId: String?=null,
            mimeType: String=CastMediaResolver.fromAddress(url).mimeType,live: Boolean=mimeType in setOf("application/x-mpegURL","application/dash+xml"),fragmentedMp4: Boolean=false): MediaInfo {
            require(channel.providerChannel==null) { "Account-bound provider media cannot be cast" }
            val metadata=MediaMetadata(MediaMetadata.MEDIA_TYPE_TV_SHOW).apply {
                putString(MediaMetadata.KEY_TITLE,"${channel.number} · ${channel.name}")
                putString(MediaMetadata.KEY_SUBTITLE,programme?.title ?: channel.genre)
                putString(MediaMetadata.KEY_SERIES_TITLE,channel.name)
                channel.logoUrl?.takeIf { it.startsWith("https://") || it.startsWith("http://") }?.let { runCatching { addImage(WebImage(Uri.parse(it))) } }
            }
            return MediaInfo.Builder(url).setContentUrl(url).setContentType(mimeType)
                .apply { if(fragmentedMp4) { setHlsSegmentFormat("fmp4");setHlsVideoSegmentFormat("fmp4") } }
                .setStreamType(if(live) MediaInfo.STREAM_TYPE_LIVE else MediaInfo.STREAM_TYPE_BUFFERED).setMetadata(metadata)
                .setCustomData(JSONObject().put("channelNumber",channel.number).put("channelId",channel.tvgId ?: channel.number.toString())
                    .apply { if(loadId!=null) put("autvLoadId",loadId) }).build()
        }
    }
}
