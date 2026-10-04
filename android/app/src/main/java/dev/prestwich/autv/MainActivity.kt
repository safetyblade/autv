package dev.prestwich.autv

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.tv.material3.*
import dev.prestwich.autv.data.Channel
import dev.prestwich.autv.data.Guide
import dev.prestwich.autv.data.GuideRepository
import dev.prestwich.autv.guide.GuideNavigator
import dev.prestwich.autv.player.AuTvPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var auPlayer: AuTvPlayer
    private var navigator: GuideNavigator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auPlayer = AuTvPlayer(this)

        setContent {
            var guide by remember { mutableStateOf<Guide?>(null) }
            var selected by remember { mutableStateOf<Channel?>(null) }
            var guideOpen by remember { mutableStateOf(true) }
            var error by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(Unit) {
                runCatching {
                    withContext(Dispatchers.IO) { GuideRepository().load() }
                }.onSuccess {
                    guide = it
                    navigator = GuideNavigator(it.channels)
                    selected = it.channels.firstOrNull { channel -> channel.available }
                    auPlayer.play(selected?.streamUrl)
                }.onFailure {
                    error = it.message ?: "Guide unavailable"
                }
            }

            AuTvScreen(
                guide = guide,
                selected = selected,
                guideOpen = guideOpen,
                error = error,
                player = auPlayer,
                onSelect = { channel ->
                    selected = channel
                    if (channel.available) auPlayer.play(channel.streamUrl)
                    guideOpen = false
                },
                onToggleGuide = { guideOpen = !guideOpen },
                onNext = {
                    navigator?.next(selected)?.let {
                        selected = it
                        auPlayer.play(it.streamUrl)
                    }
                },
                onPrevious = {
                    navigator?.previous(selected)?.let {
                        selected = it
                        auPlayer.play(it.streamUrl)
                    }
                }
            )
        }
    }

    override fun onDestroy() {
        auPlayer.release()
        super.onDestroy()
    }
}

@Composable
private fun AuTvScreen(
    guide: Guide?,
    selected: Channel?,
    guideOpen: Boolean,
    error: String?,
    player: AuTvPlayer,
    onSelect: (Channel) -> Unit,
    onToggleGuide: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_CHANNEL_UP -> { onNext(); true }
                    KeyEvent.KEYCODE_CHANNEL_DOWN -> { onPrevious(); true }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { onToggleGuide(); true }
                    else -> false
                }
            }
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    this.player = player.player
                    useController = false
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (selected != null) {
            Surface(modifier = Modifier.align(Alignment.TopStart).padding(24.dp)) {
                Text(
                    text = selected.number.toString() + "  " + selected.name,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }

        if (guideOpen) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(520.dp)
                    .align(Alignment.CenterStart)
                    .padding(24.dp)
            ) {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Text("AU TV", style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.height(8.dp))
                    if (guide != null) {
                        Text(
                            guide.availableCount.toString() + " available • " +
                                guide.activeCount.toString() + " in guide"
                        )
                        Spacer(Modifier.height(12.dp))
                        LazyColumn {
                            items(guide.channels) { channel ->
                                Button(
                                    onClick = { onSelect(channel) },
                                    enabled = channel.available,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                ) {
                                    Column(Modifier.fillMaxWidth()) {
                                        Text(channel.number.toString() + "  " + channel.name)
                                        Text(channel.genre + " • " + channel.subgenre)
                                    }
                                }
                            }
                        }
                    } else if (error != null) {
                        Text(error)
                    } else {
                        Text("Loading guide…")
                    }
                }
            }
        }
    }
}
