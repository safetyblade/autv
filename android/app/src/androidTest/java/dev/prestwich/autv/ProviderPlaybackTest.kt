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
    @Test fun multipleProviderChannelsKeepUniqueNumbersAcrossCatalogueReordering() {
        ready()
        val extra = dev.prestwich.autv.data.provider.ProviderChannel(
            dev.prestwich.autv.data.provider.ProviderChannelId("9now", "ref:fixture-nine"), "fixture-nine", "Nine")
        onMain { it.adapter.catalogue = it.adapter.catalogue + extra; it.playback.refreshProviderChannels() }
        await { onMain { it.playback.providerChannelCount == 2 && !it.playback.providerBusy } }
        val before = onMain { it.playback.guide!!.channels.filter { c -> c.providerChannel != null }.associate { c -> c.providerChannel!!.id to c.number } }
        assertEquals(2, before.values.toSet().size)
        val number = before.getValue(extra.id)
        onMain { it.adapter.catalogue = it.adapter.catalogue.reversed(); it.playback.refreshProviderChannels() }
        await { onMain { !it.playback.providerBusy } }
        onMain {
            assertEquals(before, it.playback.guide!!.channels.filter { c -> c.providerChannel != null }.associate { c -> c.providerChannel!!.id to c.number })
            it.playback.request(TuneTarget.Number(number))
        }
        await { onMain { it.playback.selected?.providerChannel?.id == extra.id && it.playback.playing } }
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
    @Test fun lateProviderResolutionCannotReplaceFastTune() {
        ready()
        val adapter = onMain { it.adapter }
        onMain { adapter.blockResolve = true; it.playback.request(TuneTarget.Number(-9001)) }
        assertTrue(adapter.resolveEntered.await(5, java.util.concurrent.TimeUnit.SECONDS))
        onMain { it.playback.request(TuneTarget.Number(100)) }
        adapter.releaseResolve.countDown()
        // Wait for the blocked transport worker to finish before assessing stale publication.
        SystemClock.sleep(500)
        onMain {
            assertEquals(100, it.playback.selected!!.number)
            assertEquals("100", it.playback.player.currentMediaItem!!.mediaId)
        }
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
