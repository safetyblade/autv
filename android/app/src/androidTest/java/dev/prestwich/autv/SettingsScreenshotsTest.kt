package dev.prestwich.autv

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/** Rendered Android UI with synthetic activation only; never broadcaster playback evidence. */
@androidx.test.filters.SdkSuppress(minSdkVersion=29)
@OptIn(ExperimentalTestApi::class)
class SettingsScreenshotsTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val phase get() = InstrumentationRegistry.getArguments().getString("autvScreenshotPhase", "after")
    private fun shell(command: String): String {
        return instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes().toString(Charsets.UTF_8) }
        }
    }
    private fun screenshot(name: String) {
        val resolver=instrumentation.targetContext.contentResolver
        val values=android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"$phase-$name.png")
            put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png")
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/AUTV-settings-screenshots")
            put(android.provider.MediaStore.Images.Media.IS_PENDING,1)
        }
        val uri=checkNotNull(resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values))
        val bitmap=instrumentation.uiAutomation.takeScreenshot()
        try {
            val expected=when {
                name.startsWith("phone") -> 1080 to 1920
                name.startsWith("tv-1080p") -> 1920 to 1080
                name.startsWith("tv-4k") -> 3840 to 2160
                else -> 1280 to 720
            }
            assertEquals("Screenshot width for $name",expected.first,bitmap.width)
            assertEquals("Screenshot height for $name",expected.second,bitmap.height)
            checkNotNull(resolver.openOutputStream(uri)).use { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
            resolver.update(uri,android.content.ContentValues().apply {put(android.provider.MediaStore.Images.Media.IS_PENDING,0)},null,null)
            checkNotNull(resolver.openInputStream(uri)).use { check(it.read()>0) {"Empty screenshot for $name"} }
        } finally {bitmap.recycle()}
    }
    @Test fun phoneAndTelevisionSettingsAndActivation() {
        val requested=InstrumentationRegistry.getArguments().getString("autvScreenshotDevice")
        try {
            for ((name,size,density) in listOf(Triple("phone","1080x1920",420),Triple("tv-720p","1280x720",160),
                Triple("tv-1080p","1920x1080",240),Triple("tv-4k","3840x2160",480),Triple("tv-720p-large-text","1280x720",160))) {
                if(requested!=null && requested!=name) continue
                if(requested==null) { shell("wm size $size");shell("wm density $density");SystemClock.sleep(1000) }
                ActivationFixture.reset()
                val tv=name.startsWith("tv")
                ProviderScreenshotActivity.configureForScreenshot(tv, if(name.endsWith("large-text")) 1.3f else 1f)
                val intent=Intent(instrumentation.targetContext,ProviderScreenshotActivity::class.java)
                ActivityScenario.launch<ProviderScreenshotActivity>(intent).use { scenario ->
                    ui.onNodeWithTag("settings-button").performClick()
                    ui.waitForIdle();screenshot("$name-settings")
                    // Account-linking button behaviour is tested separately by ProviderQrIntegrationTest.
                    // This screenshot harness starts an explicit in-memory challenge so resizing cannot
                    // accidentally turn a layout check into an asynchronous connection test.
                    scenario.onActivity { it.playback.connectNineNow() }
                    ui.waitUntil(15_000) { ActivationFixture.codeRequests.get() > 0 }
                    // A window recreation may restore Settings directly to the pending provider.
                    // Open the card only when the overview is still the active screen.
                    if(ui.onAllNodesWithTag("provider-9now").fetchSemanticsNodes().isNotEmpty())
                        ui.onNodeWithTag("provider-9now").performScrollTo().performClick()
                    try {
                        ui.waitUntil(15000) { ui.onAllNodesWithTag("activation-qr").fetchSemanticsNodes().isNotEmpty() }
                    } catch (timeout: androidx.compose.ui.test.ComposeTimeoutException) {
                        screenshot("$name-activation-timeout")
                        var state = "activity unavailable"
                        scenario.onActivity { activity ->
                            val playback = activity.playback
                            state = "codes=${ActivationFixture.codeRequests.get()}, " +
                                "connection=${playback.providerConnection}, busy=${playback.providerBusy}, " +
                                "activation=${playback.providerActivation != null}, " +
                                "status=${playback.providerAuthStatus}, error=${playback.providerError}"
                        }
                        throw AssertionError("QR not visible for $name: $state", timeout)
                    }
                    ui.waitForIdle()
                    ui.onNodeWithTag("activation-qr").performScrollTo()
                    ui.waitForIdle();screenshot("$name-activation")
                    if(phase=="after") {
                        val qr=ui.onNodeWithTag("activation-qr").getUnclippedBoundsInRoot()
                        val viewport=ui.onNodeWithTag("provider-activation-screen").getUnclippedBoundsInRoot()
                        assertTrue("QR centred in usable viewport ($name)",abs((qr.left.value+qr.right.value-viewport.left.value-viewport.right.value)/2)<2f)
                        try { ui.onNodeWithTag("activation-qr").assertIsDisplayed() }
                        catch (error: AssertionError) { throw AssertionError("QR not displayed for $name: qr=$qr viewport=$viewport",error) }
                        ui.onNodeWithTag("activation-code").assertIsDisplayed()
                        val before=qr
                        ui.onNodeWithTag("activation-open-website").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
                        ui.onNodeWithTag("activation-open-website").performKeyInput {
                            keyDown(androidx.compose.ui.input.key.Key.DirectionDown);keyUp(androidx.compose.ui.input.key.Key.DirectionDown)
                        }
                        ui.onNodeWithTag("activation-cancel").assertIsFocused()
                        val after=ui.onNodeWithTag("activation-qr").getUnclippedBoundsInRoot()
                        assertEquals((before.left.value+before.right.value)/2,(after.left.value+after.right.value)/2,0.5f)
                    }
                    scenario.onActivity { it.playback.disconnectNineNow() }
                }
            }
        } finally { if(requested==null) {shell("wm size reset");shell("wm density reset")};ProviderScreenshotActivity.configureForScreenshot(false,1f);ActivationFixture.reset() }
    }
}
