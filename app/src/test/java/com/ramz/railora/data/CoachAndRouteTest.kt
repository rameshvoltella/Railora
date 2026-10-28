package com.ramz.railora.data

import com.ramz.railora.data.easemytrip.EaseMyTripCoachParser
import com.ramz.railora.data.ntes.NtesSession
import com.ramz.railora.data.railyatri.RailYatriParser
import com.ramz.railora.domain.TrainPlacement
import com.ramz.railora.domain.model.CoachKind
import com.ramz.railora.domain.stationsBetween
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class CoachAndRouteTest {

    private fun page(path: String) = Jsoup.parse(javaClass.getResourceAsStream(path)!!, "UTF-8", NtesSession.BASE)

    @Test
    fun easeMyTripCoaches() {
        val c = EaseMyTripCoachParser.parse(page("/easemytrip/coach_12626.html"), "12626")!!
        assertEquals(
            listOf("ENG", "EOG", "GS", "GS", "A2", "A1", "B6", "B5", "B4", "B3", "B2", "B1", "M1", "PC",
                "S6", "S5", "S4", "S3", "S2", "S1", "GS", "SLR", "HCP"),
            c.coaches.map { it.code },
        )
        assertEquals(CoachKind.ENGINE, c.coaches[0].kind)
        assertEquals(0, c.coaches[0].position)
        assertEquals(1, c.coaches[1].position)
        assertEquals(CoachKind.AC_TWO, c.coaches.first { it.code == "A1" }.kind)
        assertEquals(CoachKind.AC_THREE_ECONOMY, c.coaches.first { it.code == "M1" }.kind)
        assertEquals(CoachKind.SLEEPER, c.coaches.first { it.code == "S3" }.kind)
        assertEquals(CoachKind.PANTRY, c.coaches.first { it.code == "PC" }.kind)
        assertTrue(c.coaches.drop(1).all { it.layoutImageUrl?.endsWith(".png") == true })
    }

    /** 12626 has just passed Chata (CHJ, 110 km): between New Delhi and Mathura it runs through ~20 stations. */
    @Test
    fun stationsBetweenStops() {
        val props = RailYatriParser.pageProps(page("/railyatri/lts_12626.html"))!!
        val status = RailYatriParser.status(props.getJSONObject("ltsData"), props, "12626")!!
        val route = RailYatriParser.route(props, "12626")
        val now = LocalDateTime.of(2026, 10, 28, 21, 35)
        val km = TrainPlacement.place(status, route, now).kmNow

        val between = stationsBetween(status, route, 0, km)   // NDLS → MTJ
        val codes = between.map { it.point.station.code }
        assertTrue(codes.containsAll(listOf("NZM", "FDB", "PWL", "KSV", "CHJ", "AJH", "VRBD")))
        assertTrue("NDLS" !in codes && "MTJ" !in codes)
        assertTrue(between.first { it.point.station.code == "CHJ" }.passed)
        assertTrue(!between.first { it.point.station.code == "BTSR" }.passed)
        assertTrue(between.all { it.expected != null })
    }
}
