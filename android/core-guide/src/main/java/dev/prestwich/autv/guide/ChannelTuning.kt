package dev.prestwich.autv.guide

import dev.prestwich.autv.data.Channel

sealed interface TuneTarget {
    data class Number(val value: Int) : TuneTarget
    data class Id(val value: String) : TuneTarget
}

/** One identity resolver for guide, direct entry, home cards and the isolated input probe. */
object ChannelTuning {
    fun resolve(channels: List<Channel>, target: TuneTarget): Channel? = when (target) {
        is TuneTarget.Number -> channels.singleOrNull { it.number == target.value }
        is TuneTarget.Id -> channels.filter { it.tvgId == target.value }.singleOrNull()
    }?.takeIf { !it.streamUrl.isNullOrBlank() }

    /** One recent item and a small genre-balanced selection; no hundred-item home rows. */
    fun featured(channels: List<Channel>, recent: Int? = null, limit: Int = 8): List<Channel> {
        val playable = channels.filter { !it.streamUrl.isNullOrBlank() }.sortedBy { it.number }
        return (listOfNotNull(playable.firstOrNull { it.number == recent }) +
            GuideBrowse.categories.drop(1).mapNotNull { genre -> playable.firstOrNull { it.genre == genre } })
            .distinctBy { it.number }.take(limit.coerceAtLeast(0))
    }
}
