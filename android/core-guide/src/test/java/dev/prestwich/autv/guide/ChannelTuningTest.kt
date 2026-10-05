package dev.prestwich.autv.guide

import dev.prestwich.autv.data.Channel
import org.junit.Assert.*
import org.junit.Test

class ChannelTuningTest {
    private fun channel(n: Int, genre: String, url: String? = "https://example.com/live.m3u8", id: String = "id-$n") =
        Channel(n, "Channel $n", genre, "", "", "Full", false, url, id, null)
    @Test fun directResolutionAllowsKnownStreamsAndRejectsMissingOrAmbiguousIdentity() {
        val channels = listOf(channel(419, "Game Shows"), channel(500, "Crime", null))
        assertEquals(419, ChannelTuning.resolve(channels, TuneTarget.Number(419))?.number)
        assertEquals(419, ChannelTuning.resolve(channels, TuneTarget.Id("id-419"))?.number)
        assertNull(ChannelTuning.resolve(channels, TuneTarget.Number(500)))
        assertNull(ChannelTuning.resolve(channels, TuneTarget.Number(999)))
        assertNull(ChannelTuning.resolve(channels + channel(420, "Game Shows", id = "id-419"), TuneTarget.Id("id-419")))
    }
    @Test fun homeSelectionIsBoundedGenreBalancedAndRecentFirst() {
        val channels = listOf(channel(100, "News"), channel(110, "News"), channel(200, "Sport"), channel(419, "Game Shows"), channel(500, "Crime", null))
        assertEquals(listOf(419, 100, 200), ChannelTuning.featured(channels, 419).map { it.number })
        assertEquals(2, ChannelTuning.featured(channels, limit = 2).size)
        assertTrue(ChannelTuning.featured(emptyList()).isEmpty())
    }
}
