package dev.prestwich.autv.provider

/**
 * The six live channels physically approved by the owner in Victoria on 6 Nov 2026.
 * No account link is required for these external public HLS streams.
 *
 * Provider labels describe the programming; the URLs are maintained by third parties
 * and do not imply broadcaster distribution approval or a registered ABC/10 session.
 * The streams retain their normal in-stream/DAI advertising.
 */
internal object AustralianLiveChannels {
    internal data class LiveChannel(
        val number: Int,
        val name: String,
        val tvgId: String,
        val provider: String,
        val url: String,
    )

    val approved = listOf(
        LiveChannel(2, "ABC TV VIC", "autv.abc-tv-vic", "ABC iview",
            "https://c.mjh.nz/abc-vic.m3u8"),
        LiveChannel(20, "ABC NEWS", "autv.abc-news", "ABC iview",
            "https://c.mjh.nz/abc-news.m3u8"),
        LiveChannel(22, "ABC Kids", "autv.abc-kids", "ABC iview",
            "https://c.mjh.nz/abc-kids.m3u8"),
        LiveChannel(10, "10 VIC", "autv.10-vic-live", "10",
            "https://i.mjh.nz/.r/10-vic.m3u8"),
        LiveChannel(11, "10 Drama", "autv.10-drama", "10",
            "https://i.mjh.nz/.r/10bold-vic.m3u8"),
        LiveChannel(12, "10 Comedy", "autv.10-comedy", "10",
            "https://i.mjh.nz/.r/10peach-vic.m3u8"),
    )

    fun count(provider: String): Int = approved.count {
        if (provider == "abc") it.provider == "ABC iview" else provider == "ten" && it.provider == "10"
    }
}
