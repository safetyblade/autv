package dev.prestwich.autv.data.provider

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class AbcHttpAccessTest {
    private class Connection(url: URL,val body: String="{}",val status: Int=200): HttpURLConnection(url) {
        val sent=ByteArrayOutputStream();var closed=false
        override fun getOutputStream()=sent
        override fun getInputStream()=ByteArrayInputStream(body.toByteArray())
        override fun getResponseCode()=status
        override fun connect() { }
        override fun disconnect() { closed=true }
        override fun usingProxy()=false
    }
    private fun registration(authorise: (String,String,String?)->String={_,url,_->url})=
        AbcClientRegistration("autv-registered-fixture","synthetic-profile-key","VIC","returned-main-vic",authorise)
    @Test fun profileKeyIsScopedToLinkUnlinkAndUidRenewalHasNoOldBearer() {
        val calls=mutableListOf<Connection>()
        val access=AbcHttpAccess(registration()) { Connection(it).also(calls::add) }
        access.link("persistent-device");access.linkStatus("returned-device","private-request");access.accountToken("private-uid");access.unlink("returned-device")
        assertEquals("AUTV",JSONObject(calls.first().sent.toString("UTF-8")).getString("deviceName"))
        assertEquals("synthetic-profile-key",calls.first().getRequestProperty("X-API-KEY"))
        val token=calls.first { it.url.host=="api.seesaw.abc.net.au" }
        assertEquals("UID=private-uid&source=iview",token.url.query)
        assertNull(token.getRequestProperty("X-API-KEY"));assertNull(token.getRequestProperty("Authorization"))
        assertNull(calls[1].getRequestProperty("X-API-KEY"));assertTrue(calls.all { it.closed && !it.instanceFollowRedirects })
        assertEquals("returned-device",JSONObject(calls.last().sent.toString("UTF-8")).getString("deviceId"))
    }
    @Test fun pending204And404AreNotLostAccountsAndRedirectsAreRefused() {
        for(code in listOf(204,404)) {
            val access=AbcHttpAccess(registration()) { Connection(it,status=code) }
            assertEquals(0,access.linkStatus("device",null).length())
        }
        val access=AbcHttpAccess(registration()) { Connection(it,status=302) }
        try { access.accountToken("uid");fail() } catch(e: ProviderException) { assertEquals(302,e.httpStatus) }
    }
    @Test fun programmePathAndCatalogueAreScopedWithoutAnyAccountHeaders() {
        val calls=mutableListOf<Connection>()
        val access=AbcHttpAccess(registration()) { Connection(it).also(calls::add) }
        access.home();access.collection("returned-collection");access.programme("https://api.iview.abc.net.au/v2/video/returned-main-vic")
        assertEquals(listOf("/v2/home","/v2/collection/returned-collection","/v2/video/returned-main-vic"),calls.map { it.url.path })
        assertTrue(calls.all { it.getRequestProperty("X-API-KEY")==null && it.getRequestProperty("Authorization")==null })
        try { access.programme("https://foreign.example/v2/video/id");fail() } catch(_: ProviderException) { }
        assertEquals(3,calls.size)
    }
    @Test fun authorisationReceivesExactOriginalAndCannotFallbackOrTuneAnotherRegion() {
        val original="https://cdn.example/main.m3u8?complete=original"
        val access=AbcHttpAccess(registration { house,url,token ->
            assertEquals("returned-main-vic",house);assertEquals(original,url);assertEquals("synthetic-token",token)
            throw IllegalStateException("private signed URL must not reach error")
        }) { error("Media authorisation must not send profile credentials to CDN") }
        try { access.authoriseMedia("returned-main-vic",original,"synthetic-token");fail() } catch(e: ProviderException) { assertFalse(e.toString().contains("private")) }
        try { access.authoriseMedia("another-region",original,null);fail() } catch(e: ProviderException) { assertEquals(ProviderFailure.UNAVAILABLE,e.failure) }
        assertFalse(registration().toString().contains("synthetic-profile-key"))
    }
}
