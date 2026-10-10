package dev.prestwich.autv.guide

import org.junit.Assert.*
import org.junit.Test

class GuideViewportTest {
    @Test fun downRevealsPartiallyVisibleBottomRowOnFirstPress() {
        val rows = (0..7).map { GuideRowBounds(it, it * 100, 100) }
        assertEquals(GuideScroll(6, -600), GuideViewport.reveal(6, 0, 750, rows))
        assertEquals(GuideScroll(7, -650), GuideViewport.reveal(7, 0, 750, rows))
    }
    @Test fun upRevealsClippedTopRowSymmetrically() {
        val rows = (5..12).map { GuideRowBounds(it, (it - 5) * 100 - 50, 100) }
        assertEquals(GuideScroll(5, 0), GuideViewport.reveal(5, 0, 700, rows))
        assertEquals(GuideScroll(4, 0), GuideViewport.reveal(4, 0, 700, rows))
    }
    @Test fun reversalBeforeLayoutOverridesTheEarlierOffscreenRequest() {
        val rows = (0..6).map { GuideRowBounds(it, it * 100, 100) }
        assertEquals(GuideScroll(20, -600), GuideViewport.reveal(20, 0, 700, rows))
        assertEquals(GuideScroll(0, 0), GuideViewport.reveal(0, 0, 700, rows))
    }
    @Test fun offscreenTargetsRequestTheirOwnIndexEvenDuringRapidInput() {
        val rows = (0..6).map { GuideRowBounds(it, it * 100, 100) }
        (7..650).forEach { index -> assertEquals(GuideScroll(index, -600), GuideViewport.reveal(index, 0, 700, rows)) }
        assertEquals(GuideScroll(0, 0), GuideViewport.reveal(0, 0, 700, emptyList()))
    }
}
