package com.ramz.railora.data.confirmtkt

import com.ramz.railora.data.ntes.NtesStations
import com.ramz.railora.domain.model.PnrPassenger
import com.ramz.railora.domain.model.PnrStatus
import com.ramz.railora.domain.model.PnrUnavailable
import com.ramz.railora.domain.model.SeatState
import com.ramz.railora.domain.model.Station
import com.ramz.railora.domain.source.PnrSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * ConfirmTkt PNR status: the JSON its PNR page (www.confirmtkt.com/pnr-status) asks for,
 * `https://api.confirmtkt.com/api/pnr/statusnewvilkki/{pnr}?locale=en&…`.
 */
class ConfirmTktPnr : PnrSource {

    companion object {
        private const val UA =
            "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36"
    }

    override suspend fun pnrStatus(pnr: String): PnrStatus = withContext(Dispatchers.IO) {
        val body = Jsoup.connect(
            "https://api.confirmtkt.com/api/pnr/statusnewvilkki/$pnr?email=&querysource=&locale=en&getHighChanceText=true"
        ).userAgent(UA).referrer("https://www.confirmtkt.com/").header("Origin", "https://www.confirmtkt.com")
            .ignoreContentType(true).timeout(30_000).maxBodySize(0).execute().body()
        ConfirmTktPnrParser.parse(JSONObject(body), pnr)
    }
}

/**
 * Top level: `Pnr`, `TrainNo`, `TrainName`, `Doj` (travel date from `From`), `SourceDoj` (taken
 * to be the run's start date at the train's origin), `From`/`SourceName`, `To`/`DestinationName`,
 * `BoardingPoint`/`BoardingStationName`, `Class`, `Quota`, `ChartPrepared`, `TrainCancelledFlag`,
 * `DepartureTime`, `ArrivalTime`, `Duration`, `ExpectedPlatformNo`, `TicketFare`/`BookingFare`,
 * `Error`/`ErrorCode` (e.g. 122 during the railways' nightly downtime), and `PassengerStatus[]`
 * with `Number`, `BookingStatus`, `CurrentStatus`, `Coach`/`CurrentCoachId`,
 * `Berth`/`CurrentBerthNo`, `PredictionPercentage`.
 */
object ConfirmTktPnrParser {

    private val DATES = listOf("dd-MM-yyyy", "d-M-yyyy", "yyyy-MM-dd", "dd/MM/yyyy", "d MMM yyyy", "dd-MMM-yyyy")
        .map { DateTimeFormatter.ofPattern(it, Locale.ENGLISH) }

    fun parse(j: JSONObject, pnr: String): PnrStatus {
        val trainNo = j.str("TrainNo")
        if (trainNo == null) {
            throw PnrUnavailable(j.str("Error") ?: "No status for PNR $pnr. Check the number and try again.")
        }
        val passengers = j.optJSONArray("PassengerStatus")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.mapIndexed { i, p ->
                val current = p.str("CurrentStatus") ?: p.str("CurrentStatusNew") ?: ""
                val booking = p.str("BookingStatus") ?: p.str("BookingStatusNew") ?: current
                val state = SeatState.from(current.ifEmpty { booking })
                PnrPassenger(
                    number = p.optInt("Number", i + 1),
                    booking = booking,
                    current = current.ifEmpty { booking },
                    state = state,
                    coach = p.str("CurrentCoachId") ?: p.str("Coach") ?: p.str("BookingCoachId"),
                    berth = (p.str("CurrentBerthNo") ?: p.str("Berth") ?: p.str("BookingBerthNo"))?.takeIf { it != "0" },
                    confirmChance = if (state == SeatState.WAITLIST || state == SeatState.RAC) {
                        p.str("PredictionPercentage")?.toDoubleOrNull()?.toInt()?.coerceIn(0, 100)
                    } else null,
                )
            }
        }.orEmpty()

        return PnrStatus(
            pnr = j.str("Pnr") ?: pnr,
            trainNumber = trainNo,
            trainName = j.str("TrainName"),
            journeyDate = j.str("Doj")?.let(::date),
            trainStartDate = j.str("SourceDoj")?.let(::date),
            from = station(j.str("From"), j.str("SourceName")),
            to = station(j.str("To"), j.str("DestinationName")),
            boarding = station(j.str("BoardingPoint"), j.str("BoardingStationName")),
            travelClass = j.str("Class"),
            quota = j.str("Quota"),
            departure = j.str("DepartureTime"),
            arrival = j.str("ArrivalTime"),
            duration = j.str("Duration"),
            platform = j.str("ExpectedPlatformNo"),
            chartPrepared = j.optBoolean("ChartPrepared"),
            trainCancelled = j.optBoolean("TrainCancelledFlag"),
            fare = j.str("TicketFare") ?: j.str("BookingFare"),
            passengers = passengers,
        )
    }

    private fun station(code: String?, name: String?): Station? =
        code?.let { Station(it.uppercase(), name?.let(NtesStations::titleCase) ?: it.uppercase()) }

    private fun date(text: String): LocalDate? = DATES.firstNotNullOfOrNull {
        runCatching { LocalDate.parse(text.trim().take(11).trim(), it) }.getOrNull()
    }

    /** Non-blank string, or null (also for JSON null and "null"). */
    private fun JSONObject.str(key: String): String? =
        if (isNull(key)) null else optString(key).trim().takeIf { it.isNotEmpty() && it != "null" }
}
