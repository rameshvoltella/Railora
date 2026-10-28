package com.ramz.railora.data.settings

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The sites Spot My Train can ask. */
enum class LiveSource(val label: String) {
    WHERE_IS_MY_TRAIN("Where is my Train"),
    NTES("NTES (Indian Railways)"),
    RAILYATRI("RailYatri"),
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

    /** The last train tracked, to show again when Spot My Train opens. */
    var lastTrain: String?
        get() = prefs.getString("last_train", null)
        set(value) = prefs.edit { putString("last_train", value) }
}
