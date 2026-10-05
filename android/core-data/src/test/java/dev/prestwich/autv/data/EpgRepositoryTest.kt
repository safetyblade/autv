package dev.prestwich.autv.data

import org.junit.Assert.*
import org.junit.Test

class EpgRepositoryTest {
    @Test fun xmltvUsesProviderIdsAndTimezoneAndExcludesPlaceholders() {
        val xml = """<tv>
            <programme channel="ink" start="20261005100000 +1000" stop="20261005110000 +1000"><title>Ink Master &amp; friends</title></programme>
            <programme channel="ink" start="20261005110000 +1000" stop="20261005120000 +1000"><title>Next episode</title></programme>
            <programme channel="other" start="20261005000000 +0000" stop="20261006000000 +0000"><title>Other</title><desc>Live channel</desc></programme>
            <programme channel="broken" start="bad" stop="bad"><title>Invalid</title></programme>
        </tv>"""
        val epg = EpgRepository().parse(xml.byteInputStream())
        val first = epg.programmes.getValue("ink").first()
        val schedule = epg.at("ink", first.start + 1000)
        assertEquals("Ink Master & friends", schedule.now?.title)
        assertEquals("Next episode", schedule.next?.title)
        assertNull(epg.at("ink", first.stop).now?.takeIf { it.title == first.title })
        assertTrue(epg.programmes["other"].isNullOrEmpty())
        assertTrue(epg.programmes["broken"].isNullOrEmpty())
        assertNull(epg.at("missing", first.start).now)
    }
    @Test fun nameFallbackIsUniqueAndExactProviderIdWins() {
        val show = Programme("Name-matched programme", 100, 200)
        val exact = Programme("Exact-ID programme", 100, 200)
        val channel = Channel(419, "Ink Master", "Game Shows", "", "", "", true, "https://example.com/live", "old-id", null)
        val fallback = Epg(mapOf("ink" to listOf(show)), mapOf("ink" to setOf("INK MASTER")))
        assertEquals(show, fallback.at(channel, 150).now)
        val ambiguous = Epg(mapOf("ink" to listOf(show)), mapOf("ink" to setOf("Ink Master"), "other" to setOf("Ink Master")))
        assertNull(ambiguous.at(channel, 150).now)
        val withExact = Epg(mapOf("old-id" to listOf(exact), "ink" to listOf(show)), mapOf("ink" to setOf("Ink Master")))
        assertEquals(exact, withExact.at(channel, 150).now)
    }

    @Test fun xmltvDisplayNamesSupportFallbackWithoutChangingChannelIdentity() {
        val xml = """<tv><channel id="ink"><display-name>Ink Master</display-name></channel>
            <programme channel="ink" start="20261005100000 +0000" stop="20261005110000 +0000"><title>Episode</title></programme></tv>"""
        val epg = EpgRepository().parse(xml.byteInputStream())
        assertEquals(setOf("Ink Master"), epg.channelNames["ink"])
    }
}
