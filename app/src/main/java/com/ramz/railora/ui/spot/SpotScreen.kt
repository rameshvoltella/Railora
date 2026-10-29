package com.ramz.railora.ui.spot

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramz.railora.data.RailData
import com.ramz.railora.data.alerts.ArrivalAlerts
import com.ramz.railora.data.saved.SavedTrain
import com.ramz.railora.domain.ArrivalAlert
import com.ramz.railora.domain.PassingStation
import com.ramz.railora.domain.Placement
import com.ramz.railora.domain.TrainPlacement
import com.ramz.railora.domain.model.IST
import com.ramz.railora.domain.model.LiveStop
import com.ramz.railora.domain.model.LiveTrainStatus
import com.ramz.railora.domain.model.PositionEvent
import com.ramz.railora.domain.model.RunState
import com.ramz.railora.domain.model.TrainRoute
import com.ramz.railora.domain.stationsBetween
import com.ramz.railora.ui.common.AlertCard
import com.ramz.railora.ui.common.SaveStar
import com.ramz.railora.ui.common.ScreenHeader
import com.ramz.railora.ui.common.TrainSearchCard
import com.ramz.railora.ui.common.rememberAlertStarter
import com.ramz.railora.ui.theme.ActionBlue
import com.ramz.railora.ui.theme.ActionGreen
import com.ramz.railora.ui.theme.ToolPurple
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private val DateFormat = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)
private val ClockFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private val Green = Color(0xFF1FA463)
private val Orange = Color(0xFFF57C00)
private val Red = Color(0xFFE53935)

@Composable
fun SpotScreen(
    modifier: Modifier = Modifier,
    /** A train to show, e.g. tapped in Trains Between Stations; cleared via [onRequestHandled]. */
    request: String? = null,
    onRequestHandled: () -> Unit = {},
    vm: SpotViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val trains by vm.trains.collectAsState()
    var now by remember { mutableStateOf(LocalDateTime.now(IST)) }
    var fullMap by remember { mutableStateOf(false) }
    var showAllStops by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    // Where the current station's row sits inside the stops card (px), once laid out.
    var currentRowY by remember { mutableStateOf<Int?>(null) }
    var scrollToStops by remember { mutableStateOf(false) }

    LaunchedEffect(request) {
        request?.let { vm.track(it); onRequestHandled() }
    }
    // Move the train along between reports, and ask the site again every minute.
    LaunchedEffect(Unit) {
        var ticks = 0
        while (true) {
            delay(15_000)
            now = LocalDateTime.now(IST)
            if (++ticks % 4 == 0) vm.refresh()
        }
    }

    val status = state.status
    val placement = remember(status, state.route, now) { status?.let { TrainPlacement.place(it, state.route, now) } }

    // Index of the stops card in the list: header, run picker, status card, map, coaches, stops.
    val stopsItem = 1 + (if (state.tracked != null) 1 else 0) + 1 +
        (if (state.route != null) 1 else 0) + (if (state.coaches != null) 1 else 0)
    // "View Full Route": scroll so the train's current station is near the top, with a little
    // of the route before it; "Show Upcoming": back to the top of the card.
    LaunchedEffect(scrollToStops, currentRowY, showAllStops) {
        if (!scrollToStops) return@LaunchedEffect
        if (!showAllStops) {
            listState.animateScrollToItem(stopsItem)
            scrollToStops = false
        } else currentRowY?.let { y ->
            val margin = with(density) { 120.dp.roundToPx() }
            listState.animateScrollToItem(stopsItem, (y - margin).coerceAtLeast(0))
            scrollToStops = false
        }
    }

    LazyColumn(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        state = listState,
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Box {
                ScreenHeader("Spot My Train", "See live train location on map")
                TrainSearchCard(
                    query = state.query,
                    trains = trains,
                    tracked = state.tracked,
                    onQuery = vm::setQuery,
                    onTrack = vm::track,
                    modifier = Modifier.padding(top = 128.dp),
                )
            }
        }
        if (state.tracked != null) {
            item {
                RunPicker(
                    selected = state.runDate ?: status?.startDate,
                    today = now.toLocalDate(),
                    onPick = vm::pickRun,
                )
            }
        }

        when {
            status != null && placement != null -> {
                item { StatusCard(status, placement, state.loading, vm::refresh) }
                state.route?.let { route ->
                    item {
                        Surface(
                            Modifier
                                .padding(horizontal = 16.dp)
                                .fillMaxWidth()
                                .height(320.dp),
                            shape = RoundedCornerShape(20.dp),
                            shadowElevation = 2.dp,
                        ) { TrainMapView(status, route, placement, callout(status, placement)) }
                    }
                }
                state.coaches?.let { coaches ->
                    item { CoachCard(coaches, Modifier.padding(horizontal = 16.dp)) }
                }
                item {
                    StopsCard(
                        status, state.route, placement, showAllStops,
                        onCurrentRow = { currentRowY = it },
                    ) {
                        showAllStops = !showAllStops
                        currentRowY = null
                        scrollToStops = true
                    }
                }
                item {
                    Actions(
                        status, placement,
                        runDate = state.runDate ?: status.startDate,
                        onMap = { fullMap = true },
                    )
                }
                item {
                    SourceNote(
                        when (val by = state.answeredBy ?: state.source) {
                            state.source -> "Live data from ${by.label} · change the site in Settings"
                            else -> "${state.source.label} shows only the latest run, so this run is from ${by.label}"
                        },
                        state.error,
                    )
                }
            }
            state.loading -> item { Message { CircularProgressIndicator() } }
            state.error != null -> item {
                Message {
                    Text(state.error!!, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.tracked != null) TextButton(onClick = vm::refresh) { Text("Retry") }
                }
            }
            else -> item {
                Message {
                    Icon(Icons.Rounded.Train, contentDescription = null, tint = ActionBlue, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Enter a train number or name to see where it is right now.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (fullMap && status != null && placement != null && state.route != null) {
        Dialog(onDismissRequest = { fullMap = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize()) {
                TrainMapView(status, state.route!!, placement, callout(status, placement), Modifier.fillMaxSize())
                Surface(
                    onClick = { fullMap = false },
                    shape = CircleShape,
                    color = Color.White,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(12.dp)
                        .size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Close, contentDescription = "Close map", tint = Color.Black) }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(status: LiveTrainStatus, placement: Placement, loading: Boolean, onRefresh: () -> Unit) {
    Surface(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
    ) {
        Column {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    status.number,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(ActionBlue)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        status.name ?: "Train ${status.number}",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val route = listOfNotNull(status.origin?.name, status.destination?.name)
                    if (route.size == 2) {
                        Text(
                            "${route[0]} → ${route[1]}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    StatusBadge(status)
                }
                SaveStar(
                    SavedTrain(status.number, status.name, status.origin, status.destination),
                    Modifier.align(Alignment.Top),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(
                Modifier
                    .height(IntrinsicSize.Min)
                    .padding(vertical = 12.dp, horizontal = 4.dp)
            ) {
                Tile(Icons.Rounded.CalendarMonth, "Journey Date", status.startDate?.format(DateFormat) ?: "—", Modifier.weight(1f))
                TileDivider()
                if (status.speedKmph != null) {
                    Tile(Icons.Rounded.Speed, "Current Speed", "${status.speedKmph} km/h", Modifier.weight(0.8f))
                } else {
                    Tile(Icons.Rounded.Speed, "Delay", delayText(status) ?: "—", Modifier.weight(0.8f))
                }
                TileDivider()
                Tile(
                    Icons.Rounded.Schedule,
                    "Last Updated",
                    status.lastUpdated?.format(ClockFormat) ?: "—",
                    Modifier.weight(0.9f),
                    trailing = {
                        if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = "Refresh",
                            tint = ActionBlue,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable(onClick = onRefresh),
                        )
                    },
                )
                TileDivider()
                val next = placement.nextStop
                Tile(
                    Icons.Rounded.Train,
                    "Next Station",
                    next?.let { "${it.station.name} (${it.station.code})" } ?: "—",
                    Modifier.weight(1f),
                    footer = placement.kmToNextStop?.let { "in ${it.roundToInt()} km" },
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(status: LiveTrainStatus) {
    val delay = status.delayMinutes
    val (text, color) = when (status.state) {
        RunState.NOT_STARTED -> "Not started yet" to Color(0xFF6B7280)
        RunState.REACHED -> "Reached destination" to ActionBlue
        RunState.RUNNING -> when {
            delay == null -> "Running" to Green
            delay <= 0 -> "Running On Time" to Green
            delay < 30 -> "Running $delay min late" to Orange
            else -> "Running ${lateText(delay)} late" to Red
        }
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Tile(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier,
    footer: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier.padding(horizontal = 8.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
            trailing?.let { Spacer(Modifier.width(4.dp)); it() }
        }
        footer?.let { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun TileDivider() = VerticalDivider(Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

@Composable
private fun StopsCard(
    status: LiveTrainStatus,
    route: TrainRoute?,
    placement: Placement,
    showAll: Boolean,
    /** Reports the y (px, within this card) of the train's current station row. */
    onCurrentRow: (Int) -> Unit,
    onToggle: () -> Unit,
) {
    val routeKm = remember(route) { route?.points?.associate { it.station.code to it.kmFromOrigin }.orEmpty() }
    val nextIndex = placement.nextStop?.let { status.stops.indexOf(it) } ?: -1
    // The station the train is at or last left; before it starts, the origin; after, the end.
    val currentIndex = when {
        status.state == RunState.REACHED || nextIndex < 0 -> status.stops.lastIndex
        else -> (nextIndex - 1).coerceAtLeast(0)
    }
    // Upcoming: the stop the train last left, so its segment (where the train is) can be opened too.
    val shownIndices = when {
        showAll -> status.stops.indices.toList()
        nextIndex >= 0 -> ((nextIndex - 1).coerceAtLeast(0)..(nextIndex + 3).coerceAtMost(status.stops.lastIndex)).toList()
        else -> listOf(status.stops.lastIndex)
    }
    var open by remember(status.number, status.startDate) { mutableStateOf(setOf<String>()) }
    Surface(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(Modifier.padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (showAll) "Full Route" else if (nextIndex >= 0) "Upcoming Stations" else "Destination",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onToggle) {
                    Text(if (showAll) "Show Upcoming" else "View Full Route", color = ActionBlue)
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = ActionBlue)
                }
            }
            Text(
                "Tap a station to see the stations the train runs through before the next stop.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            shownIndices.forEachIndexed { i, index ->
                val stop = status.stops[index]
                val km = (stop.kmFromOrigin ?: routeKm[stop.station.code])?.let { k -> placement.kmNow?.let { (k - it) } }
                val between = remember(status, route, index, placement.kmNow) { stationsBetween(status, route, index, placement.kmNow) }
                val expanded = stop.station.code in open
                // The train is in this segment: it has left this stop and not reached the next.
                val trainHere = stop.passed && status.stops.getOrNull(index + 1)?.passed == false && status.state == RunState.RUNNING
                val train = if (!trainHere || expanded) null else trainOnSegment(status, index, routeKm, placement.kmNow)
                StopRow(
                    modifier = if (index == currentIndex) {
                        Modifier.onGloballyPositioned { onCurrentRow(it.positionInParent().y.toInt()) }
                    } else Modifier,
                    stop = stop,
                    isNext = stop == placement.nextStop,
                    kmAway = km?.takeIf { it > 0 },
                    isFirst = i == 0,
                    isLast = i == shownIndices.lastIndex && !expanded,
                    betweenCount = between.size,
                    expanded = expanded,
                    train = train,
                    onClick = if (between.isEmpty()) null else {
                        { open = if (expanded) open - stop.station.code else open + stop.station.code }
                    },
                )
                if (expanded) {
                    val firstAhead = between.indexOfFirst { !it.passed }.let { if (it < 0) between.size else it }
                    between.forEachIndexed { j, p ->
                        if (trainHere && j == firstAhead) TrainHereRow()
                        PassingRow(p, kmAway = placement.kmNow?.let { p.point.kmFromOrigin - it }?.takeIf { it > 0 })
                    }
                    if (trainHere && firstAhead == between.size) TrainHereRow()
                }
            }
        }
    }
}

@Composable
private fun StopRow(
    modifier: Modifier = Modifier,
    stop: LiveStop,
    isNext: Boolean,
    kmAway: Double?,
    isFirst: Boolean,
    isLast: Boolean,
    betweenCount: Int,
    expanded: Boolean,
    /** The train, when it's on the track after this stop (or standing at it). */
    train: TrainOnTrack?,
    onClick: (() -> Unit)?,
) {
    val dotColor = when {
        stop.passed -> Green
        isNext -> Green
        else -> Color(0xFF9CA3AF)
    }
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 20.dp, end = 20.dp)
    ) {
        // Timeline: railway track through, station dot on this stop.
        Box(
            Modifier
                .width(28.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.TopCenter,
        ) {
            RailTrack(
                Modifier.fillMaxSize(),
                passed = stop.passed,
                top = if (isFirst) DotCenter else 0.dp,
                end = if (isLast) DotCenter else null,
                train = train,
            )
            // Standing at this station: the train covers the dot.
            if (train?.at != 0f) Box(
                Modifier
                    .padding(top = 16.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(if (isNext) 5.dp else 2.5.dp, dotColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (stop.passed) Icon(Icons.Rounded.Check, contentDescription = null, tint = Green, modifier = Modifier.size(11.dp))
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(
            Modifier
                .weight(1f)
                .heightIn(min = 64.dp)
                .padding(vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${stop.station.name} (${stop.station.code})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (stop.passed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isNext) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        kmAway?.let { "Arriving in ${it.roundToInt()} km" } ?: "Next stop",
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Green.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        color = Green,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Text(stopLine(stop), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (betweenCount > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "$betweenCount ${if (betweenCount == 1) "station" else "stations"} in between",
                        fontSize = 12.sp,
                        color = ActionBlue,
                        fontWeight = FontWeight.Medium,
                    )
                    Icon(
                        if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        tint = ActionBlue,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        kmAway?.let {
            Text(
                "${it.roundToInt()} km",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
    }
}

/** A station run through without stopping, indented under the stop it follows. */
@Composable
private fun PassingRow(p: PassingStation, kmAway: Double?) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(start = 20.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(28.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
            RailTrack(Modifier.fillMaxSize(), passed = p.passed)
            Box(
                Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(if (p.passed) Color(0xFF546E7A) else MaterialTheme.colorScheme.surface)
                    .border(1.5.dp, if (p.passed) Color(0xFF546E7A) else Color(0xFF9CA3AF), CircleShape)
            )
        }
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f).padding(vertical = 7.dp)) {
            Text(
                "${p.point.station.name} (${p.point.station.code})",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            p.expected?.let {
                Text(
                    "${if (p.passed) "Passed ~" else "~"}${it.format(ClockFormat)} · no stop",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
        }
        kmAway?.let { Text("${it.roundToInt()} km", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** Marks where the train is among the stations in between. */
@Composable
private fun TrainHereRow() {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(start = 20.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(28.dp).fillMaxHeight()) {
            RailTrack(Modifier.fillMaxSize(), passed = false, train = TrainOnTrack(at = 0.5f, moving = true))
        }
        Spacer(Modifier.width(20.dp))
        Text("Train is here", fontSize = 13.sp, color = Green, fontWeight = FontWeight.SemiBold)
    }
}

/** Centre of a stop's dot from the top of its row. */
private val DotCenter = 25.dp

/**
 * The train on the track after stop [index]: standing on the dot if it's at that station,
 * otherwise part-way along by km towards the next stop (kept clear of the dot).
 */
private fun trainOnSegment(status: LiveTrainStatus, index: Int, routeKm: Map<String, Double>, kmNow: Double?): TrainOnTrack {
    val stop = status.stops[index]
    val pos = status.position
    if (pos?.event == PositionEvent.ARRIVED && pos.station.code == stop.station.code) {
        return TrainOnTrack(at = 0f, moving = false, from = DotCenter)
    }
    val next = status.stops[index + 1]
    val kmA = stop.kmFromOrigin ?: routeKm[stop.station.code]
    val kmB = next.kmFromOrigin ?: routeKm[next.station.code]
    val progress = if (kmA != null && kmB != null && kmNow != null && kmB > kmA) ((kmNow - kmA) / (kmB - kmA)).toFloat() else 0.5f
    return TrainOnTrack(at = progress.coerceIn(0.3f, 0.9f), moving = true, from = DotCenter)
}

/** "Platform 3 • 9:58 PM (Exp)" — arrival, or departure at the origin. */
private fun stopLine(stop: LiveStop): String {
    val t = stop.arrival ?: stop.departure
    val time = t?.best?.format(ClockFormat)?.let { "$it (${if (t.isActual) "Act" else if (t.expected != null) "Exp" else "Sch"})" }
    return listOfNotNull(stop.platform?.let { "Platform $it" }, time).joinToString(" • ")
}

@Composable
private fun Actions(status: LiveTrainStatus, placement: Placement, runDate: LocalDate?, onMap: () -> Unit) {
    val context = LocalContext.current
    val alerts by RailData.alerts.alerts.collectAsState()
    val mine = alerts.filter { it.trainNumber == status.number && (it.startDate == null || it.startDate == runDate) }
    var picking by remember { mutableStateOf(false) }
    val startAlert = rememberAlertStarter()

    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        mine.forEach { a -> AlertCard(a, onCancel = { ArrivalAlerts.cancel(context, a.id) }) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Action("View on Map", Icons.Rounded.Map, ActionBlue, Modifier.weight(1f), onMap)
            Action("Set Alert", Icons.Rounded.NotificationsActive, ToolPurple, Modifier.weight(1f)) {
                if (status.state == RunState.REACHED) {
                    Toast.makeText(context, "This run has already reached its destination", Toast.LENGTH_SHORT).show()
                } else picking = true
            }
            Action("Share", Icons.Rounded.Share, ActionGreen, Modifier.weight(1f)) {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText(status, placement))
                }
                context.startActivity(Intent.createChooser(send, "Share train status"))
            }
        }
    }

    if (picking) {
        BoardingPicker(status, onDismiss = { picking = false }) { stop ->
            picking = false
            val alert = ArrivalAlert(
                trainNumber = status.number,
                trainName = status.name,
                station = stop.station,
                startDate = runDate,
                eta = stop.arrival?.best ?: stop.departure?.best,
                delayMinutes = stop.arrival?.delayMinutes ?: status.delayMinutes,
                lastChecked = null,
            )
            startAlert(alert)
        }
    }
}

/** "Which station are you boarding at?" — the stops still ahead, with expected times. */
@Composable
private fun BoardingPicker(status: LiveTrainStatus, onDismiss: () -> Unit, onPick: (LiveStop) -> Unit) {
    val ahead = status.stops.filter { !it.passed }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(vertical = 18.dp)) {
                Text(
                    "Which station are you boarding at?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Text(
                    "We'll keep an eye on the train and notify you when it's about to reach your station, " +
                        "even if it's running late.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 10.dp),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(ahead) { stop ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(stop) }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("${stop.station.name} (${stop.station.code})", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                stop.platform?.let { Text("Platform $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            (stop.arrival ?: stop.departure)?.best?.let {
                                Text(it.format(ClockFormat), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).padding(end = 8.dp)) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun Action(label: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(color.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(30.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, color = color, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Long journeys have several runs on the way at once (12626 takes three days), so the user
 * picks which by the day it left its origin: today, yesterday, 2 days ago, or "Earlier" (3–7
 * days ago) for a train running days late.
 */
@Composable
private fun RunPicker(selected: LocalDate?, today: LocalDate, onPick: (LocalDate) -> Unit) {
    val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    var choosingEarlier by remember { mutableStateOf(false) }
    val earlier = selected?.takeIf { it.isBefore(today.minusDays(2)) }
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(
            "Train started on",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Today", "Yesterday", "2 days ago").forEachIndexed { i, label ->
                val date = today.minusDays(i.toLong())
                RunChip(label, date.format(dayMonth), on = date == selected, Modifier.weight(1f)) { onPick(date) }
            }
            RunChip(
                earlier?.let { "${ChronoUnit.DAYS.between(it, today)} days ago" } ?: "Earlier",
                earlier?.format(dayMonth) ?: "3+ days",
                on = earlier != null,
                Modifier.weight(1f),
            ) { choosingEarlier = true }
        }
    }

    if (choosingEarlier) {
        Dialog(onDismissRequest = { choosingEarlier = false }) {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(vertical = 18.dp)) {
                    Text(
                        "Train started on",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    Text(
                        "For a train running very late: pick the day it left its first station.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
                    )
                    (3L..7L).forEach { back ->
                        val date = today.minusDays(back)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { choosingEarlier = false; onPick(date) }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("$back days ago", fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            Text(
                                date.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)),
                                fontSize = 14.sp,
                                color = if (date == selected) ActionBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (date == selected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                    TextButton(onClick = { choosingEarlier = false }, modifier = Modifier.align(Alignment.End).padding(end = 8.dp)) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

@Composable
private fun RunChip(label: String, sub: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (on) ActionBlue else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (on) ActionBlue else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (on) Color.White else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Text(
            sub,
            fontSize = 12.sp,
            color = if (on) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun SourceNote(note: String, error: String?) {
    Text(
        error ?: note,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        textAlign = TextAlign.Center,
        fontSize = 12.sp,
        color = if (error != null) Red else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Message(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

private fun lateText(minutes: Int): String =
    if (minutes < 60) "$minutes min" else "${minutes / 60}h ${minutes % 60}m"

private fun delayText(status: LiveTrainStatus): String? = status.delayMinutes?.let {
    when {
        status.state == RunState.NOT_STARTED -> null
        it <= 0 -> "On time"
        else -> "${lateText(it)} late"
    }
}

/** Where the train is, in words: for the map callout and sharing. */
private fun where(status: LiveTrainStatus, placement: Placement): String {
    val pos = status.position
    return when {
        status.state == RunState.NOT_STARTED -> "Not started from ${status.origin?.name ?: "origin"}"
        status.state == RunState.REACHED -> "Reached ${status.destination?.name ?: "destination"}"
        pos != null && placement.nextStop != null && pos.event.name != "ARRIVED" ->
            "Between ${pos.station.name} and ${placement.nextStop.station.name}"
        pos != null -> "At ${pos.station.name} (${pos.station.code})"
        else -> "Running"
    }
}

private fun callout(status: LiveTrainStatus, placement: Placement): String =
    listOfNotNull("${status.number} ${status.name.orEmpty()}".trim(), where(status, placement), delayText(status))
        .joinToString("\n")

private fun shareText(status: LiveTrainStatus, placement: Placement): String = buildString {
    append("${status.number} ${status.name.orEmpty()}".trim())
    append(": ").append(where(status, placement))
    delayText(status)?.let { append(", ").append(it) }
    placement.nextStop?.let { s ->
        append(". Next: ${s.station.name} (${s.station.code})")
        s.arrival?.best?.let { append(" at ${it.format(ClockFormat)}") }
    }
    append(". — via Railora")
}
