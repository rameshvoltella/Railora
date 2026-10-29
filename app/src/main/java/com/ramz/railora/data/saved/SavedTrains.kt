package com.ramz.railora.data.saved

import android.content.Context
import androidx.core.content.edit
import com.ramz.railora.domain.model.Station
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** A train the user starred: [from]/[to] are the stations it was saved with (journey or route), when known. */
data class SavedTrain(val number: String, val name: String?, val from: Station?, val to: Station?)

/** Starred trains, newest first, saved on the phone. */
class SavedTrains(context: Context) {

    private val prefs = context.getSharedPreferences("saved_trains", Context.MODE_PRIVATE)
    private val _trains = MutableStateFlow(load())
    val trains: StateFlow<List<SavedTrain>> = _trains

    fun isSaved(number: String) = _trains.value.any { it.number == number }

    /** Saves [train], or removes it if it's already saved. */
    @Synchronized
    fun toggle(train: SavedTrain) =
        save(if (isSaved(train.number)) _trains.value.filter { it.number != train.number } else listOf(train) + _trains.value)

    @Synchronized
    fun remove(number: String) = save(_trains.value.filter { it.number != number })

    private fun save(list: List<SavedTrain>) {
        _trains.value = list
        prefs.edit { putString("trains", JSONArray(list.map(::toJson)).toString()) }
    }

    private fun load(): List<SavedTrain> = runCatching {
        val arr = JSONArray(prefs.getString("trains", "[]"))
        (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private fun toJson(t: SavedTrain) = JSONObject().apply {
        put("number", t.number)
        put("name", t.name ?: "")
        t.from?.let { put("fromCode", it.code); put("fromName", it.name) }
        t.to?.let { put("toCode", it.code); put("toName", it.name) }
    }

    private fun fromJson(o: JSONObject) = SavedTrain(
        number = o.getString("number"),
        name = o.optString("name").ifEmpty { null },
        from = o.optString("fromCode").ifEmpty { null }?.let { Station(it, o.optString("fromName")) },
        to = o.optString("toCode").ifEmpty { null }?.let { Station(it, o.optString("toName")) },
    )
}
