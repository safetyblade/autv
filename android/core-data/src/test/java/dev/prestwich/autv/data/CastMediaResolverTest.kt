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
    @Test fun htmlAndForbiddenResponsesFailButAcceptedMacroFieldsAreNeverStripped() {
        MockWebServer().use { server -> server.start()
            server.enqueue(MockResponse().setResponseCode(403).setBody("private body must never escape"))
            try { CastMediaResolver(OkHttpClient.Builder().followRedirects(false).build()).resolve(server.url("/live.m3u8").toString());fail() }
            catch(e: CastMediaException) { assertEquals(CastLoadFailure.URL_UNAVAILABLE,e.kind);assertEquals(403,e.status);assertFalse(e.toString().contains("private")) }
            server.enqueue(MockResponse().setHeader("Content-Type","text/html").setBody("<html>not video</html>"))
            try { CastMediaResolver(OkHttpClient.Builder().followRedirects(false).build()).resolve(server.url("/live.m3u8").toString());fail() }
            catch(e: CastMediaException) { assertEquals(CastLoadFailure.UNSUPPORTED_FORMAT,e.kind) }
            server.enqueue(MockResponse().setHeader("Content-Type","application/x-mpegURL").setBody(hls))
            val supplied=server.url("/live.m3u8?session=%5BTOKEN%5D&ads=normal").toString()
            assertEquals(supplied,CastMediaResolver(OkHttpClient.Builder().followRedirects(false).build()).resolve(supplied).url)
            assertEquals(3,server.requestCount)
        }
    }
    @Test fun redirectLoopsDoNotHangOrRemoveQueryTokens() {
        MockWebServer().use { server -> server.start()
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location","/live?token=original"))
            try { CastMediaResolver(OkHttpClient.Builder().followRedirects(false).build()).resolve(server.url("/live?token=original").toString());fail() }
            catch(e: CastMediaException) { assertEquals(CastLoadFailure.URL_UNAVAILABLE,e.kind) }
        }
    }
    @Test fun inspectsVariantAndSegmentWithoutReplacingMasterOrLoggingSecrets() {
        MockWebServer().use { server ->
            server.start()
            val master="#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1000,CODECS=\"avc1.640028,mp4a.40.2\"\nvariant.m3u8?token=variant-secret\n"
            val variant="#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXT-X-MAP:URI=\"init.mp4?token=init-secret\"\n#EXT-X-KEY:METHOD=SAMPLE-AES,URI=\"secret-key\"\n#EXTINF:6,\nsegment.m4s?token=segment-secret\n"
            server.enqueue(MockResponse().setChunkedBody(master,7))
            server.enqueue(MockResponse().setBody(variant))
            server.enqueue(MockResponse().setResponseCode(206).setBody("0000styprest"))
            val events=mutableListOf<String>()
            val resolver=CastMediaResolver(OkHttpClient.Builder().followRedirects(false).build(),false,events::add)
            val source=resolver.resolve(server.url("/master.m3u8?token=master-secret").toString())
            assertEquals("Child diagnostics must not delay LOAD",1,server.requestCount)
            resolver.diagnose(source)
            assertEquals(server.url("/master.m3u8?token=master-secret").toString(),source.url)
            assertFalse("Variant evidence does not mutate an already submitted LOAD",source.fragmentedMp4)
            assertTrue(events.any { it.contains("map=true") })
            assertTrue(events.any { it.contains("codecs=avc1,mp4a") })
            assertTrue(events.any { it.contains("encryption=SAMPLE-AES") })
            assertTrue(events.any { it.contains("stage=segment container=fmp4") })
            assertFalse(events.joinToString().contains("secret"))
            assertFalse(events.joinToString().contains("token="))
            server.takeRequest();server.takeRequest()
            assertEquals("bytes=0-1023",server.takeRequest().getHeader("Range"))
        }
    }
    @Test fun failedChildProbeIsEvidenceAndDoesNotRejectOtherwiseAcceptedMaster() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1000\nvariant.m3u8\n"))
            server.enqueue(MockResponse().setResponseCode(403).setBody("sensitive rejection body"))
            val events=mutableListOf<String>()
            val url=server.url("/master.m3u8").toString()
            assertEquals(url,CastMediaResolver(OkHttpClient.Builder().followRedirects(false).build(),true,events::add).resolve(url).url)
            assertTrue(events.any { it.contains("stage=variant") && it.contains("http=403") })
            assertFalse(events.joinToString().contains("sensitive"))
        }
    }

}
