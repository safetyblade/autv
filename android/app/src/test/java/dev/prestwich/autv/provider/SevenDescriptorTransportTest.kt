package dev.prestwich.autv.provider

import dev.prestwich.autv.data.provider.*
import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

class SevenDescriptorTransportTest {
    private class Rejected(private val body: String,private val status: Int=400) : HttpURLConnection(URL(SevenPlusAdapter.PLAYBACK)) {
        override fun getResponseCode() = status
        override fun getErrorStream() = body.byteInputStream()
        override fun connect() { }
        override fun disconnect() { }
        override fun usingProxy() = false
    }
    private val parameters = linkedMapOf("appId" to "7plus", "deviceType" to "androidtv", "platformType" to "tv",
        "deviceId" to "local-fixture", "accountId" to "account-fixture", "referenceId" to "reference-fixture",
        "marketId" to "ip-market-fixture", "videoType" to "live")
    private fun rejection(body: String,status: Int=400): ProviderException {
        val connection = Rejected(body,status)
        val transport = HttpSevenTransport { address ->
            assertTrue(address.startsWith(SevenPlusAdapter.PLAYBACK + "?"))
            connection
        }
        try { transport.playback(parameters, "secret-fixture"); throw AssertionError("Expected descriptor rejection") }
        catch (error: ProviderException) {
            assertEquals(status, error.httpStatus)
            assertEquals(ProviderPlaybackStage.SEVEN_DESCRIPTOR, error.stage)
            assertEquals("GET", connection.requestMethod)
            assertEquals("Bearer secret-fixture", connection.getRequestProperty("Authorization"))
            assertFalse(connection.instanceFollowRedirects)
            return error
        }
    }
    @Test fun descriptor400RetainsOnlyAllowlistedHintsAndKnownCodes() {
        val error = rejection("""{"error":"location_verification_required","description":"pc and sdkverification invalid; secret-fixture https://private.invalid/session","errors":[{"field":"deliveryId","value":"private-value"}]}""")
        val diagnostic = error.sevenRejection!!
        assertEquals(SevenDescriptorRejection.Reason.LOCATION, diagnostic.reason)
        assertEquals(setOf(SevenDescriptorRejection.Field.POSTCODE, SevenDescriptorRejection.Field.SDK, SevenDescriptorRejection.Field.DELIVERY), diagnostic.fields)
        assertFalse(diagnostic.summary.contains("private"))
        assertFalse(diagnostic.summary.contains("secret"))
        assertFalse(error.toString().contains("session"))
    }
    @Test fun unknownProviderCodesAndTextNeverEscape() {
        val diagnostic = rejection("""[{"error_code":"private-provider-code","message":"accountId rejected private-value"}]""").sevenRejection!!
        assertEquals(SevenDescriptorRejection.Reason.UNRECOGNISED, diagnostic.reason)
        assertEquals(setOf(SevenDescriptorRejection.Field.ACCOUNT), diagnostic.fields)
        assertFalse(diagnostic.summary.contains("private"))
        assertEquals(setOf(SevenDescriptorRejection.Field.REFERENCE),
            rejection("""{"errors":{"referenceId":["private-value"]}}""").sevenRejection!!.fields)
        assertEquals(SevenDescriptorRejection.Reason.MARKET,
            rejection("""{"error":"video_not_in_market"}""").sevenRejection!!.reason)
    }
    @Test fun forbiddenHtmlIsNotEvidenceOfRegionalDenial() {
        val html=rejection("<html>private access message</html>",403)
        assertEquals(ProviderFailure.UNAVAILABLE,html.failure)
        assertEquals(ProviderPlaybackStage.SEVEN_DESCRIPTOR,html.stage)
        assertEquals(SevenDescriptorRejection.Format.HTML,html.sevenRejection!!.format)
        val explicit=rejection("{\"error\":\"video_not_in_market\"}",403)
        assertEquals(ProviderFailure.REGION_DENIED,explicit.failure)
    }
    @Test fun malformedAndOversizedBodiesHaveFixedDiagnostics() {
        val html=rejection("<html>private</html>").sevenRejection!!
        assertEquals(SevenDescriptorRejection.Reason.NON_JSON,html.reason)
        assertEquals(SevenDescriptorRejection.Format.HTML,html.format)
        assertFalse(html.summary.contains("private"))
        assertEquals(SevenDescriptorRejection.Reason.TOO_LARGE, rejection("x".repeat(17000)).sevenRejection!!.reason)
    }
}
