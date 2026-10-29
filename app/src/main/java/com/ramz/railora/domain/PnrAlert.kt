package com.ramz.railora.domain

import com.ramz.railora.domain.model.PnrStatus
import com.ramz.railora.domain.model.Station
import com.ramz.railora.domain.model.TrainRoute
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * An arrival alert for a booked ticket: at [station] (by default where the passenger boards),
 * on the run that ticket is for. The run is the site's start date when given; otherwise the
 * travel date moved back by the days the train takes to reach the passenger's station (from the
 * route), so a long-distance train boarded on its third day follows the right run.
 */
object PnrAlert {

    fun defaultStation(pnr: PnrStatus): Station? = pnr.boarding ?: pnr.from

    fun startDate(pnr: PnrStatus, route: TrainRoute?): LocalDate? {
        pnr.trainStartDate?.let { return it }
        val travel = pnr.journeyDate ?: return null
        val fromCode = pnr.from?.code ?: return travel
        val minutes = route?.points?.firstOrNull { it.station.code == fromCode }?.scheduledMinutes ?: return travel
        return travel.minusDays((minutes / (24 * 60)).toLong())
    }

    /** When the train is due at [station]: the ticket's departure time there, else the timetable. */
    fun eta(pnr: PnrStatus, station: Station, route: TrainRoute?): LocalDateTime? {
        val departure = pnr.departure?.let(::clock)
        if (station.code == pnr.from?.code && pnr.journeyDate != null && departure != null) {
            return pnr.journeyDate.atTime(departure)
        }
        val start = startDate(pnr, route) ?: return null
        val minutes = route?.points?.firstOrNull { it.station.code == station.code }?.scheduledMinutes ?: return null
        return start.atStartOfDay().plusMinutes(minutes.toLong())
    }

    fun alert(pnr: PnrStatus, station: Station, route: TrainRoute?): ArrivalAlert? {
        val number = pnr.trainNumber ?: return null
        return ArrivalAlert(
            trainNumber = number,
            trainName = pnr.trainName,
            station = station,
            startDate = startDate(pnr, route),
            eta = eta(pnr, station, route),
            delayMinutes = null,
            lastChecked = null,
        )
    }

    private fun clock(text: String): LocalTime? =
        Regex("""(\d{1,2}):(\d{2})""").find(text)?.destructured?.let { (h, m) -> LocalTime.of(h.toInt() % 24, m.toInt()) }
}
