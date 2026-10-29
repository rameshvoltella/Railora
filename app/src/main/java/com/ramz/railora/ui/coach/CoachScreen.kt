package com.ramz.railora.ui.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramz.railora.data.RailData
import com.ramz.railora.data.saved.SavedTrain
import com.ramz.railora.data.settings.AppSettings
import com.ramz.railora.domain.model.CoachPosition
import com.ramz.railora.domain.model.TrainRef
import com.ramz.railora.ui.common.SaveStar
import com.ramz.railora.ui.common.ScreenHeader
import com.ramz.railora.ui.common.TrainSearchCard
import com.ramz.railora.ui.spot.CoachCard
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CoachState(
    val query: String = "",
    val shown: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val coaches: CoachPosition? = null,
    /** Train numbers looked up before, newest first. */
    val recent: List<String> = emptyList(),
)

class CoachViewModel(private val settings: AppSettings = RailData.settings) : ViewModel() {

    private val _state = MutableStateFlow(CoachState(recent = settings.recentCoachTrains))
    val state: StateFlow<CoachState> = _state
    private val _trains = MutableStateFlow<List<TrainRef>>(emptyList())
    val trains: StateFlow<List<TrainRef>> = _trains
    private var job: Job? = null

    init {
        viewModelScope.launch { runCatching { RailData.trains.allTrains() }.onSuccess { _trains.value = it } }
    }

    fun setQuery(q: String) = _state.update { it.copy(query = q) }

    fun show(input: String) {
        val number = Regex("""\d{5}""").find(input)?.value
        if (number == null) {
            _state.update { it.copy(error = "Enter a 5-digit train number, or pick a train from the list.") }
            return
        }
        val name = nameOf(number)
        _state.update { it.copy(query = listOfNotNull(number, name).joinToString(" "), shown = number, loading = true, error = null, coaches = null) }
        job?.cancel()
        job = viewModelScope.launch {
            val coaches = runCatching { RailData.coaches.coachPosition(number) }
            settings.recentCoachTrains = listOf(number) + settings.recentCoachTrains.filter { it != number }
            _state.update {
                it.copy(
                    loading = false,
                    coaches = coaches.getOrNull(),
                    recent = settings.recentCoachTrains,
                    error = when {
                        coaches.isFailure -> "Couldn't load the coach position. Check your connection and try again."
                        coaches.getOrNull() == null -> "No coach position found for $number."
                        else -> null
                    },
                )
            }
        }
    }

    fun nameOf(number: String): String? = _trains.value.firstOrNull { it.number == number }?.name
}

/** Coach Position: enter a train, see its coach order; trains looked up before are listed below. */
@Composable
fun CoachScreen(modifier: Modifier = Modifier, vm: CoachViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val trains by vm.trains.collectAsState()
    LazyColumn(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Box {
                ScreenHeader("Coach Position", "See the coach order of any train")
                TrainSearchCard(
                    query = state.query,
                    trains = trains,
                    tracked = state.shown,
                    onQuery = vm::setQuery,
                    onTrack = vm::show,
                    modifier = Modifier.padding(top = 128.dp),
                    action = "Show",
                )
            }
        }
        when {
            state.loading -> item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            state.coaches != null -> item {
                val c = state.coaches!!
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            listOfNotNull(c.trainNumber, vm.nameOf(c.trainNumber)).joinToString(" "),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f).padding(start = 4.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        SaveStar(SavedTrain(c.trainNumber, vm.nameOf(c.trainNumber), null, null))
                    }
                    CoachCard(c)
                }
            }
            state.error != null -> item {
                Text(
                    state.error!!,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                )
            }
        }
        if (state.recent.isNotEmpty()) {
            item { RecentList(state.recent, nameOf = vm::nameOf, onPick = vm::show) }
        }
    }
}

@Composable
private fun RecentList(recent: List<String>, nameOf: (String) -> String?, onPick: (String) -> Unit) {
    Surface(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Searched before", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            recent.forEachIndexed { i, number ->
                if (i > 0) HorizontalDivider(Modifier.padding(start = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(number) }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Text(number, fontWeight = FontWeight.Bold, modifier = Modifier.width(64.dp))
                    Text(nameOf(number) ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
