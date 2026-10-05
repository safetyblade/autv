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
    fun at(channel: Channel?, time: Long): NowNext {
        if (channel == null) return NowNext(null, null)
        if (channel.tvgId != null && programmes.containsKey(channel.tvgId)) return at(channel.tvgId, time)
        fun identity(name: String) = name.lowercase(Locale.ROOT).replace(Regex("""[^\p{L}\p{N}]"""), "")
        val matches = channelNames.filterValues { names -> names.any { identity(it) == identity(channel.name) } }.keys
        return at(matches.singleOrNull(), time)
    }
    fun at(id: String?, time: Long): NowNext {
        val schedule = programmes[id].orEmpty()
        return NowNext(schedule.firstOrNull { it.start <= time && time < it.stop }, schedule.firstOrNull { it.start > time })
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
