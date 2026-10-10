package dev.prestwich.autv.guide

import dev.prestwich.autv.data.Programme

/** Pure remote/timeline rules. Browsing produces no playback side effects. */
object ProgrammeNavigation {
    const val HALF_HOUR = 30 * 60_000L
    const val WINDOW = 3 * 60 * 60_000L

    fun airing(programme: Programme, now: Long) = now >= programme.start && now < programme.stop
    fun at(schedule: List<Programme>, time: Long): Programme? =
        schedule.firstOrNull { time >= it.start && time < it.stop }
            ?: schedule.firstOrNull { it.start >= time }
    fun move(schedule: List<Programme>, selected: Programme?, direction: Int): Programme? {
        if (schedule.isEmpty()) return null
        val index = schedule.indexOfFirst { it.start == selected?.start && it.stop == selected?.stop }
        return schedule[(if (index < 0) 0 else index + direction).coerceIn(0, schedule.lastIndex)]
    }
    fun windowStart(time: Long) = Math.floorDiv(time, HALF_HOUR) * HALF_HOUR
    fun fraction(time: Long, start: Long, duration: Long = WINDOW): Float =
        ((time - start).toDouble() / duration).toFloat().coerceIn(0f, 1f)
    fun block(programme: Programme, start: Long, duration: Long = WINDOW): Pair<Float, Float>? {
        if (programme.stop <= programme.start || programme.stop <= start || programme.start >= start + duration) return null
        val left = fraction(programme.start, start, duration)
        return left to (fraction(programme.stop, start, duration) - left)
    }
}
