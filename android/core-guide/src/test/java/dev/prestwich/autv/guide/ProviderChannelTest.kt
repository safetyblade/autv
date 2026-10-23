package dev.prestwich.autv.guide

import dev.prestwich.autv.data.*
import dev.prestwich.autv.data.provider.*
import org.junit.Assert.*
import org.junit.Test

class ProviderChannelTest {
    private fun fast() = Channel(1, "FAST", "News", "", "", "", true, "https://example.invalid/live.m3u8", "fast", null)
    private fun provider() = Channel(-9001, "Seinfeld 24/7", "Australian TV", "9Now", "", "", true, null, null, null,
        ProviderChannel(NineNowAdapter.SEINFELD, "official-reference", "Seinfeld 24/7"))
    @Test fun providerNeedsNoFakeStreamUrlToBeTunable() {
        val provider = provider()
        assertNull(provider.streamUrl)
        assertSame(provider, ChannelTuning.resolve(listOf(provider), TuneTarget.Number(-9001)))
        assertEquals(provider, GuideNavigator(listOf(fast(), provider)).next(fast()))
        assertEquals(fast(), GuideNavigator(listOf(fast(), provider)).next(provider))
        assertEquals("", provider.numberLabel)
    }
    @Test fun favouritesDoNotDependOnAccountReferenceOrPlaybackUrl() {
        val provider = provider()
        assertEquals("provider:9now:seinfeld", FavouriteIdentity.key(provider))
        assertEquals(FavouriteIdentity.key(provider), FavouriteIdentity.key(provider.copy(
            providerChannel = provider.providerChannel!!.copy(reference = "new-region-reference"))))
        assertEquals("id:fast", FavouriteIdentity.key(fast()))
    }
    @Test fun privateServiceChannelsAreNotPublishedToTvHome() {
        assertTrue(ChannelTuning.featured(listOf(provider())).isEmpty())
        assertEquals(listOf(fast()), ChannelTuning.featured(listOf(fast(), provider())))
    }
}
