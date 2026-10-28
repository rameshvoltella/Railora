package com.ramz.railora.data.ntes

import com.ramz.railora.domain.model.Station
import com.ramz.railora.domain.source.StationSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * NTES's station list, `javascripts/station_data.js`:
 * `var arrStationList = [{"code":"AA","name":"ATARIA"}, …]` (~8,700 stations, ~320 KB).
 * Kept in [cacheFile] for a week so it isn't downloaded on every launch.
 */
class NtesStations(private val session: NtesSession, private val cacheFile: File) : StationSource {

    companion object {
        private val ENTRY = Regex("""\{\s*"code"\s*:\s*"([^"]+)"\s*,\s*"name"\s*:\s*"([^"]*)"\s*\}""")
        private const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

        internal fun parse(js: String): List<Station> =
            ENTRY.findAll(js).map { Station(it.groupValues[1].trim().uppercase(), titleCase(it.groupValues[2])) }
                .distinctBy { it.code }.toList()

        /** "ERNAKULAM  JN" → "Ernakulam Jn" */
        internal fun titleCase(name: String): String =
            name.trim().split(Regex("\\s+")).joinToString(" ") { word ->
                word.lowercase().split('-').joinToString("-") { it.replaceFirstChar(Char::uppercase) }
            }
    }

    private val lock = Mutex()
    private var stations: List<Station>? = null

    override suspend fun allStations(): List<Station> = lock.withLock {
        stations ?: withContext(Dispatchers.IO) { load() }.also { stations = it }
    }

    private fun load(): List<Station> {
        val fresh = cacheFile.exists() && System.currentTimeMillis() - cacheFile.lastModified() < MAX_AGE_MS
        if (fresh) parse(cacheFile.readText()).takeIf { it.isNotEmpty() }?.let { return it }
        return try {
            val js = session.get("javascripts/station_data.js")
            parse(js).also { if (it.isNotEmpty()) cacheFile.writeText(js) }
        } catch (e: Exception) {
            // Offline: an old copy is better than nothing.
            if (cacheFile.exists()) parse(cacheFile.readText()) else throw e
        }
    }
}
