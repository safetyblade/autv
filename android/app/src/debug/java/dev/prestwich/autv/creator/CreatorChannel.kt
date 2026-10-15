package dev.prestwich.autv.creator

/** Native-only identities. Never serialize these into the public M3U or numbered FAST guide. */
internal sealed interface PlaybackSource {
    data class Hls(val url: String) : PlaybackSource
    data class Twitch(val login: String) : PlaybackSource
    data class YouTube(val channelId: String) : PlaybackSource
}

internal data class CreatorChannel(val id: String, val name: String, val source: PlaybackSource)
internal enum class LiveStatus { LIVE, OFFLINE, UNAVAILABLE, UNKNOWN }
internal data class Availability(
    val status: LiveStatus = LiveStatus.UNKNOWN,
    val message: String = "Live status has not been checked",
    val videoId: String? = null,
)

internal object CreatorCatalogue {
    val gdq = CreatorChannel("twitch:gamesdonequick", "Games Done Quick", PlaybackSource.Twitch("gamesdonequick"))
    // Official WWE channel: live pre-shows/events are intermittent, never represented as permanent HLS.
    val wwe = CreatorChannel("youtube:UCJ5v_MCY6GNUBTO8-D3XoAg", "WWE live events",
        PlaybackSource.YouTube("UCJ5v_MCY6GNUBTO8-D3XoAg"))
    val channels = listOf(gdq, wwe)
}
