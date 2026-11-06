package dev.prestwich.autv.provider

import org.junit.Assert.*
import org.junit.Test

/** Only actual owner-approved live streams may be published by default. */
class AustralianLiveChannelsTest {
    private val channels get() = AustralianLiveChannels.approved

    @Test fun sixApprovedChannelsAreStableAndNotBoundToAccountSessions() {
        assertEquals(6, channels.size)
        assertEquals(6, channels.map { it.number }.distinct().size)
        assertEquals(6, channels.map { it.tvgId }.distinct().size)
        assertEquals(3, AustralianLiveChannels.count("abc"))
        assertEquals(3, AustralianLiveChannels.count("ten"))
        assertEquals(0, AustralianLiveChannels.count("9now"))
        assertEquals(
            listOf("ABC TV VIC", "ABC NEWS", "ABC Kids", "10 VIC", "10 Drama", "10 Comedy"),
            channels.map { it.name }
        )
        assertEquals(listOf(2, 20, 22, 10, 11, 12), channels.map { it.number })
    }

    @Test fun noUnapprovedOrUnavailableChannelIsPublished() {
        assertFalse(channels.any {
            it.name.contains("Nickelodeon", ignoreCase = true) ||
                it.name.contains("trial", ignoreCase = true)
        })
        channels.forEach { item ->
            assertTrue(item.tvgId.startsWith("autv."))
            assertFalse(item.tvgId.startsWith("autv-debug"))
            assertTrue(item.url.startsWith("https://"))
            assertFalse(item.url.contains("token=", ignoreCase = true))
            assertFalse(item.url.contains("jwt=", ignoreCase = true))
        }
    }

    @Test fun validatedTenSourcesKeepDaiRedirectFormAndAbcSourcesRemainIndependent() {
        val abc = channels.filter { it.provider == "ABC iview" }
        val ten = channels.filter { it.provider == "10" }
        assertEquals(3, abc.size)
        assertEquals(3, ten.size)
        assertTrue(abc.all { it.url.startsWith("https://c.mjh.nz/abc-") })
        assertTrue(ten.all { it.url.startsWith("https://i.mjh.nz/.r/10") })
    }
}
