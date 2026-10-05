package dev.prestwich.autv.data

import org.junit.Assert.*
import org.junit.Test

class GuideRepositoryTest {
    @Test fun unavailableAndMissingStreamsDoNotPreventLoading() {
        val guide = GuideRepository().parse("""{"activeCount":999,"availableCount":999,"channels":[
            {"number":300,"name":"Missing URL","available":true,"streamUrl":null},
            {"number":200,"name":"Off air","available":false,"streamUrl":"https://example.com/off.m3u8"},
            {"number":100,"name":"Live","available":true,"streamUrl":"https://example.com/live.m3u8"}
        ]}""")
        assertEquals(listOf(100, 200, 300), guide.channels.map { it.number })
        assertEquals(3, guide.activeCount)
        assertEquals(2, guide.availableCount)
        assertFalse(guide.channels.last().available)
    }

    @Test fun malformedRowsAreIsolatedAndDuplicateNumbersRemoved() {
        val guide = GuideRepository().parse("""{"channels":[null,{},
            {"number":100,"name":"Live","available":true,"streamUrl":"https://example.com/live.m3u8"},
            {"number":100,"name":"Duplicate","available":true,"streamUrl":"https://example.com/other.m3u8"},
            {"number":200,"name":"Invalid URL","available":true,"streamUrl":"file:///tmp/video"}
        ]}""")
        assertEquals(2, guide.activeCount)
        assertEquals(1, guide.availableCount)
        assertEquals("Live", guide.channels.first().name)
    }

    @Test fun emptyGuideIsValid() {
        assertTrue(GuideRepository().parse("""{"channels":[]}""").channels.isEmpty())
    }
    @Test fun playlistRestoresInkMasterDespiteNumberDrift() {
        val repo = GuideRepository()
        val guide = repo.parse("""{"channels":[{"number":419,"name":"Ink Master","available":false,"streamUrl":null}]}""")
        val playlist = """#EXTM3U
#EXTINF:-1 tvg-chno="418" tvg-id="ink" tvg-name="Ink Master" tvg-logo="https://example.com/logo.png",Ink Master
https://example.com/ink.m3u8
"""
        val channel = repo.mergePlaylist(guide, playlist).channels.single()
        assertEquals(419, channel.number)
        assertTrue(channel.available)
        assertEquals("https://example.com/ink.m3u8", channel.streamUrl)
        assertEquals("ink", channel.tvgId)
        assertEquals("https://example.com/logo.png", channel.logoUrl)
    }

    @Test fun declaredUnavailableStillAllowsAnExistingStream() {
        val guide = GuideRepository().parse("""{"channels":[{"number":419,"name":"Ink Master","available":false,"streamUrl":"https://example.com/ink.m3u8"}]}""")
        assertTrue(guide.channels.single().available)
    }
}
