package dev.prestwich.autv.guide

import dev.prestwich.autv.data.*
import dev.prestwich.autv.data.provider.ProviderChannel
import dev.prestwich.autv.data.provider.ProviderChannelId
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

    @Test fun nativeProviderInternalNumbersAreNotVisibleOrSearchable() {
        val native = Channel(-9042, "Travel Australia", "Australian TV", "7plus", "", "", true,
            null, "provider:seven:travel-australia", null,
            ProviderChannel(ProviderChannelId("seven", "travel-australia"), "official-ref", "Travel Australia"))
        assertEquals("", native.numberLabel)
        assertTrue(GuideBrowse.filter(listOf(native), "All", "-9042", Epg(emptyMap()), 150).isEmpty())
        assertEquals(listOf(native), GuideBrowse.filter(listOf(native), "All", "Travel", Epg(emptyMap()), 150))
        assertEquals(listOf(native), GuideBrowse.filter(listOf(native), "All", " ", Epg(emptyMap()), 150))
        assertEquals("100", news.numberLabel)
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
