package dev.prestwich.autv.player

import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test

class ProviderRedirectHeadersTest {
    @Test fun actualHttpsRedirectDoesNotForwardCredentialsToAnotherOrigin() {
        val cert = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(cert).build()
        val trust = HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        val source = MockWebServer(); val destination = MockWebServer()
        source.useHttps(serverTls.sslSocketFactory(),false); destination.useHttps(serverTls.sslSocketFactory(),false)
        source.start(); destination.start()
        try {
            source.enqueue(MockResponse().setResponseCode(302).setHeader("Location",destination.url("/media")))
            destination.enqueue(MockResponse().setBody("fixture"))
            val origin = "https://localhost:${source.port}"
            val client = ProviderMediaSourceFactory.scopedClient(mapOf(origin to mapOf("Authorization" to "private-fixture", "X-Signed" to "private-scope")))
                .newBuilder().sslSocketFactory(trust.sslSocketFactory(),trust.trustManager).build()
            client.newCall(Request.Builder().url(source.url("/initial")).build()).execute().use { assertEquals("fixture",it.body!!.string()) }
            assertEquals("private-fixture",source.takeRequest().getHeader("Authorization"))
            val redirected = destination.takeRequest()
            assertNull(redirected.getHeader("Authorization")); assertNull(redirected.getHeader("X-Signed")); assertNull(redirected.getHeader("Cookie"))
        } finally { source.shutdown(); destination.shutdown() }
    }
}
