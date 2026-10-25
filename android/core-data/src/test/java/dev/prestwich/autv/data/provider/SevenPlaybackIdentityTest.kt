package dev.prestwich.autv.data.provider

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Source-derived shapes, not a captured owner-session response. */
class SevenPlaybackIdentityTest {
    @Test fun queryOnlyExtractionPreservesProviderIdsAcrossMetadataAddressShapes() {
        for (prefix in listOf("https://videoservice.swm.digital/playback", "https://videoservice.swm.digital/playback/v1", "/playback", "https://metadata.invalid/reference")) {
            val identity = SevenPlaybackIdentity.read("$prefix?accountId=provider-account&referenceId=ref%3ASeven%20Sydney%2FHD*&flag&extra=1&extra=2&#tracking")
            assertEquals("provider-account", identity.account)
            assertEquals("ref:Seven Sydney/HD*", identity.reference)
            assertFalse(identity.toString().contains("provider-account"))
        }
    }
    @Test fun missingAndConflictingFieldsReportExactNamesWithoutPrivateValues() {
        val cases = listOf(
            JSONObject().put("url", "private-fixture") to ProviderPlaybackStage.SEVEN_IDENTITY_TYPE,
            "https://metadata.invalid/no-query" to ProviderPlaybackStage.SEVEN_IDENTITY_QUERY,
            "?referenceId=private-reference" to ProviderPlaybackStage.SEVEN_IDENTITY_ACCOUNT,
            "?accountId=private-account" to ProviderPlaybackStage.SEVEN_IDENTITY_REFERENCE,
            "?accountId=one&accountId=two&referenceId=private-reference" to ProviderPlaybackStage.SEVEN_IDENTITY_AMBIGUOUS,
            "?accountId=private-account&referenceId=%0Asecret" to ProviderPlaybackStage.SEVEN_IDENTITY_VALUE)
        for ((value, stage) in cases) {
            try { SevenPlaybackIdentity.read(value); fail("Expected safe identity failure") } catch (error: ProviderException) {
                assertEquals(stage, error.stage)
                assertEquals(ProviderFailure.PLAYBACK_UNSUPPORTED, error.failure)
                assertFalse(error.toString().contains("private"))
                assertFalse(error.toString().contains("secret"))
            }
        }
    }
}
