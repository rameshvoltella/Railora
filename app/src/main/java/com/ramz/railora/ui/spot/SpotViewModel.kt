package com.ramz.railora.ui.spot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ramz.railora.data.RailData
import com.ramz.railora.data.settings.AppSettings
import com.ramz.railora.data.settings.LiveSource
import com.ramz.railora.domain.model.CoachPosition
import com.ramz.railora.domain.model.IST
import com.ramz.railora.domain.model.LiveTrainStatus
import com.ramz.railora.domain.model.TrainRef
import com.ramz.railora.domain.model.TrainRoute
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class SpotState(
    val query: String = "",
    /** The train on screen (number), once one is tracked. */
    val tracked: String? = null,
    val source: LiveSource = LiveSource.WHERE_IS_MY_TRAIN,
    /** The run picked by start date; null = whichever run is current. */
    val runDate: LocalDate? = null,
    /** The site that answered: [source], or NTES when [source] couldn't show the picked run. */
    val answeredBy: LiveSource? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val status: LiveTrainStatus? = null,
    /** For the map; null if the route couldn't be loaded (the rest of the screen still works). */
    val route: TrainRoute? = null,
    /** Coach order; null until loaded or if unavailable. */
    val coaches: CoachPosition? = null,
)

class SpotViewModel(private val settings: AppSettings = RailData.settings) : ViewModel() {

    private val _state = MutableStateFlow(SpotState(source = settings.liveSource.value))
    val state: StateFlow<SpotState> = _state

    private val _trains = MutableStateFlow<List<TrainRef>>(emptyList())
    /** Every train, for suggestions while typing. */
    val trains: StateFlow<List<TrainRef>> = _trains

    private var loadJob: Job? = null

    init {
        viewModelScope.launch { runCatching { RailData.trains.allTrains() }.onSuccess { _trains.value = it } }
        // Changing the site in Settings reloads the train on screen from the new site.
        viewModelScope.launch {
            settings.liveSource.drop(1).collect { source ->
                _state.update { it.copy(source = source) }
                if (_state.value.tracked != null) refresh()
            }
        }
        settings.lastTrain?.let { track(it) }
    }

    fun setQuery(q: String) = _state.update { it.copy(query = q) }

    /** [input]: "12626", or a suggestion like "12626 KERALA EXP". */
    fun track(input: String) {
        val number = Regex("""\d{5}""").find(input)?.value
        if (number == null) {
            _state.update { it.copy(error = "Enter a 5-digit train number, or pick a train from the list.") }
            return
        }
        settings.lastTrain = number
        val name = _trains.value.firstOrNull { it.number == number }?.name
        _state.update {
            it.copy(
                tracked = number,
                query = listOfNotNull(number, name).joinToString(" "),
                status = if (it.tracked == number) it.status else null,
                route = if (it.tracked == number) it.route else null,
                runDate = if (it.tracked == number) it.runDate else null,
                coaches = if (it.tracked == number) it.coaches else null,
            )
        }
        refresh()
        if (_state.value.coaches == null) {
            viewModelScope.launch {
                val coaches = runCatching { RailData.coaches.coachPosition(number) }.getOrNull()
                _state.update { if (it.tracked == number) it.copy(coaches = coaches) else it }
            }
        }
    }

    /** Show the run that left its origin on [date] (null = the current run). */
    fun pickRun(date: LocalDate?) {
        _state.update { it.copy(runDate = date, status = null) }
        refresh()
    }

    fun refresh() {
        val number = _state.value.tracked ?: return
        val source = _state.value.source
        val runDate = _state.value.runDate
        loadJob?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        loadJob = viewModelScope.launch {
            val route = async { _state.value.route ?: runCatching { RailData.routes.route(number) }.getOrNull() }
            try {
                val (status, answeredBy) = RailData.liveStatusAnswer(source, number, LocalDateTime.now(IST), runDate)
                _state.update {
                    it.copy(
                        loading = false,
                        status = status,
                        answeredBy = answeredBy,
                        route = route.await(),
                        error = when {
                            status != null -> null
                            runDate != null -> "No run of $number started on ${runDate.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH))}."
                            else -> "${source.label} has no running status for $number."
                        },
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update {
                    it.copy(
                        loading = false,
                        route = route.await(),
                        error = "Couldn't reach ${source.label}. Check your connection, or try another site in Settings.",
                    )
                }
            }
        }
    }
}
