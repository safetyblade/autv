package dev.prestwich.autv.data

import java.net.HttpURLConnection
import java.net.URL
import java.io.InputStream
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.zip.GZIPInputStream
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler

data class Programme(val title: String, val start: Long, val stop: Long)
data class NowNext(val now: Programme?, val next: Programme?)

class Epg(val programmes: Map<String, List<Programme>>, val channelNames: Map<String, Set<String>> = emptyMap()) {
    private fun identity(name: String) = name.lowercase(Locale.ROOT).replace(Regex("""[^\p{L}\p{N}]"""), "")
    private val idsByNormalizedName: Map<String, String> = buildMap {
        val candidates = mutableMapOf<String, MutableSet<String>>()
        channelNames.forEach { (id, names) ->
            names.forEach { name -> candidates.getOrPut(identity(name)) { mutableSetOf() }.add(id) }
        }
        candidates.forEach { (name, ids) -> if (ids.size == 1) put(name, ids.first()) }
    }
    fun at(channel: Channel?, time: Long): NowNext {
        if (channel == null) return NowNext(null, null)
        if (channel.tvgId != null && programmes.containsKey(channel.tvgId)) return at(channel.tvgId, time)
        return at(idsByNormalizedName[identity(channel.name)], time)
    }
    fun at(id: String?, time: Long): NowNext {
        val schedule = programmes[id].orEmpty()
        if (schedule.isEmpty()) return NowNext(null, null)

        // Schedules are sorted by start time during parsing. Find the first programme
        // that starts after 'time' so guide rows/search do O(log n) work instead of
        // scanning the same schedule twice on every lookup.
        var low = 0
        var high = schedule.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (schedule[mid].start <= time) low = mid + 1 else high = mid
        }

        val current = schedule.getOrNull(low - 1)?.takeIf { time < it.stop }
        val next = schedule.getOrNull(low)
        return NowNext(current, next)
    }
}

class EpgRepository {
    fun load(): Epg {
        val connection = URL("https://raw.githubusercontent.com/safetyblade/autv/main/epg.xml.gz").openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000
        return try { GZIPInputStream(connection.inputStream).use { parse(it) } }
        finally { connection.disconnect() }
    }

    internal fun parse(input: InputStream): Epg {
        val result = mutableMapOf<String, MutableList<Programme>>()
        val names = mutableMapOf<String, MutableSet<String>>()
        val format = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("UTC")
        }
        fun timestamp(value: String?): Long? = runCatching {
            val normalized = value?.trim() ?: return@runCatching null
            format.parse(if (normalized.length == 14) "$normalized +0000" else normalized)?.time
        }.getOrNull()
        val handler = object : DefaultHandler() {
            var id: String? = null
            var start: Long? = null
            var stop: Long? = null
            var title = ""
            var description = ""
            var channelId: String? = null
            val text = StringBuilder()
            override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
                if (qName == "channel") channelId = attributes.getValue("id")
                if (qName == "programme") {
                    id = attributes.getValue("channel")
                    start = timestamp(attributes.getValue("start"))
                    stop = timestamp(attributes.getValue("stop"))
                    title = ""; description = ""
                }
                text.setLength(0)
            }
            override fun characters(ch: CharArray, offset: Int, length: Int) { text.append(ch, offset, length) }
            override fun endElement(uri: String?, localName: String?, qName: String) {
                if (qName == "display-name") channelId?.let { names.getOrPut(it) { mutableSetOf() }.add(text.toString().trim()) }
                if (qName == "channel") channelId = null
                if (qName == "title") title = text.toString().trim()
                if (qName == "desc") description = text.toString().trim()
                if (qName == "programme") {
                    val from = start; val until = stop; val channel = id
                    if (channel != null && from != null && until != null && until > from && title.isNotBlank() && description != "Live channel") {
                        result.getOrPut(channel) { mutableListOf() }.add(Programme(title, from, until))
                    }
                    id = null
                }
                text.setLength(0)
            }
        }
        val factory = SAXParserFactory.newInstance()
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        val reader = factory.newSAXParser().xmlReader
        reader.contentHandler = handler
        reader.setEntityResolver { _, _ -> InputSource(StringReader("")) }
        reader.parse(InputSource(input))
        return Epg(result.mapValues { (_, entries) -> entries.sortedBy { it.start } }, names)
    }
}
