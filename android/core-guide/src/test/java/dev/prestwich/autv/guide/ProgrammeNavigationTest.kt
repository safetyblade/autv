package dev.prestwich.autv.guide

import dev.prestwich.autv.data.Programme
import org.junit.Assert.*
import org.junit.Test

class ProgrammeNavigationTest {
    private val halfHour = ProgrammeNavigation.HALF_HOUR
    @Test fun representativeEditorialNumbersArePreservedWithoutScheduleFiltering() {
        val numbers = listOf(200, 201, 202, 203, 297)
        val names = listOf("Watch AEW", "CW Presents WWE NXT", "TNA Wrestling Channel", "TNA Wrestling Xumo", "Wrestling Central")
        val channels = numbers.zip(names).map { (number, name) ->
            dev.prestwich.autv.data.Channel(number, name, "Sport", "", "", "", true, "https://example.com/$number", "id-$number", null)
        }
        val rows = GuideBrowse.filter(channels.reversed(), "Sport", "", dev.prestwich.autv.data.Epg(emptyMap()), 0)
        assertEquals(numbers, rows.map { it.number })
        assertEquals(names, rows.map { it.name })
    }
    @Test fun realDurationAndClippingDetermineTimelineGeometry() {
        val short = Programme("Short", 0, halfHour)
        val long = Programme("Long", halfHour, halfHour * 3)
        val first = ProgrammeNavigation.block(short, 0)!!
        val second = ProgrammeNavigation.block(long, 0)!!
        assertEquals(0f, first.first, 0.0001f)
        assertEquals(first.second * 2, second.second, 0.0001f)
        assertEquals(first.second, second.first, 0.0001f)
        assertEquals(0f, ProgrammeNavigation.block(Programme("Clipped", -halfHour, halfHour), 0)!!.first, 0.0001f)
        assertNull(ProgrammeNavigation.block(Programme("Expired", -halfHour, 0), 0))
        assertNull(ProgrammeNavigation.block(Programme("Invalid", 100, 99), 0))
    }
    @Test fun airingUsesExactBoundariesAndFutureCannotTune() {
        val item = Programme("Show", 100, 200)
        assertFalse(ProgrammeNavigation.airing(item, 99))
        assertTrue(ProgrammeNavigation.airing(item, 100))
        assertFalse(ProgrammeNavigation.airing(item, 200))
    }
    @Test fun missingExpiredAndGapsDoNotInventProgrammes() {
        val expired = Programme("Past", 0, 100)
        val future = Programme("Future", 300, 400)
        assertNull(ProgrammeNavigation.at(emptyList(), 200))
        assertNull(ProgrammeNavigation.at(listOf(expired), 200))
        assertEquals(future, ProgrammeNavigation.at(listOf(expired, future), 200))
        assertFalse(ProgrammeNavigation.airing(future, 200))
    }
    @Test fun programmeNavigationClampsRapidInputAndRetainsIdentityOnRefresh() {
        val items = (0..100).map { Programme("$it", it * halfHour, (it + 1) * halfHour) }
        var selected: Programme? = items.first()
        repeat(1000) { selected = ProgrammeNavigation.move(items, selected, 1) }
        assertEquals(items.last(), selected)
        repeat(1000) { selected = ProgrammeNavigation.move(items, selected, -1) }
        assertEquals(items.first(), selected)
        assertEquals(items[1], ProgrammeNavigation.move(items, items.first().copy(title = "Updated"), 1))
    }
}
