package com.ramz.railora.ui.pnr

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramz.railora.data.RailData
import com.ramz.railora.data.alerts.ArrivalAlerts
import com.ramz.railora.domain.PnrAlert
import com.ramz.railora.domain.model.PnrPassenger
import com.ramz.railora.domain.model.PnrStatus
import com.ramz.railora.domain.model.SeatState
import com.ramz.railora.domain.model.TrainRoute
import com.ramz.railora.ui.common.AlertCard
import com.ramz.railora.ui.common.ScreenHeader
import com.ramz.railora.ui.common.rememberAlertStarter
import com.ramz.railora.ui.theme.ActionBlue
import com.ramz.railora.ui.theme.ToolPurple
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DateFormat = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)

private fun SeatState.color(): Color = when (this) {
    SeatState.CONFIRMED -> Color(0xFF1FA463)
    SeatState.RAC -> Color(0xFFF59E0B)
    SeatState.WAITLIST -> Color(0xFFF57C00)
    SeatState.CANCELLED -> Color(0xFFE53935)
    SeatState.UNKNOWN -> Color(0xFF6B7280)
}

@Composable
fun PnrScreen(modifier: Modifier = Modifier, vm: PnrViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val focus = LocalFocusManager.current
    fun go(pnr: String = state.input) {
        focus.clearFocus()
        vm.check(pnr)
    }

    LazyColumn(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Box {
                ScreenHeader("PNR Status", "Check live PNR & berth details")
                Surface(
                    Modifier
                        .padding(top = 128.dp, start = 16.dp, end = 16.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 6.dp,
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Row(
                                Modifier
                                    .weight(1f)
                                    .height(56.dp)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(10.dp))
                                BasicTextField(
                                    value = state.input,
                                    onValueChange = vm::setInput,
                                    singleLine = true,
                                    textStyle = TextStyle(fontSize = 18.sp, letterSpacing = 1.5.sp, color = MaterialTheme.colorScheme.onSurface),
                                    cursorBrush = SolidColor(ActionBlue),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = { go() }),
                                    modifier = Modifier.weight(1f),
                                    decorationBox = { inner ->
                                        if (state.input.isEmpty()) Text("Enter 10-digit PNR", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        inner()
                                    },
                                )
                                Text("${state.input.length}/10", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.width(10.dp))
                            Button(
                                onClick = { go() },
                                enabled = !state.loading,
                                modifier = Modifier.height(56.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ActionBlue, contentColor = Color.White),
                            ) { Text("Check", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
                        }
                        if (state.recent.isNotEmpty()) {
                            Row(
                                Modifier
                                    .padding(top = 10.dp)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.History, contentDescription = "Recent", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                state.recent.forEach { pnr ->
                                    Text(
                                        pnr,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(ActionBlue.copy(alpha = 0.08f))
                                            .clickable { go(pnr) }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        color = ActionBlue,
                                        fontSize = 13.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        val status = state.status
        when {
            state.loading -> item { Message { CircularProgressIndicator() } }
            state.error != null -> item {
                Message {
                    Text(state.error!!, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.input.length == 10) TextButton(onClick = { go() }) { Text("Try again") }
                }
            }
            status != null -> {
                item { JourneyCard(status) }
                if (!status.trainCancelled && status.trainNumber != null) {
                    item { PnrAlertCard(status, state.route) }
                }
                item {
                    Text(
                        "Passengers",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
                items(status.passengers, key = { it.number }) { p -> PassengerCard(p) }
                item {
                    Text(
                        "Status from ${state.site.label} · change the site in Settings",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> item {
                Message {
                    Text(
                        "Enter the 10-digit PNR printed at the top left of your ticket.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun JourneyCard(s: PnrStatus) {
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
                s.trainNumber?.let {
                    Text(
                        it,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(ActionBlue)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(14.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        s.trainName ?: "PNR ${s.pnr}",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("PNR ${s.pnr}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Badge(
                        when {
                            s.trainCancelled -> "Train cancelled"
                            s.chartPrepared -> "Chart prepared"
                            else -> "Chart not prepared"
                        },
                        when {
                            s.trainCancelled -> Color(0xFFE53935)
                            s.chartPrepared -> Color(0xFF1FA463)
                            else -> Color(0xFF6B7280)
                        },
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                End(s.from?.code, s.from?.name, s.departure, Alignment.Start, Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 8.dp)) {
                    s.duration?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text("→", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                End(s.to?.code, s.to?.name, s.arrival, Alignment.End, Modifier.weight(1f))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Fact("Journey", s.journeyDate?.format(DateFormat) ?: "—", Modifier.weight(1.3f))
                Fact("Class", s.travelClass ?: "—", Modifier.weight(0.7f))
                Fact("Quota", s.quota ?: "—", Modifier.weight(0.7f))
                Fact("Platform", s.platform ?: "—", Modifier.weight(0.8f))
            }
            if (s.boarding != null && s.boarding.code != s.from?.code) {
                Text(
                    "Boarding at ${s.boarding.name} (${s.boarding.code})",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun End(code: String?, name: String?, time: String?, align: Alignment.Horizontal, modifier: Modifier) {
    Column(modifier, horizontalAlignment = align) {
        Text(time ?: "—", fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Text(code ?: "—", fontSize = 15.sp)
        name?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun Fact(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PassengerCard(p: PnrPassenger) {
    val color = p.state.color()
    Surface(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) { Text("${p.number}", color = color, fontWeight = FontWeight.Bold, fontSize = 17.sp) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Passenger ${p.number}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("Booked: ${p.booking}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val seat = listOfNotNull(p.coach?.let { "Coach $it" }, p.berth?.let { "Berth $it" })
                if (seat.isNotEmpty() && p.state == SeatState.CONFIRMED) {
                    Text(seat.joinToString(" · "), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                p.confirmChance?.let {
                    Text("$it% chance of confirmation", fontSize = 13.sp, color = color, fontWeight = FontWeight.Medium)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Badge(p.state.label, color)
                Spacer(Modifier.height(4.dp))
                Text(p.current, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color)
            }
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Text(
        text,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
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

/**
 * Arrival alert for this ticket: by default at the boarding station on the travel day; the
 * station can be changed to any stop on the route.
 */
@Composable
private fun PnrAlertCard(pnr: PnrStatus, route: TrainRoute?) {
    val context = LocalContext.current
    val alerts by RailData.alerts.alerts.collectAsState()
    val startAlert = rememberAlertStarter()
    var station by remember(pnr.pnr) { mutableStateOf(PnrAlert.defaultStation(pnr)) }
    var picking by remember { mutableStateOf(false) }
    val runStart = PnrAlert.startDate(pnr, route)
    val on = alerts.filter { it.trainNumber == pnr.trainNumber && it.startDate == runStart }

    Column(Modifier.padding(horizontal = 16.dp)) {
        if (on.isNotEmpty()) {
            on.forEach { a -> AlertCard(a, onCancel = { ArrivalAlerts.cancel(context, a.id) }) }
            return@Column
        }
        val s = station ?: return@Column
        Surface(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = ToolPurple.copy(alpha = 0.08f),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = ToolPurple)
                    Spacer(Modifier.width(10.dp))
                    Text("Arrival alert", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
                val eta = PnrAlert.eta(pnr, s, route)
                Text(
                    "Get an alarm when ${pnr.trainNumber} is about to reach ${s.name} (${s.code})" +
                        (eta?.let { " on ${it.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH))}, due ${it.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))}" } ?: "") +
                        ". We follow the train on the day and adjust for any delay.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { PnrAlert.alert(pnr, s, route)?.let(startAlert) },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ToolPurple, contentColor = Color.White),
                    ) { Text("Set alert", fontWeight = FontWeight.SemiBold) }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { picking = true }, enabled = route != null) {
                        Text(if (route == null) "Loading stations…" else "Change station", color = ToolPurple)
                    }
                }
            }
        }
    }

    if (picking && route != null) {
        val stops = route.points.filter { it.isStop }
        Dialog(onDismissRequest = { picking = false }) {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(vertical = 18.dp)) {
                    Text(
                        "Alert me at which station?",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    Text(
                        "Usually where you board. Pick another if you're meeting someone off the train.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 10.dp),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(stops) { p ->
                            val mark = when (p.station.code) {
                                pnr.boarding?.code -> "Boarding"
                                pnr.from?.code -> "From"
                                pnr.to?.code -> "To"
                                else -> null
                            }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { station = p.station; picking = false }
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "${p.station.name} (${p.station.code})",
                                    fontSize = 15.sp,
                                    fontWeight = if (mark != null) FontWeight.SemiBold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f),
                                )
                                mark?.let { Badge(it, ToolPurple) }
                            }
                        }
                    }
                    TextButton(onClick = { picking = false }, modifier = Modifier.align(Alignment.End).padding(end = 8.dp)) { Text("Cancel") }
                }
            }
        }
    }
}

