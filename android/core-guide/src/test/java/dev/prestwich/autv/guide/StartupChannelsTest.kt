package dev.prestwich.autv.guide

import dev.prestwich.autv.data.Channel
import org.junit.Assert.*
import org.junit.Test

class StartupChannelsTest {
    private fun row(number: Int, id: String, genre: String = "News", url: String? = "https://example.com/$id") =
        Channel(number, id, genre, "", "", "", url != null, url, id, null)
    private val lead = row(100, "autv.110-leadstory-breaking-news")
    private val news24 = row(101, "autv.105-news24-now")
    private fun key(channel: Channel) = FavouriteIdentity.key(channel)!!
    @Test fun defaultUsesHistoryThenFirstFavouriteThenLeadStoryThenCatalogue() {
        val last = row(250, "last", "Sport")
        val first = row(210, "first", "Sport")
        val rows = listOf(last, news24, lead, first)
        assertEquals(listOf(last, first, lead, news24), StartupChannels.candidates(rows, StartupMode.LAST_WATCHED,
            null, key(last), null, setOf(key(last),key(first))))
    }
    @Test fun noHistoryOrFavouritesStartsLeadStoryAndKeepsNews24() {
        assertEquals(listOf(lead, news24), StartupChannels.candidates(listOf(news24, lead), StartupMode.LAST_WATCHED,
            null, null, null, emptySet()))
    }
    @Test fun stableIdentitySurvivesRenumberingAndMissingHistoryFallsBack() {
        val moved = row(900, "saved")
        assertEquals(moved, StartupChannels.candidates(listOf(lead,moved), StartupMode.LAST_WATCHED,
            null,key(moved),100,emptySet()).first())
        assertEquals(lead, StartupChannels.candidates(listOf(lead,moved), StartupMode.LAST_WATCHED,
            null,"id:missing",900,emptySet()).first())
    }
    @Test fun unavailableCandidatesAreSkippedWithoutReorderingFavourites() {
        val gone = row(90,"gone",url=null)
        assertEquals(lead, StartupChannels.candidates(listOf(news24,lead,gone), StartupMode.FAVOURITE,
            null,null,null,setOf(key(gone),key(news24))).first())
    }
    @Test fun preferredSportIsAnIdentityAndCategoryIsRestricted() {
        val sport = row(200,"sport","Sport")
        assertEquals(listOf(sport), StartupChannels.choices(listOf(lead,sport),StartupMode.SPORTS))
        assertEquals(sport, StartupChannels.candidates(listOf(lead,sport),StartupMode.SPORTS,
            key(sport),null,null,emptySet()).first())
    }
    @Test fun favouriteModeIgnoresLastWatchedAndUsesGuideOrder() {
        val favourite = row(210,"favourite","Sport")
        assertEquals(news24, StartupChannels.candidates(listOf(favourite,lead,news24),StartupMode.FAVOURITE,
            null,key(lead),null,linkedSetOf(key(favourite),key(news24))).first())
    }
    @Test fun legacyNumberIsUsedOnlyWhenNoStableHistoryExists() {
        assertEquals(news24, StartupChannels.candidates(listOf(lead,news24),StartupMode.LAST_WATCHED,
            null,null,101,emptySet()).first())
    }
    @Test fun noPlayableChannelsReturnsEmpty() {
        assertTrue(StartupChannels.candidates(listOf(lead.copy(streamUrl=null)),StartupMode.LAST_WATCHED,
            null,null,null,emptySet()).isEmpty())
    }
    @Test fun directAustralianTvIsAvailableByChoiceButDoesNotHijackDefaultStartup() {
        val abc = row(2, "autv.abc-tv-vic", "Australian TV")
        val ten = row(10, "autv.10-vic-live", "Australian TV")
        val fast = row(100, "fixture-fast", "News")
        val rows = listOf(abc, ten, fast)
        assertEquals(fast, StartupChannels.candidates(rows, StartupMode.LAST_WATCHED,
            null, null, null, emptySet()).first())
        assertEquals(abc, StartupChannels.candidates(rows, StartupMode.AUSTRALIAN,
            null, null, null, emptySet()).first())
        assertEquals(ten, StartupChannels.candidates(rows, StartupMode.FAVOURITE,
            null, null, null, setOf(key(ten))).first())
    }

}
