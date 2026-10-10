package dev.prestwich.autv.data

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream
import org.junit.Assert.*
import org.junit.Test

class EpgCacheTest {
    private fun gzip(text: String): ByteArray = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(text.toByteArray()) } }.toByteArray()
    @Test fun cacheSurvivesUnavailableEmptyAndMalformedRefresh() {
        val directory = kotlin.io.path.createTempDirectory().toFile()
        try {
            val file = File(directory, "epg.gz")
            val xml = """<tv><programme channel="aew" start="20261010100000 +1100" stop="20261010110000 +1100"><title>Real schedule</title><desc>Programme description</desc></programme></tv>"""
            val expected = EpgRepository(file) { gzip(xml) }.load()
            val original = file.readBytes()
            assertEquals("Programme description", expected.programmes["aew"]!!.first().description)
            for (download in listOf<() -> ByteArray>({ error("Offline") }, { gzip("<tv/>") }, { gzip("broken") })) {
                assertEquals(expected.programmes, EpgRepository(file, download).load().programmes)
                assertArrayEquals(original, file.readBytes())
            }
            assertEquals(expected.programmes, EpgRepository(file).cached()!!.programmes)
        } finally { directory.deleteRecursively() }
    }
    @Test fun badCacheDoesNotCrashCachedStartupAndRenameKeepsProviderIdentity() {
        val file = File.createTempFile("epg", ".gz")
        try {
            file.writeText("invalid")
            assertNull(EpgRepository(file).cached())
            val schedule = listOf(Programme("Actual title", 100, 200))
            val epg = Epg(mapOf("stable-id" to schedule))
            val channel = Channel(200, "Watch AEW", "Sport", "", "", "", true, "https://example.com/live", "stable-id", null)
            assertEquals(schedule, epg.schedule(channel.copy(name = "Renamed channel")))
        } finally { file.delete() }
    }
}
