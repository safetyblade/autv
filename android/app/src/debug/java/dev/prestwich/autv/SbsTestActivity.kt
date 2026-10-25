package dev.prestwich.autv

import dev.prestwich.autv.data.*
import dev.prestwich.autv.data.provider.*
import dev.prestwich.autv.cast.CastConnection

/** Both accounts and all media are fixtures; this activity never calls a broadcaster. */
class SbsTestActivity : MainActivity() {
    val nine = FixtureProviderAdapter()
    val sbs = FixtureSbsAdapter()
    override fun createPlaybackModel() = PlaybackModel(application, {
        val fast = Channel(100, "Fixture FAST", "News", "", "", "", true,
            "asset:///pip-test.m3u8?channel=100", "fixture-fast", null)
        Guide(1, 1, listOf(fast))
    }, { Epg(emptyMap()) }, { _ -> object : CastConnection {
        override val connected = false
        override fun start() = Unit
        override fun load(channel: Channel, reusePlayingMedia: Boolean) = Unit
        override fun release() = Unit
    } }, providerAdapter = nine, sbsAdapter = sbs).also { it.player.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL }
}
class FixtureSbsAdapter : ProviderAdapter {
    override var connection = ProviderConnection.NOT_CONNECTED
    var approved = false
    var blocked = false
    val entered = java.util.concurrent.CountDownLatch(1)
    val release = java.util.concurrent.CountDownLatch(1)
    override fun beginSignIn(): DeviceActivation {
        connection = ProviderConnection.CONNECTING
        return DeviceActivation("1234", "private-sbs-fixture", SbsAdapter.ACTIVATION, System.currentTimeMillis() + 120000, 1000)
    }
    override fun pollSignIn(activation: DeviceActivation): Boolean {
        if (approved) connection = ProviderConnection.CONNECTED
        return approved
    }
    override fun channels() = listOf(
        ProviderChannel(ProviderChannelId("sbs", "fixture-sbs"), "fixture-sbs", "SBS NSW fixture"),
        ProviderChannel(ProviderChannelId("sbs", "fixture-viceland"), "fixture-viceland", "SBS VICELAND fixture"))
    override fun resolve(channel: ProviderChannelId): ProviderPlaybackRequest {
        if (blocked) { entered.countDown(); release.await(10, java.util.concurrent.TimeUnit.SECONDS) }
        return ProviderPlaybackRequest(channel, "asset:///pip-test.m3u8?channel=sbs-fixture", "application/x-mpegURL")
    }
    override fun signOut() { connection = ProviderConnection.NOT_CONNECTED }
}
