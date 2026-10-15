package dev.prestwich.autv

import dev.prestwich.autv.creator.*
import org.junit.Assert.*
import org.junit.Test

/** Contract fixtures, not claims of authenticated live API or video playback verification. */
class CreatorAvailabilityTest {
    private val channelId = "UCJ5v_MCY6GNUBTO8-D3XoAg"
    private val videoId = "abcdefghijk"
    private fun youtube(embeddable: Boolean = true, owner: String = channelId, ended: Boolean = false) =
        CreatorAvailability { url, _ ->
            if (url.contains("/search?")) """{"items":[{"id":{"videoId":"$videoId"}}]}"""
            else """{"items":[{"id":"$videoId","snippet":{"channelId":"$owner","liveBroadcastContent":"live"},"status":{"embeddable":$embeddable},"liveStreamingDetails":{${if (ended) "\"actualEndTime\":\"2026-10-15T01:00:00Z\"" else ""}}}]}"""
        }
    @Test fun missingCredentialsNeverIssueRequestsOrClaimOffline() {
        val api = CreatorAvailability { _, _ -> error("Must not call network") }
        assertEquals(LiveStatus.UNKNOWN, api.youtube(channelId, "").status)
        assertEquals(LiveStatus.UNKNOWN, api.twitch("gamesdonequick", "", "").status)
    }
    @Test fun twitchFiltersTheRequestedIdentityAndUsesOfficialHeaders() {
        val api = CreatorAvailability { url, headers ->
            assertEquals("https://api.twitch.tv/helix/streams?user_login=gamesdonequick", url)
            assertEquals("Bearer token", headers["Authorization"])
            assertEquals("client", headers["Client-Id"])
            """{"data":[{"user_login":"gamesdonequick"}]}"""
        }
        assertEquals(LiveStatus.LIVE, api.twitch("gamesdonequick", "client", "token").status)
        assertEquals(LiveStatus.OFFLINE, CreatorAvailability { _, _ -> """{"data":[]}""" }.twitch("gamesdonequick", "client", "token").status)
    }
    @Test fun apiFailuresAndMalformedResponsesRemainUnknown() {
        for (api in listOf(CreatorAvailability { _, _ -> error("HTTP 401/403/429/500 or network") }, CreatorAvailability { _, _ -> "not json" })) {
            assertEquals(LiveStatus.UNKNOWN, api.youtube(channelId, "key").status)
            assertEquals(LiveStatus.UNKNOWN, api.twitch("gamesdonequick", "client", "token").status)
        }
    }
    @Test fun youtubeDistinguishesOfflineAndEmbeddingRestriction() {
        assertEquals(LiveStatus.OFFLINE, CreatorAvailability { _, _ -> """{"items":[]}""" }.youtube(channelId, "key").status)
        assertEquals(LiveStatus.UNAVAILABLE, youtube(embeddable = false).youtube(channelId, "key").status)
        assertNull(youtube(embeddable = false).youtube(channelId, "key").videoId)
    }
    @Test fun youtubeVerifiesOwnerAndCurrentLiveStateBeforeEmbedding() {
        val live = youtube().youtube(channelId, "key")
        assertEquals(LiveStatus.LIVE, live.status)
        assertEquals(videoId, live.videoId)
        assertEquals(LiveStatus.UNKNOWN, youtube(owner = "another-channel").youtube(channelId, "key").status)
        assertEquals(LiveStatus.UNKNOWN, youtube(ended = true).youtube(channelId, "key").status)
    }
    @Test fun curatedIdentitiesHaveNoFastNumbersOrStreamUrls() {
        assertEquals(2, CreatorCatalogue.channels.size)
        assertTrue(CreatorCatalogue.gdq.source is PlaybackSource.Twitch)
        assertTrue(CreatorCatalogue.wwe.source is PlaybackSource.YouTube)
        assertEquals(2, CreatorCatalogue.channels.map { it.id }.distinct().size)
    }
}
