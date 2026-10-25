package dev.prestwich.autv.data

import dev.prestwich.autv.data.provider.*
import org.junit.Assert.*
import org.junit.Test

class NativeProviderEpgTest {
    @Test fun privateProgrammeIdentityOverridesPublicNameAliasesAndFeedsSearch() {
        val native=ProviderChannel(ProviderChannelId("ten","returned-key"),"returned-key","10 VIC",programmes=listOf(
            ProviderProgramme("Next native",2000,3000),ProviderProgramme("Current native",1000,2000)))
        val channel=Channel(-9010,"10 VIC","Australian TV","10","","",true,null,native.id.favouriteKey,null,native)
        val epg=Epg(mapOf("public-ten" to listOf(Programme("Wrong region",0,5000))),mapOf("public-ten" to setOf("10 VIC")))
        assertEquals("Current native",epg.at(channel,1500).now?.title)
        assertEquals("Next native",epg.at(channel,1500).next?.title)
        assertEquals(listOf("Current native","Next native"),epg.schedule(channel).map { it.title })
        assertEquals("Wrong region",epg.at("public-ten",1500).now?.title)
    }
}
