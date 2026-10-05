package dev.prestwich.autv

import dev.prestwich.autv.data.*
import dev.prestwich.autv.cast.CastConnection
import dev.prestwich.autv.guide.*

/** Non-exported, debug-only activity: the real lifecycle/player with deterministic HLS data. */
open class PlatformTestActivity : MainActivity() {
    var allowPip = false
    lateinit var castProbe: DebugCastConnection; private set
    override fun automaticPipAllowed() = allowPip
    override fun createPlaybackModel(): PlaybackModel = PlaybackModel(application, {
        fun channel(number: Int, name: String, genre: String, url: String?) = Channel(number, name, genre, "", "", "Full", url != null, url, "fixture-$number", null)
        val channels = listOf(channel(100, "Fixture News", "News", "asset:///pip-test.m3u8?channel=100"),
            channel(419, "Fixture Ink Master", "Game Shows", "asset:///pip-test.m3u8?channel=419"), channel(500, "Guide only", "Crime", null)) +
            if (this is TvInteractionTestActivity) List(12) { index ->
                val number = 1200 + index
                channel(number, "Fixture News $number", "News", "asset:///pip-test.m3u8?channel=$number")
            } else emptyList()
        Guide(channels.size, channels.count { it.streamUrl != null }, channels)
    }, { Epg(emptyMap()) }, { changed -> DebugCastConnection(changed).also { castProbe = it } })
        .also { it.player.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL }
}

/** Same production activity/player, with TV configuration for actual remote-key tests. */
class TvInteractionTestActivity : PlatformTestActivity() {
    override fun attachBaseContext(base: android.content.Context) {
        val config = android.content.res.Configuration(base.resources.configuration)
        config.uiMode = (config.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK.inv()) or
            android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
        super.attachBaseContext(base.createConfigurationContext(config))
    }
}

/** Deterministic transport events, not a claim of real receiver compatibility. Debug builds only. */
class DebugCastConnection(private val changed: (CastPlayback) -> Unit) : CastConnection {
    private val transfer = CastTransfer()
    override var connected = false; private set
    val loads = mutableListOf<Channel>()
    override fun start() = Unit
    fun connect(channel: Channel) { connected = true; transfer.connected("Fixture receiver"); changed(transfer.state); load(channel) }
    override fun load(channel: Channel, reusePlayingMedia: Boolean) {
        loads.add(channel); transfer.request(channel.number, channel.streamUrl!!); changed(transfer.state)
    }
    fun accept() { transfer.accepted(transfer.generation); changed(transfer.state) }
    fun playing() { transfer.started(transfer.generation, transfer.expectedUrl); changed(transfer.state) }
    fun fail() { transfer.failed(transfer.generation, "Receiver test load rejected (2100)"); changed(transfer.state) }
    fun disconnect() { connected = false; transfer.disconnected(); changed(transfer.state) }
    override fun release() = Unit
}
