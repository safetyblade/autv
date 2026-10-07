package dev.prestwich.autv.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.SocketTimeoutException
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class GuideStartupTest {
    private val repository = GuideRepository()
    private fun guide(name: String = "News", number: Int = 100) = repository.validated("""{"channels":[{"number":$number,"name":"$name","streamUrl":"https://example.com/live.m3u8"}]}""")
    private class MemoryCache(var saved: Guide?) : GuideCache {
        override fun read() = saved
        override fun write(guide: Guide) { saved = guide }
    }
    @Test fun remoteJsonIsPublishedBeforePlaylistStarts() = runBlocking {
        val events = mutableListOf<GuideUpdate>()
        val remote = guide()
        val enriched = remote.copy(channels = remote.channels.map { it.copy(logoUrl = "https://example.com/logo") })
        GuideStartup(null, { remote }, {
            assertEquals(GuideStartupState.READY, events.last().state)
            assertSame(remote, events.last().guide)
            enriched
        }).refresh { events.add(it) }
        assertEquals(listOf(GuideStartupState.LOADING, GuideStartupState.READY, GuideStartupState.READY), events.map { it.state })
        assertEquals(enriched, events.last().guide)
    }
    @Test fun remoteTimeoutPreservesCache() = runBlocking {
        val saved = guide()
        val events = mutableListOf<GuideUpdate>()
        GuideStartup(MemoryCache(saved), { throw SocketTimeoutException("Timed out") }).refresh { events.add(it) }
        assertEquals(GuideStartupState.CACHED, events.first().state)
        assertSame(saved, events.first().guide)
        assertSame(saved, events.last().guide)
        assertTrue(events.last().refreshFailed)
    }
    @Test fun remoteFailureWithoutCacheEndsInRetryState() = runBlocking {
        val events = mutableListOf<GuideUpdate>()
        GuideStartup(null, { error("Offline") }).refresh { events.add(it) }
        assertEquals(GuideStartupState.ERROR, events.last().state)
        assertNull(events.last().guide)
    }
    @Test fun blockingRequestCannotHoldLoadingBeyondUiDeadline() = runBlocking {
        val started = System.nanoTime()
        var terminalAt = Long.MAX_VALUE
        GuideStartup(null, { Thread.sleep(250); guide() }, timeoutMs = 25).refresh {
            if (it.state == GuideStartupState.ERROR) terminalAt = (System.nanoTime() - started) / 1_000_000
        }
        assertTrue("UI error arrived only after blocking IO ($terminalAt ms)", terminalAt < 200)
    }
    @Test fun playlistFailureDoesNotUndoSuccessfulRemoteGuideOrCache() = runBlocking {
        val fresh = guide()
        val cache = MemoryCache(null)
        val events = mutableListOf<GuideUpdate>()
        GuideStartup(cache, { fresh }, { throw SocketTimeoutException("Playlist offline") }).refresh { events.add(it) }
        assertEquals(GuideStartupState.READY, events.last().state)
        assertSame(fresh, events.last().guide)
        assertSame(fresh, cache.saved)
        assertFalse(events.last().refreshFailed)
    }
    @Test fun cacheIsDisplayedWhileRemoteRefreshIsStillBlocked() = runBlocking {
        val saved = guide("Saved")
        val refreshed = guide("Fresh")
        val release = CountDownLatch(1)
        val cache = MemoryCache(saved)
        val events = mutableListOf<GuideUpdate>()
        GuideStartup(cache, { check(release.await(2, TimeUnit.SECONDS)); refreshed }).refresh {
            events.add(it)
            if (it.state == GuideStartupState.CACHED) { assertSame(saved, it.guide); release.countDown() }
        }
        assertEquals(refreshed, events.last().guide)
        assertEquals(refreshed, cache.saved)
    }
    @Test fun malformedEmptyAndDuplicateRemoteSnapshotsCannotOverwriteGoodCache() = runBlocking {
        val directory = Files.createTempDirectory("autv-guide-cache").toFile()
        try {
            val file = File(directory, "guide.json")
            val cache = FileGuideCache(file)
            val saved = guide()
            cache.write(saved)
            val original = file.readText()
            for (body in listOf("{", """{"channels":[]}""", """{"channels":[{"number":100,"name":"A"},{"number":100,"name":"B"}]}""",
                """{"channels":[{"number":100,"name":"A"},{}]}""")) {
                var last: GuideUpdate? = null
                GuideStartup(cache, { repository.validated(body) }).refresh { last = it }
                assertEquals(GuideStartupState.CACHED, last!!.state)
                assertEquals(original, file.readText())
                assertEquals(saved, cache.read())
            }
            try { cache.write(Guide(0, 0, emptyList())); fail("Empty cache write accepted") } catch (_: IllegalArgumentException) { }
            assertEquals(original, file.readText())
        } finally { directory.deleteRecursively() }
    }
    @Test fun jsonDownloadDoesNotFetchPlaylistAndRemoteRefreshDropsRemovedChannels() = runBlocking {
        val requests = mutableListOf<String>()
        val repo = GuideRepository(fetch = { endpoint ->
            requests.add(endpoint)
            """{"channels":[{"number":100,"name":"News"}]}"""
        })
        val cache = MemoryCache(guide("Withdrawn", 297))
        val events = mutableListOf<GuideUpdate>()
        GuideStartup(cache, repo::load).refresh { events.add(it) }
        assertEquals(listOf(GuideRepository.GUIDE_ENDPOINT), requests)
        assertEquals(listOf(100), events.last().guide!!.channels.map { it.number })
        assertEquals(events.last().guide, cache.saved)
    }
    @Test fun validRemoteRefreshAtomicallyReplacesDiskCacheIncludingRemovedChannels() = runBlocking {
        val directory = Files.createTempDirectory("autv-cache-replacement").toFile()
        try {
            val cache = FileGuideCache(File(directory, "guide.json"))
            cache.write(guide("Withdrawn", 297))
            val fresh = guide()
            GuideStartup(cache, { fresh }).refresh { }
            assertEquals(fresh, cache.read())
            assertFalse(cache.read()!!.channels.any { it.number == 297 })
        } finally { directory.deleteRecursively() }
    }
}
