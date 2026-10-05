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
    private val pip = mutableStateOf(false)
    private fun channel(number: Int, name: String, genre: String, url: String? = "https://example.com/live.m3u8") =
        Channel(number, name, genre, "", "", "Full", url != null, url, "id-$number", "android.resource://dev.prestwich.autv/drawable/app_icon")

    private fun launch(tv: Boolean = false) {
        val channels = listOf(channel(100, "ABC NEWS", "News"), channel(110, "SBS World News", "News"), channel(200, "Sport", "Sport"),
            channel(300, "Cinema", "Movies"), channel(419, "Ink Master", "Game Shows"), channel(500, "Crime", "Crime", null),
            channel(600, "Comedy Central", "Comedy"), channel(700, "Entertainment", "Entertainment"), channel(1000, "Lifestyle", "Reality & Lifestyle"),
            channel(1100, "Documentary", "Factual"), channel(1200, "Kids", "Kids & Animation"), channel(1300, "Music", "Music"))
        compose.runOnUiThread { androidx.core.view.WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false) }
        compose.setContent {
            val context = LocalContext.current
            val holder = remember { AuTvPlayer(context) }
            DisposableEffect(holder) { onDispose { holder.release() } }
            var opened by remember { mutableStateOf(true) }
            var selected by remember { mutableStateOf(channels.first { it.number == 419 }) }
            val time = remember { System.currentTimeMillis() }
            val epg = remember { Epg(mapOf("id-419" to listOf(Programme("Master vs Apprentice", time - 900_000, time + 900_000), Programme("The next challenge", time + 900_000, time + 3_600_000)))) }
            val configuration = android.content.res.Configuration(androidx.compose.ui.platform.LocalConfiguration.current)
            if (tv) configuration.uiMode = android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
            CompositionLocalProvider(androidx.compose.ui.platform.LocalConfiguration provides configuration) {
                AuTvScreen(Guide(channels.size, channels.size - 1, channels), selected, opened, false, emptyMap(), false, epg, holder.player,
                    onSelect = { selected = it; opened = false }, onCloseGuide = { opened = false }, onOpenGuide = { opened = true }, onRetry = {},
                    onNext = { GuideNavigator(channels).next(selected)?.let { selected = it; opened = false } },
                    onPrevious = { GuideNavigator(channels).previous(selected)?.let { selected = it; opened = false } },
                    onChromeVisibilityChanged = { setPlayerSystemBars(compose.activity.window, it) }, pictureInPicture = pip.value,
                    onTuneNumber = { number -> channels.firstOrNull { it.number == number && !it.streamUrl.isNullOrBlank() }?.let { selected = it; opened = false } })
            }
        }
    }

    private fun awaitHidden() {
        compose.waitUntil(timeoutMillis = 12_000) { compose.onAllNodesWithTag("chrome").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("guide-toggle").assertDoesNotExist()
        compose.onNodeWithTag("cast-control").assertDoesNotExist()
        compose.onNodeWithTag("video").assertIsDisplayed()
    }

    @Test fun cleanPlaybackTimesOutAndReturnsOnTouchAndOk() {
        launch()
        compose.onNodeWithTag("close-guide").performClick()
        awaitHidden()
        screenshot("clean")
        compose.onNodeWithTag("app-root").performTouchInput { click(center) }
        compose.onNodeWithTag("chrome").assertIsDisplayed()
        compose.onNodeWithTag("cast-control").assertIsDisplayed()
        awaitHidden()
        compose.onNodeWithTag("app-root").performKeyInput { keyDown(androidx.compose.ui.input.key.Key.DirectionCenter); keyUp(androidx.compose.ui.input.key.Key.DirectionCenter) }
        compose.onNodeWithTag("chrome").assertIsDisplayed()
        compose.onNodeWithTag("guide-panel").assertDoesNotExist()
    }

    @Test fun hardwareChannelKeysWorkWhileChromeIsHidden() {
        launch(tv = true)
        compose.onNodeWithTag("close-guide").performClick()
        awaitHidden()
        compose.onNodeWithTag("app-root").performKeyInput { keyDown(androidx.compose.ui.input.key.Key.ChannelUp); keyUp(androidx.compose.ui.input.key.Key.ChannelUp) }
        compose.onNodeWithText("600  Comedy Central").assertIsDisplayed()
        compose.onNodeWithTag("ch-minus").performClick()
        compose.onNodeWithText("419  Ink Master").assertIsDisplayed()
        compose.onNodeWithTag("ch-plus").performClick()
        compose.onNodeWithText("600  Comedy Central").assertIsDisplayed()
    }

    @Test fun guidePinsChromeAndDismissesViaCloseBackAndOutside() {
        launch()
        Thread.sleep(4_000)
        compose.onNodeWithTag("guide-panel").assertIsDisplayed()
        compose.onNodeWithTag("cast-control").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("guide-panel").assertDoesNotExist()
        compose.onNodeWithTag("guide-toggle").performClick()
        compose.onNodeWithTag("close-guide").performClick()
        compose.onNodeWithTag("guide-panel").assertDoesNotExist()
        compose.onNodeWithTag("guide-toggle").performClick()
        compose.onNodeWithTag("guide-scrim").performTouchInput { click(Offset(2f, center.y)) }
        compose.onNodeWithTag("guide-panel").assertDoesNotExist()
    }

    @Test fun searchUsesNumberNameAndCurrentProgramme() {
        launch()
        compose.onNodeWithTag("guide-search").performTextInput("419")
        compose.onNodeWithTag("channel-419").assertIsDisplayed()
        compose.onNodeWithTag("channel-100").assertDoesNotExist()
        compose.onNodeWithTag("guide-search").performTextReplacement("ink")
        compose.onNodeWithTag("channel-419").assertIsDisplayed()
        compose.onNodeWithTag("guide-search").performTextReplacement("apprentice")
        compose.onNodeWithTag("channel-419").assertIsDisplayed()
        compose.onNodeWithTag("guide-search").performTextReplacement("does not exist")
        compose.onNodeWithText("No channels match your search.").assertIsDisplayed()
    }

    @Test fun categorySwitchingAndPlayingChannelFocusAreReliable() {
        launch(tv = true)
        compose.onNodeWithTag("channel-419").assertIsDisplayed().assertIsFocused()
        compose.onNodeWithTag("guide-categories").performScrollToNode(hasTestTag("category-News"))
        compose.onNodeWithTag("category-News").performClick()
        compose.onNodeWithTag("channel-100").assertIsDisplayed()
        compose.onNodeWithTag("channel-419").assertDoesNotExist()
        compose.onNodeWithTag("app-root").performKeyInput { keyDown(androidx.compose.ui.input.key.Key.PageDown); keyUp(androidx.compose.ui.input.key.Key.PageDown) }
        compose.onNodeWithTag("channel-200").assertIsDisplayed()
        compose.onNodeWithTag("close-guide").performClick()
        compose.onNodeWithTag("guide-toggle").performClick()
        compose.onNodeWithTag("channel-419").assertIsDisplayed().assertIsFocused()
        compose.onNodeWithTag("guide-categories").performScrollToNode(hasTestTag("category-Game Shows"))
        compose.onNodeWithTag("category-Game Shows").assertIsSelected()
    }

    @Test fun tvOkSelectsFocusedPlayingChannelRatherThanInterceptingIt() {
        launch(tv = true)
        compose.onNodeWithTag("channel-419").assertIsFocused().performKeyInput {
            keyDown(androidx.compose.ui.input.key.Key.DirectionCenter); keyUp(androidx.compose.ui.input.key.Key.DirectionCenter)
        }
        compose.onNodeWithTag("guide-panel").assertDoesNotExist()
        compose.onNodeWithText("419  Ink Master").assertIsDisplayed()
    }

    @Test fun cardsAndCastRemainReadableInBothOrientations() {
        launch()
        compose.onNodeWithTag("channel-419").assertIsDisplayed()
        compose.onNodeWithContentDescription("Ink Master logo").assertIsDisplayed()
        compose.onNodeWithText("Master vs Apprentice").assertIsDisplayed()
        compose.onNodeWithTag("programme-progress").assertIsDisplayed()
        compose.onNodeWithText("Stream known").assertDoesNotExist()
        compose.onNodeWithTag("cast-control").assertIsDisplayed()
        val cast = compose.onNodeWithTag("cast-control").fetchSemanticsNode().boundsInRoot
        val panel = compose.onNodeWithTag("guide-panel").fetchSemanticsNode().boundsInRoot
        assertTrue(cast.bottom <= panel.top)
        screenshot("guide")
    }

    @Test fun pipRemovesAllChromeAndRestoresGuideState() {
        launch()
        compose.runOnIdle { pip.value = true }
        compose.onNodeWithTag("video").assertIsDisplayed()
        compose.onNodeWithTag("chrome").assertDoesNotExist()
        compose.onNodeWithTag("guide-panel").assertDoesNotExist()
        compose.onNodeWithTag("cast-control").assertDoesNotExist()
        compose.runOnIdle { pip.value = false }
        compose.onNodeWithTag("guide-panel").assertIsDisplayed()
        compose.onNodeWithTag("channel-419").assertIsDisplayed()
    }

    @Test fun tvNumericSelectionCallsTheSameTuningCallback() {
        launch(tv = true)
        compose.onNodeWithTag("close-guide").performClick()
        compose.onNodeWithTag("app-root").performKeyInput {
            pressKey(androidx.compose.ui.input.key.Key.One)
            pressKey(androidx.compose.ui.input.key.Key.Zero)
            pressKey(androidx.compose.ui.input.key.Key.Zero)
            pressKey(androidx.compose.ui.input.key.Key.Enter)
        }
        compose.onNodeWithText("100  ABC NEWS").assertIsDisplayed()
    }

    @Test fun landscapeGuideUsesHalfWidthAndKeepsMultipleRowsAndVideoVisible() {
        org.junit.Assume.assumeTrue(compose.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE)
        launch()
        val root = compose.onNodeWithTag("app-root").fetchSemanticsNode().boundsInRoot
        val panel = compose.onNodeWithTag("guide-panel").fetchSemanticsNode().boundsInRoot
        assertTrue(panel.width / root.width in 0.45f..0.60f)
        compose.onNodeWithTag("channel-419").assertIsDisplayed()
        compose.onNodeWithTag("channel-500").assertIsDisplayed()
        compose.onNodeWithTag("channel-600").assertIsDisplayed()
        compose.onNodeWithTag("ch-plus").assertDoesNotExist()
        compose.onNodeWithTag("video").assertIsDisplayed()
        compose.onNodeWithTag("guide-categories").assertIsDisplayed()
        compose.onNodeWithTag("channel-600").performClick()
        compose.onNodeWithTag("guide-panel").assertDoesNotExist()
        compose.onNodeWithText("600  Comedy Central").assertIsDisplayed()
        screenshot("landscape-responsive")
    }

    private fun screenshot(name: String) {
        val bitmap = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val orientation = if (compose.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) "landscape" else "portrait"
        val output = java.io.File(compose.activity.getExternalFilesDir(null), "pass3-$name-$orientation.png")
        output.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
