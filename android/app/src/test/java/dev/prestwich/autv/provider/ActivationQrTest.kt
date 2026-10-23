package dev.prestwich.autv.provider

import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import dev.prestwich.autv.data.provider.DeviceActivation
import org.junit.Assert.*
import org.junit.Test

class ActivationQrTest {
    private fun challenge(url: String) = DeviceActivation("manual-code-fixture", "private-device-fixture", url, 999999, 5000)
    @Test fun qrRoundTripContainsOnlyTheOfficialAddress() {
        val matrix = ActivationQr.encode(challenge(ActivationDestination.NINE_NOW.address), ActivationDestination.NINE_NOW)!!
        val pixels = IntArray(matrix.width * matrix.height) { i -> if (matrix[i % matrix.width, i / matrix.width]) -0x1000000 else -1 }
        val decoded = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(matrix.width, matrix.height, pixels)))).text
        assertEquals("https://www.9now.com.au/activate", decoded)
        assertFalse(decoded.contains("manual-code-fixture"))
        assertFalse(decoded.contains("private-device-fixture"))
    }
    @Test fun sbsUsesOnlyItsReviewedOfficialAddress() {
        val sbs = challenge(ActivationDestination.SBS.address)
        assertEquals("https://sbs.com.au/connect", ActivationQr.payload(sbs, ActivationDestination.SBS))
        assertNull(ActivationQr.payload(sbs, ActivationDestination.NINE_NOW))
        assertNull(ActivationQr.payload(challenge("https://sbs.com.au/connect?code=fixture"), ActivationDestination.SBS))
    }
    @Test fun rejectsUnreviewedUrlsAndPrefillParameters() {
        for (url in listOf("http://www.9now.com.au/activate", "https://www.9now.com.au.evil.invalid/activate",
            "https://www.9now.com.au/activate?code=fixture", "https://www.9now.com.au/activate#token=fixture",
            "https://user:password@www.9now.com.au/activate", "https://example.invalid/redirect")) {
            assertNull(ActivationQr.payload(challenge(url), ActivationDestination.NINE_NOW))
            assertNull(ActivationQr.encode(challenge(url), ActivationDestination.NINE_NOW))
        }
    }
}
