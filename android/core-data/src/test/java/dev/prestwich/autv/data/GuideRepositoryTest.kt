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
        assertEquals(1, guide.availableCount)
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
}
