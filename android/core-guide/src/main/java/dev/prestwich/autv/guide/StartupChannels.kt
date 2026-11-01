package dev.prestwich.autv.guide

import dev.prestwich.autv.data.Channel

enum class StartupMode(val label: String) {
    LAST_WATCHED("Last watched"), FAVOURITE("Favourite #1"), AUSTRALIAN("Australian TV"),
    NEWS("Preferred news channel"), SPORTS("Preferred sports channel")
}

/** Candidate order only: playback must confirm success; a URL is never a health check. */
object StartupChannels {
    fun choices(rows: List<Channel>, mode: StartupMode): List<Channel> = rows.filter {
        it.tunable && FavouriteIdentity.key(it) != null && when(mode) {
            StartupMode.AUSTRALIAN -> it.providerChannel != null || it.genre == "Australian TV"
            StartupMode.NEWS -> it.genre == "News"
            StartupMode.SPORTS -> it.genre == "Sport"
            else -> false
        }
    }.let { if (mode == StartupMode.AUSTRALIAN) AustralianChannelOrder.order(it) else it }

    fun candidates(rows: List<Channel>, mode: StartupMode, preferred: String?, recent: String?,
        legacyNumber: Int?, favourites: Set<String>): List<Channel> {
        val last = if (recent != null) rows.firstOrNull { FavouriteIdentity.key(it) == recent }
            else legacyNumber?.let { number -> rows.firstOrNull { it.number == number } }
        // Favourites have always been displayed in catalogue order, not SharedPreferences set order.
        val favourite = rows.sortedBy { it.number }.firstOrNull { FavouriteIdentity.contains(it, favourites) }
        val choices = choices(rows, mode)
        val chosen = when(mode) {
            StartupMode.LAST_WATCHED -> last
            StartupMode.FAVOURITE -> favourite
            else -> choices.firstOrNull { FavouriteIdentity.key(it) == preferred } ?: choices.firstOrNull()
        }
        val leadStory = rows.firstOrNull { it.tvgId == "autv.110-leadstory-breaking-news" }
        return (listOfNotNull(chosen, favourite, leadStory) + rows.sortedBy { it.number }).filter { it.tunable }
            .distinctBy { FavouriteIdentity.key(it) ?: "number:${it.number}" }
    }
}
