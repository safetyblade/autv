package dev.prestwich.autv

import dev.prestwich.autv.data.*
import dev.prestwich.autv.data.provider.*
import dev.prestwich.autv.cast.CastConnection
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Memory stands in for encrypted persistence; no Nine endpoints or real account records. */
object ActivationFixture {
    val challenges = MemoryProviderActivationStore()
    @Volatile private var saved: ProviderSession? = null
    val sessions = object : ProviderSessionStore {
        override fun read() = saved
        override fun write(session: ProviderSession) { saved = session }
        override fun clear() { saved = null }
    }
    val codeRequests = AtomicInteger()
    val polls = AtomicInteger()
    val approved = AtomicBoolean()
    val transientFailure = AtomicBoolean(true)
    fun reset() { challenges.clearActivation(); sessions.clear(); codeRequests.set(0); polls.set(0); approved.set(false); transientFailure.set(true) }
    val transport = NineNowTransport { _, url, _ ->
        when {
            url.contains("/code?") -> {
                codeRequests.incrementAndGet()
                JSONObject("""{"auth_code":"FIXTURE","device_code":"fixture-device","expires_in":120,"interval":1}""")
            }
            url.contains("/token?") -> {
                polls.incrementAndGet()
                if (transientFailure.getAndSet(false)) throw ProviderException(ProviderFailure.OFFLINE)
                if (approved.get()) JSONObject("""{"accessToken":"fixture-access","refresh_token":"fixture-refresh","expiresIn":3600}""")
                else JSONObject("""{"error":{"code":"authorization_pending"}}""")
            }
            else -> JSONObject("""{"data":{"getLivestream":{"channels":[]}}}""")
        }
    }
}
class ProviderActivationTestActivity : MainActivity() {
    override fun createPlaybackModel() = PlaybackModel(application, {
        val channel = Channel(100, "Fixture FAST", "News", "", "", "", true,
            "asset:///pip-test.m3u8?channel=100", "fixture-fast", null)
        Guide(1, 1, listOf(channel))
    }, { Epg(emptyMap()) }, { _ ->
        object : CastConnection {
            override val connected = false
            override fun start() = Unit
            override fun load(channel: Channel, reusePlayingMedia: Boolean) = Unit
            override fun release() = Unit
        }
    }, providerAdapter = NineNowAdapter(ActivationFixture.transport, ActivationFixture.sessions,
        activationStore = ActivationFixture.challenges))
}
