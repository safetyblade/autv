package dev.prestwich.autv

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.ViewModelStore
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.prestwich.autv.data.*
import dev.prestwich.autv.guide.TuneTarget
import org.junit.Assert.*
import org.junit.Test

/** Real Media3 asset playback on an Android test runtime; no provider/network fixture. */
class FullEpgPlaybackRecoveryTest {
    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }
    private fun await(check: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < end) {
            if (onMain(check)) return
            SystemClock.sleep(50)
        }
        fail("Expected retained playback state did not arrive")
    }
    @Test fun failedFullGuideRequestRestoresPreviousThroughExistingTunePath() {
        val good = Channel(100, "Good", "News", "", "", "", true, "asset:///pip-test.m3u8?channel=100", "good", null)
        val bad = good.copy(number = 101, name = "Missing fixture", streamUrl = "asset:///no-such-guide-test.m3u8", tvgId = "bad")
        val catalogue = Guide(2, 2, listOf(good, bad))
        val store = ViewModelStore()
        val model = onMain {
            PlaybackModel(ApplicationProvider.getApplicationContext<Application>(), { catalogue }, { Epg(emptyMap()) },
                { DebugCastConnection(it) }).also { store.put("recovery", it); it.player.repeatMode = Player.REPEAT_MODE_ALL }
        }
        try {
            await { model.guide != null }
            onMain { model.request(TuneTarget.Number(100)) }
            await { model.selected?.number == 100 && model.player.isPlaying }
            val retainedPlayer = onMain { model.player }
            onMain { model.guideOpen = true; model.request(TuneTarget.Number(101), restoreOnFailure = true) }
            await { model.failed.containsKey(101) && model.selected?.number == 100 && model.player.isPlaying }
            onMain {
                assertSame(retainedPlayer, model.player)
                assertEquals("100", model.player.currentMediaItem?.mediaId)
                assertFalse(model.guideOpen)
                assertTrue(model.notice!!.contains("Returned to Good"))
            }
        } finally { onMain { store.clear() } }
    }
}
