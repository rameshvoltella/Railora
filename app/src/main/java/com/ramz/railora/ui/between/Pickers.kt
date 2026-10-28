package com.ramz.railora.ui.between

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ramz.railora.domain.model.Station
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Full-screen station search: by code first ("ERS"), then by name ("ernakulam"). */
@Composable
fun StationPickerDialog(
    title: String,
    stations: List<Station>,
    onPick: (Station) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val matches = remember(query, stations) { search(stations, query) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier
                    .padding(top = 8.dp)
                    .imePadding()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                    Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .focusRequester(focus),
                    placeholder = { Text("Station name or code") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                )
                LazyColumn(Modifier.fillMaxSize()) {
                    // Station list not loaded (offline): still allow a typed code.
                    val code = query.trim().uppercase()
                    if (stations.isEmpty() && code.matches(Regex("[A-Z]{2,5}"))) {
                        item { StationRow(Station(code, code)) { onPick(it) } }
                    }
                    items(matches, key = { it.code }) { s -> StationRow(s) { onPick(it) } }
                }
            }
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

@Composable
private fun StationRow(station: Station, onPick: (Station) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onPick(station) }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(station.code, fontWeight = FontWeight.Bold, modifier = Modifier.width(64.dp))
        Spacer(Modifier.width(8.dp))
        Text(station.name)
    }
    HorizontalDivider(Modifier.padding(start = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

private fun search(stations: List<Station>, query: String): List<Station> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    val exact = stations.filter { it.code.equals(q, ignoreCase = true) }
    val codeStart = stations.filter { it.code.startsWith(q, ignoreCase = true) && it !in exact }
    val nameStart = stations.filter { it.name.startsWith(q, ignoreCase = true) }
    val nameHas = stations.filter { it.name.contains(q, ignoreCase = true) }
    return (exact + codeStart + nameStart + nameHas).distinct().take(60)
}

/** Material date picker limited to today onwards. Picker millis are UTC midnight. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelDatePicker(date: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val today = LocalDate.now()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = !millisToDate(utcTimeMillis).isBefore(today)
            override fun isSelectableYear(year: Int) = year >= today.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onPick(millisToDate(it)) } ?: onDismiss() }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state) }
}

private fun millisToDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
