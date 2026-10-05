package dev.prestwich.autv

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import dev.prestwich.autv.data.*
import dev.prestwich.autv.guide.GuideNavigator
import dev.prestwich.autv.player.AuTvPlayer
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue

@OptIn(ExperimentalTestApi::class)
class GuideInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun channel(number: Int, name: String, url: String? = "https://example.com/live.m3u8") =
        Channel(number, name, "Entertainment", "", "", "Full", url != null, url, "id-$number", null)

    private fun launch(tv: Boolean = false) {
        val channels = listOf(channel(100, "ABC NEWS"), channel(419, "Ink Master"), channel(500, "Guide-only channel", null), channel(600, "Comedy Central"))
        compose.setContent {
            val context = LocalContext.current
            val holder = remember { AuTvPlayer(context) }
            DisposableEffect(holder) { onDispose { holder.release() } }
            var opened by remember { mutableStateOf(true) }
            var selected by remember { mutableStateOf(channels.first()) }
            val time = System.currentTimeMillis()
            val epg = Epg(mapOf("id-419" to listOf(Programme("Ink Master: Master vs Apprentice", time - 900_000, time + 900_000), Programme("Ink Master: The next challenge", time + 900_000, time + 3_600_000))))
            val configuration = android.content.res.Configuration(androidx.compose.ui.platform.LocalConfiguration.current)
            if (tv) configuration.uiMode = android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
            CompositionLocalProvider(androidx.compose.ui.platform.LocalConfiguration provides configuration) {
            AuTvScreen(Guide(4, 3, channels), selected, opened, false, emptyMap(), false, epg, holder.player,
                onSelect = { selected = it; opened = false }, onCloseGuide = { opened = false }, onOpenGuide = { opened = true }, onRetry = {},
                onNext = { GuideNavigator(channels).next(selected)?.let { selected = it } },
                onPrevious = { GuideNavigator(channels).previous(selected)?.let { selected = it } })
            }
        }
    }

    @Test fun closeToggleAndBackCloseGuideBeforeLeaving() {
        launch()
        compose.onNodeWithTag("close-guide").performClick()
        compose.onNodeWithTag("guide-panel").assertDoesNotExist()
        compose.onNodeWithTag("guide-toggle").performClick()
        compose.onNodeWithTag("guide-panel").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("guide-panel").assertDoesNotExist()
        compose.onNodeWithTag("guide-toggle").assertIsDisplayed()
    }

    @Test fun outsideTouchAndChannelButtonsWork() {
        launch()
        compose.onNodeWithTag("guide-scrim").performTouchInput { click(Offset(2f, center.y)) }
        compose.onNodeWithTag("guide-panel").assertDoesNotExist()
        compose.onNodeWithTag("ch-plus").performClick()
        compose.onNodeWithText("419  Ink Master").assertIsDisplayed()
        compose.onNodeWithTag("ch-plus").performClick()
        compose.onNodeWithText("600  Comedy Central").assertIsDisplayed()
        compose.onNodeWithTag("ch-minus").performClick()
        compose.onNodeWithText("419  Ink Master").assertIsDisplayed()
    }

    @Test fun dpadAndChannelKeysDoNotStealChannelSelection() {
        launch()
        compose.onNodeWithTag("channel-100").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithTag("channel-100").performKeyInput { keyDown(androidx.compose.ui.input.key.Key.DirectionDown); keyUp(androidx.compose.ui.input.key.Key.DirectionDown); keyDown(androidx.compose.ui.input.key.Key.DirectionCenter); keyUp(androidx.compose.ui.input.key.Key.DirectionCenter) }
        compose.onNodeWithText("419  Ink Master").assertIsDisplayed()
        compose.onNodeWithTag("app-root").performKeyInput { keyDown(androidx.compose.ui.input.key.Key.ChannelUp); keyUp(androidx.compose.ui.input.key.Key.ChannelUp) }
        compose.onNodeWithText("600  Comedy Central").assertIsDisplayed()
        compose.onNodeWithTag("app-root").performKeyInput { keyDown(androidx.compose.ui.input.key.Key.DirectionCenter); keyUp(androidx.compose.ui.input.key.Key.DirectionCenter) }
        compose.onNodeWithTag("guide-panel").assertIsDisplayed()
    }

    @Test fun castStaysAboveTheGuideAndInkMasterIsNotDisabled() {
        launch()
        compose.onNodeWithTag("cast-control").assertIsDisplayed()
        val cast = compose.onNodeWithTag("cast-control").fetchSemanticsNode().boundsInRoot
        val panel = compose.onNodeWithTag("guide-panel").fetchSemanticsNode().boundsInRoot
        assertTrue(cast.bottom <= panel.top)
        compose.onNodeWithTag("channel-419").assertHasClickAction().performClick()
        compose.onNodeWithText("419  Ink Master").assertIsDisplayed()
        compose.onNodeWithText("Ink Master: Master vs Apprentice").assertIsDisplayed()
    }
    @Test fun captureBrandedLayout() {
        launch()
        compose.onNodeWithTag("guide-panel").assertIsDisplayed()
        val bitmap = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val orientation = if (compose.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) "landscape" else "portrait"
        val output = java.io.File(compose.activity.getExternalFilesDir(null), "pass2-$orientation.png")
        output.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun tvLayoutUsesDpadAndChannelKeysWithReachableCloseControl() {
        launch(tv = true)
        compose.onNodeWithTag("guide-scrim").assertDoesNotExist()
        compose.onNodeWithTag("close-guide").assertIsDisplayed().performClick()
        compose.onNodeWithTag("app-root").performKeyInput { keyDown(androidx.compose.ui.input.key.Key.ChannelUp); keyUp(androidx.compose.ui.input.key.Key.ChannelUp) }
        compose.onNodeWithText("419  Ink Master").assertIsDisplayed()
        compose.onNodeWithTag("app-root").performKeyInput { keyDown(androidx.compose.ui.input.key.Key.ChannelDown); keyUp(androidx.compose.ui.input.key.Key.ChannelDown) }
        compose.onNodeWithText("100  ABC NEWS").assertIsDisplayed()
        compose.onNodeWithTag("app-root").performKeyInput { keyDown(androidx.compose.ui.input.key.Key.DirectionCenter); keyUp(androidx.compose.ui.input.key.Key.DirectionCenter) }
        compose.onNodeWithTag("guide-panel").assertIsDisplayed()
        compose.onNodeWithTag("close-guide").assertIsDisplayed()
    }
}
