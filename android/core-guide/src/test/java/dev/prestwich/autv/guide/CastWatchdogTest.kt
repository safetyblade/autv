package dev.prestwich.autv.guide

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CastWatchdogTest {
    @Test fun pokemonStyleTimeoutOccursAtThirtySecondsDespiteRepeatedBuffering() = runTest {
        val expired=mutableListOf<Long>()
        val watchdog=CastWatchdog(this,expired::add)
        watchdog.arm(1215);runCurrent()
        advanceTimeBy(29_999);watchdog.arm(1215);runCurrent()
        assertTrue(expired.isEmpty())
        advanceTimeBy(1);runCurrent()
        assertEquals(listOf(1215L),expired)
    }
    @Test fun newChannelAndConfirmedPlaybackCancelOldTimeouts() = runTest {
        val expired=mutableListOf<Long>()
        val watchdog=CastWatchdog(this,expired::add)
        watchdog.arm(1);runCurrent();advanceTimeBy(10_000)
        watchdog.arm(2);runCurrent();advanceTimeBy(20_000);runCurrent()
        assertTrue(expired.isEmpty())
        watchdog.cancel();advanceUntilIdle()
        assertTrue(expired.isEmpty())
    }
}
