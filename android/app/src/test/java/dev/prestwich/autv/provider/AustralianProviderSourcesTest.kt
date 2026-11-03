package dev.prestwich.autv.provider

import dev.prestwich.autv.data.provider.*
import org.junit.Assert.*
import org.junit.Test

class AustralianProviderSourcesTest {
    private fun abc() = AbcProviderConfiguration(AbcClientRegistration(
        "synthetic-product", "synthetic-key", "VIC", "MAIN_FIXTURE", { _, _, _ -> error("must not sign during construction") }))
    private fun ten() = TenProviderConfiguration(object : TenClientRegistration {
        override val allowedOrigins = setOf("https://10.com.au")
        override val configParameters = mapOf("client" to "fixture")
        override val deviceFields = mapOf("machine" to "fixture")
        override val playbackParameters = mapOf("client" to "fixture")
        override val liveFormat = TenLiveProtocol.Format.HLS
        override fun authorise(method: String, exactUrl: String, jsonBody: String?): Map<String, String> = error("must not sign during construction")
    }, "MAIN_FIXTURE")

    @Test fun providersLoadIndependentlyAndAbsentSourcesNeverBecomeConnections() {
        val abc=abc(); val ten=ten()
        val onlyAbc=loadAustralianProviderConfiguration({abc},{null})
        assertSame(abc.registration,onlyAbc.abc); assertNull(onlyAbc.ten)
        val onlyTen=loadAustralianProviderConfiguration({null},{ten})
        assertSame(ten.registration,onlyTen.ten); assertNull(onlyTen.abc)
        val absent=loadAustralianProviderConfiguration({null},{null})
        assertNull(absent.abc); assertNull(absent.ten); assertTrue(absent.configurationFailures.isEmpty())
    }
    @Test fun sourceFailureCannotSuppressSiblingOrDisclosePrivateError() {
        val abc=abc(); val ten=ten()
        val badAbc=loadAustralianProviderConfiguration({error("private-signing-input")},{ten})
        assertEquals(setOf("abc"),badAbc.configurationFailures); assertSame(ten.registration,badAbc.ten)
        val badTen=loadAustralianProviderConfiguration({abc},{throw NoClassDefFoundError("private-input")})
        assertEquals(setOf("ten"),badTen.configurationFailures); assertSame(abc.registration,badTen.abc)
        assertFalse(badAbc.toString().contains("private")); assertFalse(badTen.toString().contains("private"))
    }
    @Test fun missingOrWrongSourceClassFailsOnlyItsOwnProvider() {
        val ten=ten()
        val configuration=loadAustralianProviderConfiguration(
            { registrationSource<AbcRegistrationSource>("not.installed.AbcSource"); null }, {ten})
        assertEquals(setOf("abc"),configuration.configurationFailures);assertSame(ten.registration,configuration.ten)
        assertNull(registrationSource<AbcRegistrationSource>(""))
        assertThrows(ClassCastException::class.java) { registrationSource<AbcRegistrationSource>("java.lang.String") }
    }
}
