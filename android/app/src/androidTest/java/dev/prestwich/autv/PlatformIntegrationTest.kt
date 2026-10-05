package dev.prestwich.autv

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.platform.app.InstrumentationRegistry
import dev.prestwich.autv.guide.TuneTarget
import org.junit.Assert.*
import org.junit.After
import org.junit.Rule
import org.junit.Test

class PlatformIntegrationTest {
    @get:Rule val activityRule = ActivityScenarioRule(PlatformTestActivity::class.java)
    private var scenarioIntent: Intent? = null
    @After fun restoreScenarioLaunchIdentity() {
        onMain { activity ->
            activity.allowPip = false; activity.updatePipParams()
            scenarioIntent?.let { activity.intent = it }
        }
    }
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun <T> onMain(block: (PlatformTestActivity) -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync {
            result = runCatching {
                val monitor = ActivityLifecycleMonitorRegistry.getInstance()
                val activity = Stage.values().flatMap { monitor.getActivitiesInStage(it) }
                    .filterIsInstance<PlatformTestActivity>().first { !it.isDestroyed }
                block(activity)
            }
        }
        return result!!.getOrThrow()
    }
    private fun awaitState(check: (PlatformTestActivity) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 60_000
        var passed = false
        while (!passed && SystemClock.uptimeMillis() < deadline) {
            passed = onMain(check)
            if (!passed) SystemClock.sleep(100)
        }
        assertTrue("Platform state did not arrive within 60 seconds", passed)
    }
    private fun ready() {
        awaitState { it.playback.playing && it.playback.guide != null }
        onMain { scenarioIntent = Intent(it.intent); it.playback.player.volume = 0f }
    }
    @Test fun intentContractRoundTripsAndInvalidTargetsReturnToGuide() {
        ready()
        for (target in listOf(TuneTarget.Number(419), TuneTarget.Id("provider/id with spaces"))) {
            assertEquals(target, ChannelIntents.target(Intent(Intent.ACTION_VIEW, ChannelIntents.uri(target))))
        }
        assertNull(ChannelIntents.target(Intent(Intent.ACTION_VIEW, Uri.parse("autv://channel/not-a-number"))))
        assertNull(ChannelIntents.target(Intent(Intent.ACTION_VIEW, Uri.parse("other://channel/419"))))
        onMain { activity ->
            activity.startActivity(ChannelIntents.intent(activity, TuneTarget.Number(419)).setClass(activity, PlatformTestActivity::class.java))
        }
        awaitState { it.playback.selected?.number == 419 && it.playback.playing }
        onMain { assertFalse(it.playback.guideOpen) }
        onMain { activity ->
            activity.startActivity(ChannelIntents.intent(activity, TuneTarget.Number(500)).setClass(activity, PlatformTestActivity::class.java))
        }
        awaitState { it.playback.guideOpen }
        onMain { assertTrue(it.playback.guideOpen) }
        onMain { activity ->
            assertEquals(419, activity.playback.selected?.number)
            val before = activity.playback.player.currentMediaItem
            activity.playback.request(TuneTarget.Id("fixture-419"))
            assertSame(before, activity.playback.player.currentMediaItem)
        }
    }
    @Test fun activityRecreationRetainsThePlayerAndTunedChannel() {
        ready()
        onMain { it.playback.request(TuneTarget.Number(419)) }
        awaitState { it.playback.playing && it.playback.selected?.number == 419 }
        val player = onMain { it.playback.player }
        activityRule.scenario.recreate()
        awaitState { it.playback.playing }
        onMain {
            assertSame(player, it.playback.player)
            assertEquals(419, it.playback.selected?.number)
            assertFalse(it.playback.guideOpen)
        }
    }
    @Test fun homeEntersRealMobilePipAndReturnKeepsTheSameMedia() {
        ready()
        org.junit.Assume.assumeTrue("Mobile PiP is not advertised on this device", onMain { it.mobilePipSupported() })
        onMain { it.allowPip = true; it.updatePipParams() }
        val player = onMain { it.playback.player }
        val media = onMain { player.currentMediaItem }
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME").close()
        awaitState { it.isInPictureInPictureMode && it.playback.playing }
        SystemClock.sleep(2_000)
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        val output = onMain { java.io.File(it.getExternalFilesDir(null), "platform-pip.png") }
        output.outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        onMain { activity ->
            assertTrue(player.isPlaying)
            assertSame(media, player.currentMediaItem)
            activity.allowPip = false; activity.updatePipParams()
            activity.startActivity(Intent(activity, PlatformTestActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        }
        awaitState { !it.isInPictureInPictureMode && it.playback.playing }
        onMain { assertSame(player, it.playback.player); assertSame(media, player.currentMediaItem) }
        onMain { assertTrue(it.playback.guideOpen) }
    }
}
