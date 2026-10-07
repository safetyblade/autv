package dev.prestwich.autv

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.prestwich.autv.data.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Cached-to-remote refresh exercises the real retained model/Media3 instance. */
class GuideRefreshIntegrationTest {
    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }
    @Test fun refreshedCacheUpdatesMetadataWithoutReplacingPlayerOrMediaOrClosingNavigation() {
        val channel = Channel(100, "Saved News", "News", "", "", "Full", true,
            "asset:///pip-test.m3u8?channel=100", "fixture-news", null)
        val saved = Guide(1, 1, listOf(channel))
        val fresh = saved.copy(channels = listOf(channel.copy(name = "Fresh News")))
        val release = CountDownLatch(1)
        var persisted: Guide? = saved
        val cache = object : GuideCache {
            override fun read() = saved
            override fun write(guide: Guide) { persisted = guide }
        }
        val store = ViewModelStore()
        val model = onMain {
            PlaybackModel(ApplicationProvider.getApplicationContext<Application>(),
                { check(release.await(10, TimeUnit.SECONDS)); fresh }, { Epg(emptyMap()) },
                { DebugCastConnection(it) }, cache).also { store.put("refresh", it) }
        }
        fun await(check: () -> Boolean) {
            val end = SystemClock.uptimeMillis() + 15_000
            while (SystemClock.uptimeMillis() < end) {
                if (onMain(check)) return
                SystemClock.sleep(50)
            }
            fail("Expected guide refresh state did not arrive")
        }
        try {
            await { model.startupState == GuideStartupState.CACHED && model.selected?.name == "Saved News" }
            val player = onMain { model.player }
            val media = onMain { player.currentMediaItem }
            onMain { model.guideOpen = false }
            release.countDown()
            await { model.startupState == GuideStartupState.READY && model.selected?.name == "Fresh News" }
            await { persisted == fresh }
            onMain {
                assertSame(player, model.player)
                assertSame(media, player.currentMediaItem)
                assertEquals(100, model.selected!!.number)
                assertFalse(model.guideOpen)
                assertFalse(model.loadError)
            }
        } finally { release.countDown(); onMain { store.clear() } }
    }
}
