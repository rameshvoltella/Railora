package com.ramz.railora.data.wimt

import com.ramz.railora.domain.delayFromStops
import com.ramz.railora.domain.model.EventTime
import com.ramz.railora.domain.model.LiveStop
import com.ramz.railora.domain.model.LiveTrainStatus
import com.ramz.railora.domain.model.PositionEvent
import com.ramz.railora.domain.model.RunState
import com.ramz.railora.domain.model.Station
import com.ramz.railora.domain.model.TrainPosition
import com.ramz.railora.domain.source.LiveStatusSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.random.Random

/**
 * Live status from `https://whereismytrain.org.in/?trainNo={train}` (server-rendered HTML).
 *
 * The site is asked gently, like one person browsing: one request at a time across the app,
 * 1.5–3 s apart, with browser headers and its cookie; a train asked about in the last 45 s is
 * answered from memory.
 */
class WhereIsMyTrain : LiveStatusSource {

    companion object {
        private const val BASE = "https://whereismytrain.org.in/"
        private const val UA =
            "Mozilla/5.0 (Linux; Android 14; SM-S911B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        private const val CACHE_FOR_MS = 45_000L

        private val lock = Any()
        private var lastRequest = 0L
        private val cookies = mutableMapOf<String, String>()
        private val recent = mutableMapOf<String, Pair<Long, Document>>()
    }

    /**
     * The site shows only its latest run and ignores any date in the URL, so a [startDate]
     * other than that run's gives null (the app then asks another site).
     */
    override suspend fun liveStatus(trainNumber: String, now: LocalDateTime, startDate: LocalDate?): LiveTrainStatus? =
        withContext(Dispatchers.IO) {
            WimtParser.parse(fetch(trainNumber), trainNumber, now)?.takeIf { startDate == null || it.startDate == startDate }
        }

    private fun fetch(trainNumber: String): Document = synchronized(lock) {
        recent[trainNumber]?.takeIf { System.currentTimeMillis() - it.first < CACHE_FOR_MS }?.let { return it.second }
        val wait = lastRequest + Random.nextLong(1_500L, 3_000L) - System.currentTimeMillis()
        if (wait > 0) Thread.sleep(wait)
        try {
            val resp = Jsoup.connect("$BASE?trainNo=$trainNumber")
                .userAgent(UA)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-IN,en;q=0.9")
                .referrer(BASE)
                .cookies(cookies)
                .timeout(20_000)
                .execute()
            cookies.putAll(resp.cookies())
            resp.parse().also { recent[trainNumber] = System.currentTimeMillis() to it }
        } finally {
            lastRequest = System.currentTimeMillis()
        }
    }
}

/**
 * Each station is a card `div.flex-grow.rounded-lg` with `<h3>Name (CODE)</h3>`, an optional
 * "• PF 3" badge, and ARRIVAL/DEPARTURE columns: the bold `div.font-semibold` time is
 * actual/expected, the grey `div.text-gray-400` one (only when different) is scheduled.
 * The train is drawn as `#train-icon` inside the wrapper of the card it last reached; the
 * header has "Last Updated: 09:34:30 PM" and "11min Late". Times have no dates, so dates are
 * worked out from the order of stops and the current time.
 */
object WimtParser {

    private val TITLE = Regex("""^(.*?)\s*\(([A-Z0-9]+)\)$""")
    private val PAGE_TITLE = Regex("""Status of (.+?) \((\d{5})\)""")
    private val CLOCK = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val CLOCK_SECONDS = DateTimeFormatter.ofPattern("h:mm:ss a", Locale.ENGLISH)
    private val LATE = Regex("""(\d+)\s*min\s*Late""", RegexOption.IGNORE_CASE)
    private val UPDATED = Regex("""Last Updated:\s*(\d{1,2}:\d{2}(?::\d{2})?\s*[AP]M)""", RegexOption.IGNORE_CASE)

    private class Card(val station: Station, val platform: String?, val schArr: LocalTime?, val expArr: LocalTime?,
                       val schDep: LocalTime?, val expDep: LocalTime?)

    fun parse(doc: Document, trainNumber: String, now: LocalDateTime): LiveTrainStatus? {
        val pairs = doc.select("div.flex-grow.rounded-lg").mapNotNull { el -> card(el)?.let { el to it } }
            // The site sometimes repeats a station on consecutive cards (e.g. TVP twice at the end).
            .fold(mutableListOf<Pair<org.jsoup.nodes.Element, Card>>()) { acc, p ->
                if (acc.lastOrNull()?.second?.station?.code != p.second.station.code) acc += p
                acc
            }
        if (pairs.isEmpty()) return null
        val cards = pairs.map { it.second }
        val current = pairs.indexOfFirst { it.first.parent()?.parent()?.selectFirst("#train-icon") != null }
            .takeIf { it >= 0 } ?: pairs.indexOfLast { it.first.hasClass("bg-green-50") }

        // Scheduled times in order, rolling over midnight; minutes from midnight of the start day.
        var day = 0
        var last = -1
        fun rel(t: LocalTime?): Int? = t?.let {
            val m = it.hour * 60 + it.minute
            if (last >= 0 && m < last - 60) day++
            last = m
            day * 1440 + m
        }
        val relTimes = cards.map { rel(it.schArr) to rel(it.schDep) }

        // Anchor the start date so the train's latest reported time is closest to now.
        val anchorRel = if (current >= 0) relTimes[current].let { it.second ?: it.first } else relTimes[0].second
        val today = now.toLocalDate()
        val start = (-3L..1L).map { today.plusDays(it) }.minByOrNull { d ->
            anchorRel?.let { abs(Duration.between(d.atStartOfDay().plusMinutes(it.toLong()), now).toMinutes()) } ?: 0
        }!!
        fun schedAt(r: Int?) = r?.let { start.atStartOfDay().plusMinutes(it.toLong()) }
        // Expected clock time on the date closest to the scheduled time.
        fun expAt(sch: LocalDateTime?, clock: LocalTime?): LocalDateTime? {
            if (clock == null) return null
            val base = sch ?: return null
            return (-1L..1L).map { base.toLocalDate().plusDays(it).atTime(clock) }
                .minBy { abs(Duration.between(base, it).toMinutes()) }
        }

        val reachedEnd = current == cards.lastIndex
        val stops = cards.mapIndexed { i, c ->
            val sa = schedAt(relTimes[i].first)
            val sd = schedAt(relTimes[i].second)
            val ea = expAt(sa, c.expArr)
            val ed = expAt(sd, c.expDep)
            val departed = i < current || (i == current && ed != null && !ed.isAfter(now))
            LiveStop(
                station = c.station,
                kmFromOrigin = null,
                platform = c.platform,
                arrival = if (i == 0) null else sa?.let { EventTime(it, ea, isActual = i <= current) },
                departure = if (i == cards.lastIndex) null else sd?.let { EventTime(it, ed, isActual = departed) },
                passed = i <= current,
            )
        }

        val startedYet = current > 0 || (current == 0 && stops[0].departure?.isActual == true)
        val state = when {
            reachedEnd -> RunState.REACHED
            startedYet -> RunState.RUNNING
            else -> RunState.NOT_STARTED
        }
        val position = if (state != RunState.RUNNING) null else stops[current].let { s ->
            val left = s.departure?.isActual == true
            TrainPosition(
                station = s.station,
                event = if (left) PositionEvent.DEPARTED else PositionEvent.ARRIVED,
                at = if (left) s.departure?.best else s.arrival?.best ?: s.departure?.best,
                kmFromOrigin = null,
            )
        }

        val header = doc.text()
        val updated = UPDATED.find(header)?.groupValues?.get(1)?.let { clock(it, CLOCK_SECONDS) ?: clock(it, CLOCK) }
            ?.let { t -> listOf(today, today.minusDays(1)).map { it.atTime(t) }.first { !it.isAfter(now.plusMinutes(5)) } }
        val title = PAGE_TITLE.find(doc.title())

        return LiveTrainStatus(
            number = trainNumber,
            name = title?.groupValues?.get(1),
            origin = stops.first().station,
            destination = stops.last().station,
            startDate = start,
            state = state,
            delayMinutes = if (state == RunState.NOT_STARTED) null else headerDelay(doc) ?: delayFromStops(stops),
            lastUpdated = updated,
            speedKmph = null,
            position = position,
            stops = stops,
        )
    }

    private fun card(el: org.jsoup.nodes.Element): Card? {
        val title = TITLE.find(el.selectFirst("h3")?.text()?.trim() ?: return null) ?: return null
        val cols = el.select("div.grid > div")
        fun times(i: Int): Pair<LocalTime?, LocalTime?> {
            val col = cols.getOrNull(i) ?: return null to null
            val exp = col.selectFirst("div.font-semibold")?.text()?.let { clock(it, CLOCK) }
            val sch = col.selectFirst("div.text-gray-400")?.text()?.let { clock(it, CLOCK) } ?: exp
            return sch to exp
        }
        val (sa, ea) = times(0)
        val (sd, ed) = times(1)
        val pf = el.select("span").map { it.text() }.firstOrNull { "PF" in it }
            ?.substringAfter("PF")?.trim()?.takeIf { it.isNotEmpty() }
        return Card(Station(title.groupValues[2], title.groupValues[1]), pf, sa, ea, sd, ed)
    }

    private fun clock(text: String, format: DateTimeFormatter): LocalTime? =
        runCatching { LocalTime.parse(text.trim().uppercase(), format) }.getOrNull()

    /** The header badge: "11min Late" or "On Time" (the first such badge on the page). */
    private fun headerDelay(doc: Document): Int? {
        val badge = doc.select("span").map { it.text() }
            .firstOrNull { LATE.containsMatchIn(it) || it.contains("On Time", ignoreCase = true) } ?: return null
        return LATE.find(badge)?.groupValues?.get(1)?.toInt() ?: 0
    }
}
