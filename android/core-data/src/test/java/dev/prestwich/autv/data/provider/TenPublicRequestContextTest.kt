package dev.prestwich.autv.data.provider
import org.junit.Assert.*
import org.junit.Test
class TenPublicRequestContextTest {
    @Test fun postTimestampIsPublicUtcContextAndGetSigningIsNotFabricated() {
        assertEquals("MTk3MDAxMDEwMDAwMDA=",TenPublicRequestContext.headers("POST",0)["X-Network-Ten-Auth"])
        assertTrue(TenPublicRequestContext.headers("GET",0).isEmpty())
    }
}
