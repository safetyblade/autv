package dev.prestwich.autv.guide
import org.junit.Assert.*
import org.junit.Test
import dev.prestwich.autv.data.CastLoadFailure
class CastStatusMatchTest {
    @Test fun missingCustomDataRequiresAckAndFreshSessionForTheExactMedia() {
        assertFalse(CastStatusMatch.matches("url","new","url",null,null,false,2,1))
        assertFalse(CastStatusMatch.matches("url","new","url",null,null,true,1,1))
        assertTrue(CastStatusMatch.matches("url","new","url",null,null,true,2,1))
        assertFalse(CastStatusMatch.matches("url","new","other",null,null,true,2,1))
    }
    @Test fun echoedGenerationSurvivesRedirectButCannotAdoptStaleSameUrlStatus() {
        assertTrue(CastStatusMatch.matches("original","new","redirected",null,"new",false,2,1))
        assertFalse(CastStatusMatch.matches("original","new","original",null,"old",true,2,1))
    }
    @Test fun bufferingDoesNotTakeInitialOwnershipAndFailureReleasesPriorOwnership() {
        val t=CastTransfer();t.connected("receiver");val ticket=t.request(1,"url")
        assertTrue(t.prepared(ticket,"issued"));assertEquals("issued",t.expectedUrl)
        assertFalse(t.prepared(ticket-1,"stale"))
        assertTrue(t.prepared(ticket,"url"))
        t.accepted(ticket);assertTrue(t.buffering(ticket,"url"));assertFalse(t.state.ownsPlayback)
        assertFalse(t.started(ticket,"url",paused=true));assertTrue(t.started(ticket,"url"))
        assertTrue(t.buffering(ticket,"url"));assertTrue(t.state.ownsPlayback)
        t.failed(ticket,"timeout");assertFalse(t.state.ownsPlayback)
    }
    @Test fun safeDiagnosticsNeverIncludeArbitraryReceiverMessagesOrUrls() {
        val result=CastDiagnostics.failure(CastLoadFailure.RECEIVER_REJECTED,2100,301,reason="https://private.invalid/token")
        assertFalse(result.contains("private"));assertTrue(result.contains("2100"))
        assertTrue(CastDiagnostics.failure(CastLoadFailure.URL_UNAVAILABLE,403,http=true).contains("HTTP 403"))
    }
}
