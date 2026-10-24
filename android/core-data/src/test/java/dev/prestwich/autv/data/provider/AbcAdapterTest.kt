package dev.prestwich.autv.data.provider

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AbcAdapterTest {
    private class Store : ProviderSessionStore {
        var value: ProviderSession?=null
        override fun read()=value
        override fun write(session: ProviderSession) { value=session }
        override fun clear() { value=null }
    }
    private class Access : AbcProviderAccess {
        var links=0;var pending=true;var tokens=0;var denyMedia=false;var offlineToken=false
        override fun link(deviceId: String): JSONObject { links++;return JSONObject("""{"data":{"code":"TEST","deviceId":"returned-device","requestId":"private-request","expiresIn":300,"interval":3}}""") }
        override fun linkStatus(deviceId: String,requestId: String?): JSONObject {
            assertEquals("returned-device",deviceId);assertEquals("private-request",requestId)
            return if(pending) JSONObject("""{"status":"pending"}""") else JSONObject("""{"UID":"private-uid"}""")
        }
        override fun accountToken(uid: String): JSONObject { assertEquals("private-uid",uid);if(offlineToken) throw ProviderException(ProviderFailure.OFFLINE);tokens++;return JSONObject("""{"auth":{"access_token":"account-token","expires_in":60}}""") }
        override fun unlink(deviceId: String) { throw ProviderException(ProviderFailure.OFFLINE) }
        override fun home()=JSONObject("""{"_embedded":{"collections":[{"title":"Watch ABC Channels Live","id":"live"}]}}""")
        override fun collection(id: String)=JSONObject("""{"items":[{"type":"livestream","houseNumber":"VIC_FIXTURE","title":"Main fixture","_links":{"self":{"href":"/video/VIC_FIXTURE"}}}]}""")
        override fun programme(officialUrl: String)=JSONObject("""{"houseNumber":"VIC_FIXTURE","_embedded":{"playlist":[{"type":"livestream","streams":{"hls":{"720":"https://media.invalid/original?ads=normal"}}}]}}""")
        override fun authoriseMedia(houseNumber: String,originalHls: String,accountToken: String?): String {
            if(denyMedia) throw ProviderException(ProviderFailure.UNAVAILABLE)
            assertEquals("account-token",accountToken);return "$originalHls&hdnea=issued-fixture"
        }
    }
    @Test fun pendingRecreationAndUidRenewalAreIndependentFromMediaAuthorisation() {
        var now=1000L;val store=Store();val pending=MemoryProviderActivationStore();val access=Access()
        var adapter=AbcAdapter(access,store,pending,"local-device","VIC_FIXTURE",now={now})
        val code=adapter.beginSignIn();assertFalse(adapter.pollSignIn(code))
        adapter=AbcAdapter(access,store,pending,"local-device","VIC_FIXTURE",now={now})
        assertSame(code,adapter.beginSignIn());assertEquals(1,access.links)
        access.pending=false;assertTrue(adapter.pollSignIn(code));assertNull(pending.readActivation())
        assertEquals("private-uid",store.value!!.privateContext["uid"])
        assertEquals("",store.value!!.refreshToken)
        now=40000;val id=adapter.channels().single().id
        assertEquals(2,access.tokens)
        assertTrue(adapter.resolve(id).uri.endsWith("hdnea=issued-fixture"))
        access.denyMedia=true
        try { adapter.resolve(id);fail() } catch(_: ProviderException) { }
        assertEquals(ProviderConnection.CONNECTED,adapter.connection)
    }
    @Test fun expiryClearsOnlyAbcChallengeAndOfflineSignoutIsDeterministic() {
        var now=1000L;val store=Store();val pending=MemoryProviderActivationStore();val access=Access()
        val adapter=AbcAdapter(access,store,pending,"local-device","VIC_FIXTURE",now={now})
        val code=adapter.beginSignIn();now=code.expiresAt
        try { adapter.pollSignIn(code);fail() } catch(e: ProviderException) { assertEquals(ProviderFailure.ACTIVATION_EXPIRED,e.failure) }
        assertNull(pending.readActivation());adapter.signOut();assertNull(store.value)
    }
    @Test fun temporaryRenewalFailureKeepsPrivateUidAndSavedAccount() {
        val access=Access();access.offlineToken=true
        val original=ProviderSession("old-token","",100,mapOf("uid" to "private-uid"))
        val store=Store().apply { value=original }
        val adapter=AbcAdapter(access,store,MemoryProviderActivationStore(),"id","VIC_FIXTURE",now={1000})
        try { adapter.channels();fail() } catch(error: ProviderException) { assertEquals(ProviderFailure.OFFLINE,error.failure) }
        assertSame(original,store.value);assertEquals(ProviderConnection.CONNECTED,adapter.connection)
        access.offlineToken=false;assertEquals(1,adapter.channels().size)
    }
    @Test fun anonymousLiveMustBeExplicitAndNeverReportsConnectedAccount() {
        val access=Access();val store=Store();val pending=MemoryProviderActivationStore()
        val linkedOnly=AbcAdapter(access,store,pending,"id","VIC_FIXTURE")
        try { linkedOnly.channels();fail() } catch(e: ProviderException) { assertEquals(ProviderFailure.AUTH_REQUIRED,e.failure) }
        val anonymous=AbcAdapter(access,store,pending,"id","VIC_FIXTURE",anonymousLiveApproved=true)
        assertEquals(1,anonymous.channels().size);assertEquals(ProviderConnection.NOT_CONNECTED,anonymous.connection)
        assertNull(store.read())
    }
}
