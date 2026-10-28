package com.ramz.railora.data.railyatri

import com.ramz.railora.data.ntes.NtesStations
import com.ramz.railora.domain.model.EventTime
import com.ramz.railora.domain.model.GeoPoint
import com.ramz.railora.domain.model.LiveStop
import com.ramz.railora.domain.model.LiveTrainStatus
import com.ramz.railora.domain.model.PositionEvent
import com.ramz.railora.domain.model.RoutePoint
import com.ramz.railora.domain.model.RunState
import com.ramz.railora.domain.model.Station
import com.ramz.railora.domain.model.TrainPosition
import com.ramz.railora.domain.model.TrainRoute
import com.ramz.railora.domain.source.LiveStatusSource
import com.ramz.railora.domain.source.TrainRouteSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * RailYatri live status: `https://www.railyatri.in/live-train-status/{train}?start_day={n}`, where
 * n = days since the run left its origin (0 = today). The data is JSON in
 * `<script id="__NEXT_DATA__">` under `props.pageProps`:
 *  • `timeTableData[0].route`: every station, stopping or not, with `lat`/`lng`,
 *    `distance_from_source`, `stop`, `platform_number`, `sta_min`/`std_min` (minutes after midnight
 *    of the start date; the origin's `sta_min` is 0).
 *  • `ltsData`: `train_start_date`, `current_station_code`, `status` (A arrived, D departed,
 *    T passed), `distance_from_source`, `delay`, `avg_speed`, `update_time`, `at_src`/`at_dstn`,
 *    `previous_stations` (with `arrival_delay`/`departure_delay`) and `upcoming_stations`
 *    (with `eta_a_min`/`eta_d_min`).
 *    The page fills this only for today's run (n = 0); for every run it comes from
 *    `https://livestatus.railyatri.in/api/v3/train_eta_data/{train}/{n}.json?start_day={n}`,
 *    which returns the same object.
 *
 * The route also serves as the map for every site ([TrainRouteSource]).
 */
class RailYatri : LiveStatusSource, TrainRouteSource {

    companion object {
        private const val UA =
            "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36"
        private const val CACHE_FOR_MS = 30_000L
    }

    /** The page's props per train, for its timetable (same for every run): fetched once. */
    private val pages = HashMap<String, JSONObject>()
    private val routes = HashMap<String, TrainRoute>()
    private val live = HashMap<Pair<String, Int>, Pair<Long, JSONObject?>>()

    override suspend fun liveStatus(trainNumber: String, now: LocalDateTime, startDate: LocalDate?): LiveTrainStatus? {
        val page = page(trainNumber) ?: return null
        fun status(day: Int, lts: JSONObject?) = lts?.let { RailYatriParser.status(it, page, trainNumber) }
        if (startDate != null) {
            val day = ChronoUnit.DAYS.between(startDate, now.toLocalDate()).toInt()
            if (day < 0) return null
            return status(day, live(trainNumber, day))?.takeIf { it.startDate == startDate }
        }
        // A long journey may have started up to two days ago: take the run that's on its way.
        var first: LiveTrainStatus? = null
        for (day in 0..2) {
            val s = status(day, live(trainNumber, day)) ?: continue
            if (s.state == RunState.RUNNING) return s
            if (first == null) first = s
        }
        return first
    }

    override suspend fun route(trainNumber: String): TrainRoute? =
        routes[trainNumber] ?: page(trainNumber)?.let { RailYatriParser.route(it, trainNumber) }
            ?.takeIf { it.points.isNotEmpty() }?.also { routes[trainNumber] = it }

    private suspend fun page(trainNumber: String): JSONObject? = withContext(Dispatchers.IO) {
        synchronized(pages) { pages[trainNumber]?.let { return@withContext it } }
        val doc = Jsoup.connect("https://www.railyatri.in/live-train-status/$trainNumber?start_day=0")
            .userAgent(UA).timeout(25_000).maxBodySize(0).get()
        RailYatriParser.pageProps(doc)?.also { synchronized(pages) { pages[trainNumber] = it } }
    }

    /**
     * Live data for the run that started [startDay] days ago. The page only carries it for
     * today's run; the site's own API (what the page calls in the browser) has every run, in
     * the same shape as the page's `ltsData`.
     */
    private suspend fun live(trainNumber: String, startDay: Int): JSONObject? = withContext(Dispatchers.IO) {
        val key = trainNumber to startDay
        synchronized(live) { live[key]?.takeIf { System.currentTimeMillis() - it.first < CACHE_FOR_MS }?.let { return@withContext it.second } }
        val body = Jsoup.connect(
            "https://livestatus.railyatri.in/api/v3/train_eta_data/$trainNumber/$startDay.json" +
                "?start_day=$startDay&user_id=&device_type_id=4"
        ).userAgent(UA).referrer("https://www.railyatri.in/").ignoreContentType(true)
            .timeout(25_000).maxBodySize(0).execute().body()
        JSONObject(body).takeIf { it.optBoolean("success", true) && it.has("train_start_date") }
            .also { synchronized(live) { live[key] = System.currentTimeMillis() to it } }
    }
}

object RailYatriParser {

    private val UPDATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun pageProps(doc: Document): JSONObject? {
        val json = doc.selectFirst("script#__NEXT_DATA__")?.data() ?: return null
        return JSONObject(json).optJSONObject("props")?.optJSONObject("pageProps")
    }

    fun route(props: JSONObject, trainNumber: String): TrainRoute {
        val arr = props.optJSONArray("timeTableData")?.optJSONObject(0)?.optJSONArray("route")
        val points = (0 until (arr?.length() ?: 0)).map { arr!!.getJSONObject(it) }.mapNotNull { s ->
            val code = s.optString("station_code").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val lat = s.optString("lat").toDoubleOrNull()
            val lng = s.optString("lng").toDoubleOrNull()
            RoutePoint(
                station = Station(code, NtesStations.titleCase(s.optString("station_name"))),
                kmFromOrigin = s.optString("distance_from_source").toDoubleOrNull() ?: 0.0,
                location = if (lat != null && lng != null && (lat != 0.0 || lng != 0.0)) GeoPoint(lat, lng) else null,
                isStop = s.optBoolean("stop"),
                // The origin's sta_min is 0; its departure is std_min.
                scheduledMinutes = (if (s.optInt("sta_min") > 0) s.optInt("sta_min") else s.optInt("std_min")).takeIf { it > 0 },
            )
        }.distinctBy { it.station.code }
        return TrainRoute(trainNumber, points)
    }

    /** [lts]: live data (the page's `ltsData`, or the API's reply); [props]: the page, for its timetable. */
    fun status(lts: JSONObject, props: JSONObject, trainNumber: String): LiveTrainStatus? {
        val startDate = lts.optString("train_start_date").takeIf { it.isNotBlank() }?.let(LocalDate::parse) ?: return null
        val midnight = startDate.atStartOfDay()
        fun at(min: Int) = midnight.plusMinutes(min.toLong())

        // Stops come from the timetable; actual/expected times from previous/upcoming stations.
        val arr = props.optJSONArray("timeTableData")?.optJSONObject(0)?.optJSONArray("route")
        val rows = (0 until (arr?.length() ?: 0)).map { arr!!.getJSONObject(it) }.filter { it.optBoolean("stop") }
        val previous = objects(lts, "previous_stations").associateBy { it.optString("station_code") }
        val upcoming = objects(lts, "upcoming_stations").associateBy { it.optString("station_code") }
        val currentCode = lts.optString("current_station_code").takeIf { it.isNotBlank() && it != "null" }
        val status = lts.optString("status")
        val atSource = lts.optBoolean("at_src")
        val atDestination = lts.optBoolean("at_dstn")

        val stops = rows.mapIndexed { i, s ->
            val code = s.optString("station_code")
            val schArr = if (i == 0) null else at(s.optInt("sta_min"))
            val schDep = if (i == rows.lastIndex) null else at(s.optInt("std_min"))
            val prev = previous[code]
            val next = upcoming[code]
            val passed = prev != null || atDestination || (code == currentCode && status == "A")
            fun expected(scheduled: LocalDateTime?, prevDelay: String, etaKey: String): EventTime? = scheduled?.let {
                when {
                    prev != null -> EventTime(it, it.plusMinutes(prev.optLong(prevDelay)), isActual = true)
                    next != null && next.has(etaKey) -> EventTime(it, at(next.optInt(etaKey)), isActual = false)
                    else -> EventTime(it, null, isActual = false)
                }
            }
            LiveStop(
                station = Station(code, NtesStations.titleCase(s.optString("station_name"))),
                kmFromOrigin = s.optString("distance_from_source").toDoubleOrNull(),
                platform = s.optInt("platform_number").takeIf { it > 0 }?.toString()
                    ?: next?.optInt("platform_number")?.takeIf { it > 0 }?.toString(),
                arrival = expected(schArr, "arrival_delay", "eta_a_min"),
                departure = expected(schDep, "departure_delay", "eta_d_min"),
                passed = passed,
            )
        }

        val running = currentCode != null && !atSource && !atDestination
        val updated = lts.optString("update_time").takeIf { it.length >= 19 }
            ?.let { runCatching { LocalDateTime.parse(it.take(19), UPDATE_TIME) }.getOrNull() }
        val position = if (!running) null else {
            val km = lts.optDouble("distance_from_source", 0.0).takeIf { it > 0 }
            TrainPosition(
                station = Station(currentCode!!, NtesStations.titleCase(lts.optString("current_station_name").trimEnd('~', '\'').trim())),
                event = when (status) { "A" -> PositionEvent.ARRIVED; "D" -> PositionEvent.DEPARTED; else -> PositionEvent.PASSED },
                at = updated ?: at(lts.optInt("a_min")),
                kmFromOrigin = km,
            )
        }

        return LiveTrainStatus(
            number = trainNumber,
            name = lts.optString("train_name").takeIf { it.isNotBlank() },
            origin = lts.optString("source").takeIf { it.isNotBlank() }
                ?.let { Station(it, NtesStations.titleCase(lts.optString("source_stn_name"))) },
            destination = lts.optString("destination").takeIf { it.isNotBlank() }
                ?.let { Station(it, NtesStations.titleCase(lts.optString("dest_stn_name"))) },
            startDate = startDate,
            state = when {
                atDestination -> RunState.REACHED
                running -> RunState.RUNNING
                else -> RunState.NOT_STARTED
            },
            delayMinutes = if (running || atDestination) lts.optInt("delay") else null,
            lastUpdated = updated,
            speedKmph = lts.optInt("avg_speed").takeIf { it > 0 },
            position = position,
            stops = stops,
        )
    }

    private fun objects(obj: JSONObject, key: String): List<JSONObject> {
        val arr = obj.optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
    }
}
