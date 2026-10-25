package dev.prestwich.autv.guide

import dev.prestwich.autv.data.Channel

/** Display order only. Provider IDs, persisted numbers and manual Favourites are never rewritten. */
object AustralianChannelOrder {
    private val networks=listOf("abc","sbs","seven","9now","ten")
    private val names=mapOf(
        "abc" to listOf("abc","abctv","abc1","abckids","abcfamily","abcentertains","abcnews"),
        "sbs" to listOf("sbs","sbshd","sbsworldmovies","sbsviceland","sbsfood","sbsworldwatch","nitv"),
        "seven" to listOf("7","seven","channel7","7hd","7two","7mate","7flix","7bravo"),
        "9now" to listOf("9","nine","channel9","channelnine","9hd","9gem","9go","9life","9rush"),
        "ten" to listOf("10","ten","channel10","network10","10hd","10peach","10bold","nickelodeon"))
    fun withinNativeSlots(rows: List<Channel>): List<Channel> {
        val native=order(rows.filter { it.providerChannel!=null }).iterator()
        return rows.map { if(it.providerChannel!=null) native.next() else it }
    }
    fun order(rows: List<Channel>): List<Channel> = rows.withIndex().sortedWith(compareBy(
        { networks.indexOf(it.value.providerChannel?.id?.provider).takeIf { rank -> rank>=0 } ?: networks.size },
        { val provider=it.value.providerChannel?.id?.provider
            val name=it.value.name.lowercase().filter(Char::isLetterOrDigit).removeSuffix("victoria").removeSuffix("vic").removeSuffix("hd")
            names[provider]?.indexOf(name)?.takeIf { rank -> rank>=0 } ?: 100 },
        { it.index })).map { it.value }
}
