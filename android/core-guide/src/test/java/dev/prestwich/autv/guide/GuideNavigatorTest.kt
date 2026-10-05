package dev.prestwich.autv.guide

import dev.prestwich.autv.data.Channel
import org.junit.Assert.*
import org.junit.Test

class GuideNavigatorTest {
    private fun channel(number: Int, available: Boolean = true, url: String? = "https://example.com/live.m3u8") =
        Channel(number, "Channel $number", "News", "", "", "", available, url, null, null)

    @Test fun navigationSkipsUnavailableAndMissingStreamsAndWraps() {
        val first = channel(100)
        val last = channel(400)
        val navigator = GuideNavigator(listOf(last, channel(200, false), channel(300, true, null), first))
        assertEquals(last, navigator.next(first))
        assertEquals(first, navigator.next(last))
        assertEquals(last, navigator.previous(first))
        assertEquals(first, navigator.previous(last))
    }

    @Test fun noSelectionStartsAtFirstOrLastPlayableChannel() {
        val first = channel(100)
        val last = channel(300)
        val navigator = GuideNavigator(listOf(last, first, channel(200)))
        assertEquals(first, navigator.next(null))
        assertEquals(last, navigator.previous(null))
        assertEquals(first, navigator.next(channel(999)))
        assertEquals(last, navigator.previous(channel(999)))
    }

    @Test fun emptyAndEntirelyUnavailableGuidesDoNotTune() {
        assertNull(GuideNavigator(emptyList()).next(null))
        val unavailable = channel(100, false)
        val navigator = GuideNavigator(listOf(unavailable))
        assertNull(navigator.next(unavailable))
        assertNull(navigator.previous(null))
    }
}
