package com.ramz.railora.ui.pnr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ramz.railora.data.RailData
import com.ramz.railora.data.settings.AppSettings
import com.ramz.railora.data.settings.PnrSite
import com.ramz.railora.domain.model.CoachPosition
import com.ramz.railora.domain.model.PnrStatus
import com.ramz.railora.domain.model.PnrUnavailable
import com.ramz.railora.domain.model.TrainRoute
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PnrState(
    val input: String = "",
    val site: PnrSite = PnrSite.CONFIRMTKT,
    val loading: Boolean = false,
    val error: String? = null,
    val status: PnrStatus? = null,
    val recent: List<String> = emptyList(),
    /** The ticket's train route, for the alert's station choice and run date; null until loaded. */
    val route: TrainRoute? = null,
    /** The ticket's train coach order; null until loaded or if unavailable. */
    val coaches: CoachPosition? = null,
)

class PnrViewModel(private val settings: AppSettings = RailData.settings) : ViewModel() {

    private val _state = MutableStateFlow(PnrState(site = settings.pnrSite.value, recent = settings.recentPnrs))
    val state: StateFlow<PnrState> = _state

    private var job: Job? = null

    init {
        viewModelScope.launch { settings.pnrSite.collect { s -> _state.update { it.copy(site = s) } } }
    }

    fun setInput(text: String) = _state.update { it.copy(input = text.filter(Char::isDigit).take(10), error = null) }

    fun check(pnr: String = _state.value.input) {
        if (pnr.length != 10) {
            _state.update { it.copy(error = "A PNR has 10 digits.") }
            return
        }
        val site = _state.value.site
        job?.cancel()
        _state.update { it.copy(input = pnr, loading = true, error = null) }
        job = viewModelScope.launch {
            try {
                val status = RailData.pnr(site).pnrStatus(pnr)
                settings.recentPnrs = listOf(pnr) + settings.recentPnrs.filter { it != pnr }
                _state.update { it.copy(loading = false, status = status, recent = settings.recentPnrs, route = null, coaches = null) }
                status.trainNumber?.let { number ->
                    launch {
                        val coaches = runCatching { RailData.coaches.coachPosition(number) }.getOrNull()
                        _state.update { if (it.status === status) it.copy(coaches = coaches) else it }
                    }
                    val route = runCatching { RailData.routes.route(number) }.getOrNull()
                    _state.update { if (it.status === status) it.copy(route = route) else it }
                }
            } catch (e: PnrUnavailable) {
                _state.update { it.copy(loading = false, status = null, error = e.message) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update {
                    it.copy(
                        loading = false,
                        status = null,
                        error = "Couldn't reach ${site.label}. Check your connection, or try the other site in Settings.",
                    )
                }
            }
        }
    }
}
