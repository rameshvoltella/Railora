package com.ramz.railora.data

import com.ramz.railora.data.confirmtkt.ConfirmTktPnrParser
import com.ramz.railora.domain.model.PnrUnavailable
import com.ramz.railora.domain.model.SeatState
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PnrTest {

    /** Saved from ConfirmTkt at 00:15 IST, during the railways' nightly PNR downtime. */
    @Test
    fun confirmTktDowntimeIsShownAsMessage() {
        val json = JSONObject(javaClass.getResourceAsStream("/confirmtkt/pnr_downtime.json")!!.bufferedReader().readText())
        try {
            ConfirmTktPnrParser.parse(json, "2412345678")
            fail("expected PnrUnavailable")
        } catch (e: PnrUnavailable) {
            assertTrue(e.message!!.contains("Indian railways network will be down"))
        }
    }

    @Test
    fun seatStates() {
        assertEquals(SeatState.CONFIRMED, SeatState.from("CNF/S4/45/LB"))
        assertEquals(SeatState.CONFIRMED, SeatState.from("CNF"))
        assertEquals(SeatState.CONFIRMED, SeatState.from("B2/34/MB"))
        assertEquals(SeatState.RAC, SeatState.from("RAC 12"))
        assertEquals(SeatState.WAITLIST, SeatState.from("GNWL 34"))
        assertEquals(SeatState.WAITLIST, SeatState.from("RLWL/12"))
        assertEquals(SeatState.WAITLIST, SeatState.from("WL 5"))
        assertEquals(SeatState.CANCELLED, SeatState.from("CAN"))
    }
}
