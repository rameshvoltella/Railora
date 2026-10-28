package com.ramz.railora.data

import com.ramz.railora.data.ntes.NtesLiveStatusParser
import com.ramz.railora.data.ntes.NtesSession
import com.ramz.railora.data.railyatri.RailYatriParser
import com.ramz.railora.data.wimt.WimtParser
import com.ramz.railora.domain.TrainPlacement
import com.ramz.railora.domain.model.LiveTrainStatus
import com.ramz.railora.domain.model.RunState
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The same moment from all three sites: 12626 Kerala Express (NDLS → TVC), started 28 Oct 2026,
 * pages saved about 21:35 IST just after it passed Chata (CHJ), heading for Mathura (MTJ).
 * Every parser must land on the same app model.
 */
class LiveStatusParsersTest {

    private val now = LocalDateTime.of(2026, 10, 28, 21, 35)

    private fun page(path: String) = Jsoup.parse(javaClass.getResourceAsStream(path)!!, "UTF-8", NtesSession.BASE)

    /** [lastStop]: Where is my Train's page ends at Thiruvananthapuram Pettah, the others at Central. */
    private fun checkCommon(s: LiveTrainStatus, lastStop: String = "TVC") {
        assertEquals("12626", s.number)
        assertEquals(RunState.RUNNING, s.state)
        assertEquals(LocalDate.of(2026, 10, 28), s.startDate)
        assertEquals("NDLS", s.stops.first().station.code)
        assertEquals(lastStop, s.stops.last().station.code)
        assertTrue(s.stops.first().passed)
        val next = s.stops.first { !it.passed }
        assertEquals("MTJ", next.station.code)
        assertEquals("1", next.platform)
        assertEquals(LocalDateTime.of(2026, 10, 28, 21, 38), next.arrival!!.scheduled)
        assertNotNull(s.delayMinutes)
        assertTrue(s.delayMinutes!! in 1..15)
    }

    @Test
    fun ntes() {
        val s = NtesLiveStatusParser.parse(page("/ntes/running_12626.html"), "12626", now)!!
        checkCommon(s)
        assertEquals("Kerala Sf Exp", s.name)
        assertEquals("CHJ", s.position!!.station.code)
        assertEquals(110.0, s.position!!.kmFromOrigin)
        assertEquals(LocalDateTime.of(2026, 10, 28, 21, 31), s.lastUpdated)
        assertEquals(141.0, s.stops.first { it.station.code == "MTJ" }.kmFromOrigin)
    }

    @Test
    fun ntesPicksRunByStartDate() {
        val doc = page("/ntes/running_12626.html")   // saved with the 28 Oct and 29 Oct runs
        val tomorrow = NtesLiveStatusParser.parse(doc, "12626", now, LocalDate.of(2026, 10, 29))!!
        assertEquals(LocalDate.of(2026, 10, 29), tomorrow.startDate)
        assertEquals(RunState.NOT_STARTED, tomorrow.state)
        assertEquals(null, NtesLiveStatusParser.parse(doc, "12626", now, LocalDate.of(2026, 10, 20)))
    }

    @Test
    fun railYatri() {
        val props = RailYatriParser.pageProps(page("/railyatri/lts_12626.html"))!!
        val s = RailYatriParser.status(props.getJSONObject("ltsData"), props, "12626")!!
        checkCommon(s)
        assertEquals("Kerala Express", s.name)
        assertEquals("CHJ", s.position!!.station.code)
        assertEquals(115.0, s.position!!.kmFromOrigin)
        assertEquals(9, s.delayMinutes)

        val route = RailYatriParser.route(props, "12626")
        assertEquals(459, route.points.size)
        assertTrue(route.points.all { it.location != null })
        val placed = TrainPlacement.place(s, route, now)
        assertEquals("MTJ", placed.nextStop!!.station.code)
        assertTrue(placed.kmNow!! in 115.0..141.0)
        assertNotNull(placed.location)
    }

    @Test
    fun whereIsMyTrain() {
        val s = WimtParser.parse(page("/wimt/12626.html"), "12626", now)!!
        checkCommon(s, lastStop = "TVP")
        assertEquals("Kerala Express", s.name)
        assertEquals("AJH", s.position!!.station.code)
        assertEquals(11, s.delayMinutes)
        assertEquals(LocalDateTime.of(2026, 10, 28, 21, 34, 30), s.lastUpdated)
        // Times roll over midnight: Gwalior departure is 00:05 on the 29th.
        assertEquals(LocalDateTime.of(2026, 10, 29, 0, 5), s.stops.first { it.station.code == "GWL" }.departure!!.scheduled)

        // No km from this site: the route gives it.
        val route = RailYatriParser.route(RailYatriParser.pageProps(page("/railyatri/lts_12626.html"))!!, "12626")
        val placed = TrainPlacement.place(s, route, now)
        assertTrue(placed.kmNow!! in 110.0..141.0)
        assertNotNull(placed.kmToNextStop)
    }
}
