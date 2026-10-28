package com.ramz.railora.data.ntes

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
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

/** NTES running status: official, updated as the train reports at stations. The page is large (1–7 MB). */
class NtesLiveStatus(private val session: NtesSession) : LiveStatusSource {

    override suspend fun liveStatus(trainNumber: String, now: LocalDateTime, startDate: LocalDate?): LiveTrainStatus? =
        withContext(Dispatchers.IO) {
            val doc = session.post("tr?opt=TrainRunning&subOpt=FindRunningInstance", mapOf("trainNo" to trainNumber))
            NtesLiveStatusParser.parse(doc, trainNumber, now, startDate)
        }
}

/**
 * The NTES "FindRunningInstance" page has one `div.tab-pane#trainDD-mon-YYYY` per run (usually
 * tomorrow back to five days ago). Every station is a `div.w3-card-2`:
 *  • `.stopRow` – a halt: left column arrival, right column departure; each holds the scheduled
 *    time, then the actual/expected one ("21:41 28-Oct", a trailing `*` = forecast). Code and
 *    platform: `<b>MTJ <span>PF 1*</span></b>`; then "141 KMs" ("SRC" at the origin).
 *  • `.nonStopRow` – a station run through: "NAME - CODE" and km.
 * While the run is on its way, the pane's `<h4>` says "Current Position" and the `<h6>` says
 * "Departed from CHATA(CHJ) at 21:29 28-Oct" or "Arrived at …". "Last Updates On" follows.
 */
object NtesLiveStatusParser {

    private val TIME = Regex("""(\d{1,2}):(\d{2})\s+(\d{1,2})-([A-Za-z]{3})(\*?)""")
    private val KM = Regex("""(\d+)\s*KMs""")
    private val NON_STOP_LABEL = Regex("""^(.+?)\s+-\s+([A-Z0-9]+)$""")
    private val POSITION = Regex("""(Arrived at|Departed from)\s+(.+?)\s*\(([A-Z0-9]+)\)\s+at\s+(\d{1,2}:\d{2}\s+\d{1,2}-[A-Za-z]{3})""")
    private val UPDATED = Regex("""Last Updates On\s*(\d{1,2}-[A-Za-z]{3}-\d{4})\s+(\d{1,2}):(\d{2})""")
    private val TITLE = Regex("""^(\d{5})\s+(.+)$""")
    private val PANE_DATE: DateTimeFormatter =
        DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d-MMM-yyyy").toFormatter(Locale.ENGLISH)
    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    private data class Run(val status: LiveTrainStatus, val start: LocalDate)

    /** Every run on the page is there, so any [startDate] NTES lists can be shown. */
    fun parse(doc: Document, trainNumber: String, now: LocalDateTime, startDate: LocalDate? = null): LiveTrainStatus? {
        val name = doc.select("h3").firstNotNullOfOrNull { TITLE.find(it.text().trim()) }
            ?.groupValues?.get(2)?.let(NtesStations::titleCase)
        val runs = doc.select("div.tab-pane[id^=train]").mapNotNull { pane ->
            runCatching { parsePane(pane, trainNumber, name) }.getOrNull()
        }
        if (startDate != null) return runs.firstOrNull { it.start == startDate }?.status
        val today = now.toLocalDate()
        return (runs.filter { it.status.state == RunState.RUNNING }.maxByOrNull { it.start }
            ?: runs.firstOrNull { it.start == today }
            ?: runs.filter { !it.start.isAfter(today) }.maxByOrNull { it.start }
            ?: runs.firstOrNull())?.status
    }

    private fun parsePane(pane: Element, trainNumber: String, name: String?): Run? {
        val start = LocalDate.parse(pane.id().removePrefix("train"), PANE_DATE)
        val kmByCode = HashMap<String, Double>()
        val namesByCode = HashMap<String, String>()
        val stops = ArrayList<LiveStop>()
        for (row in pane.select("div.w3-card-2.stopRow, div.w3-card-2.nonStopRow")) {
            if (row.hasClass("nonStopRow")) {
                val label = row.select("b").map { it.text().trim() }.firstNotNullOfOrNull { NON_STOP_LABEL.find(it) } ?: continue
                val km = KM.find(row.text())?.groupValues?.get(1)?.toDouble() ?: continue
                kmByCode[label.groupValues[2]] = km
                namesByCode[label.groupValues[2]] = NtesStations.titleCase(label.groupValues[1])
            } else {
                parseStop(row, start)?.let { stop ->
                    stops += stop
                    stop.kmFromOrigin?.let { kmByCode[stop.station.code] = it }
                    namesByCode[stop.station.code] = stop.station.name
                }
            }
        }
        if (stops.isEmpty()) return null

        val updated = UPDATED.find(pane.text())?.destructured?.let { (date, h, m) ->
            LocalDate.parse(date, PANE_DATE).atTime(h.toInt(), m.toInt())
        }
        var position = parsePosition(pane, start, kmByCode, namesByCode)
        val lastPassed = stops.indexOfLast { it.passed }
        val state = when {
            position != null -> RunState.RUNNING
            lastPassed == stops.lastIndex -> RunState.REACHED
            lastPassed >= 0 -> RunState.RUNNING
            else -> RunState.NOT_STARTED
        }
        if (state == RunState.RUNNING && position == null) {
            val s = stops[lastPassed]
            position = TrainPosition(
                s.station,
                if (s.departure?.isActual == true) PositionEvent.DEPARTED else PositionEvent.ARRIVED,
                s.departure?.takeIf { it.isActual }?.expected ?: s.arrival?.expected,
                s.kmFromOrigin,
            )
        }

        return Run(
            LiveTrainStatus(
                number = trainNumber,
                name = name,
                origin = stops.first().station,
                destination = stops.last().station,
                startDate = start,
                state = state,
                delayMinutes = delayFromStops(stops),
                lastUpdated = updated,
                speedKmph = null,
                position = position,
                stops = stops,
            ),
            start,
        )
    }

    private fun parsePosition(
        pane: Element,
        start: LocalDate,
        kmByCode: Map<String, Double>,
        names: Map<String, String>,
    ): TrainPosition? {
        if (pane.selectFirst("h4")?.text()?.contains("Current Position") != true) return null
        val m = POSITION.find(pane.selectFirst("h6")?.text() ?: return null) ?: return null
        val (verb, name, code, time) = m.destructured
        return TrainPosition(
            station = Station(code, names[code] ?: NtesStations.titleCase(name)),
            event = if (verb.startsWith("Departed")) PositionEvent.DEPARTED else PositionEvent.ARRIVED,
            at = TIME.find(time)?.let { toDateTime(it, start) },
            kmFromOrigin = kmByCode[code],
        )
    }

    private fun parseStop(row: Element, start: LocalDate): LiveStop? {
        val info = row.selectFirst("div[style*='float:left;flex:1']") ?: return null
        val name = info.selectFirst("font > b")?.text()?.trim().orEmpty()
        val codeEl = info.selectFirst("div[style*='float:left;padding'] > b") ?: return null
        val code = codeEl.ownText().trim()
        if (code.isEmpty()) return null
        val platform = codeEl.selectFirst("span")?.text()
            ?.replace("PF", "")?.replace("*", "")?.trim()?.takeIf { it.isNotEmpty() && it != "-" }
        val km = KM.find(info.text())?.groupValues?.get(1)?.toDouble() ?: 0.0   // origin shows "SRC"
        val arrival = eventTime(row.selectFirst("div[style*='float:left;width:100px']"), start)
        val departure = eventTime(row.selectFirst("div[style*='float:right;text-align:right;width:100px']"), start)
        return LiveStop(
            station = Station(code, NtesStations.titleCase(name)),
            kmFromOrigin = km,
            platform = platform,
            arrival = arrival,
            departure = departure,
            passed = arrival?.isActual == true || departure?.isActual == true,
        )
    }

    /** Column spans: [0] scheduled, [1] actual/expected. "SRC"/"DSTN" columns have no times. */
    private fun eventTime(col: Element?, start: LocalDate): EventTime? {
        val spans = col?.children()?.filter { it.tagName() == "span" } ?: return null
        val scheduled = spans.getOrNull(0)?.text()?.let { TIME.find(it) }
        val expected = spans.getOrNull(1)?.text()?.let { TIME.find(it) }
        if (scheduled == null && expected == null) return null
        return EventTime(
            scheduled = scheduled?.let { toDateTime(it, start) },
            expected = expected?.let { toDateTime(it, start) },
            isActual = expected != null && expected.groupValues[5].isEmpty(),
        )
    }

    // "21:41 28-Oct": no year — take the start date's, moving to next year for Dec → Jan.
    private fun toDateTime(m: MatchResult, start: LocalDate): LocalDateTime? {
        val (h, min, day, mon) = m.destructured
        val month = MONTHS.indexOf(mon.lowercase()) + 1
        if (month == 0) return null
        val year = if (month < start.monthValue - 6) start.year + 1 else start.year
        return LocalDate.of(year, month, day.toInt()).atTime(h.toInt(), min.toInt())
    }
}
