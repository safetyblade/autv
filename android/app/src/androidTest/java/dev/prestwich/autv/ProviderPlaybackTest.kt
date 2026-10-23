package dev.prestwich.autv

import android.os.SystemClock
import androidx.test.ext.junit.rules.ActivityScenarioRule
import dev.prestwich.autv.guide.TuneTarget
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Proves native wiring with fixture media only, not live 9Now authorization/playback. */
class ProviderPlaybackTest {
    @get:Rule val activity = ActivityScenarioRule(ProviderTestActivity::class.java)
    private fun <T> onMain(block: (ProviderTestActivity) -> T): T {
        var result: Result<T>? = null
        activity.scenario.onActivity { result = runCatching { block(it) } }
        return result!!.getOrThrow()
    }
    private fun await(check: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 30_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (check()) return
            SystemClock.sleep(100)
        }
        fail("Provider fixture state did not arrive")
    }
    private fun ready() {
        await { onMain { it.playback.playing && it.playback.guide?.channels?.any { c -> c.providerChannel != null } == true } }
        onMain { it.playback.player.volume = 0f }
    }
    @Test fun nativeProviderUsesRetainedPlayerAndSignOutRestoresFast() {
        ready()
        val player = onMain { it.playback.player }
        onMain { it.playback.request(TuneTarget.Number(-9001)) }
        await { onMain { it.playback.selected?.providerChannel != null && player.isPlaying &&
            player.currentMediaItem?.mediaId == "-9001" } }
        onMain {
            assertSame(player, it.playback.player)
            assertEquals("asset:///pip-test.m3u8?channel=provider", player.currentMediaItem!!.localConfiguration!!.uri.toString())
            assertNull(it.playback.selected!!.streamUrl)
            it.playback.pause()
            assertFalse(player.playWhenReady)
            it.playback.resume()
            it.playback.disconnectNineNow()
        }
        await { onMain { it.playback.selected?.number == 100 && player.isPlaying &&
            it.playback.guide!!.channels.none { c -> c.providerChannel != null } } }
        onMain { assertSame(player, it.playback.player) }
    }
    @Test fun resolutionDenialKeepsFastAndReportsError() {
        ready()
        onMain { it.adapter.denied = true; it.playback.request(TuneTarget.Number(-9001)) }
        await { onMain { it.playback.providerError != null && !it.playback.buffering } }
        onMain {
            assertEquals(100, it.playback.selected!!.number)
            assertEquals("100", it.playback.player.currentMediaItem!!.mediaId)
            assertTrue(it.playback.player.isPlaying)
        }
    }
}
