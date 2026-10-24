package dev.prestwich.autv

import dev.prestwich.autv.data.*
import dev.prestwich.autv.data.provider.*
import dev.prestwich.autv.cast.CastConnection
import org.json.JSONObject

/** Runs the actual Seven resolver against fixtures, then substitutes local test media.
 * Never evidence of a real Seven manifest, entitlement, ads, licence or video/audio. */
class SevenPlaybackTestActivity : MainActivity() {
    var denied = false
    var brokenMedia = false
    var resolutions = 0
    private val store = object : ProviderSessionStore {
        var value: ProviderSession? = ProviderSession("fixture-access", "fixture-refresh", Long.MAX_VALUE)
        override fun read() = value
        override fun write(session: ProviderSession) { value = session }
        override fun clear() { value = null }
    }
    private val realAdapter = SevenPlusAdapter(object : SevenPlusTransport {
        override fun request(method: String, url: String, form: Map<String, String>): JSONObject = when {
            url == SevenPlusAdapter.MARKET -> JSONObject("""{"_id":"fixture-market"}""")
            url.startsWith(SevenPlusAdapter.CONTENT) -> JSONObject("""{"items":[{"childType":"channelItem","mediaItems":[{"type":"ChannelItem","channelId":"official-main-fixture","name":"Seven","contentLink":{"url":"/live-tv?channel-id=MAIN_FIXTURE"}}]}]}""")
            else -> JSONObject("""{"videoPlayer":{"videoUrl":"https://metadata.invalid/reference?accountId=account-fixture&referenceId=ref%3Amain-fixture&unused=1&unused=2&flag&"}}""")
        }
        override fun playback(parameters: Map<String, String>, accessToken: String): JSONObject {
            resolutions++
            if (denied) throw ProviderException(ProviderFailure.REGION_DENIED)
            return JSONObject("""{"media":{"sources":[{"type":"application/x-mpegURL","src":"https://csm-fixture.yospace.com/master.m3u8?ssai=fixture"}]}}""")
        }
    }, store, MemoryProviderActivationStore(), "fixture-device")
    private val localAdapter = object : ProviderAdapter by realAdapter {
        override fun resolve(channel: ProviderChannelId): ProviderPlaybackRequest {
            val request = realAdapter.resolve(channel)
            check(request.headers.isEmpty())
            return ProviderPlaybackRequest(request.channelId, if (brokenMedia) "asset:///missing-seven-fixture.m3u8" else "asset:///seven-ssai-fixture.m3u8?channel=seven-proof-fixture", request.mimeType)
        }
    }
    override fun createPlaybackModel() = PlaybackModel(application, {
        Guide(1, 1, listOf(Channel(100, "Fixture FAST", "News", "", "", "", true,
            "asset:///pip-test.m3u8?channel=100", "fixture-fast", null)))
    }, { Epg(emptyMap()) }, { _ -> object : CastConnection {
        override val connected = false
        override fun start() = Unit
        override fun load(channel: Channel, reusePlayingMedia: Boolean) = Unit
        override fun release() = Unit
    } }, providerAdapter = FixtureProviderAdapter(), sbsAdapter = FixtureSbsAdapter(), sevenAdapter = localAdapter)
        .also { it.player.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL }
}
