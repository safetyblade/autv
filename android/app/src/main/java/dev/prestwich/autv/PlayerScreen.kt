package dev.prestwich.autv

import android.content.res.Configuration
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.mediarouter.app.MediaRouteButton
import coil.compose.AsyncImage
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import dev.prestwich.autv.data.*
import dev.prestwich.autv.guide.GuideBrowse
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Ink = Color(0xFF070A10)
private val Glass = Color(0xF0181C24)
private val WarmWhite = Color(0xFFF2EFE9)
private val Silver = Color(0xFFCEC4B4)
private val Muted = Color(0xFFA5A8AF)

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun AuTvScreen(
    guide: Guide?, selected: Channel?, guideOpen: Boolean, loadError: Boolean,
    failed: Map<Int, String>, buffering: Boolean, epg: Epg, player: Player,
    onSelect: (Channel) -> Unit, onCloseGuide: () -> Unit, onOpenGuide: () -> Unit,
    onRetry: () -> Unit, onNext: () -> Unit, onPrevious: () -> Unit,
    onChromeVisibilityChanged: (Boolean) -> Unit = {},
    pictureInPicture: Boolean = false, notice: String? = null, onTuneNumber: (Int) -> Unit = {},
) {
    val isTv = LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val keyboardOpen = guideOpen && !isTv && WindowInsets.isImeVisible
    val keyboard = LocalSoftwareKeyboardController.current
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val inputMode = LocalInputModeManager.current
    val rootFocus = remember { FocusRequester() }
    val selectedFocus = remember { FocusRequester() }
    val closeFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val categoryState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var category by rememberSaveable { mutableStateOf("All") }
    var query by rememberSaveable { mutableStateOf("") }
    var chrome by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var digits by remember { mutableStateOf("") }
    LaunchedEffect(digits) {
        if (digits.isNotEmpty()) { delay(1_500); digits.toIntOrNull()?.let(onTuneNumber); digits = "" }
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    fun wake() { chrome = true; interaction++ }
    fun chooseCategory(value: String) {
        category = value
        scope.launch { listState.scrollToItem(0) }
        wake()
    }
    val filtered = remember(guide, category, query, epg, now) {
        GuideBrowse.filter(guide?.channels.orEmpty(), category, query, epg, now)
    }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(15_000) } }
    LaunchedEffect(guideOpen, interaction, selected?.number, buffering, failed[selected?.number], windowFocused) {
        chrome = true
        if (!guideOpen && windowFocused) { delay(3_500); chrome = false }
    }
    LaunchedEffect(chrome, guideOpen, pictureInPicture) {
        onChromeVisibilityChanged(!pictureInPicture && (chrome || guideOpen))
        if (!chrome && !pictureInPicture) rootFocus.requestFocus()
    }
    BackHandler(enabled = guideOpen && !pictureInPicture) { wake(); onCloseGuide() }
    LaunchedEffect(guideOpen, guide, windowFocused, pictureInPicture) {
        if (!windowFocused || pictureInPicture) return@LaunchedEffect
        wake()
        if (guideOpen) {
            if (isTv) inputMode.requestInputMode(InputMode.Keyboard)
            query = ""
            category = GuideBrowse.categoryForPlaying(category, selected)
            categoryState.scrollToItem(GuideBrowse.categories.indexOf(category).coerceAtLeast(0))
            val rows = GuideBrowse.filter(guide?.channels.orEmpty(), category, "", epg, now)
            val index = rows.indexOfFirst { it.number == selected?.number }
            if (index >= 0) listState.scrollToItem(index)
            withFrameNanos { }
            if (index >= 0 && !selected?.streamUrl.isNullOrBlank()) selectedFocus.requestFocus() else closeFocus.requestFocus()
        } else { withFrameNanos { }; rootFocus.requestFocus() }
    }
    val playable = guide?.channels?.any { !it.streamUrl.isNullOrBlank() } == true
    MaterialTheme(colorScheme = darkColorScheme(primary = Silver, onPrimary = Ink, background = Ink, surface = Glass,
        onSurface = WarmWhite, onSurfaceVariant = Muted, outline = Color(0xFF595B62),
        primaryContainer = Color(0xFF34343B), onPrimaryContainer = WarmWhite,
        secondary = Silver, secondaryContainer = Color(0xFF34343B), onSecondaryContainer = WarmWhite,
        surfaceTint = Silver)) {
        CompositionLocalProvider(LocalContentColor provides WarmWhite) {
            Box(Modifier.fillMaxSize().background(Color.Black)
                .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial); wake() } }
                .onPreviewKeyEvent { event ->
                    if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) false else {
                        val wasHidden = !chrome
                        wake()
                        when {
                            isTv && !guideOpen && event.nativeKeyEvent.keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                                digits = (digits + (event.nativeKeyEvent.keyCode - KeyEvent.KEYCODE_0)).takeLast(4); true
                            }
                            digits.isNotEmpty() && event.nativeKeyEvent.keyCode in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER) -> {
                                digits.toIntOrNull()?.let(onTuneNumber); digits = ""; true
                            }
                            else -> when (event.nativeKeyEvent.keyCode) {
                            KeyEvent.KEYCODE_CHANNEL_UP -> { onNext(); true }
                            KeyEvent.KEYCODE_CHANNEL_DOWN -> { onPrevious(); true }
                            KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_MENU -> { if (guideOpen) onCloseGuide() else onOpenGuide(); true }
                            KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_BUTTON_L1 -> {
                                if (guideOpen) { chooseCategory(GuideBrowse.categories[(GuideBrowse.categories.indexOf(category) - 1 + GuideBrowse.categories.size) % GuideBrowse.categories.size]); true } else false
                            }
                            KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_BUTTON_R1 -> {
                                if (guideOpen) { chooseCategory(GuideBrowse.categories[(GuideBrowse.categories.indexOf(category) + 1) % GuideBrowse.categories.size]); true } else false
                            }
                            else -> wasHidden && event.nativeKeyEvent.keyCode in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)
                            }
                        }
                    }
                }
                .onKeyEvent { event ->
                    if (!guideOpen && event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && event.nativeKeyEvent.keyCode in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER)) { wake(); true } else false
                }.focusRequester(rootFocus).focusable().testTag("app-root")) {
                AndroidView(factory = { ctx -> PlayerView(ctx).apply {
                    this.player = player; useController = false; isFocusable = false; isFocusableInTouchMode = false
                } }, modifier = Modifier.fillMaxSize().testTag("video"))
                if (!pictureInPicture) AnimatedVisibility(visible = chrome || guideOpen, enter = fadeIn(tween(180)), exit = fadeOut(tween(250))) {
                    Box(Modifier.fillMaxSize().testTag("chrome")) {
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Ink.copy(alpha = 0.82f), Color.Transparent, Ink.copy(alpha = 0.92f)))))
                        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = if (isTv) 32.dp else 12.dp)) {
                            Row(Modifier.fillMaxWidth().height(if (keyboardOpen && landscape) 48.dp else 60.dp), verticalAlignment = Alignment.CenterVertically) {
                                Image(painterResource(R.drawable.autv_logo), "AUTV logo", Modifier.size(32.dp))
                                Text("AUTV", Modifier.padding(start = 10.dp).weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                if (digits.isNotEmpty()) Text("Tune $digits", color = Silver, modifier = Modifier.padding(end = 12.dp))
                                CastControl(isTv, onInteraction = { wake() })
                            }
                            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                                if (!guideOpen) Column(Modifier.align(Alignment.BottomStart).padding(bottom = 12.dp).widthIn(max = 640.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        ChannelLogo(selected, Modifier.size(52.dp))
                                        Column(Modifier.padding(start = 12.dp)) {
                                            Text(selected?.let { "${it.number}  ${it.name}" } ?: "Welcome to AUTV", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                                            Text(selected?.genre ?: "Find your channel in the guide", color = Muted, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    ProgrammeInfo(epg.at(selected, now), now)
                                    if (buffering) Text("Connecting…", color = Muted, style = MaterialTheme.typography.labelSmall)
                                    failed[selected?.number]?.let { Text("Could not play this channel. Select it in the guide to retry.", color = Color(0xFFD8B3AD), style = MaterialTheme.typography.bodySmall) }
                                }
                                if (guideOpen) {
                                    if (!isTv) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).clickable { wake(); onCloseGuide() }.testTag("guide-scrim"))
                                    val panelWidth = (maxWidth * if (isTv) 0.65f else 0.92f).coerceAtMost(680.dp)
                                    Surface(Modifier.fillMaxHeight().width(panelWidth).align(if (isTv) Alignment.CenterStart else Alignment.CenterEnd)
                                        .pointerInput(Unit) { detectTapGestures { } }.testTag("guide-panel"), color = Glass, contentColor = WarmWhite,
                                        shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))) {
                                        Column(Modifier.padding(12.dp)) {
                                            if (!keyboardOpen || !landscape) Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("Channel guide", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                                TextButton(onClick = { wake(); onCloseGuide() }, modifier = Modifier.focusRequester(closeFocus).testTag("close-guide")) { Text("Close") }
                                            }
                                            notice?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) }
                                            OutlinedTextField(value = query, onValueChange = { query = it; wake(); scope.launch { listState.scrollToItem(0) } },
                                                placeholder = { Text("Search channels or programmes", style = MaterialTheme.typography.bodySmall) }, singleLine = true,
                                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("guide-search"),
                                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                                                trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { query = ""; wake() }) { Text("Clear") } }, shape = RoundedCornerShape(12.dp))
                                            LazyRow(state = categoryState, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp).testTag("guide-categories")) {
                                                items(GuideBrowse.categories) { genre ->
                                                    FilterChip(selected = category == genre, onClick = { chooseCategory(genre) }, label = { Text(genre) }, modifier = Modifier.testTag("category-$genre"))
                                                }
                                            }
                                            if (guide != null) {
                                                if (filtered.isEmpty()) Text("No channels match your search.", color = Muted, modifier = Modifier.padding(12.dp))
                                                LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f).testTag("guide-list")) {
                                                    items(filtered, key = { it.number }) { channel ->
                                                        ChannelRow(channel, selected?.number == channel.number, failed[channel.number], epg.at(channel, now), now,
                                                            Modifier.then(if (channel.number == selected?.number) Modifier.focusRequester(selectedFocus) else Modifier)) { wake(); onSelect(it) }
                                                    }
                                                }
                                            } else if (loadError) { Text("The guide could not be loaded."); Button(onClick = onRetry) { Text("Retry") } }
                                            else { CircularProgressIndicator(Modifier.size(28.dp)); Text("Loading your channels…", color = Muted) }
                                        }
                                    }
                                }
                            }
                            if (!keyboardOpen) Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { wake(); onPrevious() }, enabled = playable, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("ch-minus")) { Text("CH −") }
                                OutlinedButton(onClick = { wake(); if (guideOpen) onCloseGuide() else onOpenGuide() }, modifier = Modifier.weight(1.2f).heightIn(min = 48.dp).testTag("guide-toggle")) { Text(if (guideOpen) "Close guide" else "Guide") }
                                OutlinedButton(onClick = { wake(); onNext() }, enabled = playable, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("ch-plus")) { Text("CH +") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(channel: Channel, selected: Boolean, failed: String?, schedule: NowNext, now: Long, modifier: Modifier, onSelect: (Channel) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val known = !channel.streamUrl.isNullOrBlank()
    Row(modifier.fillMaxWidth().focusProperties { canFocus = known }.onFocusChanged { focused = it.isFocused }
        .background(if (selected || focused) Color.White.copy(alpha = 0.075f) else Color.Black.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
        .border(if (focused) 2.dp else 1.dp, if (focused) Silver else if (selected) Silver.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
        .clickable(enabled = known) { onSelect(channel) }.padding(10.dp).testTag("channel-${channel.number}"), verticalAlignment = Alignment.Top) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ChannelLogo(channel, Modifier.size(44.dp))
            Text(channel.number.toString(), color = Silver, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(channel.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            ProgrammeInfo(schedule, now, channel.genre)
            if (!known) Text("Not currently available", color = Muted, style = MaterialTheme.typography.labelSmall)
            if (failed != null) Text("Could not play · Select to retry", color = Color(0xFFD8B3AD), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ProgrammeInfo(schedule: NowNext, now: Long, fallback: String = "Live television") {
    val current = schedule.now
    Text(current?.title ?: fallback, color = if (current == null) Muted else WarmWhite, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    if (current != null) {
        Text("${clock(current.start)} – ${clock(current.stop)}", color = Muted, style = MaterialTheme.typography.labelSmall)
        LinearProgressIndicator(progress = { ((now - current.start).toDouble() / (current.stop - current.start).coerceAtLeast(1)).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).height(2.dp).testTag("programme-progress"), color = Silver, trackColor = Color.White.copy(alpha = 0.12f))
    }
    schedule.next?.let { Text("Next  ${clock(it.start)} · ${it.title}", color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

@Composable
private fun ChannelLogo(channel: Channel?, modifier: Modifier) {
    Box(modifier.background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(8.dp)).padding(4.dp), contentAlignment = Alignment.Center) {
        if (channel?.logoUrl != null) AsyncImage(model = channel.logoUrl, contentDescription = "${channel.name} logo", modifier = Modifier.fillMaxSize(), error = painterResource(R.drawable.autv_logo))
        else Image(painterResource(R.drawable.autv_logo), "Channel logo", Modifier.fillMaxSize())
    }
}

@Composable
private fun CastControl(isTv: Boolean, onInteraction: () -> Unit) {
    val context = LocalContext.current
    val supported = remember(context, isTv) { !isTv && runCatching { CastContext.getSharedInstance(context); true }.getOrDefault(false) }
    var explanation by remember { mutableStateOf(false) }
    Box(Modifier.size(52.dp).testTag("cast-control"), contentAlignment = Alignment.Center) {
        if (supported) AndroidView(factory = { ctx -> MediaRouteButton(ctx).also {
            it.setAlwaysVisible(true); it.contentDescription = "Cast to a screen"
            CastButtonFactory.setUpMediaRouteButton(ctx, it)
        } }, modifier = Modifier.size(48.dp))
        else TextButton(onClick = { onInteraction(); explanation = true }, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(0.dp)) {
            Text("Cast", color = Silver, maxLines = 1)
        }
    }
    if (explanation) AlertDialog(onDismissRequest = { explanation = false; onInteraction() }, title = { Text("Google Cast") },
        text = { Text(if (isTv) "Use AUTV on a phone or tablet to choose a Cast screen." else "Cast needs Google Play services and a compatible screen on the same Wi-Fi network.") },
        confirmButton = { TextButton(onClick = { explanation = false; onInteraction() }) { Text("OK") } })
}

private fun clock(time: Long) = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(time))
