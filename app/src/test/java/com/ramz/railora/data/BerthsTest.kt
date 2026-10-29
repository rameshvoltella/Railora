package com.ramz.railora.data

import com.ramz.railora.domain.BerthType
import com.ramz.railora.domain.Berths
import com.ramz.railora.domain.model.PnrPassenger
import com.ramz.railora.domain.model.SeatState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BerthsTest {

    @Test
    fun sleeperAndThreeTier() {
        assertEquals(BerthType.LOWER, Berths.position("SL", 1)!!.type)
        assertEquals(BerthType.MIDDLE, Berths.position("SL", 2)!!.type)
        assertEquals(BerthType.UPPER, Berths.position("SL", 6)!!.type)
        assertEquals(BerthType.SIDE_LOWER, Berths.position("3A", 7)!!.type)
        assertEquals(BerthType.SIDE_UPPER, Berths.position("3A", 72)!!.type)
        // Bay 6 is berths 41–48: LB MB UB LB MB UB SL SU.
        val b45 = Berths.position("SL", 45)!!
        assertEquals(BerthType.MIDDLE, b45.type)
        assertEquals(BerthType.SIDE_UPPER, Berths.position("SL", 48)!!.type)
        assertEquals(6, b45.bay)
        assertEquals(9, b45.bays)
    }

    @Test
    fun twoTierAndFirst() {
        assertEquals(BerthType.LOWER, Berths.position("2A", 1)!!.type)
        assertEquals(BerthType.UPPER, Berths.position("2A", 4)!!.type)
        assertEquals(BerthType.SIDE_LOWER, Berths.position("2A", 5)!!.type)
        assertEquals(BerthType.SIDE_UPPER, Berths.position("2A", 12)!!.type)
        assertEquals(BerthType.UPPER, Berths.position("1A", 2)!!.type)
        assertNull(Berths.position("CC", 23))
    }

    @Test
    fun seatFromStatusText() {
        fun p(current: String, coach: String? = null, berth: String? = null) =
            PnrPassenger(1, current, current, SeatState.from(current), coach, berth, null)
        assertEquals("S4" to 45, Berths.seatOf(p("CNF/S4/45/LB")))
        assertEquals("B2" to 34, Berths.seatOf(p("B2/34/MB")))
        assertEquals("A1" to 12, Berths.seatOf(p("CNF", coach = "A1", berth = "12")))
        assertNull(Berths.seatOf(p("GNWL 34")))
    }
}
