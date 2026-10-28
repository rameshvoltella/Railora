package com.ramz.railora.data

import com.ramz.railora.data.ntes.NtesSession
import com.ramz.railora.data.railyatri.RailYatriParser
import com.ramz.railora.data.wimt.WimtParser
import com.ramz.railora.domain.AlertPlanner
import com.ramz.railora.domain.AlertStep
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime

/**
 * 12626 at 21:35 on 28 Oct: left New Delhi (NDLS), just passed Chata, next stop Mathura (MTJ)
 * at ~21:47, then Agra Cantt (AGC) at ~22:3x, … Kottayam (KTYM) two days later.
 */
class AlertPlannerTest {

    private val now = LocalDateTime.of(2026, 10, 28, 21, 35)
    private fun page(path: String) = Jsoup.parse(javaClass.getResourceAsStream(path)!!, "UTF-8", NtesSession.BASE)
    private val railYatri by lazy {
        val props = RailYatriParser.pageProps(page("/railyatri/lts_12626.html"))!!
        RailYatriParser.status(props.getJSONObject("ltsData"), props, "12626")!!
    }

    @Test
    fun nextStopAfterLeavingThePreviousOneAlertsNow() {
        // Boarding at Mathura: the train has left New Delhi, the stop before → "about to reach".
        val step = AlertPlanner.next(railYatri, "MTJ", now, null)
        assertTrue(step is AlertStep.Approaching)
    }

    @Test
    fun aStopFurtherOnIsCheckedAgainHalfAnHourBefore() {
        val step = AlertPlanner.next(railYatri, "AGC", now, null) as AlertStep.CheckAgain
        val eta = step.eta!!
        assertTrue(eta.isAfter(now.plusMinutes(45)))
        assertEquals(eta.minusMinutes(30), step.at)
    }

    @Test
    fun aFarStopIsCheckedAtLeastEveryTwoHours() {
        val step = AlertPlanner.next(railYatri, "KTYM", now, null) as AlertStep.CheckAgain
        assertEquals(now.plusHours(2), step.at)
        assertTrue(Duration.between(now, step.eta).toHours() > 24)
    }

    @Test
    fun aStationAlreadyPassedSaysSo() {
        assertTrue(AlertPlanner.next(railYatri, "NDLS", now, null) is AlertStep.Arrived)
    }

    @Test
    fun aStationNotOnTheRouteStopsTheAlert() {
        assertTrue(AlertPlanner.next(railYatri, "HWH", now, null) is AlertStep.GiveUp)
    }

    @Test
    fun noAnswerTriesAgainSoon() {
        val step = AlertPlanner.next(null, "AGC", now, now.plusHours(1)) as AlertStep.CheckAgain
        assertEquals(now.plusMinutes(5), step.at)
    }

    @Test
    fun sameAnswerFromWhereIsMyTrain() {
        val wimt = WimtParser.parse(page("/wimt/12626.html"), "12626", now)!!
        assertTrue(AlertPlanner.next(wimt, "MTJ", now, null) is AlertStep.Approaching)
        assertTrue(AlertPlanner.next(wimt, "AGC", now, null) is AlertStep.CheckAgain)
    }
}
