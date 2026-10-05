package dev.prestwich.autv.guide

import dev.prestwich.autv.data.Channel
import dev.prestwich.autv.data.Epg

object GuideBrowse {
    val categories = listOf("All", "News", "Sport", "Movies", "Game Shows", "Crime", "Comedy", "Entertainment", "Reality & Lifestyle", "Factual", "Kids & Animation", "Music")

    fun filter(channels: List<Channel>, category: String, query: String, epg: Epg, time: Long): List<Channel> {
        val search = query.trim()
        return channels.filter { channel ->
            (category == "All" || channel.genre == category) &&
                (search.isEmpty() || channel.number.toString().contains(search) || channel.name.contains(search, ignoreCase = true) ||
                    epg.at(channel, time).now?.title?.contains(search, ignoreCase = true) == true)
        }.sortedBy { it.number }
    }

    fun categoryForPlaying(current: String, channel: Channel?): String =
        if (current == "All" || channel == null || channel.genre == current) current
        else channel.genre.takeIf { it in categories } ?: "All"
}
