package com.ramz.railora.data.easemytrip

import com.ramz.railora.domain.model.Coach
import com.ramz.railora.domain.model.CoachKind
import com.ramz.railora.domain.model.CoachPosition
import com.ramz.railora.domain.source.CoachPositionSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Coach order from `https://www.easemytrip.com/railways/train-coach/{train}/`. Coach order
 * rarely changes, so a train's answer is kept for the session.
 */
class EaseMyTripCoaches : CoachPositionSource {

    companion object {
        private const val UA =
            "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36"
    }

    private val cache = HashMap<String, CoachPosition?>()

    override suspend fun coachPosition(trainNumber: String): CoachPosition? = withContext(Dispatchers.IO) {
        synchronized(cache) { if (trainNumber in cache) return@withContext cache[trainNumber] }
        val doc = Jsoup.connect("https://www.easemytrip.com/railways/train-coach/$trainNumber/")
            .userAgent(UA).timeout(20_000).maxBodySize(0).get()
        EaseMyTripCoachParser.parse(doc, trainNumber).also { synchronized(cache) { cache[trainNumber] = it } }
    }
}

/**
 * The strip is `div#engin-wrap`, one `div.train-list` per coach in order, engine first
 * (`.engin-train`). Each has `<div class="train-name" onclick="CoachType('<seat map png>', 'i')">
 * <p>A1</p></div>`.
 */
object EaseMyTripCoachParser {

    private val LAYOUT = Regex("""CoachType\('([^']+)'""")

    fun parse(doc: Document, trainNumber: String): CoachPosition? {
        val items = doc.select("#engin-wrap div.train-list")
        if (items.isEmpty()) return null
        var n = 0
        val coaches = items.mapNotNull { item ->
            val label = item.selectFirst("div.train-name p")?.text()?.trim().orEmpty()
            if (label.isEmpty()) return@mapNotNull null
            val engine = item.hasClass("engin-train") || label.equals("Engine", ignoreCase = true)
            Coach(
                position = if (engine) 0 else ++n,
                code = if (engine) "ENG" else label.uppercase(),
                kind = if (engine) CoachKind.ENGINE else CoachKind.fromCode(label),
                layoutImageUrl = item.selectFirst("div.train-name[onclick]")?.attr("onclick")
                    ?.let { LAYOUT.find(it)?.groupValues?.get(1) },
            )
        }
        return CoachPosition(trainNumber, coaches).takeIf { coaches.size > 1 }
    }
}
