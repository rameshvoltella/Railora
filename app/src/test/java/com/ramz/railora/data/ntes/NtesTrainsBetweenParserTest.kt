package com.ramz.railora.data.ntes

import com.ramz.railora.domain.model.TrainType
import com.ramz.railora.domain.model.TravelClass
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime

/** Saved NTES pages (Oct 2026). If NTES changes its HTML, refresh the fixtures and fix the parser. */
class NtesTrainsBetweenParserTest {

    private fun page(name: String) =
        Jsoup.parse(javaClass.getResourceAsStream("/ntes/$name")!!, "UTF-8", NtesSession.BASE)

    @Test
    fun kottayamToErnakulam() {
        val trains = NtesTrainsBetweenParser.parse(page("tbs_ktym_ern.html"))
        assertEquals(61, trains.size)

        val first = trains[0]
        assertEquals("16347", first.number)
        assertEquals("MANGALORE EXP", first.name)
        assertEquals(TrainType.MAIL_EXPRESS, first.type)
        assertTrue(first.runsDaily)
        assertEquals("KTYM", first.from.station.code)
        assertEquals("Kottayam", first.from.station.name)
        assertEquals(LocalTime.of(0, 4), first.from.time)
        assertEquals("ERN", first.to.station.code)
        assertEquals(LocalTime.of(1, 5), first.to.time)
        assertEquals(Duration.ofMinutes(61), first.duration)
        assertEquals(
            listOf(TravelClass.SECOND_AC, TravelClass.THIRD_AC, TravelClass.THIRD_AC_ECONOMY, TravelClass.SLEEPER, TravelClass.GENERAL),
            first.classes.map { it.travelClass },   // PWD dropped
        )

        val lttExp = trains.first { it.number == "22114" }
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), lttExp.runDays)
        assertEquals(TrainType.SUPERFAST, lttExp.type)
        assertTrue(lttExp.classes.isEmpty())

        val vandeBharat = trains.first { it.number == "20634" }
        assertEquals(TrainType.VANDE_BHARAT, vandeBharat.type)
        assertEquals(6, vandeBharat.runDays.size)

        // NTES also lists trains ending at the nearby Ernakulam Jn.
        assertTrue(trains.any { it.to.station.code == "ERS" })
    }

    @Test
    fun multiDayJourney() {
        val trains = NtesTrainsBetweenParser.parse(page("tbs_ers_ndls.html"))
        assertEquals(14, trains.size)
        assertTrue(trains.all { it.duration > Duration.ofHours(24) })
        assertTrue(trains.all { it.arrivalDayOffset >= 1 })
    }

    @Test
    fun stationNames() {
        assertEquals("Ernakulam Jn", NtesStations.titleCase("ERNAKULAM  JN"))
        val list = NtesStations.parse("""var arrStationList = [{"code":"KTYM","name":"KOTTAYAM"},
            |{"code":"ERS","name":"ERNAKULAM JN"}];""".trimMargin())
        assertEquals(listOf("KTYM", "ERS"), list.map { it.code })
    }
}
