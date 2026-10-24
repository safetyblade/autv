package dev.prestwich.autv.data.provider

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class TenHttpAccessTest {
    private class Connection(url: URL,val body: String,val status: Int=200,val dai: String?=null): HttpURLConnection(url) {
        val sent=ByteArrayOutputStream();var closed=false
        override fun getOutputStream()=sent
        override fun getInputStream()=ByteArrayInputStream(body.toByteArray())
        override fun getErrorStream()=inputStream
        override fun getResponseCode()=status
        override fun getHeaderField(name: String)=if(name.equals("X-DAI-AUTH",true)) dai else null
        override fun connect() { }
        override fun disconnect() { closed=true }
        override fun usingProxy()=false
    }
    private class Registration: TenClientRegistration {
        override val allowedOrigins=setOf("https://10.com.au")
        override val configParameters=mapOf("client" to "autv-approved-fixture")
        override val deviceFields=mapOf("machine" to "AUTV test")
        override val playbackParameters=mapOf("client" to "autv-approved-fixture")
        override val liveFormat=TenLiveProtocol.Format.DASH
        val signed=mutableListOf<Triple<String,String,String?>>()
        override fun authorise(method: String,exactUrl: String,jsonBody: String?): Map<String,String> {
            signed+=Triple(method,exactUrl,jsonBody);return mapOf("X-Test-Registration" to "synthetic-only")
        }
    }
    private val config="""{"authConfig":{"generateCode":"https://10.com.au/api/code","validateCode":"https://10.com.au/api/validate","refreshToken":"https://10.com.au/api/refresh"},"liveTvEndpoint":"https://10.com.au/api/live"}"""
    @Test fun deviceConfigAndPollUseExactAuthorisedBodyWithoutDeviceImpersonation() {
        val registration=Registration();val requests=mutableListOf<Connection>()
        val access=TenHttpAccess(registration) { url -> Connection(url,if(url.path.endsWith("config")) config else "{}").also(requests::add) }
        access.generateCode("persistent-device");access.validateCode("ABCDE","persistent-device",1234);access.refresh("synthetic-token","synthetic-refresh")
        val code=requests.first { it.url.path=="/api/code" }
        assertEquals("POST",code.requestMethod);assertFalse(code.instanceFollowRedirects)
        val payload=JSONObject(code.sent.toString("UTF-8"))
        assertEquals("persistent-device",payload.getString("deviceIdentifier"));assertEquals("AUTV test",payload.getString("machine"));assertFalse(payload.has("platform"))
        assertTrue(requests.all { it.closed && it.getRequestProperty("Authorization")==null })
        assertEquals(code.sent.toString("UTF-8"),registration.signed.first { it.second.endsWith("/api/code") }.third)
        assertEquals(1234,JSONObject(requests.first { it.url.path=="/api/validate" }.sent.toString("UTF-8")).getInt("expiry"))
    }
    @Test fun vicCatalogueAndIssuedDaiAuthUseActualDescriptorAndNegotiatedDash() {
        val requests=mutableListOf<Connection>()
        val access=TenHttpAccess(Registration()) { url -> Connection(url,when(url.path) {
            "/api/v1/config"->config
            "/api/live/VIC"->"{\"channels\":[]}"
            else->"{\"dai\":{\"assetKey\":\"synthetic-provider-asset\"}}"
        },dai="synthetic-issued-dai").also(requests::add) }
        access.liveChannels("VIC")
        assertEquals("limit=4",requests.last().url.query)
        val descriptor=access.playback("https://10.com.au/api/playback/provider-id","synthetic-token")
        assertEquals("application/dash+xml",descriptor.mimeType);assertEquals("synthetic-issued-dai",descriptor.daiAuth)
        assertEquals("Bearer synthetic-token",requests.last().getRequestProperty("Authorization"))
        assertTrue(requests.dropLast(1).all { it.getRequestProperty("Authorization")==null })
    }
    @Test fun redirectsAndForeignConfigEndpointsCannotReceiveAuthorisation() {
        var opened=0
        val access=TenHttpAccess(Registration()) { url -> opened++;Connection(url,"{}",302) }
        try { access.playback("https://10.com.au/api/live","synthetic-token");fail() } catch(e: ProviderException) { assertEquals(302,e.httpStatus) }
        try { access.playback("https://foreign.example/api/live","synthetic-token");fail() } catch(e: ProviderException) { assertEquals(ProviderFailure.AUTH_RESPONSE_UNSUPPORTED,e.failure) }
        assertEquals(1,opened)
    }
    @Test fun missingDaiAuthAndProviderFailuresAreSafeAndNeverBecomePlainMedia() {
        for(status in listOf(200,401,403,429,503)) {
            val access=TenHttpAccess(Registration()) { url -> Connection(url,"{\"dai\":{\"assetKey\":\"synthetic\"},\"private\":\"not-for-errors\"}",status) }
            try { access.playback("https://10.com.au/api/live","synthetic-token");fail() } catch(e: ProviderException) { assertFalse(e.toString().contains("not-for-errors"));assertFalse(e.toString().contains("synthetic-token")) }
        }
    }
}
