package com.ramz.railora.data

import com.ramz.railora.data.ntes.NtesSession
import com.ramz.railora.data.railyatri.RailYatriParser
import com.ramz.railora.domain.AlertPlanner
import com.ramz.railora.domain.AlertStep
import com.ramz.railora.domain.PnrAlert
import com.ramz.railora.domain.model.PnrStatus
import com.ramz.railora.domain.model.Station
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** A ticket on 12626 (New Delhi → Thiruvananthapuram) from Kottayam, on its third day. */
class PnrAlertTest {

    private val route by lazy {
        val doc = Jsoup.parse(javaClass.getResourceAsStream("/railyatri/lts_12626.html")!!, "UTF-8", NtesSession.BASE)
        RailYatriParser.route(RailYatriParser.pageProps(doc)!!, "12626")
    }

    private fun ticket(startDate: LocalDate? = null) = PnrStatus(
        pnr = "4412345678", trainNumber = "12626", trainName = "Kerala Express",
        journeyDate = LocalDate.of(2026, 10, 30), trainStartDate = startDate,
        from = Station("KTYM", "Kottayam"), to = Station("TVC", "Thiruvananthapuram Central"),
        boarding = Station("KTYM", "Kottayam"), travelClass = "SL", quota = "GN",
        departure = "16:02", arrival = "18:50", duration = "2:48", platform = null,
        chartPrepared = false, trainCancelled = false, fare = null, passengers = emptyList(),
    )

    @Test
    fun runIsWorkedOutFromTheRouteWhenTheSiteDoesNotSay() {
        // Kottayam is on the train's third day, so the run left New Delhi two days earlier.
        assertEquals(LocalDate.of(2026, 10, 28), PnrAlert.startDate(ticket(), route))
    }

    @Test
    fun siteStartDateWins() {
        assertEquals(LocalDate.of(2026, 10, 27), PnrAlert.startDate(ticket(LocalDate.of(2026, 10, 27)), route))
    }

    @Test
    fun boardingStationIsTheDefaultAndItsTimeComesFromTheTicket() {
        val alert = PnrAlert.alert(ticket(), PnrAlert.defaultStation(ticket())!!, route)!!
        assertEquals("KTYM", alert.station.code)
        assertEquals(LocalDateTime.of(2026, 10, 30, 16, 2), alert.eta)
        assertEquals(LocalDate.of(2026, 10, 28), alert.startDate)
    }

    @Test
    fun anotherStationTakesItsTimeFromTheTimetable() {
        // 12626 stops at Ernakulam Town, 3870 min after leaving New Delhi on 28 Oct.
        val eta = PnrAlert.eta(ticket(), Station("ERN", "Ernakulam Town"), route)
        assertEquals(LocalDateTime.of(2026, 10, 30, 16, 30), eta)
    }

    @Test
    fun alertSetDaysAheadWaitsUntilAFewHoursBefore() {
        val eta = LocalDateTime.of(2026, 10, 30, 16, 2)
        val step = AlertPlanner.next(null, "KTYM", LocalDateTime.of(2026, 10, 28, 9, 0), eta) as AlertStep.CheckAgain
        assertEquals(eta.minusHours(3), step.at)
    }
}
