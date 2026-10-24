package dev.prestwich.autv

import dev.prestwich.autv.data.*
import dev.prestwich.autv.data.provider.*
import dev.prestwich.autv.cast.CastConnection

/** Account and catalogue fixtures only. No live 7plus playback or network calls. */
class SevenTestActivity : MainActivity() {
    var approved = false
    val seven = SevenPlusAdapter(SevenPlusTransport { _, url, _ ->
        org.json.JSONObject(when {
            url.endsWith("/authorize") -> """{"user_code":"SEVEN","device_code":"private-seven-fixture","verification_uri_complete":"https://7plus.com.au/connect?code=SEVEN","expires_in":120,"interval":1}"""
            url.endsWith("/token") -> if (approved) """{"access_token":"fixture-access","refresh_token":"fixture-refresh","expires_in":3600}""" else """{"error":"authorization_pending"}"""
            url == SevenPlusAdapter.MARKET -> """{"_id":"fixture-market"}"""
            else -> """{"items":[{"childType":"channelItem","mediaItems":[{"type":"ChannelItem","channelId":"fixture-seven","name":"Seven account fixture"}]}]}"""
        })
    }, object : ProviderSessionStore {
        var value: ProviderSession? = null
        override fun read() = value
        override fun write(session: ProviderSession) { value = session }
        override fun clear() { value = null }
    }, MemoryProviderActivationStore(), "fixture-seven-device")
    override fun createPlaybackModel() = PlaybackModel(application, {
        Guide(1, 1, listOf(Channel(100, "Fixture FAST", "News", "", "", "", true,
            "asset:///pip-test.m3u8?channel=100", "fixture-fast", null)))
    }, { Epg(emptyMap()) }, { _ -> object : CastConnection {
        override val connected = false
        override fun start() = Unit
        override fun load(channel: Channel, reusePlayingMedia: Boolean) = Unit
        override fun release() = Unit
    } }, providerAdapter = FixtureProviderAdapter(), sbsAdapter = FixtureSbsAdapter(), sevenAdapter = seven)
}
