package dev.prestwich.autv.provider

import androidx.test.platform.app.InstrumentationRegistry
import dev.prestwich.autv.data.provider.*
import org.junit.Assert.*
import org.junit.Test

class AustralianConfigurationIsolationTest {
    @Test fun invalidTenConstructionPreservesAbcAndWorkingProviders() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val abc=AbcClientRegistration("", "", "VIC", "VIC_FIXTURE", { _, _, _ -> error("no signing during construction") }, accountLinkingEnabled=false)
        val ten=object : TenClientRegistration {
            override val allowedOrigins=setOf("https://foreign.invalid")
            override val configParameters=emptyMap<String,String>()
            override val deviceFields=mapOf("machine" to "fixture")
            override val playbackParameters=mapOf("client" to "fixture")
            override val liveFormat=TenLiveProtocol.Format.HLS
            override fun authorise(method: String, exactUrl: String, jsonBody: String?)=emptyMap<String,String>()
        }
        val failures=mutableSetOf<String>()
        val adapters=configuredAustralianAdapters(context,AustralianProviderConfiguration(
            abc=abc,abcAnonymousLiveApproved=true,ten=ten,tenProofChannelKey="TEN_FIXTURE"),failures::add)
        assertEquals(setOf("abc","sbs","seven"),adapters.keys)
        assertEquals(setOf("ten"),failures)
        assertEquals(ProviderConnection.NOT_CONNECTED,adapters.getValue("abc").connection)
    }
}
