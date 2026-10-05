package dev.prestwich.autv

import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import dev.prestwich.autv.guide.TuneTarget
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

// Compose tags belong to virtual accessibility nodes, not Android resource IDs.
private fun accessibilityNode(tag: String): AccessibilityNodeInfo? {
    val root = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow ?: return null
    val pending = ArrayDeque<AccessibilityNodeInfo>()
    pending.add(root)
    while (pending.isNotEmpty()) {
        val node = pending.removeFirst()
        if (node.viewIdResourceName == tag) return node
        for (index in 0 until node.childCount) node.getChild(index)?.let(pending::addLast)
    }
    return null
}

/** Real Activity key dispatch, accessibility focus and retained ExoPlayer on the platform clock. */
class TvPlaybackInteractionTest {
    @get:Rule val activity = ActivityScenarioRule(TvInteractionTestActivity::class.java)
    private val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation
    private fun <T> onMain(block: (TvInteractionTestActivity) -> T): T {
        var result: Result<T>? = null
        activity.scenario.onActivity { result = runCatching { block(it) } }
        return result!!.getOrThrow()
    }
    private fun await(message: String, check: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 60_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (check()) return
            SystemClock.sleep(100)
        }
        fail(message + onMain {
            val m = it.playback
            "; guide=${m.guide != null}, guideOpen=${m.guideOpen}, channel=${m.selected?.number}, media=${m.player.currentMediaItem?.mediaId}, state=${m.player.playbackState}, play=${m.player.playWhenReady}, suppression=${m.player.playbackSuppressionReason}, error=${m.player.playerError}"
        })
    }
    private fun node(tag: String): AccessibilityNodeInfo? = accessibilityNode(tag)
    private fun focused(tag: String) = await("Focus did not reach $tag") { node(tag)?.isFocused == true }
    private fun key(code: Int) = onMain {
        it.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        it.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        Unit
    }
    private fun playing(number: Int) {
        await("Channel $number did not actually play") { onMain {
            val model = it.playback
            model.selected?.number == number && model.player.currentMediaItem?.mediaId == number.toString() && model.player.isPlaying
        } }
        onMain { assertEquals("asset:///pip-test.m3u8?channel=$number", it.playback.player.currentMediaItem!!.localConfiguration!!.uri.toString()) }
    }
    private fun ready() {
        await("Initial playback failed") { onMain { it.playback.guide != null && it.playback.playing } }
        onMain { it.playback.player.volume = 0f; it.playback.request(TuneTarget.Number(100)) }
        playing(100)
        await("AU TV window did not become accessible") { onMain { it.hasWindowFocus() } && node("app-root") != null }
        await("Controls did not auto-hide") { node("guide-toggle") == null }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        focused("guide-toggle")
    }
    @Test fun focusedChannelButtonsChangeActualMediaAndKeepOnePlayer() {
        ready()
        val player = onMain { it.playback.player }
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        focused("ch-plus")
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        playing(419)
        focused("ch-plus")
        key(KeyEvent.KEYCODE_DPAD_LEFT)
        focused("guide-toggle")
        key(KeyEvent.KEYCODE_DPAD_LEFT)
        focused("ch-minus")
        key(KeyEvent.KEYCODE_ENTER)
        playing(100)
        onMain { assertSame(player, it.playback.player) }
    }
    @Test fun categoryAndRowDpadTunesActualPlaybackAndBackClosesInOrder() {
        ready()
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        focused("channel-100")
        key(KeyEvent.KEYCODE_DPAD_UP)
        focused("category-News")
        key(KeyEvent.KEYCODE_DPAD_LEFT)
        focused("category-All")
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        await("All category not selected") { node("category-All")?.isSelected == true }
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        focused("channel-100")
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        focused("channel-419")
        val bitmap = automation.takeScreenshot()
        val output = onMain { java.io.File(it.getExternalFilesDir(null), "pass4-tv-guide.png") }
        output.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        key(KeyEvent.KEYCODE_ENTER)
        playing(419)
        await("Guide stayed open after tuning") { !onMain { it.playback.guideOpen } }
        focused("guide-toggle")
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        focused("channel-419")
        await("Playing genre not selected") { node("category-Game Shows")?.isSelected == true }
        key(KeyEvent.KEYCODE_BACK)
        await("Back did not close guide") { !onMain { it.playback.guideOpen } }
        playing(419)
        key(KeyEvent.KEYCODE_BACK)
        await("Back did not hide controls") { node("guide-toggle") == null }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        focused("guide-toggle")
        onMain { assertFalse(it.playback.guideOpen) }
    }
    @Test fun hiddenHardwareChannelKeysReplaceTheMediaItemAndDirectTuneUsesSamePlayer() {
        ready()
        val player = onMain { it.playback.player }
        await("Controls stayed visible") { node("guide-toggle") == null }
        key(KeyEvent.KEYCODE_CHANNEL_UP)
        playing(419)
        key(KeyEvent.KEYCODE_CHANNEL_DOWN)
        playing(100)
        onMain { it.playback.request(TuneTarget.Id("fixture-419")) }
        playing(419)
        onMain { assertSame(player, it.playback.player) }
    }
    @Test fun rowFocusSurvivesLazyScrollingAndReopensAtThePlayingChannel() {
        ready()
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        focused("channel-100")
        for (number in 1200..1207) {
            key(KeyEvent.KEYCODE_DPAD_DOWN)
            focused("channel-$number")
        }
        playing(100) // Browsing must not tune until OK.
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        playing(1207)
        focused("guide-toggle")
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        focused("channel-1207")
    }
}

class PhonePlaybackInteractionTest {
    @get:Rule val activity = ActivityScenarioRule(PlatformTestActivity::class.java)
    @Test fun touchGuideSelectionReplacesActualLocalMediaInEitherOrientation() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun <T> onMain(block: (PlatformTestActivity) -> T): T {
            var result: Result<T>? = null
            activity.scenario.onActivity { result = runCatching { block(it) } }
            return result!!.getOrThrow()
        }
        fun await(message: String, check: () -> Boolean) {
            val deadline = SystemClock.uptimeMillis() + 60_000
            while (SystemClock.uptimeMillis() < deadline) {
                if (check()) return
                SystemClock.sleep(100)
            }
            fail(message)
        }
        fun click(tag: String) {
            await("Missing control $tag") {
                accessibilityNode(tag)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
            }
        }
        await("Initial playback failed") { onMain { it.playback.playing && it.playback.guide != null } }
        val player = onMain { it.playback.player }
        onMain { it.playback.player.volume = 0f; it.playback.request(TuneTarget.Number(100)); it.playback.guideOpen = true }
        click("channel-419")
        await("Touch selection did not change actual playback") { onMain {
            it.playback.selected?.number == 419 && player.currentMediaItem?.mediaId == "419" && player.isPlaying && !it.playback.guideOpen
        } }
        onMain {
            assertEquals("asset:///pip-test.m3u8?channel=419", player.currentMediaItem!!.localConfiguration!!.uri.toString())
            assertSame(player, it.playback.player)
        }
    }
}
