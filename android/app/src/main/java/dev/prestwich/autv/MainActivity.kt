package dev.prestwich.autv

import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.Image
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.ui.PlayerView
import androidx.mediarouter.app.MediaRouteButton
import coil.compose.AsyncImage
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import dev.prestwich.autv.data.*
import dev.prestwich.autv.guide.GuideNavigator
import dev.prestwich.autv.player.AuTvPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Ink = Color(0xFF0A101B)
private val Panel = Color(0xFF131D2C)
private val Accent = Color(0xFF5CE2CE)
private val Muted = Color(0xFF9CAEC5)

class MainActivity : AppCompatActivity() {
    private lateinit var auPlayer: AuTvPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auPlayer = AuTvPlayer(this)
        setContent {
            var guide by remember { mutableStateOf<Guide?>(null) }
            var selected by remember { mutableStateOf<Channel?>(null) }
            var guideOpen by remember { mutableStateOf(true) }
            var loadError by remember { mutableStateOf(false) }
            var attempt by remember { mutableIntStateOf(0) }
            var epg by remember { mutableStateOf(Epg(emptyMap())) }
            val failed = remember { mutableStateMapOf<Int, String>() }
            var buffering by remember { mutableStateOf(false) }

            fun tune(channel: Channel) {
                if (channel.streamUrl.isNullOrBlank()) return
                selected = channel
                failed.remove(channel.number)
                buffering = true
                auPlayer.play(channel.streamUrl, channel.number)
            }
            DisposableEffect(Unit) {
                val listener = object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        // Associate the result with the actual media item, not a newer selection.
                        auPlayer.player.currentMediaItem?.mediaId?.toIntOrNull()?.let {
                            failed[it] = "Playback failed · select to retry"
                        }
                        buffering = false
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        buffering = state == Player.STATE_BUFFERING
                        if (state == Player.STATE_READY) auPlayer.player.currentMediaItem?.mediaId?.toIntOrNull()?.let { failed.remove(it) }
                    }
                }
                auPlayer.player.addListener(listener)
                onDispose { auPlayer.player.removeListener(listener) }
            }
            LaunchedEffect(attempt) {
                loadError = false
                try {
                    val loaded = withContext(Dispatchers.IO) { GuideRepository().load() }
                    guide = loaded
                    loaded.channels.firstOrNull { !it.streamUrl.isNullOrBlank() }?.let { tune(it) }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    loadError = true
                }
            }
            LaunchedEffect(Unit) {
                try { epg = withContext(Dispatchers.IO) { EpgRepository().load() } }
                catch (error: Exception) { if (error is CancellationException) throw error }
            }
            AuTvScreen(
                guide, selected, guideOpen, loadError, failed.toMap(), buffering, epg, auPlayer.player,
                onSelect = { tune(it); guideOpen = false },
                onCloseGuide = { guideOpen = false }, onOpenGuide = { guideOpen = true },
                onRetry = { attempt++ },
                onNext = { GuideNavigator(guide?.channels.orEmpty()).next(selected)?.let { tune(it); guideOpen = false } },
                onPrevious = { GuideNavigator(guide?.channels.orEmpty()).previous(selected)?.let { tune(it); guideOpen = false } },
            )
        }
    }
    override fun onStop() { auPlayer.player.pause(); super.onStop() }
    override fun onStart() { super.onStart(); if (auPlayer.player.mediaItemCount > 0) auPlayer.player.play() }
    override fun onDestroy() { auPlayer.release(); super.onDestroy() }
}

@Composable
internal fun AuTvScreen(
    guide: Guide?, selected: Channel?, guideOpen: Boolean, loadError: Boolean,
    failed: Map<Int, String>, buffering: Boolean, epg: Epg, player: Player,
    onSelect: (Channel) -> Unit, onCloseGuide: () -> Unit, onOpenGuide: () -> Unit,
    onRetry: () -> Unit, onNext: () -> Unit, onPrevious: () -> Unit,
) {
    val context = LocalContext.current
    val isTv = LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
    val rootFocus = remember { FocusRequester() }
    val closeFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(30_000) } }
    BackHandler(enabled = guideOpen, onBack = onCloseGuide)
    LaunchedEffect(guideOpen) {
        withFrameNanos { }
        if (guideOpen) closeFocus.requestFocus() else rootFocus.requestFocus()
    }
    LaunchedEffect(guideOpen, guide) {
        if (guideOpen && guide != null) {
            val index = guide.channels.indexOfFirst { it.number == selected?.number }
            if (index >= 0) listState.scrollToItem(index)
        }
    }
    val playable = guide?.channels?.any { !it.streamUrl.isNullOrBlank() } == true
    MaterialTheme(colorScheme = darkColorScheme(primary = Accent, onPrimary = Ink, background = Ink, surface = Panel, onSurface = Color.White, onSurfaceVariant = Muted)) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Box(Modifier.fillMaxSize().background(Ink)
                .onPreviewKeyEvent { event ->
                    if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) false
                    else when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_CHANNEL_UP -> { onNext(); true }
                        KeyEvent.KEYCODE_CHANNEL_DOWN -> { onPrevious(); true }
                        KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_MENU -> { if (guideOpen) onCloseGuide() else onOpenGuide(); true }
                        else -> false
                    }
                }
                .onKeyEvent { event ->
                    if (!guideOpen && event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && event.nativeKeyEvent.keyCode in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER)) {
                        onOpenGuide(); true
                    } else false
                }.focusRequester(rootFocus).focusable().testTag("app-root")) {
                AndroidView(factory = { ctx -> PlayerView(ctx).apply {
                    this.player = player; useController = false; isFocusable = false; isFocusableInTouchMode = false
                } }, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Ink.copy(alpha = 0.9f), Color.Transparent, Ink.copy(alpha = 0.95f)))))
                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = if (isTv) 32.dp else 12.dp)) {
                    // Header is outside the drawer and always inside the system safe area.
                    Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.autv_logo), "AUTV logo", Modifier.size(40.dp))
                        Column(Modifier.padding(start = 10.dp).weight(1f)) {
                            Text("AU TV", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
                            Text("YOUR CHANNELS. YOUR TV.", color = Accent, style = MaterialTheme.typography.labelSmall)
                        }
                        CastControl(isTv)
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        if (!guideOpen) {
                            Column(Modifier.align(Alignment.BottomStart).padding(bottom = 20.dp).widthIn(max = 600.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ChannelLogo(selected, Modifier.size(56.dp))
                                    Column(Modifier.padding(start = 12.dp)) {
                                        Text(selected?.let { "${it.number}  ${it.name}" } ?: "Welcome to AU TV", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                        Text(selected?.genre ?: "Open the guide to find your channel", color = Accent)
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                val schedule = epg.at(selected?.tvgId, now)
                                Text(schedule.now?.title ?: "Programme details unavailable", style = MaterialTheme.typography.titleMedium)
                                schedule.next?.let { Text("NEXT  ${clock(it.start)}  ${it.title}", color = Muted, style = MaterialTheme.typography.bodySmall) }
                                if (buffering) Text("Connecting to stream…", color = Accent, modifier = Modifier.padding(top = 8.dp))
                                failed[selected?.number]?.let { Text(it, color = Color(0xFFFFB4AB), modifier = Modifier.padding(top = 8.dp)) }
                            }
                        }
                        if (guideOpen) {
                            if (!isTv) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(onClick = onCloseGuide).testTag("guide-scrim"))
                            Surface(Modifier.fillMaxHeight().fillMaxWidth(if (isTv) 0.55f else 0.9f).widthIn(max = 560.dp)
                                .align(if (isTv) Alignment.CenterStart else Alignment.CenterEnd)
                                .pointerInput(Unit) { detectTapGestures { } }.testTag("guide-panel"),
                                color = Panel.copy(alpha = 0.97f), shape = RoundedCornerShape(24.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))) {
                                Column(Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text("CHANNEL GUIDE", color = Accent, style = MaterialTheme.typography.labelMedium)
                                            Text("Find your next favourite", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        }
                                        TextButton(onClick = onCloseGuide, modifier = Modifier.focusRequester(closeFocus).testTag("close-guide")) { Text("Close") }
                                    }
                                    Text("${guide?.channels?.count { !it.streamUrl.isNullOrBlank() } ?: 0} streams • ${guide?.activeCount ?: 0} channels", color = Muted, style = MaterialTheme.typography.bodySmall)
                                    Spacer(Modifier.height(12.dp))
                                    if (guide != null) LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items(guide.channels, key = { it.number }) { channel ->
                                            ChannelRow(channel, selected?.number == channel.number, failed[channel.number], epg.at(channel.tvgId, now), onSelect)
                                        }
                                    } else if (loadError) {
                                        Text("The guide could not be loaded. Check your connection.")
                                        Button(onClick = onRetry) { Text("Retry") }
                                    } else {
                                        CircularProgressIndicator(Modifier.padding(24.dp))
                                        Text("Loading your channels…", color = Muted)
                                    }
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onPrevious, enabled = playable, modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("ch-minus")) { Text("CH −") }
                        Button(onClick = { if (guideOpen) onCloseGuide() else onOpenGuide() }, modifier = Modifier.weight(1.3f).heightIn(min = 52.dp).testTag("guide-toggle")) { Text(if (guideOpen) "Close guide" else "Guide") }
                        OutlinedButton(onClick = onNext, enabled = playable, modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("ch-plus")) { Text("CH +") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(channel: Channel, selected: Boolean, failed: String?, schedule: NowNext, onSelect: (Channel) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val known = !channel.streamUrl.isNullOrBlank()
    val status = failed ?: if (known) "Stream known" else "Guide only"
    Row(Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
        .background(if (selected || focused) Color(0xFF20374A) else Ink.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
        .border(if (focused || selected) 2.dp else 1.dp, if (focused || selected) Accent else Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
        .clickable(enabled = known) { onSelect(channel) }.padding(12.dp).testTag("channel-${channel.number}"), verticalAlignment = Alignment.CenterVertically) {
        ChannelLogo(channel, Modifier.size(44.dp))
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text("${channel.number}  ${channel.name}", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(schedule.now?.title ?: channel.genre, color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            schedule.next?.let { Text("Next: ${it.title}", color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Text(if (selected && failed == null) "SELECTED · $status" else status, color = if (failed != null) Color(0xFFFFB4AB) else if (known) Accent else Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ChannelLogo(channel: Channel?, modifier: Modifier) {
    Box(modifier.background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(10.dp)).padding(5.dp), contentAlignment = Alignment.Center) {
        if (channel?.logoUrl != null) AsyncImage(model = channel.logoUrl, contentDescription = "${channel.name} logo", modifier = Modifier.fillMaxSize(), error = painterResource(R.drawable.autv_logo))
        else Image(painterResource(R.drawable.autv_logo), "Channel logo", Modifier.fillMaxSize())
    }
}

@Composable
private fun CastControl(isTv: Boolean) {
    val context = LocalContext.current
    val supported = remember(context, isTv) { !isTv && runCatching { CastContext.getSharedInstance(context); true }.getOrDefault(false) }
    var explanation by remember { mutableStateOf(false) }
    Box(Modifier.size(56.dp).testTag("cast-control"), contentAlignment = Alignment.Center) {
        if (supported) AndroidView(factory = { ctx -> MediaRouteButton(ctx).also {
            it.setAlwaysVisible(true)
            it.contentDescription = "Cast to a screen"
            CastButtonFactory.setUpMediaRouteButton(ctx, it)
        } }, modifier = Modifier.size(48.dp))
        else TextButton(onClick = { explanation = true }) { Text("Cast", color = Accent) }
    }
    if (explanation) AlertDialog(onDismissRequest = { explanation = false }, title = { Text("Google Cast") },
        text = { Text(if (isTv) "Use AU TV on a phone or tablet to choose a Cast screen." else "Cast needs Google Play services and a compatible screen on the same Wi-Fi network.") },
        confirmButton = { TextButton(onClick = { explanation = false }) { Text("OK") } })
}

private fun clock(time: Long) = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(time))
