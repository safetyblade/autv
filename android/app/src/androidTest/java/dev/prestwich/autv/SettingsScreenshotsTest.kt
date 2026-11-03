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
import java.io.File
import kotlin.math.abs

/** Rendered Android UI with synthetic activation only; never broadcaster playback evidence. */
@OptIn(ExperimentalTestApi::class)
class SettingsScreenshotsTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val phase get() = InstrumentationRegistry.getArguments().getString("autvScreenshotPhase", "after")
    private fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
    }
    private fun screenshot(name: String) {
        val target=File(instrumentation.targetContext.getExternalFilesDir(null),"settings-screenshots/$phase-$name.png")
        target.parentFile!!.mkdirs()
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
        }
        shell("mkdir -p /sdcard/Download/autv-settings-screenshots")
        shell("cp ${target.absolutePath} /sdcard/Download/autv-settings-screenshots/$phase-$name.png")
    }
    @Test fun phoneAndTelevisionSettingsAndActivation() {
        try {
            for ((name,size,density) in listOf(Triple("phone","1080x1920",420),Triple("tv-720p","1280x720",160),
                Triple("tv-1080p","1920x1080",240),Triple("tv-4k","3840x2160",480))) {
                shell("wm size $size");shell("wm density $density");SystemClock.sleep(1000)
                ActivationFixture.reset()
                val tv=name.startsWith("tv")
                ProviderScreenshotActivity.television=tv
                val intent=Intent(instrumentation.targetContext,ProviderScreenshotActivity::class.java).putExtra("tv",tv)
                ActivityScenario.launch<ProviderScreenshotActivity>(intent).use { scenario ->
                    ui.onNodeWithTag("settings-button").performClick()
                    ui.waitForIdle();screenshot("$name-settings")
                    ui.onNodeWithTag("provider-9now").performScrollTo().performClick()
                    ui.onNodeWithTag("9now-connect").performScrollTo().performClick()
                    ui.waitUntil(15000) { ui.onAllNodesWithTag("activation-qr").fetchSemanticsNodes().isNotEmpty() }
                    ui.onNodeWithTag("activation-qr").performScrollTo()
                    ui.waitForIdle();screenshot("$name-activation")
                    if(phase=="after") {
                        val qr=ui.onNodeWithTag("activation-qr").getUnclippedBoundsInRoot()
                        val viewport=ui.onNodeWithTag("provider-activation-screen").getUnclippedBoundsInRoot()
                        assertTrue("QR centred in usable viewport ($name)",abs((qr.left.value+qr.right.value-viewport.left.value-viewport.right.value)/2)<2f)
                        ui.onNodeWithTag("activation-qr").assertIsDisplayed()
                        ui.onNodeWithTag("activation-code").assertIsDisplayed()
                        val before=qr
                        ui.onNodeWithTag("activation-open-website").performScrollTo().performKeyInput {
                            keyDown(androidx.compose.ui.input.key.Key.DirectionDown);keyUp(androidx.compose.ui.input.key.Key.DirectionDown)
                        }
                        val after=ui.onNodeWithTag("activation-qr").getUnclippedBoundsInRoot()
                        assertEquals((before.left.value+before.right.value)/2,(after.left.value+after.right.value)/2,0.5f)
                    }
                    scenario.onActivity { it.playback.disconnectNineNow() }
                }
            }
        } finally { shell("wm size reset");shell("wm density reset");ActivationFixture.reset();ProviderScreenshotActivity.television=false }
    }
}
