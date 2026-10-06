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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
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
    castPlayback: dev.prestwich.autv.guide.CastPlayback = dev.prestwich.autv.guide.CastPlayback(),
) {
    val isTv = LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val keyboardOpen = guideOpen && !isTv && WindowInsets.isImeVisible
    val keyboard = LocalSoftwareKeyboardController.current
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val inputMode = LocalInputModeManager.current
    LaunchedEffect(isTv) { if (isTv) inputMode.requestInputMode(InputMode.Keyboard) }
    val rootFocus = remember { FocusRequester() }
    val selectedFocus = remember { FocusRequester() }
    val firstRowFocus = remember { FocusRequester() }
    val categoryFocus = remember { FocusRequester() }
    val guideControlFocus = remember { FocusRequester() }
    val previousFocus = remember { FocusRequester() }
    val nextFocus = remember { FocusRequester() }
    val closeFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val categoryState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var category by rememberSaveable { mutableStateOf("All") }
    var query by rememberSaveable { mutableStateOf("") }
    var committedQuery by remember { mutableStateOf("") }
    var chrome by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var wakeKey by remember { mutableIntStateOf(-1) }
    var digits by remember { mutableStateOf("") }
    LaunchedEffect(digits) {
        if (digits.isNotEmpty()) { delay(1_500); digits.toIntOrNull()?.let(onTuneNumber); digits = "" }
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    fun wake() { chrome = true; interaction++ }
    fun chooseCategory(value: String) {
        if (category == value) return
        category = value
        scope.launch {
            listState.scrollToItem(0)
            if (isTv) {
                categoryState.scrollToItem(GuideBrowse.categories.indexOf(value).coerceAtLeast(0))
                categoryFocus.requestFocus()
            }
        }
        wake()
    }
    val channelsByCategory = remember(guide) {
        val channels = guide?.channels.orEmpty()
        buildMap<String, List<Channel>> {
            put("All", channels)
            GuideBrowse.categories.drop(1).forEach { genre -> put(genre, channels.filter { it.genre == genre }) }
        }
    }
    val categoryChannels = channelsByCategory[category].orEmpty()
    // Keep text entry immediate; only commit the expensive catalogue/programme search after a short pause.
    LaunchedEffect(query) {
        if (query.isBlank()) {
            committedQuery = ""
        } else {
            delay(180)
            committedQuery = query
        }
    }
    // Normal guide browsing should not rebuild the channel list every time the 15-second
    // programme-progress clock ticks. Only programme-title searches need a time key.
    val searchClock = if (committedQuery.isBlank()) 0L else now / 60_000L
    val filtered = remember(categoryChannels, committedQuery, epg, searchClock) {
        GuideBrowse.filter(categoryChannels, "All", committedQuery, epg, now)
    }
    LaunchedEffect(committedQuery) {
        if (guideOpen && committedQuery.isNotEmpty()) listState.scrollToItem(0)
    }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(15_000) } }
    LaunchedEffect(windowFocused) { if (windowFocused) wake() }
    LaunchedEffect(guideOpen, interaction, selected?.number, buffering, failed[selected?.number], castPlayback.phase) {
        chrome = true
        if (!guideOpen) { delay(3_500); chrome = false }
    }
    LaunchedEffect(chrome, guideOpen, pictureInPicture) {
        onChromeVisibilityChanged(!pictureInPicture && (chrome || guideOpen))
        if (!chrome && !pictureInPicture) rootFocus.requestFocus()
        else if (isTv && !guideOpen && !pictureInPicture) {
            withFrameNanos { }; guideControlFocus.requestFocus()
        }
    }
    BackHandler(enabled = !pictureInPicture && (guideOpen || chrome)) {
        if (guideOpen) { wake(); onCloseGuide() } else chrome = false
    }
    LaunchedEffect(guideOpen, guide, pictureInPicture) {
        if (pictureInPicture) return@LaunchedEffect
        wake()
        if (guideOpen) {
            if (isTv) inputMode.requestInputMode(InputMode.Keyboard)
            query = ""
            committedQuery = ""
            category = if (isTv) selected?.genre?.takeIf { it in GuideBrowse.categories } ?: "All"
                else GuideBrowse.categoryForPlaying(category, selected)
            withFrameNanos { }
            categoryState.scrollToItem(GuideBrowse.categories.indexOf(category).coerceAtLeast(0))
            val rows = GuideBrowse.filter(guide?.channels.orEmpty(), category, "", epg, now)
            val index = rows.indexOfFirst { it.number == selected?.number }
            if (index >= 0) listState.scrollToItem(index)
            withFrameNanos { }
            if (isTv) {
                if (index >= 0 && !selected?.streamUrl.isNullOrBlank()) selectedFocus.requestFocus() else closeFocus.requestFocus()
            }
        } else { withFrameNanos { }; if (isTv) guideControlFocus.requestFocus() else rootFocus.requestFocus() }
    }
    val playable = guide?.channels?.any { !it.streamUrl.isNullOrBlank() } == true
    MaterialTheme(colorScheme = darkColorScheme(primary = Silver, onPrimary = Ink, background = Ink, surface = Glass,
        onSurface = WarmWhite, onSurfaceVariant = Muted, outline = Color(0xFF595B62),
        primaryContainer = Color(0xFF34343B), onPrimaryContainer = WarmWhite,
        secondary = Silver, secondaryContainer = Color(0xFF34343B), onSecondaryContainer = WarmWhite,
        surfaceTint = Silver)) {
        CompositionLocalProvider(LocalContentColor provides WarmWhite) {
            Box(Modifier.fillMaxSize().background(Color.Black).semantics { testTagsAsResourceId = true }
                .then(if (isTv) Modifier else Modifier.pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial); wake() } })
                .onPreviewKeyEvent { event ->
                    if (event.nativeKeyEvent.action == KeyEvent.ACTION_UP && event.nativeKeyEvent.keyCode == wakeKey) {
                        wakeKey = -1; true
                    } else if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN || event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK) false else {
                        val wasHidden = !chrome
                        wake()
                        when {
                            isTv && !guideOpen && event.nativeKeyEvent.keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                                digits = (digits + (event.nativeKeyEvent.keyCode - KeyEvent.KEYCODE_0)).takeLast(4); true
                            }
                            digits.isNotEmpty() && event.nativeKeyEvent.keyCode in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER) -> {
                                digits.toIntOrNull()?.let(onTuneNumber); digits = ""; wakeKey = event.nativeKeyEvent.keyCode; true
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
                            else -> if (!guideOpen && wasHidden && event.nativeKeyEvent.keyCode in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)) {
                                wakeKey = event.nativeKeyEvent.keyCode; true
                            } else false
                            }
                        }
                    }
                }
                .focusProperties { canFocus = !guideOpen && !pictureInPicture }.focusRequester(rootFocus).focusable().testTag("app-root")) {
                AndroidView(factory = { ctx -> PlayerView(ctx).apply {
                    this.player = player; useController = false; isFocusable = false; isFocusableInTouchMode = false
                    descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                } }, update = { it.player = player }, modifier = Modifier.fillMaxSize().testTag("video"))
                if (!pictureInPicture) AnimatedVisibility(visible = chrome || guideOpen, enter = fadeIn(tween(180)), exit = fadeOut(tween(250))) {
                    Box(Modifier.fillMaxSize().testTag("chrome")) {
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Ink.copy(alpha = 0.82f), Color.Transparent, Ink.copy(alpha = 0.92f)))))
                        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = if (isTv) 32.dp else 12.dp)) {
                            Row(Modifier.fillMaxWidth().height(if (landscape && !isTv) 48.dp else 60.dp), verticalAlignment = Alignment.CenterVertically) {
                                Image(painterResource(R.drawable.autv_logo), "AUTV logo", Modifier.size(32.dp))
                                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                                    Text("AUTV", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    if (castPlayback.phase != dev.prestwich.autv.guide.CastPhase.DISCONNECTED) Text(
                                        when (castPlayback.phase) {
                                            dev.prestwich.autv.guide.CastPhase.CONNECTED -> "Connected to ${castPlayback.receiver}"
                                            dev.prestwich.autv.guide.CastPhase.LOAD_REQUESTED -> "Sending to ${castPlayback.receiver}…"
                                            dev.prestwich.autv.guide.CastPhase.LOAD_ACCEPTED -> "Starting on ${castPlayback.receiver}…"
                                            dev.prestwich.autv.guide.CastPhase.PLAYING -> "Playing on ${castPlayback.receiver}"
                                            dev.prestwich.autv.guide.CastPhase.PAUSED -> "Paused on ${castPlayback.receiver}"
                                            else -> "Cast failed · playing locally"
                                        }, color = Silver, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.testTag("cast-status"))
                                }
                                if (digits.isNotEmpty()) Text("Tune $digits", color = Silver, modifier = Modifier.padding(end = 12.dp))
                                if (!isTv) CastControl(false, onInteraction = { wake() }, compact = landscape)
                                else if (!guideOpen) {
                                    FocusButton("CH −", { wake(); onPrevious() }, Modifier.focusRequester(previousFocus).focusProperties { right = guideControlFocus }.testTag("ch-minus"), playable)
                                    FocusButton("Guide", { wake(); onOpenGuide() }, Modifier.padding(horizontal = 8.dp).focusRequester(guideControlFocus)
                                        .focusProperties { left = previousFocus; right = nextFocus }.testTag("guide-toggle"))
                                    FocusButton("CH +", { wake(); onNext() }, Modifier.focusRequester(nextFocus).focusProperties { left = guideControlFocus }.testTag("ch-plus"), playable)
                                }
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
                                    castPlayback.error?.let { Text(it, color = Color(0xFFD8B3AD), style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("cast-error")) }
                                    failed[selected?.number]?.let { Text("Could not play this channel. Select it in the guide to retry.", color = Color(0xFFD8B3AD), style = MaterialTheme.typography.bodySmall) }
                                }
                                if (guideOpen) {
                                    if (!isTv) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).clickable { wake(); onCloseGuide() }.testTag("guide-scrim"))
                                    val compact = landscape && !isTv
                                    val panelWidth = if (isTv) maxWidth * 0.88f else if (compact) maxWidth * 0.54f else (maxWidth * 0.92f).coerceAtMost(680.dp)
                                    val firstPlayable = filtered.firstOrNull { !it.streamUrl.isNullOrBlank() }?.number
                                    // Lazy rows outside the viewport have no attached focus target.
                                    val selectedVisible = listState.layoutInfo.visibleItemsInfo.any { it.key == selected?.number }
                                    val rowTarget = if (selectedVisible && !selected?.streamUrl.isNullOrBlank() || firstPlayable == selected?.number && firstPlayable != null) selectedFocus
                                        else if (firstPlayable != null) firstRowFocus else closeFocus
                                    Surface(Modifier.fillMaxHeight(if (isTv) 0.96f else 1f).width(panelWidth).align(if (isTv) Alignment.Center else Alignment.CenterEnd)
                                        .pointerInput(Unit) { detectTapGestures { } }.testTag("guide-panel"), color = Glass, contentColor = WarmWhite,
                                        shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))) {
                                        Column(Modifier.padding(if (compact) 8.dp else 12.dp).focusGroup()) {
                                            if (!compact) Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("Channel guide", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                                FocusButton("Close", { wake(); onCloseGuide() }, Modifier.focusRequester(closeFocus)
                                                    .focusProperties { down = rowTarget; left = searchFocus }.testTag("close-guide"))
                                            }
                                            notice?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) }
                                            castPlayback.error?.let { Text(it, color = Color(0xFFD8B3AD), style = MaterialTheme.typography.bodySmall) }
                                            if (compact) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                BasicTextField(value = query, onValueChange = { query = it; wake() },
                                                    singleLine = true, textStyle = MaterialTheme.typography.bodyMedium.copy(color = WarmWhite), cursorBrush = androidx.compose.ui.graphics.SolidColor(Silver),
                                                    modifier = Modifier.weight(1f).height(40.dp).background(Ink.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                                        .border(1.dp, Muted.copy(alpha = 0.4f), RoundedCornerShape(8.dp)).focusRequester(searchFocus).testTag("guide-search"),
                                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                                                    decorationBox = { field -> Box(Modifier.fillMaxSize().padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
                                                        if (query.isEmpty()) Text("Search channels", color = Muted, style = MaterialTheme.typography.bodySmall)
                                                        field()
                                                    } })
                                                TextButton(onClick = { keyboard?.hide(); wake(); onCloseGuide() }, modifier = Modifier.focusRequester(closeFocus).testTag("close-guide"),
                                                    contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Close") }
                                            } else OutlinedTextField(value = query, onValueChange = { query = it; wake() },
                                                placeholder = { Text("Search channels or programmes", style = MaterialTheme.typography.bodySmall) }, singleLine = true,
                                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).focusRequester(searchFocus)
                                                    .focusProperties { down = categoryFocus; right = closeFocus }.testTag("guide-search"),
                                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                                                trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { query = ""; committedQuery = ""; wake() }, modifier = Modifier.semantics { contentDescription = "Clear search query" }) { Text("Clear") } }, shape = RoundedCornerShape(12.dp))
                                            LazyRow(state = categoryState, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = if (compact) 0.dp else 4.dp).testTag("guide-categories")) {
                                                items(GuideBrowse.categories) { genre ->
                                                    var focused by remember { mutableStateOf(false) }
                                                    FilterChip(selected = category == genre, onClick = { chooseCategory(genre) }, label = { Text(genre, style = MaterialTheme.typography.labelMedium) },
                                                        modifier = Modifier.then(if (category == genre) Modifier.focusRequester(categoryFocus) else Modifier)
                                                            .focusProperties { down = rowTarget; up = searchFocus }.onFocusChanged { focused = it.isFocused }
                                                            .then(if (focused && isTv) Modifier.border(3.dp, WarmWhite, RoundedCornerShape(8.dp)) else Modifier)
                                                            .height(if (compact) 32.dp else 40.dp).testTag("category-$genre"))
                                                }
                                            }
                                            if (guide != null) {
                                                if (filtered.isEmpty()) Text("No channels match your search.", color = Muted, modifier = Modifier.padding(12.dp))
                                                LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f).testTag("guide-list")) {
                                                    items(filtered, key = { it.number }) { channel ->
                                                        ChannelRow(channel, selected?.number == channel.number, failed[channel.number], epg.at(channel, now), now,
                                                            Modifier.then(if (channel.number == selected?.number) Modifier.focusRequester(selectedFocus)
                                                                else if (channel.number == firstPlayable) Modifier.focusRequester(firstRowFocus) else Modifier)
                                                                .focusProperties {
                                                                    if (isTv) {
                                                                        left = FocusRequester.Cancel; right = FocusRequester.Cancel
                                                                        if (channel.number == firstPlayable) up = categoryFocus
                                                                    }
                                                                }, compact = compact, tv = isTv) { wake(); onSelect(it) }
                                                    }
                                                }
                                            } else if (loadError) { Text("The guide could not be loaded."); Button(onClick = onRetry) { Text("Retry") } }
                                            else { CircularProgressIndicator(Modifier.size(28.dp)); Text("Loading your channels…", color = Muted) }
                                        }
                                    }
                                }
                            }
                            if (!isTv && !guideOpen && !keyboardOpen) Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
private fun ChannelRow(channel: Channel, selected: Boolean, failed: String?, schedule: NowNext, now: Long, modifier: Modifier,
    compact: Boolean = false, tv: Boolean = false, onSelect: (Channel) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val known = !channel.streamUrl.isNullOrBlank()
    Row(modifier.fillMaxWidth().focusProperties { canFocus = known }.onFocusChanged { focused = it.isFocused }
        .background(if (focused && tv) Silver.copy(alpha = 0.22f) else if (selected || focused) Color.White.copy(alpha = 0.075f) else Color.Black.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
        .border(if (focused && tv) 3.dp else if (focused) 2.dp else 1.dp, if (focused) WarmWhite else if (selected) Silver.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
        .clickable(enabled = known) { onSelect(channel) }.padding(if (compact) 6.dp else 10.dp).testTag("channel-${channel.number}"), verticalAlignment = Alignment.Top) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ChannelLogo(channel, Modifier.size(if (compact) 32.dp else if (tv) 48.dp else 44.dp))
            Text(channel.number.toString(), color = Silver, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
        }
        Column(Modifier.padding(start = if (compact) 8.dp else 12.dp).weight(1f)) {
            Text(channel.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            ProgrammeInfo(schedule, now, channel.genre, compact)
            if (!known) Text("No live stream", color = Muted, style = MaterialTheme.typography.labelSmall)
            if (failed != null) Text("Could not play · Select to retry", color = Color(0xFFD8B3AD), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ProgrammeInfo(schedule: NowNext, now: Long, fallback: String = "Live television", compact: Boolean = false) {
    val current = schedule.now
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(current?.title ?: fallback, modifier = Modifier.weight(1f), color = if (current == null) Muted else WarmWhite,
            style = MaterialTheme.typography.bodySmall, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
        if (compact && current != null) Text("${clock(current.start)}–${clock(current.stop)}", color = Muted,
            modifier = Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelSmall)
    }
    if (current != null) {
        if (!compact) Text("${clock(current.start)} – ${clock(current.stop)}", color = Muted, style = MaterialTheme.typography.labelSmall)
        LinearProgressIndicator(progress = { ((now - current.start).toDouble() / (current.stop - current.start).coerceAtLeast(1)).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(vertical = if (compact) 2.dp else 5.dp).height(2.dp).testTag("programme-progress"), color = Silver, trackColor = Color.White.copy(alpha = 0.12f))
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
private fun CastControl(isTv: Boolean, onInteraction: () -> Unit, compact: Boolean = false) {
    val context = LocalContext.current
    val supported = remember(context, isTv) { !isTv && runCatching { CastContext.getSharedInstance(context); true }.getOrDefault(false) }
    var explanation by remember { mutableStateOf(false) }
    Box(Modifier.size(if (compact) 48.dp else 52.dp).testTag("cast-control"), contentAlignment = Alignment.Center) {
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

@Composable
private fun FocusButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    var focused by remember { mutableStateOf(false) }
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.onFocusChanged { focused = it.isFocused }
        .heightIn(min = 48.dp).border(if (focused) 3.dp else 1.dp, if (focused) WarmWhite else Silver.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = if (focused) Silver.copy(alpha = 0.22f) else Color.Transparent)) {
        Text(label)
    }
}

private fun clock(time: Long) = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(time))
