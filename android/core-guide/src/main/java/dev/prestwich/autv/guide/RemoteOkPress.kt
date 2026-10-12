package dev.prestwich.autv.guide

/** One action per physical press, including remotes with repeats, flags or no repeats. */
class RemoteOkPress {
    enum class Action { NONE, SHORT, LONG }
    private var started: Long? = null
    private var consumed = false
    fun down(time: Long, repeated: Boolean, longFlag: Boolean, timeout: Long): Action {
        if (started == null) {
            if (repeated) return Action.NONE // Ignore an orphan repeat after focus/window loss.
            started = time
            consumed = false
        }
        return if (longFlag || time - started!! >= timeout) hold() else Action.NONE
    }
    fun hold(): Action {
        if (started == null || consumed) return Action.NONE
        consumed = true
        return Action.LONG
    }
    fun up(time: Long, timeout: Long, cancelled: Boolean): Action {
        val start = started ?: return Action.NONE
        val result = when {
            cancelled || consumed -> Action.NONE
            time - start >= timeout -> Action.LONG
            else -> Action.SHORT
        }
        cancel()
        return result
    }
    fun cancel() { started = null; consumed = false }
}
