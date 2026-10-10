package dev.prestwich.autv.guide

import dev.prestwich.autv.data.Channel

/** One-shot rollback identity only; all playback stays in the existing model tune path. */
class GuideTuneRecovery {
    private var previous: Channel? = null
    private var requested: Int? = null
    fun begin(current: Channel?, target: Channel) {
        val retained = previous ?: current
        previous = retained?.takeIf { it.number != target.number && !it.streamUrl.isNullOrBlank() }
        requested = target.number
    }
    fun clear() { previous = null; requested = null }
    fun ready(number: Int?) { if (number == requested) clear() }
    fun failed(number: Int?): Channel? {
        if (number != requested) return null
        val retained = previous
        clear() // A failed recovery must never create an infinite retuning loop.
        return retained
    }
}
