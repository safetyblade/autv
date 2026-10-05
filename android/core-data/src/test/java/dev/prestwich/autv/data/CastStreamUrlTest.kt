package dev.prestwich.autv.data

import org.junit.Assert.*
import org.junit.Test

class CastStreamUrlTest {
    @Test fun preservesDirectAndRedirectUrlsWithoutQueries() {
        for (url in listOf("https://jmp2.uk/plu-channel.m3u8", "https://example.test/live/playlist.m3u8", "https://epg.provider.plex.tv/library/parts/id/"))
            assertEquals(url, CastStreamUrl.resolve(url))
    }
    @Test fun removesUnresolvedAdvertisingMacrosOnly() {
        assertEquals("https://stream.example/live?token=a%2Bb%3D&country=AU&empty=", CastStreamUrl.resolve(
            "https://stream.example/live?ads.ifa=[IFA]&token=a%2Bb%3D&ads.ua=%5BUA%5D&country=AU&device={DEVICE_ID}&empty="))
    }
    @Test fun preservesSignedProviderUrlsExactly() {
        val url = "https://epg.provider.plex.tv/library/parts/id/?X-Plex-Token=signed%2Bvalue&X-Plex-Client-Identifier=123&includeSubtitles=1"
        assertEquals(url, CastStreamUrl.resolve(url))
    }
    @Test fun removesEmptyQueryAndNonFetchableFragment() {
        assertEquals("https://stream.example/live.m3u8", CastStreamUrl.resolve(" https://stream.example/live.m3u8?ads.ip=[IP]#fragment "))
        assertEquals("https://stream.example/live", CastStreamUrl.resolve("https://stream.example/live#x?bad"))
    }
    @Test fun rejectsUnsupportedOrStillMalformedUrls() {
        for (url in listOf("file:///tmp/live", "https:///live", "https://stream.example/[PATH]/live")) {
            assertTrue(runCatching { CastStreamUrl.resolve(url) }.isFailure)
        }
    }
}
