package dev.prestwich.autv.guide

import dev.prestwich.autv.data.Channel

class GuideNavigator(channels: List<Channel>) {
    private val ordered = channels.sortedBy { it.number }

    fun next(current: Channel?): Channel? = seek(current, 1)
    fun previous(current: Channel?): Channel? = seek(current, -1)
    fun byNumber(number: Int): Channel? = ordered.firstOrNull { it.number == number }
    fun genres(): List<String> = ordered.map { it.genre }.distinct()
    fun inGenre(genre: String): List<Channel> = ordered.filter { it.genre == genre }

    private fun seek(current: Channel?, direction: Int): Channel? {
        if (ordered.isEmpty()) return null
        val start = current?.let { c -> ordered.indexOfFirst { it.number == c.number } } ?: -1
        for (offset in 1..ordered.size) {
            val index = if (direction > 0) (start + offset + ordered.size) % ordered.size
                        else (start - offset + ordered.size * 2) % ordered.size
            val candidate = ordered[index]
            if (candidate.available) return candidate
        }
        return current
    }
}
