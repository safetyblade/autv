package dev.prestwich.autv.data.provider

import org.junit.Assert.*
import org.junit.Test

class ProviderMediaOptionsTest {
    @Test fun scopesNeverInheritMediaOrLicenceCredentials() {
        val policy = ProviderOriginHeaders(mapOf("https://media.invalid" to mapOf("Authorization" to "private-media"),
            "https://licence.invalid" to mapOf("X-Licence" to "private-licence")))
        assertEquals(mapOf("Authorization" to "private-media"), policy.forAddress("https://media.invalid/segment"))
        assertTrue(policy.forAddress("https://other.invalid/redirected").isEmpty())
        assertTrue(policy.forAddress("https://media.invalid:444/key").isEmpty())
        assertEquals(mapOf("X-Licence" to "private-licence"), policy.forAddress("https://licence.invalid/license"))
        assertFalse(policy.toString().contains("private"))
    }
    @Test fun invalidDestinationsAndTransportHeaderInjectionAreRejected() {
        for (url in listOf("http://licence.invalid", "https://user@licence.invalid", "https://licence.invalid/#private")) {
            try { ProviderWidevine(url); fail() } catch (_: IllegalArgumentException) { }
        }
        for (header in listOf("Host", "Range", "Content-Length", "bad\nname")) {
            try { ProviderWidevine("https://licence.invalid/", mapOf(header to "value")); fail() } catch (_: IllegalArgumentException) { }
        }
        try { ProviderMediaOptions(originHeaders = mapOf("https://media.invalid/path" to emptyMap())); fail() }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun valuesAreCopiedRedactedAndNeverImplicitlyInferred() {
        val map = mutableMapOf("Authorization" to "private")
        val drm = ProviderWidevine("https://licence.invalid/private", map)
        map.clear(); assertEquals("private", drm.headers["Authorization"])
        val request = ProviderPlaybackRequest(ProviderChannelId("test", "one"), "https://media.invalid", "application/dash+xml",
            options = ProviderMediaOptions(widevine = drm, imaLive = ProviderImaLive("public-sample", authToken="private")))
        assertFalse(request.toString().contains("private"))
        assertFalse(drm.toString().contains("private"))
        assertFalse(request.options.imaLive.toString().contains("private"))
        assertNull(ProviderPlaybackRequest(request.channelId,request.uri,"application/x-mpegURL").options.widevine)
    }
}
