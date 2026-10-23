package dev.prestwich.autv

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import dev.prestwich.autv.data.provider.DeviceActivation
import dev.prestwich.autv.provider.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ProviderQrUiTest {
    @get:Rule val ui = createComposeRule()
    @Test fun displayedQrScansAndRemoteCanReachCancel() {
        var cancelled = false
        val challenge = DeviceActivation("123456", "private-fixture", ActivationDestination.NINE_NOW.address, System.currentTimeMillis() + 120000, 5000)
        ui.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            ProviderActivationScreen("9Now", challenge, ActivationDestination.NINE_NOW, {}, { cancelled = true })
        } } }
        val bitmap = ui.onNodeWithTag("activation-qr").performScrollTo().captureToImage().asAndroidBitmap()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val decoded = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, pixels)))).text
        assertEquals("https://www.9now.com.au/activate", decoded)
        ui.onNodeWithTag("activation-code").performScrollTo().assertTextEquals("123456")
        ui.onNodeWithTag("activation-address").performScrollTo().assertTextEquals(decoded)
        ui.onNodeWithTag("activation-open-website").performScrollTo().performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        ui.onNodeWithTag("activation-open-website").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        ui.onNodeWithTag("activation-cancel").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        ui.runOnIdle { assertTrue(cancelled) }
    }
    @Test fun qrDisappearsWhenTheOriginalDeadlinePasses() {
        val challenge = DeviceActivation("fixture", "private-fixture", ActivationDestination.NINE_NOW.address, System.currentTimeMillis() + 2500, 5000)
        ui.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            ProviderActivationScreen("9Now", challenge, ActivationDestination.NINE_NOW, {}, {})
        } } }
        ui.onNodeWithTag("activation-qr").assertExists()
        ui.waitUntil(6000) { ui.onAllNodesWithTag("activation-qr").fetchSemanticsNodes().isEmpty() }
        ui.onNodeWithTag("activation-code").assertDoesNotExist()
        ui.onNodeWithTag("activation-open-website").performScrollTo().assertIsNotEnabled()
    }
    @Test fun expiredChallengeHidesQrAndDisablesWebsite() {
        val challenge = DeviceActivation("fixture", "private-fixture", ActivationDestination.NINE_NOW.address, System.currentTimeMillis() - 1000, 5000)
        ui.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            ProviderActivationScreen("9Now", challenge, ActivationDestination.NINE_NOW, {}, {})
        } } }
        ui.onNodeWithTag("activation-qr").assertDoesNotExist()
        ui.onNodeWithTag("activation-code").assertDoesNotExist()
        ui.onNodeWithTag("activation-expired").performScrollTo().assertExists()
        ui.onNodeWithTag("activation-open-website").performScrollTo().assertIsNotEnabled()
        ui.onNodeWithTag("activation-cancel").performScrollTo().assertIsEnabled()
    }
}
