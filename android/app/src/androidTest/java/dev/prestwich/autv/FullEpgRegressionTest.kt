package dev.prestwich.autv

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.Player
import dev.prestwich.autv.player.AuTvPlayer
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.prestwich.autv.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Android instrumentation: callback and real Compose layout checks, not physical TV certification. */
class FullEpgRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val tuned = mutableListOf<Int>()
    private var retainedPlayer: Player? = null
    private fun launch(withPlayback: Boolean = false) {
        val rows = (100..750).map { Channel(it, "Channel $it", if (it < 110) "News" else "Sport", "", "", "", true,
            "https://example.com/$it", "id-$it", null) }
        val now = System.currentTimeMillis()
        val schedule = listOf(Programme("Current", now - 60_000, now + 600_000), Programme("Future", now + 600_000, now + 3_600_000))
        compose.setContent {
            if (withPlayback) {
                val context = LocalContext.current
                val holder = remember { AuTvPlayer(context) }
                DisposableEffect(holder) {
                    holder.player.repeatMode = Player.REPEAT_MODE_ALL
                    holder.play("asset:///pip-test.m3u8?channel=100", 100)
                    retainedPlayer = holder.player
                    onDispose { holder.release() }
                }
            }
            var open by remember { mutableStateOf(true) }
            TvDualGuide(open, Guide(rows.size, rows.size, rows), rows.first(),
                Epg(rows.filter { it.number != 102 }.associate { it.tvgId!! to schedule }), now, emptyMap(),
                onTune = { error("Full EPG must use its configured shared model callback") },
                onFullTune = { tuned.add(it.number); open = false }, onClose = { open = false }, onRetry = {}, loadError = false)
        }
        compose.waitForIdle()
        compose.onNodeWithTag("category-All").performClick()
        key(KeyEvent.KEYCODE_GUIDE)
    }
    private fun key(code: Int, count: Int = 1, settle: Boolean = true) {
        compose.runOnUiThread {
            repeat(count) {
                compose.activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
                compose.activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
            }
        }
        if (settle) compose.waitForIdle()
    }
    private fun visibleFocus(number: Int) {
        val row = compose.onNodeWithTag("channel-$number").assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Focused, true)).fetchSemanticsNode().boundsInRoot
        val list = compose.onNodeWithTag("guide-list").fetchSemanticsNode().boundsInRoot
        assertTrue("Selected row must be fully visible", row.top >= list.top - 1 && row.bottom <= list.bottom + 1)
    }
    @Test fun downAtLastVisibleRowRevealsNextOnFirstPress() {
        launch(); key(KeyEvent.KEYCODE_DPAD_DOWN, 6); visibleFocus(106)
        key(KeyEvent.KEYCODE_DPAD_DOWN); visibleFocus(107)
        assertTrue(tuned.isEmpty())
    }
    @Test fun upAtFirstVisibleRowRevealsPreviousOnFirstPress() {
        launch(); key(KeyEvent.KEYCODE_DPAD_DOWN, 20); visibleFocus(120)
        // A deterministic scroll puts selected row at the viewport's first position.
        compose.onNodeWithTag("guide-list").performScrollToIndex(20)
        key(KeyEvent.KEYCODE_DPAD_UP); visibleFocus(119)
        assertTrue(tuned.isEmpty())
    }
    @Test fun rapidNavigationCrossesGenresAndEntireCatalogueWithoutWrapping() {
        launch(); key(KeyEvent.KEYCODE_DPAD_DOWN, 650); visibleFocus(750)
        key(KeyEvent.KEYCODE_DPAD_DOWN); visibleFocus(750)
        key(KeyEvent.KEYCODE_DPAD_UP, 650); visibleFocus(100)
        assertTrue(tuned.isEmpty())
    }
    @Test fun rapidReversalCancelsPendingOffscreenScroll() {
        launch()
        key(KeyEvent.KEYCODE_DPAD_DOWN, 30, settle = false)
        key(KeyEvent.KEYCODE_DPAD_UP, 30)
        visibleFocus(100)
        assertTrue(tuned.isEmpty())
    }
    @Test fun channelAndProgrammeCellsKeepAlignedAcrossPagesAndTimeSelectionPersists() {
        launch(); key(KeyEvent.KEYCODE_DPAD_RIGHT)
        key(KeyEvent.KEYCODE_DPAD_DOWN, 30); visibleFocus(130)
        val channel = compose.onNodeWithTag("channel-cell-130").fetchSemanticsNode().boundsInRoot
        val programmes = compose.onNodeWithTag("programme-row-130").fetchSemanticsNode().boundsInRoot
        assertEquals(channel.top, programmes.top, 1f); assertEquals(channel.bottom, programmes.bottom, 1f)
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.onNodeWithText("This programme is not currently airing.").assertExists()
        assertTrue(tuned.isEmpty())
    }
    @Test fun immediateOkAfterNavigationUsesLatestRowBeforeComposition() {
        launch()
        key(KeyEvent.KEYCODE_DPAD_DOWN, settle = false)
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(listOf(101), tuned)
    }
    @Test fun okOnCurrentProgrammeUsesTuneAndDismissesGuide() {
        launch(); key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(listOf(100), tuned); compose.onNodeWithTag("full-epg").assertDoesNotExist()
    }
    @Test fun okOnChannelCellTunes() {
        launch(); key(KeyEvent.KEYCODE_DPAD_LEFT); key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(listOf(100), tuned)
    }
    @Test fun clickingChannelCellUsesSameExplicitTuneCallback() {
        launch(); compose.onNodeWithTag("channel-cell-101").performClick()
        assertEquals(listOf(101), tuned)
    }
    @Test fun okWithUnavailableScheduleStillTunes() {
        launch(); key(KeyEvent.KEYCODE_DPAD_DOWN, 2); key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(listOf(102), tuned)
    }
    @Test fun futureProgrammeDetailsAndBackNeverTune() {
        launch(withPlayback = true)
        val media = compose.runOnIdle { retainedPlayer!!.currentMediaItem }
        assertNotNull(media)
        key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.onNodeWithTag("programme-confirm").assertExists()
        assertTrue(tuned.isEmpty()); key(KeyEvent.KEYCODE_BACK)
        compose.onNodeWithTag("full-epg").assertExists()
        key(KeyEvent.KEYCODE_BACK); compose.onNodeWithTag("guide-panel").assertExists()
        assertTrue(tuned.isEmpty())
        compose.runOnIdle { assertSame(media, retainedPlayer!!.currentMediaItem); assertTrue(retainedPlayer!!.playWhenReady) }
    }
}
