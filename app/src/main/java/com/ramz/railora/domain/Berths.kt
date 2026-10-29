package com.ramz.railora.domain

import com.ramz.railora.domain.model.PnrPassenger
import com.ramz.railora.domain.model.SeatState

/** Berth types as Indian Railways names them on tickets. */
enum class BerthType(val code: String, val label: String) {
    LOWER("LB", "Lower berth"),
    MIDDLE("MB", "Middle berth"),
    UPPER("UB", "Upper berth"),
    SIDE_LOWER("SL", "Side lower"),
    SIDE_UPPER("SU", "Side upper"),
}

/**
 * Where a berth is in its coach. [bay] counts from 1 at the coach's first end; [inBay] is the
 * berth's place in that bay's repeating pattern (1-based); [pattern] is the bay's berths in
 * order, so a drawing can mark this one.
 */
data class BerthPosition(
    val number: Int,
    val type: BerthType,
    val bay: Int,
    val bays: Int,
    val inBay: Int,
    val pattern: List<BerthType>,
) {
    /** Near a door and the toilets (first or last bay), or the middle of the coach. */
    val whereInCoach: String
        get() = when {
            bay == 1 || bay == bays -> "near the door and toilets at one end"
            bay <= bays / 3 || bay > bays - bays / 3 -> "towards one end"
            else -> "in the middle of the coach"
        }
}

/**
 * Berth positions from the standard layouts: Sleeper and AC 3 Tier repeat every 8 (LB MB UB LB
 * MB UB SL SU), AC 2 Tier every 6 (LB UB LB UB SL SU), AC First every 4 (LB UB LB UB). Chair cars
 * and unknown classes have no berths, so they give null.
 */
object Berths {

    private val THREE_TIER = listOf(
        BerthType.LOWER, BerthType.MIDDLE, BerthType.UPPER,
        BerthType.LOWER, BerthType.MIDDLE, BerthType.UPPER,
        BerthType.SIDE_LOWER, BerthType.SIDE_UPPER,
    )
    private val TWO_TIER = listOf(
        BerthType.LOWER, BerthType.UPPER, BerthType.LOWER, BerthType.UPPER,
        BerthType.SIDE_LOWER, BerthType.SIDE_UPPER,
    )
    private val FIRST = listOf(BerthType.LOWER, BerthType.UPPER, BerthType.LOWER, BerthType.UPPER)

    private val SEAT = Regex("""\b([A-Z]{1,2}\d{1,2})\s*[/,\s]\s*(\d{1,3})\b""")

    /**
     * A confirmed passenger's coach and berth: the site's fields, else read from the status
     * text ("CNF/S4/45/LB", "B2/34"). Null unless confirmed with a coach.
     */
    fun seatOf(p: PnrPassenger): Pair<String, Int?>? {
        if (p.state != SeatState.CONFIRMED) return null
        p.coach?.takeIf { it.isNotBlank() }?.let { return it.uppercase() to p.berth?.filter(Char::isDigit)?.toIntOrNull() }
        val m = SEAT.find(p.current.uppercase()) ?: SEAT.find(p.booking.uppercase()) ?: return null
        return m.groupValues[1] to m.groupValues[2].toIntOrNull()
    }

    /** [classCode] as on the ticket ("SL", "3A", "3E", "2A", "1A"); [berth] the berth number. */
    fun position(classCode: String?, berth: Int?): BerthPosition? {
        if (berth == null || berth <= 0) return null
        val (pattern, perCoach) = when (classCode?.uppercase()) {
            "SL" -> THREE_TIER to 72
            "3A" -> THREE_TIER to 72
            "3E" -> THREE_TIER to 80
            "2A" -> TWO_TIER to 54
            "1A" -> FIRST to 24
            else -> return null
        }
        val i = (berth - 1) % pattern.size
        val bays = (perCoach + pattern.size - 1) / pattern.size
        return BerthPosition(
            number = berth,
            type = pattern[i],
            bay = (berth - 1) / pattern.size + 1,
            bays = maxOf(bays, (berth - 1) / pattern.size + 1),
            inBay = i + 1,
            pattern = pattern,
        )
    }
}
