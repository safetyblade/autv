package dev.prestwich.autv

import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import dev.prestwich.autv.creator.CreatorPlaybackActivity
import dev.prestwich.autv.guide.TuneTarget
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Runs real Android lifecycle with existing local HLS fixture; no platform video claim. */
class CreatorReturnTest {
    @get:Rule val rule = ActivityScenarioRule(PlatformTestActivity::class.java)
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun await(check: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 30_000
        while (SystemClock.uptimeMillis() < deadline) {
            var ok = false
            instrumentation.runOnMainSync { ok = check() }
            if (ok) return
            SystemClock.sleep(100)
        }
        fail("Android state did not arrive")
    }
    private fun views(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()

    @Test fun backFromLabResumesSameFastChannelAndPlayer() {
        lateinit var fast: PlatformTestActivity
        rule.scenario.onActivity { fast = it; it.playback.request(TuneTarget.Number(419)); it.playback.player.volume = 0f }
        await { fast.playback.playing && fast.playback.selected?.number == 419 }
        val player = fast.playback.player
        rule.scenario.onActivity { it.startActivity(Intent(it, CreatorPlaybackActivity::class.java)) }
        await { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).any { it is CreatorPlaybackActivity } }
        instrumentation.runOnMainSync {
            val lab = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<CreatorPlaybackActivity>().single()
            val children = views(lab.window.decorView)
            children.filterIsInstance<Button>().single { it.text == "Check live" }.performClick()
        }
        await {
            val lab = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<CreatorPlaybackActivity>().firstOrNull()
            lab != null && views(lab.window.decorView).filterIsInstance<TextView>().any { it.text.contains("UNKNOWN") && it.text.contains("OAuth") }
        }
        // Exercise AUTV's Back contract directly; global input injection can target system
        // dialogs on an emulator and is not a substitute for physical remote verification.
        instrumentation.runOnMainSync {
            val lab = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<CreatorPlaybackActivity>().single()
            @Suppress("DEPRECATION")
            lab.onBackPressed()
        }
        await { fast.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) && fast.playback.playing }
        rule.scenario.onActivity {
            assertSame(player, it.playback.player)
            assertEquals(419, it.playback.selected?.number)
            assertTrue(it.castProbe.loads.isEmpty())
        }
    }
}
