package dev.prestwich.autv

import dev.prestwich.autv.data.*
import dev.prestwich.autv.data.provider.*
import dev.prestwich.autv.cast.CastConnection
import dev.prestwich.autv.guide.CastPlayback

/** Uses local HLS assets and an in-memory adapter; never a Nine account or network endpoint. */
class ProviderTestActivity : MainActivity() {
    val adapter = FixtureProviderAdapter()
    override fun createPlaybackModel(): PlaybackModel = PlaybackModel(application, {
        val fast = Channel(100, "Fixture FAST", "News", "", "", "", true,
            "asset:///pip-test.m3u8?channel=100", "fixture-fast", null)
        Guide(1, 1, listOf(fast))
    }, { Epg(emptyMap()) }, { _ ->
        object : CastConnection {
            override val connected = false
            override fun start() = Unit
            override fun load(channel: Channel, reusePlayingMedia: Boolean) = Unit
            override fun release() = Unit
        }
    }, providerAdapter = adapter).also { it.player.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL }
}
class FixtureProviderAdapter : ProviderAdapter {
    override var connection = ProviderConnection.CONNECTED
    var denied = false
    override fun beginSignIn() = DeviceActivation("FIXTURE", "fixture-device", "https://www.9now.com.au/activate",
        System.currentTimeMillis() + 60_000, 1000)
    override fun pollSignIn(activation: DeviceActivation) = true
    override fun channels() = listOf(ProviderChannel(NineNowAdapter.SEINFELD, "fixture-reference", "Seinfeld 24/7"))
    override fun resolve(channel: ProviderChannelId): ProviderPlaybackRequest {
        if (denied) throw ProviderException(ProviderFailure.REGION_DENIED)
        return ProviderPlaybackRequest(channel, "asset:///pip-test.m3u8?channel=provider", "application/x-mpegURL")
    }
    override fun signOut() { connection = ProviderConnection.NOT_CONNECTED }
}
