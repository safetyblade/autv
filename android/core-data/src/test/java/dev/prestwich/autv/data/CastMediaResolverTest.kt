package dev.prestwich.autv.data

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class CastMediaResolverTest {
    private val hls="#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\nsegment.ts\n"
    @Test fun resolvesFreshRedirectTargetWithoutLosingParametersOrForwardingCookies() {
        MockWebServer().use { first -> MockWebServer().use { second ->
            first.start();second.start()
            val original=first.url("/redirect?token=a%2Bb&ads=normal").toString()
            first.enqueue(MockResponse().setResponseCode(302).setHeader("Location",second.url("/issued?session=short-lived")).setHeader("Set-Cookie","private-cookie=must-not-forward"))
            second.enqueue(MockResponse().setHeader("Content-Type","application/octet-stream").setBody(hls))
            val source=CastMediaResolver(OkHttpClient.Builder().followRedirects(false).build()).resolve(original)
            assertEquals(second.url("/issued?session=short-lived").toString(),source.url);assertEquals("application/x-mpegURL",source.mimeType);assertTrue(source.live)
            assertEquals("/redirect?token=a%2Bb&ads=normal",first.takeRequest().path)
            val target=second.takeRequest();assertNull(target.getHeader("Authorization"));assertNull(target.getHeader("Cookie"))
        } }
    }
    @Test fun identifiesDashAndBufferedMediaRatherThanAlwaysHls() {
        assertEquals("application/x-mpegURL",CastMediaResolver.describe("https://example/live","https://example/live","text/html",hls.toByteArray()).mimeType)
        assertEquals("application/dash+xml",CastMediaResolver.describe("https://example/live","https://example/live",null,"<MPD type=\"dynamic\"></MPD>".toByteArray()).mimeType)
        assertFalse(CastMediaResolver.describe("https://example/movie.mp4","https://example/movie.mp4","video/mp4",byteArrayOf()).live)
        assertFalse(CastMediaResolver.describe("https://example/live.m3u8","https://example/live.m3u8",null,(hls+"#EXT-X-ENDLIST").toByteArray()).live)
        assertTrue(CastMediaResolver.describe("https://example/live","https://example/live",null,"<MPD type=\"dynamic\"></MPD>".toByteArray()).live)
        assertTrue(CastMediaResolver.describe("https://example/live","https://example/live",null,(hls+"#EXT-X-MAP:URI=\"init.mp4\"").toByteArray()).fragmentedMp4)
        assertEquals("application/dash+xml",CastMediaResolver.fromAddress("https://example/live.mpd?session=1").mimeType)
    }
    @Test fun htmlForbiddenAndUnresolvedMacrosAreDifferentFailures() {
        MockWebServer().use { server -> server.start()
            server.enqueue(MockResponse().setResponseCode(403).setBody("private body must never escape"))
            try { CastMediaResolver().resolve(server.url("/live.m3u8").toString());fail() }
            catch(e: CastMediaException) { assertEquals(CastLoadFailure.URL_UNAVAILABLE,e.kind);assertEquals(403,e.status);assertFalse(e.toString().contains("private")) }
            server.enqueue(MockResponse().setHeader("Content-Type","text/html").setBody("<html>not video</html>"))
            try { CastMediaResolver().resolve(server.url("/live.m3u8").toString());fail() }
            catch(e: CastMediaException) { assertEquals(CastLoadFailure.UNSUPPORTED_FORMAT,e.kind) }
            try { CastMediaResolver().resolve(server.url("/live.m3u8?session=%5BTOKEN%5D").toString());fail() }
            catch(e: CastMediaException) { assertEquals(CastLoadFailure.SENDER_REQUIREMENTS,e.kind) }
        }
    }
    @Test fun redirectLoopsDoNotHangOrRemoveQueryTokens() {
        MockWebServer().use { server -> server.start()
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location","/live?token=original"))
            try { CastMediaResolver().resolve(server.url("/live?token=original").toString());fail() }
            catch(e: CastMediaException) { assertEquals(CastLoadFailure.URL_UNAVAILABLE,e.kind) }
        }
    }
}
