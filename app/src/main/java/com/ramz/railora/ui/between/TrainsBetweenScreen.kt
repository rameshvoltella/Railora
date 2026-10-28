package com.ramz.railora.ui.between

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramz.railora.domain.model.ClassOffer
import com.ramz.railora.domain.model.SeatAvailability
import com.ramz.railora.domain.model.Station
import com.ramz.railora.domain.model.TrainBetween
import com.ramz.railora.ui.common.ScreenHeader
import com.ramz.railora.ui.theme.ActionBlue
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val DateFormat = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)
private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val FromGreen = Color(0xFF1FA463)
private val ToRed = Color(0xFFEF4444)

@Composable
fun TrainsBetweenScreen(
    modifier: Modifier = Modifier,
    vm: TrainsBetweenViewModel = viewModel(),
    onTrainClick: (TrainBetween) -> Unit = {},
) {
    val state by vm.state.collectAsState()
    val stations by vm.stations.collectAsState()
    var picking by remember { mutableStateOf<PickTarget?>(null) }
    var pickingDate by remember { mutableStateOf(false) }

    LazyColumn(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Box {
                ScreenHeader("Trains Between Stations", "Find trains & check schedule")
                SearchCard(
                    state = state,
                    onPickFrom = { picking = PickTarget.FROM },
                    onPickTo = { picking = PickTarget.TO },
                    onSwap = vm::swap,
                    onDate = vm::setDate,
                    onPickDate = { pickingDate = true },
                    onSearch = vm::search,
                    modifier = Modifier.padding(top = 128.dp),
                )
            }
        }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 18.dp),
            ) {
                items(ClassFilter.entries) { f ->
                    Pill(f.label, selected = f == state.filter, onClick = { vm.setFilter(f) })
                }
            }
        }

        when {
            state.loading -> item { Message { CircularProgressIndicator() } }
            state.error != null -> item {
                Message {
                    Text(state.error!!, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.from != null && state.to != null && state.from!!.code != state.to!!.code) {
                        TextButton(onClick = vm::search) { Text("Retry") }
                    }
                }
            }
            state.results == null -> item {
                Message {
                    Icon(Icons.Rounded.Train, contentDescription = null, tint = ActionBlue, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Find trains between two stations",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Tap From and To to choose your stations by name or code, pick a date, " +
                            "then tap Search Trains to see every train with its timings and classes.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            state.results != null -> {
                val trains = state.visible
                item { ResultsHeader(trains.size, state.date, state.sort, vm::setSort) }
                if (trains.isEmpty()) item {
                    Message {
                        Text(
                            "No trains run on ${state.date.format(DateFormat)} for this choice.",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(trains, key = { "${it.number}-${it.from.station.code}-${it.to.station.code}" }) { train ->
                    TrainCard(
                        train,
                        Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        onClick = { onTrainClick(train) },
                    )
                }
            }
        }
    }

    picking?.let { target ->
        StationPickerDialog(
            title = if (target == PickTarget.FROM) "From station" else "To station",
            stations = stations,
            onPick = {
                if (target == PickTarget.FROM) vm.setFrom(it) else vm.setTo(it)
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
    if (pickingDate) {
        TravelDatePicker(state.date, onPick = { vm.setDate(it); pickingDate = false }, onDismiss = { pickingDate = false })
    }
}

private enum class PickTarget { FROM, TO }

@Composable
private fun SearchCard(
    state: BetweenState,
    onPickFrom: () -> Unit,
    onPickTo: () -> Unit,
    onSwap: () -> Unit,
    onDate: (LocalDate) -> Unit,
    onPickDate: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(14.dp)) {
            Box {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StationField("From", state.from, FromGreen, onPickFrom)
                    StationField("To", state.to, ToRed, onPickTo)
                }
                // Dotted line joining the two dots.
                Canvas(
                    Modifier
                        .padding(start = 26.dp, top = 44.dp)
                        .size(2.dp, 52.dp)
                ) {
                    drawLine(
                        Color.Gray.copy(alpha = 0.6f),
                        Offset(size.width / 2, 0f),
                        Offset(size.width / 2, size.height),
                        strokeWidth = size.width,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                    )
                }
                Surface(
                    onClick = onSwap,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shadowElevation = 3.dp,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(x = (-14).dp)
                        .size(46.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.SwapVert, contentDescription = "Swap stations")
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            DateRow(state.date, onDate, onPickDate)
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onSearch,
                enabled = !state.loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ActionBlue, contentColor = Color.White),
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(12.dp))
                Text("Search Trains", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun StationField(label: String, station: Station?, dot: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .border(5.dp, dot, CircleShape)
        )
        Spacer(Modifier.width(18.dp))
        Column(Modifier.padding(end = 48.dp)) {
            Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                station?.label ?: "Select station",
                fontSize = 18.sp,
                fontWeight = if (station != null) FontWeight.SemiBold else FontWeight.Normal,
                color = if (station != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DateRow(date: LocalDate, onDate: (LocalDate) -> Unit, onPickDate: () -> Unit) {
    val today = LocalDate.now()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onPickDate)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Travel Date", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(date.format(DateFormat), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Today" to 0L, "Tmrw" to 1L, "Day After" to 2L).forEach { (label, days) ->
                val d = today.plusDays(days)
                DateChip(label, selected = d == date) { onDate(d) }
            }
        }
    }
}

@Composable
private fun DateChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) ActionBlue else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) ActionBlue.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                if (selected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
                CircleShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 11.dp),
        color = if (selected) ActionBlue else MaterialTheme.colorScheme.onSurface,
        fontSize = 15.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
    )
}

@Composable
private fun ResultsHeader(count: Int, date: LocalDate, sort: SortBy, onSort: (SortBy) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "$count ${if (count == 1) "Train" else "Trains"} Found",
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Running on ${date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            Row(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    .clickable { open = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.SwapVert, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Sort by ${sort.label}", fontSize = 14.sp)
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(20.dp))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                SortBy.entries.forEach { s ->
                    DropdownMenuItem(text = { Text(s.label) }, onClick = { onSort(s); open = false })
                }
            }
        }
    }
}

@Composable
private fun TrainCard(train: TrainBetween, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.padding(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${train.number}  ${train.name.toTitle()}",
                    modifier = Modifier.weight(1f),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                Badge(runDaysLabel(train.runDays, train.runsDaily))
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    StopColumn(train.from.time.format(TimeFormat), train.from.station, Alignment.Start, Modifier.weight(1f))
                    DurationLine(train.duration, Modifier.weight(0.9f))
                    StopColumn(
                        train.to.time.format(TimeFormat) + if (train.arrivalDayOffset > 0) " +${train.arrivalDayOffset}" else "",
                        train.to.station,
                        Alignment.End,
                        Modifier.weight(1f),
                    )
                }
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = ActionBlue,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            if (train.classes.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    train.classes.forEach { ClassChip(it) }
                }
            }
        }
    }
}

@Composable
private fun StopColumn(time: String, station: Station, align: Alignment.Horizontal, modifier: Modifier) {
    Column(modifier, horizontalAlignment = align) {
        Text(time, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Text(station.code, fontSize = 15.sp)
        Text(
            station.name,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DurationLine(duration: Duration, modifier: Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier.padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(duration.toLabel(), fontSize = 13.sp, color = color)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
        ) {
            val y = size.height / 2
            drawCircle(color, radius = 3.dp.toPx(), center = Offset(3.dp.toPx(), y))
            drawLine(color, Offset(3.dp.toPx(), y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx())
            val tip = Offset(size.width, y)
            drawLine(color, tip, Offset(size.width - 5.dp.toPx(), y - 4.dp.toPx()), strokeWidth = 1.5.dp.toPx())
            drawLine(color, tip, Offset(size.width - 5.dp.toPx(), y + 4.dp.toPx()), strokeWidth = 1.5.dp.toPx())
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun Badge(text: String) {
    Text(
        text,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(ActionBlue.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 5.dp),
        color = ActionBlue,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
    )
}

/** Class code, plus a coloured availability part when the source gives one. */
@Composable
private fun ClassChip(offer: ClassOffer) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            offer.travelClass.code,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        offer.availability?.let { a ->
            val (label, color) = a.display()
            Text(
                label,
                modifier = Modifier
                    .background(color.copy(alpha = 0.10f))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                color = color,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private fun SeatAvailability.display(): Pair<String, Color> = when (status) {
    SeatAvailability.Status.AVAILABLE -> "AVL${count?.let { "-$it" } ?: ""}" to Color(0xFF1FA463)
    SeatAvailability.Status.RAC -> "RAC${count?.let { "-$it" } ?: ""}" to Color(0xFFF59E0B)
    SeatAvailability.Status.WAITLIST -> "WL${count?.let { "-$it" } ?: ""}" to Color(0xFFF59E0B)
    SeatAvailability.Status.REGRET -> "REGRET" to Color(0xFFEF4444)
    SeatAvailability.Status.NOT_AVAILABLE -> "NA" to Color(0xFF6B7280)
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

private fun runDaysLabel(days: Set<DayOfWeek>, daily: Boolean): String =
    if (daily) "Daily"
    else days.sorted().joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) }

private fun Duration.toLabel(): String {
    val h = toHours()
    val m = toMinutes() % 60
    return if (h == 0L) "${m}m" else "${h}h ${m}m"
}

/** "MANGALORE EXP" → "Mangalore Exp", keeping short code words ("TVC NZM SF EXP" → "TVC NZM SF Exp"). */
private fun String.toTitle(): String = split(' ').joinToString(" ") { w ->
    if (w.length <= 3 && w != "EXP" && w.all { it.isUpperCase() }) w
    else w.lowercase().replaceFirstChar(Char::uppercase)
}
