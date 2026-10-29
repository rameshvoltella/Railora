package com.ramz.railora.data.settings

import android.content.Context
import androidx.core.content.edit
import com.ramz.railora.domain.model.Station
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The sites Spot My Train can ask. */
enum class LiveSource(val label: String) {
    WHERE_IS_MY_TRAIN("Where is my Train"),
    NTES("NTES (Indian Railways)"),
    RAILYATRI("RailYatri"),
}

/** The sites PNR Status can ask. */
enum class PnrSite(val label: String) {
    CONFIRMTKT("ConfirmTkt"),
    RAILMITRA("RailMitra"),
}

/** Light or dark look: follow the phone, or always one. */
enum class ThemeMode(val label: String) {
    SYSTEM("System default"),
    LIGHT("Light"),
    DARK("Dark"),
}

/** User choices, saved on the phone. */
class AppSettings(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _liveSource = MutableStateFlow(
        prefs.getString("live_source", null)?.let { s -> LiveSource.entries.firstOrNull { it.name == s } }
            ?: LiveSource.WHERE_IS_MY_TRAIN
    )
    val liveSource: StateFlow<LiveSource> = _liveSource

    fun setLiveSource(source: LiveSource) {
        _liveSource.value = source
        prefs.edit { putString("live_source", source.name) }
    }

    private val _themeMode = MutableStateFlow(
        prefs.getString("theme_mode", null)?.let { s -> ThemeMode.entries.firstOrNull { it.name == s } } ?: ThemeMode.SYSTEM
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit { putString("theme_mode", mode.name) }
    }

    private val _pnrSite = MutableStateFlow(
        prefs.getString("pnr_site", null)?.let { s -> PnrSite.entries.firstOrNull { it.name == s } } ?: PnrSite.CONFIRMTKT
    )
    val pnrSite: StateFlow<PnrSite> = _pnrSite

    fun setPnrSite(site: PnrSite) {
        _pnrSite.value = site
        prefs.edit { putString("pnr_site", site.name) }
    }

    /** Trains looked up in Coach Position lately (numbers), newest first. */
    var recentCoachTrains: List<String>
        get() = prefs.getString("recent_coach_trains", "").orEmpty().split(',').filter { it.length == 5 }
        set(value) = prefs.edit { putString("recent_coach_trains", value.take(8).joinToString(",")) }

    /** PNRs checked lately, newest first, for one-tap re-checks. */
    var recentPnrs: List<String>
        get() = prefs.getString("recent_pnrs", "").orEmpty().split(',').filter { it.length == 10 }
        set(value) = prefs.edit { putString("recent_pnrs", value.take(5).joinToString(",")) }

    /** Stations of the last Trains Between Stations search; null on a fresh install. */
    var lastFrom: Station?
        get() = station("between_from")
        set(value) = putStation("between_from", value)
    var lastTo: Station?
        get() = station("between_to")
        set(value) = putStation("between_to", value)

    private fun station(key: String): Station? =
        prefs.getString(key, null)?.split('|', limit = 2)?.takeIf { it.size == 2 }?.let { Station(it[0], it[1]) }

    private fun putStation(key: String, s: Station?) =
        prefs.edit { if (s == null) remove(key) else putString(key, "${s.code}|${s.name}") }

    /** The last train tracked, to show again when Spot My Train opens. */
    var lastTrain: String?
        get() = prefs.getString("last_train", null)
        set(value) = prefs.edit { putString("last_train", value) }
}
