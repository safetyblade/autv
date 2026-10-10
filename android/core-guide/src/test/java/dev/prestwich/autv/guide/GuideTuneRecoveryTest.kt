package dev.prestwich.autv.guide

import dev.prestwich.autv.data.Channel
import org.junit.Assert.*
import org.junit.Test

class GuideTuneRecoveryTest {
    private fun channel(number: Int) = Channel(number, "$number", "Sport", "", "", "", true, "https://example.com/$number", "id-$number", null)
    @Test fun failedTuneRestoresPreviousOnlyOnceAndIgnoresStaleFailures() {
        val recovery = GuideTuneRecovery()
        recovery.begin(channel(100), channel(101))
        assertNull(recovery.failed(100))
        assertEquals(channel(100), recovery.failed(101))
        assertNull(recovery.failed(101))
    }
    @Test fun successfulTuneCommitsSelectionAndRapidRetuningKeepsLastGoodChannel() {
        val recovery = GuideTuneRecovery()
        recovery.begin(channel(100), channel(101))
        recovery.begin(channel(101), channel(102))
        assertEquals(channel(100), recovery.failed(102))
        recovery.begin(channel(100), channel(101))
        recovery.ready(101)
        assertNull(recovery.failed(101))
    }
    @Test fun ordinaryRequestsClearRecoveryAndSameChannelHasNoLoop() {
        val recovery = GuideTuneRecovery()
        recovery.begin(channel(100), channel(101)); recovery.clear()
        assertNull(recovery.failed(101))
        recovery.begin(channel(100), channel(100))
        assertNull(recovery.failed(100))
    }
}
