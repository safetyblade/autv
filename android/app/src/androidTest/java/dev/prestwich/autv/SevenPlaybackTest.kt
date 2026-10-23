package dev.prestwich.autv

import android.os.SystemClock
import androidx.test.ext.junit.rules.ActivityScenarioRule
import dev.prestwich.autv.data.provider.ProviderConnection
import dev.prestwich.autv.guide.TuneTarget
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Local media substitution after the real fixture resolver; no live Seven playback claim. */
class SevenPlaybackTest {
    @get:Rule val activity = ActivityScenarioRule(SevenPlaybackTestActivity::class.java)
    private fun <T> main(block: (SevenPlaybackTestActivity) -> T): T {
        var value: Result<T>? = null
        activity.scenario.onActivity { value = runCatching { block(it) } }
        return value!!.getOrThrow()
    }
    private fun await(check: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 30000
        while (SystemClock.uptimeMillis() < deadline) { if (check()) return; SystemClock.sleep(100) }
        fail("Seven fixture state did not arrive")
    }
    private fun ready(): Int {
        await { main { it.playback.guide?.channels?.any { row -> row.providerChannel?.id?.provider == "seven" } == true && it.playback.playing } }
        return main { it.playback.player.volume = 0f; it.playback.guide!!.channels.first { row -> row.providerChannel?.id?.provider == "seven" }.number }
    }
    @Test fun originalResolverFeedsRetainedPlayerAndLocalSignOutRestoresFast() {
        val number = ready(); val player = main { it.playback.player }
        main { it.playback.request(TuneTarget.Number(number)) }
        await { main { player.isPlaying && player.currentMediaItem?.mediaId == number.toString() } }
        main {
            assertSame(player, it.playback.player)
            assertTrue(it.resolutions > 0)
            assertNull(it.playback.selected!!.streamUrl)
            it.playback.pause(); assertFalse(player.playWhenReady); it.playback.resume()
        }
        // Cross an 8-second synthetic discontinuity without trimming or skipping it.
        await { main { player.isPlaying && player.currentPosition > 8500 } }
        main {
            val counters = (player as androidx.media3.exoplayer.ExoPlayer).videoDecoderCounters
            assertNotNull(counters)
            counters!!.ensureUpdated()
            assertTrue(counters.renderedOutputBufferCount > 0)
        }
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        await { main { it.hasWindowFocus() } }
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_GUIDE)
        await { main { it.playback.guideOpen } }
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        await { main { !it.playback.guideOpen } }
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_CHANNEL_UP)
        await { main { it.playback.player.isPlaying && it.playback.selected?.providerChannel?.id?.provider == "9now" } }
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_CHANNEL_DOWN)
        await { main { it.playback.player.isPlaying && it.playback.player.currentMediaItem?.mediaId == number.toString() } }
        main { assertTrue(it.resolutions >= 2) }
        activity.scenario.recreate()
        await { main { it.playback.player.isPlaying } }
        main { assertSame(player, it.playback.player); it.playback.sevenAccount.disconnect() }
        await { main { !it.playback.sevenAccount.busy && player.isPlaying && player.currentMediaItem?.mediaId == "100" } }
        main {
            assertEquals(ProviderConnection.CONNECTED, it.playback.providerConnection)
            assertFalse(it.playback.guide!!.channels.any { row -> row.providerChannel?.id?.provider == "seven" })
        }
    }
    @Test fun deniedDescriptorKeepsFastAndTheSevenAccountConnected() {
        val number = ready()
        main { it.denied = true; it.playback.request(TuneTarget.Number(number)) }
        await { main { it.playback.sevenAccount.error != null && !it.playback.buffering } }
        main {
            assertEquals(ProviderConnection.CONNECTED, it.playback.sevenAccount.connection)
            assertEquals("100", it.playback.player.currentMediaItem!!.mediaId)
            assertTrue(it.playback.player.isPlaying)
        }
    }
}
