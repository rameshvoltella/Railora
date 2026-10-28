package com.ramz.railora.data.alerts

import android.content.Context
import androidx.core.content.edit
import com.ramz.railora.domain.ArrivalAlert
import com.ramz.railora.domain.model.Station
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime

/** Arrival alerts that are on, saved on the phone; the screen and the background checks share it. */
class AlertStore(context: Context) {

    private val prefs = context.getSharedPreferences("alerts", Context.MODE_PRIVATE)
    private val _alerts = MutableStateFlow(load())
    val alerts: StateFlow<List<ArrivalAlert>> = _alerts

    fun get(id: String): ArrivalAlert? = _alerts.value.firstOrNull { it.id == id }

    @Synchronized
    fun put(alert: ArrivalAlert) = save(_alerts.value.filter { it.id != alert.id } + alert)

    @Synchronized
    fun remove(id: String) = save(_alerts.value.filter { it.id != id })

    private fun save(list: List<ArrivalAlert>) {
        _alerts.value = list
        prefs.edit { putString("alerts", JSONArray(list.map(::toJson)).toString()) }
    }

    private fun load(): List<ArrivalAlert> = runCatching {
        val arr = JSONArray(prefs.getString("alerts", "[]"))
        (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private fun toJson(a: ArrivalAlert) = JSONObject().apply {
        put("train", a.trainNumber)
        put("name", a.trainName ?: "")
        put("code", a.station.code)
        put("station", a.station.name)
        put("start", a.startDate?.toString() ?: "")
        put("eta", a.eta?.toString() ?: "")
        put("delay", a.delayMinutes ?: Int.MIN_VALUE)
        put("checked", a.lastChecked?.toString() ?: "")
    }

    private fun fromJson(o: JSONObject) = ArrivalAlert(
        trainNumber = o.getString("train"),
        trainName = o.optString("name").ifEmpty { null },
        station = Station(o.getString("code"), o.getString("station")),
        startDate = o.optString("start").ifEmpty { null }?.let(LocalDate::parse),
        eta = o.optString("eta").ifEmpty { null }?.let(LocalDateTime::parse),
        delayMinutes = o.optInt("delay", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE },
        lastChecked = o.optString("checked").ifEmpty { null }?.let(LocalDateTime::parse),
    )
}
