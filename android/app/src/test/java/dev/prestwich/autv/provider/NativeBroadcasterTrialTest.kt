package dev.prestwich.autv.provider

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Provider responses are synthetic; these tests never assert live account access. */
class NativeBroadcasterTrialTest {
    @Test fun abcPublishesRealResponseNamesWithoutInventingAccountOrStreams() {
        val result = NativeBroadcasterTrial.inspect("abc") { url ->
            when {
                url.endsWith("/v2/home") -> JSONObject("""{"_embedded":{"collections":[{"id":"live-fixture","title":"Watch ABC Channels Live"}]}}""")
                url.endsWith("/v2/collection/live-fixture") -> JSONObject("""{"items":[{"type":"livestream","houseNumber":"ABC_VIC_FIXTURE","title":"ABC TV VIC","_links":{"self":{"href":"/video/ABC_VIC_FIXTURE"}}}]}""")
                else -> error("Unexpected network destination")
            }
        }
        assertEquals("ABC-CATALOGUE", result.stage)
        assertEquals(listOf("ABC TV VIC"), result.channels)
        assertTrue(result.summary.contains("not yet verified"))
        assertFalse(result.summary.contains("Connected"))
    }

    @Test fun tenConfigPresenceDoesNotCreateADeviceLink() {
        val result = NativeBroadcasterTrial.inspect("ten") { address ->
            assertEquals("https://10.com.au/api/v1/config", address)
            JSONObject("""{"data":{"authConfig":{"generateCode":"https://10.com.au/api/code"}}}""")
        }
        assertEquals("10-CONFIG", result.stage)
        assertTrue(result.summary.contains("still needs a registered client"))
        assertTrue(result.channels.isEmpty())
    }

    @Test fun missingTenLinkContractAndNetworkErrorsNeverBecomeSuccess() {
        val missing = NativeBroadcasterTrial.inspect("ten") { JSONObject("""{"data":{}}""") }
        assertTrue(missing.summary.contains("No activation code"))
        val failed = NativeBroadcasterTrial.inspect("abc") { throw java.io.IOException("secret credentials not logged") }
        assertEquals("ABC-CATALOGUE", failed.stage)
        assertFalse(failed.summary.contains("secret"))
        assertTrue(failed.channels.isEmpty())
    }
    @Test fun tenLinearTrialsAreFourDistinctDebugCandidatesNotClaimedAuthenticated() {
        val trials = NativeBroadcasterTrial.TEN_LINEAR_TRIALS
        assertEquals(4, trials.size)
        assertEquals(4, trials.map { it.number }.distinct().size)
        assertEquals(4, trials.map { it.source }.distinct().size)
        assertEquals("10 VIC — Experimental", trials.first().name)
        assertTrue(trials.first().physicallyObserved)
        assertEquals(3, trials.count { !it.physicallyObserved })
        assertTrue(trials.all { it.number < 0 && it.source.startsWith("https://i.mjh.nz/") })
    }

}
