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
}
