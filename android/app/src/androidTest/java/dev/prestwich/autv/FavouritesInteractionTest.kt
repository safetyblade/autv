package dev.prestwich.autv

import android.content.res.Configuration
import android.os.SystemClock
import android.view.KeyEvent
import android.view.ViewConfiguration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.prestwich.autv.data.*
import dev.prestwich.autv.guide.FavouriteIdentity
import dev.prestwich.autv.player.AuTvPlayer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Compose/Android tests. Must be executed on an Android target; not physical remote proof. */
class FavouritesInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val tunes = mutableListOf<Int>()
    private val toggles = mutableListOf<Int>()
    private val channel = Channel(200, "Watch AEW", "Sport", "", "", "", true, "https://example.com/200", "provider-200", null)
    private val rows = listOf(channel, channel.copy(number = 201, tvgId = "201", name = "CW Presents WWE NXT"),
        channel.copy(number = 203, tvgId = "203", name = "TNA Wrestling Xumo", available = false, streamUrl = null))
    private val catalogue = mutableStateOf(Guide(3, 2, rows))
    private val schedules = mutableStateOf(Epg(emptyMap()))
    private fun launch(open: Boolean = true, tv: Boolean = true) {
        compose.setContent {
            val context = LocalContext.current
            val holder = remember { AuTvPlayer(context) }
            DisposableEffect(holder) { onDispose { holder.release() } }
            var guideOpen by remember { mutableStateOf(open) }
            var keys by remember { mutableStateOf(emptySet<String>()) }
            val configuration = Configuration(LocalConfiguration.current).apply {
                uiMode = if (tv) Configuration.UI_MODE_TYPE_TELEVISION else Configuration.UI_MODE_TYPE_NORMAL
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                AuTvScreen(catalogue.value, channel, guideOpen, false, emptyMap(), false, schedules.value, holder.player,
                    onSelect = { tunes.add(it.number); guideOpen = false }, onFullSelect = { tunes.add(it.number); guideOpen = false },
                    onCloseGuide = { guideOpen = false }, onOpenGuide = { guideOpen = true }, onRetry = {}, onNext = {}, onPrevious = {},
                    favourites = keys, onFavourite = {
                        toggles.add(it.number)
                        FavouriteIdentity.key(it)?.let { key -> keys = if (key in keys) keys - key else keys + key }
                    })
            }
        }
        compose.waitForIdle()
    }
    private fun key(code: Int, long: Boolean = false, flag: Boolean = false) {
        compose.runOnUiThread {
            val start = SystemClock.uptimeMillis()
            compose.activity.dispatchKeyEvent(KeyEvent(start, start, KeyEvent.ACTION_DOWN, code, 0))
            val end = start + if (long) ViewConfiguration.getLongPressTimeout() + 1 else 1
            if (flag) compose.activity.dispatchKeyEvent(KeyEvent(start, end, KeyEvent.ACTION_DOWN, code, 1, 0, -1, 0, KeyEvent.FLAG_LONG_PRESS))
            compose.activity.dispatchKeyEvent(KeyEvent(start, end, KeyEvent.ACTION_UP, code, 0))
        }
        compose.waitForIdle()
    }
    @Test fun quickLongReleaseDoesNotTuneAndNextShortStillTunes() {
        launch(); key(KeyEvent.KEYCODE_DPAD_CENTER, long = true)
        assertEquals(listOf(200), toggles); assertTrue(tunes.isEmpty())
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(listOf(200), tunes)
    }
    @Test fun fullLongFlagTogglesFocusedChannelWithoutSelectingProgramme() {
        launch(); key(KeyEvent.KEYCODE_GUIDE); key(KeyEvent.KEYCODE_DPAD_DOWN)
        key(KeyEvent.KEYCODE_DPAD_CENTER, long = true, flag = true)
        assertEquals(listOf(201), toggles); assertTrue(tunes.isEmpty())
        compose.onNodeWithTag("full-epg").assertExists()
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(listOf(201), tunes)
    }
    @Test fun playbackLongCannotOpenGuideAndShortStillOpensIt() {
        launch(open = false)
        compose.onNodeWithTag("guide-toggle").requestFocus()
        key(KeyEvent.KEYCODE_DPAD_CENTER, long = true)
        assertEquals(listOf(200), toggles); assertTrue(tunes.isEmpty())
        compose.onNodeWithTag("guide-panel").assertDoesNotExist()
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.onNodeWithTag("guide-panel").assertExists()
    }
    @Test fun timerWorksWithoutRemoteRepeatsAndKeyUpCannotTune() {
        launch()
        compose.runOnUiThread {
            val start = SystemClock.uptimeMillis()
            compose.activity.dispatchKeyEvent(KeyEvent(start, start, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0))
        }
        compose.mainClock.advanceTimeBy(ViewConfiguration.getLongPressTimeout().toLong() + 50)
        compose.waitUntil(2_000) { toggles.size == 1 }
        compose.runOnUiThread { compose.activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER)) }
        compose.waitForIdle()
        assertTrue(tunes.isEmpty()); assertEquals(listOf(200), toggles)
    }
    @Test fun favouritesContainUnavailableRowsAndSurviveEpgAndMetadataRefresh() {
        launch(); key(KeyEvent.KEYCODE_DPAD_DOWN); key(KeyEvent.KEYCODE_DPAD_DOWN)
        key(KeyEvent.KEYCODE_DPAD_CENTER, long = true)
        compose.onNodeWithTag("category-Favourites").performClick()
        compose.onNodeWithTag("channel-203").assertExists()
        compose.onNodeWithText("Unavailable").assertExists()
        compose.runOnUiThread {
            catalogue.value = Guide(3, 2, rows.map { if (it.number == 203) it.copy(number = 903, name = "Renamed") else it })
            schedules.value = Epg(mapOf("203" to listOf(Programme("Expired", 100, 200))))
        }
        compose.waitForIdle()
        compose.onNodeWithTag("channel-903").assertIsDisplayed()
        key(KeyEvent.KEYCODE_MENU); key(KeyEvent.KEYCODE_BACK)
        compose.onNodeWithTag("channel-903").assertIsDisplayed()
        assertTrue(tunes.isEmpty())
    }
    @Test fun futureProgrammeLongTogglesWithoutDetailsOrTuneThenShortOpensDetails() {
        val now = System.currentTimeMillis()
        schedules.value = Epg(mapOf("provider-200" to listOf(Programme("Current", now - 60_000, now + 600_000),
            Programme("Future", now + 600_000, now + 3_600_000))))
        launch(); key(KeyEvent.KEYCODE_GUIDE); key(KeyEvent.KEYCODE_DPAD_RIGHT)
        key(KeyEvent.KEYCODE_DPAD_CENTER, long = true)
        assertEquals(listOf(200), toggles); assertTrue(tunes.isEmpty())
        compose.onNodeWithTag("programme-confirm").assertDoesNotExist()
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.onNodeWithTag("programme-confirm").assertExists()
        assertTrue(tunes.isEmpty())
    }
    @Test fun directionalInputCancelsHoldWithoutTuningOrTogglingNewFocus() {
        launch()
        compose.runOnUiThread {
            val start = SystemClock.uptimeMillis()
            compose.activity.dispatchKeyEvent(KeyEvent(start, start, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0))
        }
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.runOnUiThread { compose.activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER)) }
        compose.mainClock.advanceTimeBy(ViewConfiguration.getLongPressTimeout().toLong() + 50)
        compose.waitForIdle()
        assertTrue(toggles.isEmpty()); assertTrue(tunes.isEmpty())
        compose.onNodeWithTag("channel-201").assertIsDisplayed()
    }
    @Test fun localPreferencesPersistAcrossStoreRecreationAndRemovedChannelsReturning() {
        val context = compose.activity
        context.getSharedPreferences("favourites-v1", 0).edit().clear().commit()
        try {
            val store = Favourites(context)
            assertEquals(true, store.toggle(channel))
            assertTrue(FavouriteIdentity.contains(channel.copy(number = 900, name = "Renamed", streamUrl = null), Favourites(context).keys))
            assertEquals(false, Favourites(context).toggle(channel))
            assertTrue(Favourites(context).keys.isEmpty())
        } finally { context.getSharedPreferences("favourites-v1", 0).edit().clear().commit() }
    }
    @Test fun phoneTouchLongPressTogglesWithoutTuning() {
        launch(tv = false)
        compose.onNodeWithTag("channel-200").performTouchInput { longClick() }
        assertEquals(listOf(200), toggles); assertTrue(tunes.isEmpty())
    }
}
