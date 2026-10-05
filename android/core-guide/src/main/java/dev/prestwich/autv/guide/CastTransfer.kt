package dev.prestwich.autv.guide

enum class CastPhase { DISCONNECTED, CONNECTED, LOAD_REQUESTED, LOAD_ACCEPTED, PLAYING, PAUSED, FAILED }
data class CastPlayback(val phase: CastPhase = CastPhase.DISCONNECTED, val receiver: String = "Cast receiver",
    val channelNumber: Int? = null, val error: String? = null, val ownsPlayback: Boolean = false)

/** Transfer bookkeeping only. Channel selection remains owned by PlaybackModel. */
class CastTransfer {
    var state = CastPlayback(); private set
    var generation = 0L; private set
    var expectedUrl: String? = null; private set
    fun connected(receiver: String) {
        generation++; expectedUrl = null
        state = CastPlayback(CastPhase.CONNECTED, receiver)
    }
    fun request(number: Int, url: String): Long {
        generation++; expectedUrl = url
        state = state.copy(phase = CastPhase.LOAD_REQUESTED, channelNumber = number, error = null)
        return generation
    }
    fun accepted(ticket: Long): Boolean {
        if (ticket != generation || state.phase != CastPhase.LOAD_REQUESTED) return false
        state = state.copy(phase = CastPhase.LOAD_ACCEPTED)
        return true
    }
    fun started(ticket: Long, url: String?, paused: Boolean = false): Boolean {
        if (ticket != generation || url != expectedUrl || state.phase in listOf(CastPhase.DISCONNECTED, CastPhase.FAILED)) return false
        val phase = if (paused) CastPhase.PAUSED else CastPhase.PLAYING
        if (state.phase == phase && state.ownsPlayback) return false
        state = state.copy(phase = phase, ownsPlayback = true, error = null)
        return true
    }
    fun failed(ticket: Long, message: String): Boolean {
        if (ticket != generation || state.phase in listOf(CastPhase.DISCONNECTED, CastPhase.FAILED)) return false
        state = state.copy(phase = CastPhase.FAILED, ownsPlayback = false, error = message)
        return true
    }
    fun disconnected() {
        generation++; expectedUrl = null
        state = CastPlayback()
    }
}
