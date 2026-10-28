package com.ramz.railora.data.ntes

import com.ramz.railora.domain.model.TrainRef
import com.ramz.railora.domain.source.TrainListSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * NTES's train list, `javascripts/train_data.js`: `var arrTrainList = ["00081- VGLJ-MTJ EXAM SPL", …]`.
 * Kept in [cacheFile] for a week, like [NtesStations].
 */
class NtesTrainList(private val session: NtesSession, private val cacheFile: File) : TrainListSource {

    companion object {
        private val ENTRY = Regex(""""(\d{5})\s*-\s*([^"]+)"""")
        private const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

        internal fun parse(js: String): List<TrainRef> =
            ENTRY.findAll(js).map { TrainRef(it.groupValues[1], it.groupValues[2].trim()) }.distinctBy { it.number }.toList()
    }

    private val lock = Mutex()
    private var trains: List<TrainRef>? = null

    override suspend fun allTrains(): List<TrainRef> = lock.withLock {
        trains ?: withContext(Dispatchers.IO) { load() }.also { trains = it }
    }

    private fun load(): List<TrainRef> {
        val fresh = cacheFile.exists() && System.currentTimeMillis() - cacheFile.lastModified() < MAX_AGE_MS
        if (fresh) parse(cacheFile.readText()).takeIf { it.isNotEmpty() }?.let { return it }
        return try {
            val js = session.get("javascripts/train_data.js")
            parse(js).also { if (it.isNotEmpty()) cacheFile.writeText(js) }
        } catch (e: Exception) {
            if (cacheFile.exists()) parse(cacheFile.readText()) else throw e
        }
    }
}
