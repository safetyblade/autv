package dev.prestwich.autv.provider

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import dev.prestwich.autv.data.provider.DeviceActivation

/** Add destinations only after reviewing the provider's official HTTPS activation flow. */
internal enum class ActivationDestination(val address: String) {
    NINE_NOW("https://www.9now.com.au/activate"),
}

/** Local encoding only. The challenge's code, device ID and session never enter the QR. */
internal object ActivationQr {
    fun payload(challenge: DeviceActivation, destination: ActivationDestination): String? =
        destination.address.takeIf { challenge.verificationUrl == it }

    fun encode(challenge: DeviceActivation, destination: ActivationDestination): BitMatrix? {
        val address = payload(challenge, destination) ?: return null
        return MultiFormatWriter().encode(address, BarcodeFormat.QR_CODE, 512, 512,
            mapOf(EncodeHintType.MARGIN to 4, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M))
    }
}
