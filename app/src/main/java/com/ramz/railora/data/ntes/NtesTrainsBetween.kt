package com.ramz.railora.data.ntes

import com.ramz.railora.domain.model.ClassOffer
import com.ramz.railora.domain.model.Station
import com.ramz.railora.domain.model.StationTime
import com.ramz.railora.domain.model.TrainBetween
import com.ramz.railora.domain.model.TrainType
import com.ramz.railora.domain.model.TravelClass
import com.ramz.railora.domain.source.TrainsBetweenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/** NTES "Trains B/w Stations": every train between two codes, no dates and no seat availability. */
class NtesTrainsBetween(private val session: NtesSession) : TrainsBetweenSource {

    override suspend fun trainsBetween(fromCode: String, toCode: String, date: LocalDate): List<TrainBetween> =
        withContext(Dispatchers.IO) {
            val doc = session.post(
                "q?opt=TrainsBetweenStation&subOpt=tbs",
                mapOf("lan" to "en", "jFromStationInput" to fromCode, "jToStationInput" to toCode),
            )
            NtesTrainsBetweenParser.parse(doc)
        }
}

/**
 * Turns the NTES results page into [TrainBetween]s. Each train is a `#myTable tbody tr`:
 *
 * ```
 * <span><b>16347</b>&nbsp;&nbsp;MANGALORE EXP</span><br>
 * <span>Daily | Mail Express</span>
 * <div style="display: flex; …">
 *   <span><b>00:04</b><br>Kottayam<br>KTYM</span>
 *   <div>--01:01 Hrs.--<br>&nbsp;2A,3A,3E,SL,GEN,PWD&nbsp;</div>
 *   <span><b>01:05</b><br>Ernakulam Town<br>ERN</span>
 * </div>
 * ```
 * Rows that don't fit are skipped rather than failing the whole list.
 */
object NtesTrainsBetweenParser {

    private val NUMBER = Regex("""^\d{5}$""")
    private val TIME = Regex("""^(\d{1,2}):(\d{2})$""")
    private val DURATION = Regex("""(\d+):(\d{2})\s*Hrs""")
    private val BR = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)

    fun parse(doc: Document): List<TrainBetween> =
        doc.select("#myTable tbody tr").mapNotNull { row -> runCatching { parseRow(row) }.getOrNull() }

    private fun parseRow(row: Element): TrainBetween? {
        val title = row.selectFirst("td > span:has(> b)") ?: return null
        val number = title.selectFirst("b")!!.text().trim()
        if (!NUMBER.matches(number)) return null
        val name = title.text().removePrefix(number).trim()

        // "Daily | Mail Express" — the first span after the title without a <b>.
        val (days, type) = title.nextElementSiblings().firstOrNull { it.tagName() == "span" }
            ?.text()?.split("|")?.map { it.trim() }
            .let { (it?.getOrNull(0) ?: "") to (it?.getOrNull(1) ?: "") }

        val stops = row.select("div > span:has(> b)").filter { TIME.matches(it.selectFirst("b")!!.text().trim()) }
        if (stops.size < 2) return null
        val (fromEl, toEl) = stops[0] to stops[1]
        val middle = fromEl.nextElementSibling()?.text().orEmpty()

        val duration = DURATION.find(middle)?.destructured
            ?.let { (h, m) -> Duration.ofHours(h.toLong()).plusMinutes(m.toLong()) } ?: return null
        val classes = middle.substringAfter("Hrs.", "").replace("-", " ").split(',', ' ', ' ')
            .mapNotNull { TravelClass.fromCode(it) }.distinct().map { ClassOffer(it) }

        return TrainBetween(
            number = number,
            name = name,
            type = trainType(type, name),
            runDays = runDays(days),
            from = stationTime(fromEl) ?: return null,
            to = stationTime(toEl) ?: return null,
            duration = duration,
            classes = classes,
        )
    }

    /** `<b>00:04</b><br>Kottayam<br>KTYM` — the code is sometimes in `<b>` too, so go by lines. */
    private fun stationTime(el: Element): StationTime? {
        val lines = el.html().split(BR).map { Jsoup.parse(it).text().trim() }.filter { it.isNotEmpty() }
        if (lines.size < 3) return null
        val (h, m) = TIME.find(lines[0])?.destructured ?: return null
        return StationTime(Station(lines[2].uppercase(), lines[1]), LocalTime.of(h.toInt() % 24, m.toInt()))
    }

    /** "Daily" or "Mon,Thu". Unknown text → empty (treated as every day). */
    internal fun runDays(text: String): Set<DayOfWeek> {
        if (text.equals("Daily", ignoreCase = true)) return DayOfWeek.entries.toSet()
        return text.split(',').mapNotNull { day ->
            DayOfWeek.entries.firstOrNull { it.name.take(3).equals(day.trim().take(3), ignoreCase = true) }
        }.toSet()
    }

    internal fun trainType(type: String, name: String): TrainType {
        val t = "$type $name".uppercase()
        return when {
            "VANDE" in t -> TrainType.VANDE_BHARAT
            "RAJDHANI" in t -> TrainType.RAJDHANI
            "JAN SHATABDI" in t || "JANSHATABDI" in t -> TrainType.JAN_SHATABDI
            "SHATABDI" in t -> TrainType.SHATABDI
            "DURONTO" in t -> TrainType.DURONTO
            "GARIB" in t -> TrainType.GARIB_RATH
            "HUMSAFAR" in t -> TrainType.HUMSAFAR
            "MEMU" in t -> TrainType.MEMU
            "DEMU" in t -> TrainType.DEMU
            "EMU" in t -> TrainType.EMU
            "SPECIAL" in t || "ON DEMAND" in t || " SPL" in t -> TrainType.SPECIAL
            "SUPERFAST" in t -> TrainType.SUPERFAST
            "MAIL" in t || "EXPRESS" in t -> TrainType.MAIL_EXPRESS
            "PASSENGER" in t -> TrainType.PASSENGER
            else -> TrainType.OTHER
        }
    }
}
