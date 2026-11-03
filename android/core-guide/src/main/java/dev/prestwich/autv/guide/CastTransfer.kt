package dev.prestwich.autv.guide

enum class CastPhase { DISCONNECTED, CONNECTING, LOADING, BUFFERING, PLAYING, PAUSED, FAILED }
data class CastPlayback(val phase: CastPhase = CastPhase.DISCONNECTED, val receiver: String = "Cast receiver",
    val channelNumber: Int? = null, val error: String? = null, val ownsPlayback: Boolean = false,
    val canPause: Boolean = false, val canVolume: Boolean = false, val canMute: Boolean = false,
    val volume: Float = 0f, val muted: Boolean = false)

/** Transfer bookkeeping only. Channel selection remains owned by PlaybackModel. */
class CastTransfer {
    var state = CastPlayback(); private set
    var generation = 0L; private set
    var expectedUrl: String? = null; private set
    private var confirmedGeneration: Long? = null
    val confirmed get()=confirmedGeneration==generation
    fun connecting(receiver: String) {
        generation++; expectedUrl = null
        state = CastPlayback(CastPhase.CONNECTING, receiver)
    }
    fun controls(canPause: Boolean, canVolume: Boolean, canMute: Boolean, volume: Float, muted: Boolean): Boolean {
        val next = state.copy(canPause=canPause, canVolume=canVolume, canMute=canMute,
            volume=if(volume.isFinite()) volume.coerceIn(0f,1f) else 0f, muted=muted)
        if (next == state) return false
        state=next
        return true
    }
    fun connected(receiver: String) = connecting(receiver)
    fun request(number: Int, url: String): Long {
        generation++; expectedUrl = url
        state = state.copy(phase = CastPhase.LOADING, channelNumber = number, error = null)
        return generation
    }
    fun prepared(ticket: Long,url: String): Boolean {
        if(ticket!=generation || state.phase!=CastPhase.LOADING) return false
        expectedUrl=url
        return true
    }
    fun accepted(ticket: Long): Boolean {
        if (ticket != generation || state.phase != CastPhase.LOADING) return false
        state = state.copy(phase = CastPhase.LOADING)
        return true
    }
    fun started(ticket: Long, url: String?, paused: Boolean = false): Boolean {
        if (ticket != generation || url != expectedUrl || state.phase in listOf(CastPhase.DISCONNECTED, CastPhase.FAILED)) return false
        if(paused && confirmedGeneration!=ticket) return false
        confirmedGeneration=ticket
        val phase = if (paused) CastPhase.PAUSED else CastPhase.PLAYING
        if (state.phase == phase && state.ownsPlayback) return false
        state = state.copy(phase = phase, ownsPlayback = true, error = null)
        return true
    }
    fun buffering(ticket: Long,url: String?): Boolean {
        if(ticket!=generation || url!=expectedUrl || state.phase in listOf(CastPhase.DISCONNECTED,CastPhase.FAILED,CastPhase.BUFFERING)) return false
        state=state.copy(phase=CastPhase.BUFFERING)
        return true
    }
    fun failed(ticket: Long, message: String): Boolean {
        if (ticket != generation || state.phase in listOf(CastPhase.DISCONNECTED, CastPhase.FAILED)) return false
        state = state.copy(phase = CastPhase.FAILED, ownsPlayback = false, error = message)
        return true
    }
    fun disconnected(message: String? = null) {
        generation++; expectedUrl = null
        state = CastPlayback(error=message)
    }
}
