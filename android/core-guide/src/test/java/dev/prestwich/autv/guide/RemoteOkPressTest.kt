package dev.prestwich.autv.guide

import dev.prestwich.autv.guide.RemoteOkPress.Action.*
import org.junit.Assert.*
import org.junit.Test

class RemoteOkPressTest {
    private val press = RemoteOkPress()
    @Test fun shortActsOnReleaseOnly() {
        assertEquals(NONE, press.down(100, false, false, 500))
        assertEquals(SHORT, press.up(200, 500, false))
        assertEquals(NONE, press.up(201, 500, false))
    }
    @Test fun timerLongWithoutRepeatsSuppressesRelease() {
        press.down(100, false, false, 500)
        assertEquals(LONG, press.hold())
        assertEquals(NONE, press.hold())
        assertEquals(NONE, press.up(700, 500, false))
    }
    @Test fun repeatAndLongFlagEmitOnce() {
        press.down(100, false, false, 500)
        assertEquals(LONG, press.down(200, true, true, 500))
        assertEquals(NONE, press.down(800, true, true, 500))
        assertEquals(NONE, press.up(900, 500, false))
    }
    @Test fun lateReleaseWithoutTimerStillLong() {
        press.down(100, false, false, 500)
        assertEquals(LONG, press.up(700, 500, false))
    }
    @Test fun repeatsWithoutFlagsUseElapsedTime() {
        press.down(100, false, false, 500)
        assertEquals(NONE, press.down(300, true, false, 500))
        assertEquals(LONG, press.down(600, true, false, 500))
        assertEquals(NONE, press.up(650, 500, false))
    }
    @Test fun cancelledAndOrphanEventsCannotAct() {
        assertEquals(NONE, press.down(100, true, true, 500))
        assertEquals(NONE, press.up(600, 500, false))
        press.down(100, false, false, 500)
        assertEquals(NONE, press.up(200, 500, true))
        press.down(300, false, false, 500); press.cancel()
        assertEquals(NONE, press.hold())
        assertEquals(NONE, press.up(900, 500, false))
    }
    @Test fun nextShortPressWorksAfterLong() {
        press.down(100, false, false, 500); press.hold(); press.up(700, 500, false)
        press.down(800, false, false, 500)
        assertEquals(SHORT, press.up(900, 500, false))
    }
}
