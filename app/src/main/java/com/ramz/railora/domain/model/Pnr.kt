package com.ramz.railora.domain.model

import java.time.LocalDate

/** A ticket's status by PNR. Fields the site doesn't give are null. */
data class PnrStatus(
    val pnr: String,
    val trainNumber: String?,
    val trainName: String?,
    /** Date of travel from [from]. */
    val journeyDate: LocalDate?,
    /** The date this run left the train's origin (differs from [journeyDate] on long journeys), when the site gives it. */
    val trainStartDate: LocalDate?,
    val from: Station?,
    val to: Station?,
    val boarding: Station?,
    /** Class code as on the ticket: "SL", "3A", "2A"… */
    val travelClass: String?,
    val quota: String?,
    val departure: String?,
    val arrival: String?,
    val duration: String?,
    val platform: String?,
    val chartPrepared: Boolean,
    val trainCancelled: Boolean,
    val fare: String?,
    val passengers: List<PnrPassenger>,
)

/** [booking]: status when booked; [current]: now. [confirmChance]: 0–100, for waitlisted tickets, when the site predicts it. */
data class PnrPassenger(
    val number: Int,
    val booking: String,
    val current: String,
    val state: SeatState,
    val coach: String?,
    val berth: String?,
    val confirmChance: Int?,
)

enum class SeatState(val label: String) {
    CONFIRMED("Confirmed"),
    RAC("RAC"),
    WAITLIST("Waitlist"),
    CANCELLED("Cancelled"),
    UNKNOWN("Status");

    companion object {
        /** From Indian Railways status text ("CNF/S4/45", "RAC 12", "GNWL 34", "CAN"…), the same on every site. */
        fun from(status: String): SeatState {
            val s = status.trim().uppercase()
            return when {
                s.startsWith("CAN") || s.contains("CANCEL") -> CANCELLED
                s.startsWith("RAC") -> RAC
                Regex("""^(GN|RL|PQ|TQ|RS|CK|NP|RQ|LD|DP|HO|PQ|SS)?WL""").containsMatchIn(s) || s.startsWith("W/L") -> WAITLIST
                s.startsWith("CNF") || s.startsWith("CONFIRM") || Regex("""^[A-Z]{1,2}\d{1,2}[ /,]""").containsMatchIn(s) -> CONFIRMED
                else -> UNKNOWN
            }
        }
    }
}

/** The site answered but couldn't give a status (wrong PNR, flushed, railway system down…): [message] is for the user. */
class PnrUnavailable(message: String) : Exception(message)
