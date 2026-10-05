package dev.prestwich.autv

import dev.prestwich.autv.data.*

/** Non-exported, debug-only activity: the real lifecycle/player with deterministic HLS data. */
class PlatformTestActivity : MainActivity() {
    var allowPip = false
    override fun automaticPipAllowed() = allowPip
    override fun createPlaybackModel(): PlaybackModel = PlaybackModel(application, {
        fun channel(number: Int, name: String, genre: String, url: String?) = Channel(number, name, genre, "", "", "Full", url != null, url, "fixture-$number", null)
        val channels = listOf(channel(100, "Fixture News", "News", "asset:///pip-test.m3u8"),
            channel(419, "Fixture Ink Master", "Game Shows", "asset:///pip-test.m3u8"), channel(500, "Guide only", "Crime", null))
        Guide(channels.size, 2, channels)
    }, { Epg(emptyMap()) }).also { it.player.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL }
}
