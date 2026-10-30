package dev.prestwich.autv.data.provider

import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

class AbcHlsAuthoriserTest {
    private class Connection(url: URL,val status: Int=200): HttpURLConnection(url) {
        var closed=false
        override fun getResponseCode()=status
        override fun getInputStream()="issued-token~expiry=fixture".byteInputStream()
        override fun connect()=Unit
        override fun disconnect() { closed=true }
        override fun usingProxy()=false
    }
    @Test fun signsExactApprovedPathAndPreservesOriginalQueryWithoutAccountCredentialLeakage() {
        var path="";var connection:Connection?=null
        val registration=AbcHlsSigningRegistration("autv-approved-fixture") { path=it;"a".repeat(64) }
        val authorise=AbcHlsAuthoriser(registration,{url->Connection(url).also {connection=it}},{1700000000000})
        val original="https://cdn.example/abc.m3u8?session=original&ads=normal"
        val result=authorise("HOUSE_VIC",original,"account-must-not-leak")
        assertEquals("/auth/hls/sign?ts=1700000000&hn=HOUSE_VIC&d=autv-approved-fixture",path)
        assertTrue(result.startsWith(original+"&hdnea="));assertTrue(connection!!.closed)
        assertNull(connection!!.getRequestProperty("Authorization"));assertFalse(connection!!.instanceFollowRedirects)
        assertEquals("api.iview.abc.net.au",connection!!.url.host)
    }
    @Test fun deniedAuthorisationCannotReturnUnsignedMedia() {
        val authorise=AbcHlsAuthoriser(AbcHlsSigningRegistration("approved-fixture"){"a".repeat(64)},{Connection(it,403)},{1})
        try { authorise("HOUSE","https://cdn.example/live.m3u8",null);fail() }
        catch(error: ProviderException) { assertEquals(403,error.httpStatus);assertFalse(error.toString().contains("cdn")) }
    }
}
