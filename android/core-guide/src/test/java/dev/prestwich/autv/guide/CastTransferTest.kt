package dev.prestwich.autv.guide

import org.junit.Assert.*
import org.junit.Test

class CastTransferTest {
    @Test fun connectionAndAcceptanceAreNotPlaybackConfirmation() {
        val transfer = CastTransfer()
        transfer.connected("Living room")
        assertEquals(CastPhase.CONNECTED, transfer.state.phase)
        assertFalse(transfer.state.ownsPlayback)
        val ticket = transfer.request(419, "https://example/ink")
        assertEquals(CastPhase.LOAD_REQUESTED, transfer.state.phase)
        assertTrue(transfer.accepted(ticket))
        assertEquals(CastPhase.LOAD_ACCEPTED, transfer.state.phase)
        assertFalse(transfer.state.ownsPlayback)
        assertTrue(transfer.started(ticket, "https://example/ink"))
        assertTrue(transfer.state.ownsPlayback)
        assertEquals(CastPhase.PLAYING, transfer.state.phase)
    }
    @Test fun fastChannelChangesIgnoreOldResultsAndOldMediaStatus() {
        val transfer = CastTransfer().apply { connected("TV") }
        val old = transfer.request(1, "https://example/one")
        val current = transfer.request(2, "https://example/two")
        assertFalse(transfer.accepted(old))
        assertFalse(transfer.failed(old, "superseded"))
        assertFalse(transfer.started(current, "https://example/one"))
        assertTrue(transfer.started(current, "https://example/two"))
        assertEquals(2, transfer.state.channelNumber)
    }
    @Test fun lateAcceptedResultDoesNotDowngradeConfirmedPlayback() {
        val transfer = CastTransfer().apply { connected("TV") }
        val ticket = transfer.request(1, "https://example/one")
        transfer.started(ticket, "https://example/one")
        assertFalse(transfer.accepted(ticket))
        assertEquals(CastPhase.PLAYING, transfer.state.phase)
    }
    @Test fun remoteRetuneKeepsLocalStoppedUntilFailureOrDisconnect() {
        val transfer = CastTransfer().apply { connected("TV") }
        transfer.started(transfer.request(1, "https://example/one"), "https://example/one")
        val next = transfer.request(2, "https://example/two")
        assertTrue(transfer.state.ownsPlayback)
        assertTrue(transfer.failed(next, "load rejected 2100"))
        assertFalse(transfer.state.ownsPlayback)
        assertEquals("load rejected 2100", transfer.state.error)
        assertFalse(transfer.failed(next, "duplicate"))
        assertFalse(transfer.started(next, "https://example/two"))
        val retry = transfer.request(2, "https://example/two")
        assertTrue(transfer.started(retry, "https://example/two"))
        transfer.disconnected()
        assertFalse(transfer.state.ownsPlayback)
        assertFalse(transfer.failed(retry, "late error"))
        assertFalse(transfer.started(retry, "https://example/two"))
    }
    @Test fun receiverPauseAndResumeKeepOwnership() {
        val transfer = CastTransfer().apply { connected("TV") }
        val ticket = transfer.request(1, "https://example/one")
        assertTrue(transfer.started(ticket, "https://example/one", paused = true))
        assertEquals(CastPhase.PAUSED, transfer.state.phase)
        assertTrue(transfer.state.ownsPlayback)
        transfer.started(ticket, "https://example/one")
        assertEquals(CastPhase.PLAYING, transfer.state.phase)
    }
}
