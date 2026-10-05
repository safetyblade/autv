package dev.prestwich.autv.guide

import dev.prestwich.autv.data.*
import org.junit.Assert.*
import org.junit.Test

class GuideBrowseTest {
    private val news = Channel(100, "ABC NEWS", "News", "", "", "", true, "https://example.com/live", "news", null)
    private val ink = news.copy(number = 419, name = "Ink Master", genre = "Game Shows", tvgId = "ink")
    private val epg = Epg(mapOf("ink" to listOf(Programme("Master vs Apprentice", 100, 200))))

    @Test fun searchUsesNumberNameAndCurrentProgrammeWithinCanonicalCategory() {
        val channels = listOf(ink, news)
        assertEquals(listOf(ink), GuideBrowse.filter(channels, "All", "419", epg, 150))
        assertEquals(listOf(ink), GuideBrowse.filter(channels, "All", "INK", epg, 150))
        assertEquals(listOf(ink), GuideBrowse.filter(channels, "Game Shows", "apprentice", epg, 150))
        assertTrue(GuideBrowse.filter(channels, "News", "apprentice", epg, 150).isEmpty())
        assertTrue(GuideBrowse.filter(channels, "All", "apprentice", epg, 200).isEmpty())
        assertEquals(listOf(news, ink), GuideBrowse.filter(channels, "All", " ", epg, 150))
    }

    @Test fun openingGuideRetainsSensibleCategoryAndMakesPlayingChannelReachable() {
        assertEquals("All", GuideBrowse.categoryForPlaying("All", ink))
        assertEquals("Game Shows", GuideBrowse.categoryForPlaying("Game Shows", ink))
        assertEquals("Game Shows", GuideBrowse.categoryForPlaying("News", ink))
        assertEquals("All", GuideBrowse.categoryForPlaying("News", ink.copy(genre = "Unknown")))
        assertEquals(12, GuideBrowse.categories.size)
        assertEquals("Kids & Animation", GuideBrowse.categories[10])
    }
}
