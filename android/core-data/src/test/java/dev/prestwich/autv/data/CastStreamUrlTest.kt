package dev.prestwich.autv.data

import org.junit.Assert.*
import org.junit.Test

class CastStreamUrlTest {
    @Test fun preservesSignedAndAdvertisingParametersWithoutDeletingFields() {
        val url="https://stream.example/live.m3u8?ads.ifa=%5BIFA%5D&token=a%2Bb%3D&country=AU&empty="
        assertEquals(url,CastStreamUrl.resolve(url))
        assertTrue(CastStreamUrl.hasUnresolvedParameters(url))
        val signed="https://epg.provider.plex.tv/library/parts/id/?X-Plex-Token=signed%2Bvalue&X-Plex-Client-Identifier=123&includeSubtitles=1"
        assertEquals(signed,CastStreamUrl.resolve(signed))
        assertFalse(CastStreamUrl.hasUnresolvedParameters(signed))
    }
    @Test fun removesOnlyNonFetchableFragment() {
        assertEquals("https://stream.example/live?token=abc",CastStreamUrl.resolve(" https://stream.example/live?token=abc#fragment "))
    }
    @Test fun rejectsLocalSchemesEmbeddedCredentialsAndMalformedPaths() {
        for(url in listOf("file:///tmp/live","https:///live","https://user:password@stream.example/live","https://stream.example/[PATH]/live"))
            assertTrue(runCatching { CastStreamUrl.resolve(url) }.isFailure)
    }
}
