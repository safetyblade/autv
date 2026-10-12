package dev.prestwich.autv.guide

import dev.prestwich.autv.data.*
import org.junit.Assert.*
import org.junit.Test

class FavouriteIdentityTest {
    private val channel = Channel(200, "Watch AEW", "Sport", "", "", "", true, "https://example.com/200?token=one", "provider-200", null)
    @Test fun metadataNumbersStreamsAndAvailabilityDoNotChangeCanonicalId() {
        val key = FavouriteIdentity.key(channel)!!
        val refreshed = channel.copy(number = 900, name = "New editorial title", genre = "Other", description = "new", streamUrl = null, available = false)
        assertEquals(key, FavouriteIdentity.key(refreshed))
        assertTrue(FavouriteIdentity.contains(refreshed, setOf(key)))
    }
    @Test fun duplicateCanonicalIdsDeliberatelyShareMembership() {
        val duplicate = channel.copy(number = 201, streamUrl = "https://other.example/live")
        assertTrue(FavouriteIdentity.contains(duplicate, setOf(FavouriteIdentity.key(channel)!!)))
        assertFalse(FavouriteIdentity.contains(duplicate.copy(tvgId = "other"), setOf(FavouriteIdentity.key(channel)!!)))
    }
    @Test fun missingIdUsesStableUrlAndDropsRotatingCredentials() {
        val missing = channel.copy(tvgId = null)
        assertEquals(FavouriteIdentity.key(missing), FavouriteIdentity.key(missing.copy(number = 99, name = "Renamed", streamUrl = "https://example.com/200?token=two&jwt=three")))
        assertFalse(FavouriteIdentity.key(missing)!!.contains("example.com"))
    }
    @Test fun routingParametersRemainPartOfIdentity() {
        val missing = channel.copy(tvgId = null, streamUrl = "https://example.com/live?channel=200&token=one")
        assertNotEquals(FavouriteIdentity.key(missing), FavouriteIdentity.key(missing.copy(streamUrl = "https://example.com/live?channel=201&token=one")))
    }
    @Test fun noIdAndNoUrlNeverGuessesFromNameOrNumber() {
        assertNull(FavouriteIdentity.key(channel.copy(tvgId = " ", streamUrl = null)))
    }
    @Test fun filteringPreservesCanonicalOrderAndMissingExpiredEpgAndBrokenChannels() {
        val channels = listOf(channel, channel.copy(number = 201, tvgId = "201"), channel.copy(number = 203, tvgId = "203", available = false, streamUrl = null), channel.copy(number = 297, tvgId = "297"))
        val keys = channels.filter { it.number != 201 }.mapNotNull(FavouriteIdentity::key).toSet()
        val filtered = channels.filter { FavouriteIdentity.contains(it, keys) }
        assertEquals(listOf(200, 203, 297), filtered.map { it.number })
        val epg = Epg(mapOf("provider-200" to listOf(Programme("Current", 100, 200)), "297" to listOf(Programme("Expired", 0, 50))))
        assertEquals(listOf(200, 203, 297), GuideBrowse.filter(filtered, "All", "", epg, 150).map { it.number })
    }
}
