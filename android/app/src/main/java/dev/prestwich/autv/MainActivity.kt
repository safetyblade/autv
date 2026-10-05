package dev.prestwich.autv

import android.os.Bundle
import android.content.res.Configuration
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import androidx.tv.material3.*
import dev.prestwich.autv.data.Channel
import dev.prestwich.autv.data.Guide
import dev.prestwich.autv.data.GuideRepository
import dev.prestwich.autv.guide.GuideNavigator
import dev.prestwich.autv.player.AuTvPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
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
            var playbackError by remember { mutableStateOf<String?>(null) }
            var loadAttempt by remember { mutableIntStateOf(0) }

            DisposableEffect(auPlayer) {
                val listener = object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        playbackError = "Stream unavailable. Choose another channel."
                    }
                }
                auPlayer.player.addListener(listener)
                onDispose { auPlayer.player.removeListener(listener) }
            }

            BackHandler(enabled = guideOpen) { guideOpen = false }

            LaunchedEffect(loadAttempt) {
                error = null
                runCatching {
                    withContext(Dispatchers.IO) { GuideRepository().load() }
                }.onSuccess {
                    guide = it
                    navigator = GuideNavigator(it.channels)
                    selected = it.channels.firstOrNull { channel -> channel.available }
                    auPlayer.play(selected?.streamUrl)
                }.onFailure {
                    if (it is CancellationException) throw it
                    error = it.message ?: "Guide unavailable"
                }
            }

            AuTvScreen(
                guide = guide,
                selected = selected,
                guideOpen = guideOpen,
                error = error,
                playbackError = playbackError,
                player = auPlayer,
                onSelect = { channel ->
                    if (channel.available && !channel.streamUrl.isNullOrBlank()) {
                        playbackError = null
                        selected = channel
                        auPlayer.play(channel.streamUrl)
                        guideOpen = false
                    }
                },
                onRetry = { loadAttempt++ },
                onToggleGuide = { guideOpen = !guideOpen },
                onNext = {
                    navigator?.next(selected)?.let {
                        playbackError = null
                        selected = it
                        auPlayer.play(it.streamUrl)
                    }
                },
                onPrevious = {
                    navigator?.previous(selected)?.let {
                        playbackError = null
                        selected = it
                        auPlayer.play(it.streamUrl)
                    }
                }
            )
        }
    }

    override fun onStop() {
        auPlayer.player.pause()
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        if (auPlayer.player.mediaItemCount > 0) auPlayer.player.play()
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
    playbackError: String?,
    player: AuTvPlayer,
    onSelect: (Channel) -> Unit,
    onRetry: () -> Unit,
    onToggleGuide: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    val screenFocus = remember { FocusRequester() }
    val channelFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val firstAvailable = guide?.channels?.firstOrNull { it.available }?.number
    LaunchedEffect(guideOpen, firstAvailable) {
        if (!guideOpen || firstAvailable == null) screenFocus.requestFocus()
        else {
            listState.scrollToItem(guide!!.channels.indexOfFirst { it.number == firstAvailable })
            withFrameNanos { }
            channelFocus.requestFocus()
        }
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val isTv = context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
    val castAvailable = remember(context, isTv) {
        !isTv && runCatching { CastContext.getSharedInstance(context); true }.getOrDefault(false)
    }
    MaterialTheme {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onPreviewKeyEvent { event ->
                    if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_CHANNEL_UP -> { onNext(); true }
                        KeyEvent.KEYCODE_CHANNEL_DOWN -> { onPrevious(); true }
                        KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_MENU -> { onToggleGuide(); true }
                        else -> false
                    }
                }
                .onKeyEvent { event ->
                    if (!guideOpen && event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                        event.nativeKeyEvent.keyCode in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER)) {
                        onToggleGuide()
                        true
                    } else false
                }
                .focusRequester(screenFocus)
                .focusable()
        ) {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        this.player = player.player
                        useController = false
                        isFocusable = false
                        isFocusableInTouchMode = false
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

            Row(Modifier.align(Alignment.BottomEnd).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPrevious, enabled = firstAvailable != null) { Text("CH −") }
                Button(onClick = onNext, enabled = firstAvailable != null) { Text("CH +") }
                Button(onClick = onToggleGuide) { Text(if (guideOpen) "Close guide" else "Guide") }
                if (castAvailable) {
                    AndroidView(factory = { ctx ->
                        MediaRouteButton(ctx).also { CastButtonFactory.setUpMediaRouteButton(ctx, it) }
                    }, modifier = Modifier.size(48.dp))
                }
            }
            if (playbackError != null) {
                Surface(Modifier.align(Alignment.TopEnd).padding(24.dp)) {
                    Text(playbackError, Modifier.padding(12.dp))
                }
            }

            if (guideOpen) {
                Surface(
                    modifier = Modifier
                        .fillMaxHeight(0.85f)
                        .widthIn(max = 520.dp)
                        .fillMaxWidth()
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
                            LazyColumn(state = listState) {
                                items(guide.channels, key = { it.number }) { channel ->
                                    Button(
                                        onClick = { onSelect(channel) },
                                        enabled = channel.available,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                            .then(if (channel.number == firstAvailable) Modifier.focusRequester(channelFocus) else Modifier)
                                    ) {
                                        Column(Modifier.fillMaxWidth()) {
                                            Text(channel.number.toString() + "  " + channel.name)
                                            Text(channel.genre + " • " + channel.subgenre)
                                            if (!channel.available) Text("Currently unavailable")
                                        }
                                    }
                                }
                            }
                        } else if (error != null) {
                            Text("Could not load the guide. Check your connection.")
                            Button(onClick = onRetry) { Text("Retry") }
                        } else {
                            Text("Loading guide…")
                        }
                    }
                }
            }
        }
    }
}
