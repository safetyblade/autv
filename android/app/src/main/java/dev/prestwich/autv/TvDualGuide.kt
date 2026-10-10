package dev.prestwich.autv

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.prestwich.autv.data.*
import dev.prestwich.autv.guide.GuideBrowse
import dev.prestwich.autv.guide.GuideViewport
import dev.prestwich.autv.guide.GuideRowBounds
import dev.prestwich.autv.guide.ProgrammeNavigation as Timeline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private val GuideInk = androidx.compose.ui.graphics.Color(0xFF101722)
private val GuideAccent = androidx.compose.ui.graphics.Color(0xFFF2EFE9)
private enum class GuideZone { CHANNELS, CATEGORIES, ACTIONS }

/** A single remote focus owner keeps rapid navigation independent of lazy-row attachment. */
@Composable
internal fun TvDualGuide(
    open: Boolean, guide: Guide?, playing: Channel?, epg: Epg, now: Long,
    failed: Map<Int, String>, onTune: (Channel) -> Unit, onClose: () -> Unit,
    onRetry: () -> Unit, loadError: Boolean, onFullTune: (Channel) -> Unit = onTune,
) {
    var channelCell by remember { mutableStateOf(false) }
    var full by rememberSaveable { mutableStateOf(false) }
    var quickCategory by rememberSaveable { mutableStateOf("All") }
    var fullCategory by rememberSaveable { mutableStateOf("All") }
    val category = if (full) fullCategory else quickCategory
    var quickNumber by rememberSaveable { mutableStateOf<Int?>(null) }
    var fullNumber by rememberSaveable { mutableStateOf<Int?>(null) }
    var cursor by rememberSaveable { mutableLongStateOf(now) }
    var window by rememberSaveable { mutableLongStateOf(Timeline.windowStart(now)) }
    var zone by remember { mutableStateOf(GuideZone.CHANNELS) }
    var action by remember { mutableIntStateOf(0) }
    var details by remember { mutableStateOf<Programme?>(null) }
    var search by remember { mutableStateOf(false) }
    var quickQuery by rememberSaveable { mutableStateOf("") }
    var fullQuery by rememberSaveable { mutableStateOf("") }
    val query = if (full) fullQuery else quickQuery
    val quickList = rememberLazyListState()
    val fullList = rememberLazyListState()
    val categoryList = rememberLazyListState()
    val remoteFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val channels = remember(guide, category, query) {
        guide?.channels.orEmpty().filter {
            (category == "All" || it.genre == category) &&
                (query.isBlank() || it.name.contains(query, true) || it.number.toString().contains(query))
        }
    }
    val number = if (full) fullNumber else quickNumber
    val selected = channels.firstOrNull { it.number == number } ?: channels.firstOrNull()
    val schedule = remember(epg, selected?.number) { epg.schedule(selected) }
    val programme = remember(schedule, cursor, window) { Timeline.at(schedule, cursor)?.takeIf { Timeline.block(it, window) != null } }
    val list = if (full) fullList else quickList
    val selectedIndex = channels.indexOfFirst { it.number == selected?.number }.coerceAtLeast(0)
    fun revealRow(index: Int) {
        val layout = list.layoutInfo
        GuideViewport.reveal(index, layout.viewportStartOffset, layout.viewportEndOffset,
            layout.visibleItemsInfo.map { GuideRowBounds(it.index, it.offset, it.size) }).let {
            list.requestScrollToItem(it.index, it.offset)
        }
    }
    fun selectNumber(value: Int?) { if (full) fullNumber = value else quickNumber = value }
    fun switchMode() {
        details = null
        channelCell = false
        zone = GuideZone.CHANNELS
        if (!full) { fullCategory = quickCategory; fullQuery = quickQuery; fullNumber = quickNumber; cursor = now; window = Timeline.windowStart(now) }
        full = !full
    }
    fun back() {
        when { search -> search = false; details != null -> details = null; full -> { full = false; zone = GuideZone.CHANNELS }; else -> onClose() }
    }
    fun chooseCategory(value: String) {
        if (full) fullCategory = value else quickCategory = value
        if (full) fullQuery = "" else quickQuery = ""
        val first = guide?.channels?.firstOrNull { value == "All" || it.genre == value }
        selectNumber(first?.number)
        scope.launch { list.scrollToItem(0) }
    }
    fun focusedChannel(): Channel? = channels.firstOrNull {
        it.number == if (full) fullNumber else quickNumber
    } ?: channels.firstOrNull()
    fun tune() {
        // Key repeats are consumed below. Only an explicit initial OK invokes this callback.
        focusedChannel()?.let { tuneChannel(it, if (full) onFullTune else onTune) }
    }
    LaunchedEffect(open, guide != null) {
        if (open) {
            full = false; details = null; search = false; quickQuery = ""; zone = GuideZone.CHANNELS
            quickCategory = if (quickNumber == null) playing?.genre?.takeIf { it in GuideBrowse.categories } ?: "All"
                else GuideBrowse.categoryForPlaying(quickCategory, playing)
            quickNumber = playing?.number ?: guide?.channels?.firstOrNull()?.number
            withFrameNanos { }
            remoteFocus.requestFocus()
        }
    }
    // Catalogue/EPG refresh never changes the remote focus owner or restarts playback.
    LaunchedEffect(open, full, selected?.number, channels) {
        if (open && channels.isNotEmpty()) {
            if (number != selected?.number) selectNumber(selected?.number)
            val visible = list.layoutInfo.visibleItemsInfo
            if (full) revealRow(selectedIndex)
            else if (visible.none { it.index == selectedIndex }) list.scrollToItem(selectedIndex)
        }
    }
    LaunchedEffect(open, category) {
        if (open) categoryList.scrollToItem(GuideBrowse.categories.indexOf(category).coerceAtLeast(0))
    }
    LaunchedEffect(full, cursor) {
        if (full && (cursor < window || cursor >= window + Timeline.WINDOW))
            window = Timeline.windowStart(cursor)
    }
    // A refreshed schedule retains the selected time; programme lookup resolves against the new snapshot.
    if (!open) return
    BackHandler { back() }
    Box(Modifier.fillMaxSize(), contentAlignment = if (full) Alignment.Center else Alignment.CenterStart) {
        Surface(
            Modifier.fillMaxHeight().fillMaxWidth(if (full) 1f else 0.78f)
                .focusRequester(remoteFocus)
                .onPreviewKeyEvent { event ->
                    val key = event.nativeKeyEvent
                    val handled = key.keyCode in listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_MENU,
                        KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1)
                    if (!handled || search) false
                    else if (key.action != KeyEvent.ACTION_DOWN) true
                    else {
                        when (key.keyCode) {
                            KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_MENU -> if (key.repeatCount == 0) switchMode()
                            KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_BUTTON_L1 -> chooseCategory(GuideBrowse.categories[(GuideBrowse.categories.indexOf(category) + GuideBrowse.categories.size - 1) % GuideBrowse.categories.size])
                            KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_BUTTON_R1 -> chooseCategory(GuideBrowse.categories[(GuideBrowse.categories.indexOf(category) + 1) % GuideBrowse.categories.size])
                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> if (key.repeatCount == 0) {
                                val focusedProgramme = Timeline.at(epg.schedule(focusedChannel()), cursor)?.takeIf { Timeline.block(it, window) != null }
                                when { details != null -> { if (Timeline.airing(details!!, now)) tune() else details = null }
                                    zone == GuideZone.ACTIONS -> when (action) { 0 -> switchMode(); 1 -> search = true; else -> onClose() }
                                    zone == GuideZone.CATEGORIES -> zone = GuideZone.CHANNELS
                                    full && !channelCell && focusedProgramme != null && !Timeline.airing(focusedProgramme, now) -> details = focusedProgramme
                                    else -> tune() }
                            }
                            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> if (details == null) {
                                val delta = if (key.keyCode == KeyEvent.KEYCODE_DPAD_UP) -1 else 1
                                when (zone) {
                                    GuideZone.ACTIONS -> if (delta > 0) zone = GuideZone.CATEGORIES
                                    GuideZone.CATEGORIES -> zone = if (delta > 0) GuideZone.CHANNELS else GuideZone.ACTIONS
                                    GuideZone.CHANNELS -> if (channels.indexOfFirst { it.number == if (full) fullNumber else quickNumber } == 0 && delta < 0) zone = GuideZone.CATEGORIES
                                        else {
                                            // Read current mutable selection, not the last rendered index: repeats can arrive before composition.
                                            val current = channels.indexOfFirst { it.number == if (full) fullNumber else quickNumber }.coerceAtLeast(0)
                                            val target = (current + delta).coerceIn(0, channels.lastIndex.coerceAtLeast(0))
                                            channels.getOrNull(target)?.let {
                                                if (full) revealRow(target)
                                                selectNumber(it.number)
                                            }
                                        }
                                }
                            }
                            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> if (details == null) {
                                val delta = if (key.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1
                                when (zone) {
                                    GuideZone.ACTIONS -> action = (action + delta).coerceIn(0, 2)
                                    GuideZone.CATEGORIES -> chooseCategory(GuideBrowse.categories[(GuideBrowse.categories.indexOf(category) + delta).coerceIn(0, GuideBrowse.categories.lastIndex)])
                                    GuideZone.CHANNELS -> if (full) {
                                        val focusedSchedule = epg.schedule(focusedChannel())
                                        val focusedProgramme = Timeline.at(focusedSchedule, cursor)
                                        if (channelCell) { if (delta > 0) channelCell = false }
                                        else if (delta < 0 && (focusedProgramme == null || focusedSchedule.indexOf(focusedProgramme) <= 0)) channelCell = true
                                        else if (focusedSchedule.isEmpty()) { cursor += delta * Timeline.HALF_HOUR; window = Timeline.windowStart(cursor) }
                                        else Timeline.move(focusedSchedule, focusedProgramme, delta)?.let { cursor = it.start }
                                    } else zone = GuideZone.CATEGORIES
                                }
                            }
                        }
                        true
                    }
                }.focusable().testTag(if (full) "full-epg" else "guide-panel"),
            color = GuideInk.copy(alpha = if (full) 1f else 0.88f), contentColor = GuideAccent,
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (full) "Programme guide" else "Quick Guide", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    listOf(if (full) "Quick Guide" else "Full EPG", "Search", "Close").forEachIndexed { index, label ->
                        GuideAction(label, zone == GuideZone.ACTIONS && action == index, if (index == 2) "close-guide" else "guide-action-$index") {
                            when (index) { 0 -> switchMode(); 1 -> search = true; else -> onClose() }
                        }
                    }
                }
                LazyRow(state = categoryList, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp).testTag("guide-categories")) {
                    items(GuideBrowse.categories) { value ->
                        GuideAction(value, zone == GuideZone.CATEGORIES && value == category, "category-$value", value == category) { chooseCategory(value) }
                    }
                }
                if (full) {
                    Row(Modifier.fillMaxWidth().height(28.dp)) {
                        Text("Channel", Modifier.width(220.dp), color = GuideAccent.copy(alpha = 0.7f))
                        repeat(6) { index -> Text(guideClock(window + index * Timeline.HALF_HOUR), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge) }
                    }
                }
                if (guide == null) {
                    Text(if (loadError) "The guide could not be loaded." else "Loading channels…")
                    if (loadError) Button(onClick = onRetry) { Text("Retry") }
                } else if (channels.isEmpty()) Text("No channels match your search.")
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val rowHeight = maxHeight / 7
                    LazyColumn(state = list, modifier = Modifier.fillMaxSize().testTag("guide-list")) {
                        items(channels, key = { it.number }) { channel ->
                            val focused = zone == GuideZone.CHANNELS && channel.number == selected?.number
                            val current = epg.at(channel, now).now
                            Row(Modifier.fillMaxWidth().height(rowHeight).padding(vertical = 2.dp)
                                .background(if (focused) GuideAccent.copy(alpha = 0.16f) else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(6.dp))
                                .border(if (focused) 3.dp else 1.dp, if (focused) GuideAccent else GuideAccent.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                                .semantics { this.selected = channel.number == playing?.number; this.focused = focused; contentDescription = "${channel.number} ${channel.name}${if (channel.number == playing?.number) ", playing" else ""}" }
                                .testTag("channel-${channel.number}"), verticalAlignment = Alignment.CenterVertically) {
                                Row(Modifier.width(if (full) 220.dp else 260.dp).fillMaxHeight().then(if (full && focused && channelCell) Modifier.border(3.dp, GuideAccent) else Modifier)
                                    .testTag("channel-cell-${channel.number}")
                                    .clickable { selectNumber(channel.number); zone = GuideZone.CHANNELS; tuneChannel(channel, if (full) onFullTune else onTune) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                                        AsyncImage(channel.logoUrl, "${channel.name} logo", Modifier.fillMaxSize(), placeholder = painterResource(R.drawable.autv_logo), error = painterResource(R.drawable.autv_logo))
                                    }
                                    Column(Modifier.padding(start = 8.dp)) {
                                        Text("${channel.number}  ${channel.name}", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (channel.number == playing?.number) Text("PLAYING", style = MaterialTheme.typography.labelSmall)
                                        if (failed[channel.number] != null) Text("Playback failed · OK to retry", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                if (full) TimelineRow(epg.schedule(channel), window, now, if (focused && !channelCell) programme else null, Modifier.weight(1f).fillMaxHeight().testTag("programme-row-${channel.number}")) { item ->
                                    fullNumber = channel.number; cursor = item.start; channelCell = false; zone = GuideZone.CHANNELS
                                    if (Timeline.airing(item, now)) tuneChannel(channel, onFullTune) else details = item
                                } else Column(Modifier.weight(1f).padding(8.dp)) {
                                    Text(current?.title ?: "Schedule unavailable", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (current != null) {
                                        Text("${guideClock(current.start)} – ${guideClock(current.stop)}", style = MaterialTheme.typography.labelSmall)
                                        LinearProgressIndicator(progress = { Timeline.fraction(now, current.start, current.stop - current.start) }, modifier = Modifier.fillMaxWidth().height(3.dp).testTag("programme-progress"))
                                    }
                                }
                            }
                        }
                    }
                }
                if (full) Column(Modifier.fillMaxWidth().height(72.dp).padding(top = 8.dp).testTag("programme-info")) {
                    Text(programme?.title ?: "Schedule unavailable", fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(programme?.let { "${guideClock(it.start)} – ${guideClock(it.stop)} · ${if (Timeline.airing(it, now)) "OK to watch live" else "OK for information"}" }
                        ?: "${selected?.name.orEmpty()} · OK to watch live", style = MaterialTheme.typography.bodySmall)
                    Text(programme?.description.orEmpty(), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("↑↓ Channels   ←→ ${if (full) "Programmes" else "Categories"}   OK Select   Guide/Menu Switch   Back ${if (full) "Quick Guide" else "Close"}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
    if (search) AlertDialog(onDismissRequest = { search = false; scope.launch { remoteFocus.requestFocus() } }, title = { Text("Search channels") },
        text = { OutlinedTextField(query, { if (full) fullQuery = it else quickQuery = it }, singleLine = true, modifier = Modifier.testTag("guide-search")) },
        confirmButton = { TextButton(onClick = { search = false; zone = GuideZone.CHANNELS; scope.launch { withFrameNanos { }; remoteFocus.requestFocus() } }) { Text("Done") } })
    details?.let { item ->
        AlertDialog(onDismissRequest = { details = null }, title = { Text(item.title) },
            text = { Column { Text("${selected?.number}  ${selected?.name}"); Text("${guideClock(item.start)} – ${guideClock(item.stop)}");
                if (item.description.isNotBlank()) Text(item.description)
                if (!Timeline.airing(item, now)) Text("This programme is not currently airing.") } },
            confirmButton = { TextButton(onClick = { if (Timeline.airing(item, now)) tune() else details = null }, modifier = Modifier.testTag("programme-confirm")) {
                Text(if (Timeline.airing(item, now)) "Watch live" else "Close") } },
            dismissButton = { TextButton(onClick = { details = null }) { Text("Back") } })
    }
}

private fun tuneChannel(channel: Channel, tune: (Channel) -> Unit) { if (!channel.streamUrl.isNullOrBlank()) tune(channel) }
private fun guideClock(time: Long) = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(time))

@Composable
private fun GuideAction(label: String, focus: Boolean, tag: String, selected: Boolean = false, click: () -> Unit) {
    Text(label, Modifier.border(if (focus) 3.dp else 1.dp, if (focus) GuideAccent else GuideAccent.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
        .background(if (selected || focus) GuideAccent.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent)
        .semantics { this.selected = selected; this.focused = focus; contentDescription = if (tag == "close-guide") "Close channel guide" else label }
        .clickable(onClick = click).padding(horizontal = 10.dp, vertical = 8.dp).testTag(tag), style = MaterialTheme.typography.labelLarge)
}

@Composable
private fun TimelineRow(schedule: List<Programme>, window: Long, now: Long, selected: Programme?, modifier: Modifier, select: (Programme) -> Unit) {
    val visible = remember(schedule, window) { schedule.mapNotNull { item -> Timeline.block(item, window)?.let { item to it } } }
    BoxWithConstraints(modifier) {
        val width = maxWidth
        if (visible.isEmpty()) Text("Schedule unavailable", Modifier.align(Alignment.CenterStart).padding(8.dp), style = MaterialTheme.typography.bodySmall)
        visible.forEach { (item, block) ->
            val focused = selected?.start == item.start && selected?.stop == item.stop
            Box(Modifier.offset(x = width * block.first).width(width * block.second).fillMaxHeight().padding(2.dp)
                .background(GuideAccent.copy(alpha = if (focused) 0.2f else 0.06f), RoundedCornerShape(4.dp))
                .border(if (focused) 3.dp else 1.dp, if (focused) GuideAccent else GuideAccent.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                .clickable { select(item) }.testTag("programme-${item.start}"), contentAlignment = Alignment.CenterStart) {
                Text(item.title, Modifier.padding(6.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (now in window until window + Timeline.WINDOW) Box(Modifier.offset(x = width * Timeline.fraction(now, window)).width(2.dp).fillMaxHeight().background(androidx.compose.ui.graphics.Color(0xFFFFCC80)).testTag("current-time"))
    }
}
