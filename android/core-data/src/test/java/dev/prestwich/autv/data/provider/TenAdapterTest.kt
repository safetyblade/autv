package dev.prestwich.autv.data.provider

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TenAdapterTest {
    private class Store : ProviderSessionStore {
        var value: ProviderSession?=null
        override fun read()=value
        override fun write(session: ProviderSession) { value=session }
        override fun clear() { value=null }
    }
    private class Access : TenProviderAccess {
        var pending=true;var codes=0;var refreshes=0;var vod=false;var offlineRefresh=false;var url="https://10.com.au/api/v1/live/fixture"
        override fun generateCode(deviceId: String): JSONObject { codes++;return JSONObject("""{"code":"TEST1","expiry":301}""") }
        override fun validateCode(code: String,deviceId: String,expirySeconds: Long): JSONObject {
            assertEquals(301L,expirySeconds)
            return if(pending) JSONObject() else JSONObject().put("jwt",jwt())
        }
        private fun jwt()=JSONObject("""{"alternativeToken":"private-token","refreshToken":"private-refresh","expiresIn":60}""")
        override fun refresh(alternativeToken: String,refreshToken: String): JSONObject {
            assertEquals("private-token",alternativeToken);assertEquals("private-refresh",refreshToken);if(offlineRefresh) throw ProviderException(ProviderFailure.OFFLINE);refreshes++;return jwt().put("alternativeToken","rotated-token").put("refreshToken","rotated-refresh")
        }
        override fun liveChannels(region: String): JSONObject { assertEquals("VIC",region);return JSONObject().put("channels",org.json.JSONArray().put(
            JSONObject().put("key","main-vic-fixture").put("name","Main Ten fixture").put("streamUrl",url))) }
        override fun playback(officialDescriptorUrl: String,bearer: String): TenPlaybackDescriptor {
            assertEquals(url,officialDescriptorUrl);assertEquals(if(refreshes>0) "rotated-token" else "private-token",bearer)
            return TenPlaybackDescriptor(JSONObject().put("dai",if(vod) JSONObject().put("videoId","vod") else JSONObject().put("assetKey","provider-live-fixture")),"provider-dai-fixture",200000)
        }
    }
    @Test fun pendingRecreationRefreshAndLiveDaiUseProviderValuesOnly() {
        var now=1000L;val access=Access();val store=Store();val pending=MemoryProviderActivationStore()
        var adapter=TenAdapter(access,store,pending,"local-device","main-vic-fixture",now={now})
        val activation=adapter.beginSignIn();assertFalse(adapter.pollSignIn(activation))
        adapter=TenAdapter(access,store,pending,"local-device","main-vic-fixture",now={now})
        assertSame(activation,adapter.beginSignIn());assertEquals(1,access.codes)
        access.pending=false;assertTrue(adapter.pollSignIn(activation))
        now=40000;val id=adapter.channels().single().id;assertEquals(1,access.refreshes);assertEquals("rotated-refresh",store.value!!.refreshToken)
        val request=adapter.resolve(id)
        assertEquals("provider-live-fixture",request.options.imaLive!!.assetKey)
        assertEquals("provider-dai-fixture",request.options.imaLive!!.authToken)
        assertEquals(200000L,request.options.expiresAt)
        assertTrue(request.headers.isEmpty());assertNull(request.options.imaLive!!.networkCode)
    }
    @Test fun vodForeignResolverAndSiblingCannotBeUsedAsMainLiveProof() {
        val access=Access();val store=Store().apply { value=ProviderSession("private-token","private-refresh",999999) }
        val adapter=TenAdapter(access,store,MemoryProviderActivationStore(),"local-device","main-vic-fixture",now={1000})
        val id=adapter.channels().single().id
        access.vod=true
        try { adapter.resolve(id);fail() } catch(_: ProviderException) { }
        access.vod=false;access.url="https://foreign.invalid/api/session"
        try { adapter.resolve(id);fail() } catch(_: ProviderException) { }
        try { adapter.resolve(ProviderChannelId("ten","sibling"));fail() } catch(_: ProviderException) { }
        assertEquals(ProviderConnection.CONNECTED,adapter.connection)
    }
    @Test fun transientRenewalFailurePreservesTheConnectedAccount() {
        val access=Access();access.offlineRefresh=true
        val original=ProviderSession("private-token","private-refresh",100)
        val store=Store().apply { value=original }
        val adapter=TenAdapter(access,store,MemoryProviderActivationStore(),"id","main-vic-fixture",now={1000})
        try { adapter.channels();fail() } catch(error: ProviderException) { assertEquals(ProviderFailure.OFFLINE,error.failure) }
        assertSame(original,store.value);assertEquals(ProviderConnection.CONNECTED,adapter.connection)
        access.offlineRefresh=false;assertEquals(1,adapter.channels().size)
    }
    @Test fun expiryAndSignOutOnlyClearThisProvidersSavedState() {
        var now=1000L;val access=Access();val store=Store();val pending=MemoryProviderActivationStore()
        val adapter=TenAdapter(access,store,pending,"id","main-vic-fixture",now={now})
        val activation=adapter.beginSignIn();now=activation.expiresAt
        try { adapter.pollSignIn(activation);fail() } catch(e: ProviderException) { assertEquals(ProviderFailure.ACTIVATION_EXPIRED,e.failure) }
        assertNull(pending.readActivation());adapter.signOut();assertNull(store.value)
        assertEquals(ProviderConnection.NOT_CONNECTED,adapter.connection)
    }
}
