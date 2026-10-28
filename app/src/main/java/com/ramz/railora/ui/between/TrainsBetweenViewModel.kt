package com.ramz.railora.ui.between

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ramz.railora.data.RailData
import com.ramz.railora.data.settings.AppSettings
import com.ramz.railora.domain.model.ClassGroup
import com.ramz.railora.domain.model.Station
import com.ramz.railora.domain.model.TrainBetween
import com.ramz.railora.domain.source.StationSource
import com.ramz.railora.domain.source.TrainsBetweenSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class ClassFilter(val label: String, val group: ClassGroup?) {
    ALL("All", null),
    AC("AC", ClassGroup.AC),
    SLEEPER("Sleeper", ClassGroup.SLEEPER),
    SEATING("Seating", ClassGroup.SEATING),
    GENERAL("General", ClassGroup.GENERAL),
}

enum class SortBy(val label: String) { DEPARTURE("Departure"), ARRIVAL("Arrival"), DURATION("Duration") }

data class BetweenState(
    /** Empty until the user picks one (or the last search's stations are restored). */
    val from: Station? = null,
    val to: Station? = null,
    val date: LocalDate = LocalDate.now(),
    val loading: Boolean = false,
    val error: String? = null,
    /** Everything the source returned for the last search; null before the first one. */
    val results: List<TrainBetween>? = null,
    val filter: ClassFilter = ClassFilter.ALL,
    val sort: SortBy = SortBy.DEPARTURE,
) {
    /** What the list shows: trains running on [date], in the chosen class group, sorted. */
    val visible: List<TrainBetween>
        get() = results.orEmpty()
            .filter { it.runsOn(date) }
            .filter { t -> filter.group == null || t.classes.any { it.travelClass.group == filter.group } }
            .sortedWith(
                when (sort) {
                    SortBy.DEPARTURE -> compareBy { it.from.time }
                    SortBy.ARRIVAL -> compareBy<TrainBetween> { it.arrivalDayOffset }.thenBy { it.to.time }
                    SortBy.DURATION -> compareBy { it.duration }
                }
            )
}

class TrainsBetweenViewModel(
    private val source: TrainsBetweenSource = RailData.trainsBetween,
    private val stationSource: StationSource = RailData.stations,
    private val settings: AppSettings = RailData.settings,
) : ViewModel() {

    private val _state = MutableStateFlow(BetweenState(from = settings.lastFrom, to = settings.lastTo))
    val state: StateFlow<BetweenState> = _state

    private val _stations = MutableStateFlow<List<Station>>(emptyList())
    /** For the station picker; empty until loaded (or if it couldn't be). */
    val stations: StateFlow<List<Station>> = _stations

    private var searchJob: Job? = null

    init {
        // Search straight away only when the last search's stations were restored.
        if (_state.value.from != null && _state.value.to != null) search()
        viewModelScope.launch { runCatching { stationSource.allStations() }.onSuccess { _stations.value = it } }
    }

    fun setFrom(station: Station) = _state.update { it.copy(from = station, error = null) }
    fun setTo(station: Station) = _state.update { it.copy(to = station, error = null) }
    fun swap() = _state.update { it.copy(from = it.to, to = it.from) }
    fun setDate(date: LocalDate) = _state.update { it.copy(date = date) }
    fun setFilter(filter: ClassFilter) = _state.update { it.copy(filter = filter) }
    fun setSort(sort: SortBy) = _state.update { it.copy(sort = sort) }

    fun search() {
        val s = _state.value
        val from = s.from
        val to = s.to
        if (from == null || to == null) {
            _state.update {
                it.copy(error = if (from == null) "Choose the station you're starting from." else "Choose the station you're going to.")
            }
            return
        }
        if (from.code == to.code) {
            _state.update { it.copy(error = "From and To are the same station. Choose two different stations.") }
            return
        }
        settings.lastFrom = from
        settings.lastTo = to
        searchJob?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        searchJob = viewModelScope.launch {
            try {
                val trains = source.trainsBetween(from.code, to.code, s.date)
                _state.update { it.copy(loading = false, results = trains) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(loading = false, error = "Couldn't load trains. Check your connection and try again.") }
            }
        }
    }
}
