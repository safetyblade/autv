package dev.prestwich.autv

import android.content.res.Configuration
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.prestwich.autv.data.*
import dev.prestwich.autv.player.AuTvPlayer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DualGuideInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val tuned = mutableListOf<Int>()
    private val entered = mutableListOf<Int>()
    private val snapshot = mutableStateOf(Epg(emptyMap()))
    private fun channel(number: Int, name: String, genre: String = "Sport") =
        Channel(number, name, genre, "", "", "", true, "https://example.com/$number.m3u8", "id-$number", null)
    private fun launch() {
        val channels = listOf(channel(200, "Watch AEW"), channel(201, "CW Presents WWE NXT"),
            channel(202, "TNA Wrestling Channel"), channel(203, "TNA Wrestling Xumo"), channel(297, "Wrestling Central")) +
            (300..950).map { channel(it, "Fixture $it", "Movies") }
        val time = System.currentTimeMillis()
        snapshot.value = Epg(mapOf("id-202" to listOf(Programme("Current", time - 60_000, time + 600_000),
            Programme("Future", time + 600_000, time + 3_600_000)), "id-297" to listOf(Programme("Expired", 100, 200))))
        compose.setContent {
            val context = LocalContext.current
            val holder = remember { AuTvPlayer(context) }
            DisposableEffect(holder) { onDispose { holder.release() } }
            var opened by remember { mutableStateOf(true) }
            var playing by remember { mutableStateOf(channels[2]) }
            val config = Configuration(LocalConfiguration.current).apply { uiMode = Configuration.UI_MODE_TYPE_TELEVISION }
            CompositionLocalProvider(LocalConfiguration provides config) {
                AuTvScreen(Guide(channels.size, channels.size, channels), playing, opened, false,
                    emptyMap(), false, snapshot.value, holder.player,
                    onSelect = { tuned.add(it.number); playing = it; opened = false },
                    onCloseGuide = { opened = false }, onOpenGuide = { opened = true }, onRetry = {}, onNext = {}, onPrevious = {},
                    onTuneNumber = { entered.add(it) })
            }
        }
        compose.waitForIdle()
    }
    private fun key(code: Int, repeat: Int = 0) {
        compose.runOnUiThread {
            compose.activity.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, code, repeat))
            compose.activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
        compose.waitForIdle()
    }
    private fun focus(number: Int) = compose.onNodeWithTag("channel-$number")
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.Focused, true))

    @Test fun opensOnPlayingAndFocusNeverTunesIncludingMissingAndExpiredSchedules() {
        launch(); focus(202)
        key(KeyEvent.KEYCODE_DPAD_DOWN); focus(203)
        key(KeyEvent.KEYCODE_DPAD_DOWN); focus(297)
        assertTrue(tuned.isEmpty())
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(listOf(297), tuned)
    }
    @Test fun repeatsDoNotTuneAndOnlyExplicitOkTunes() {
        launch()
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        key(KeyEvent.KEYCODE_DPAD_CENTER, repeat = 5)
        assertTrue(tuned.isEmpty())
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(listOf(203), tuned)
    }
    @Test fun futureDetailsCannotTuneAndBackRestoresQuickSelection() {
        launch(); key(KeyEvent.KEYCODE_GUIDE)
        compose.onNodeWithTag("full-epg").assertExists()
        key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.onNodeWithTag("programme-confirm").performClick()
        assertTrue(tuned.isEmpty())
        key(KeyEvent.KEYCODE_BACK)
        compose.onNodeWithTag("guide-panel").assertExists(); focus(202)
        key(KeyEvent.KEYCODE_BACK)
        compose.onNodeWithTag("guide-panel").assertDoesNotExist()
    }
    @Test fun liveProgrammeOffersExplicitWatchAndGuideSwitchDoesNotTune() {
        launch(); key(KeyEvent.KEYCODE_MENU)
        assertTrue(tuned.isEmpty())
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.onNodeWithText("Watch live").assertExists()
        compose.onNodeWithTag("programme-confirm").performClick()
        assertEquals(listOf(202), tuned)
    }
    @Test fun categoriesAndRapidNavigationRemainVirtualizedAndRefreshKeepsFocus() {
        launch(); key(KeyEvent.KEYCODE_PAGE_DOWN)
        focus(300)
        repeat(25) { key(KeyEvent.KEYCODE_DPAD_DOWN) }
        focus(325)
        assertTrue(compose.onAllNodes(hasTestTag("channel-950")).fetchSemanticsNodes().isEmpty())
        compose.runOnUiThread { snapshot.value = Epg(emptyMap()) }
        compose.waitForIdle(); focus(325)
        assertTrue(tuned.isEmpty())
        key(KeyEvent.KEYCODE_GUIDE); key(KeyEvent.KEYCODE_DPAD_DOWN)
        key(KeyEvent.KEYCODE_BACK); focus(325)
    }
    @Test fun numberEntryUsesExistingExplicitCommitWithoutBrowseTuning() {
        launch()
        key(KeyEvent.KEYCODE_2); key(KeyEvent.KEYCODE_0); key(KeyEvent.KEYCODE_0)
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(listOf(200), entered)
        assertTrue(tuned.isEmpty())
    }
    @Test fun backClosesDetailBeforeFullGuideAndQuickGuide() {
        launch(); key(KeyEvent.KEYCODE_GUIDE); key(KeyEvent.KEYCODE_DPAD_CENTER)
        key(KeyEvent.KEYCODE_BACK)
        compose.onNodeWithTag("full-epg").assertExists()
        key(KeyEvent.KEYCODE_BACK)
        compose.onNodeWithTag("guide-panel").assertExists()
        key(KeyEvent.KEYCODE_BACK)
        compose.onNodeWithTag("guide-panel").assertDoesNotExist()
    }
}
