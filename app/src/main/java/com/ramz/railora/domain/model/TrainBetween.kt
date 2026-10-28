package com.ramz.railora.domain.model

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/** One train found between two stations. Fields a site can't supply are empty or null, never guessed. */
data class TrainBetween(
    val number: String,
    val name: String,
    val type: TrainType,
    /** Days it leaves [from]. Empty = the site didn't say (treated as running every day). */
    val runDays: Set<DayOfWeek>,
    val from: StationTime,
    val to: StationTime,
    val duration: Duration,
    val classes: List<ClassOffer>,
) {
    val runsDaily: Boolean get() = runDays.isEmpty() || runDays.size == 7

    fun runsOn(date: LocalDate): Boolean = runsDaily || date.dayOfWeek in runDays

    /** Days after departing that it arrives (0 = same day), for "+1" labels. */
    val arrivalDayOffset: Int
        get() = ((from.time.toSecondOfDay() / 60 + duration.toMinutes()) / (24 * 60)).toInt()
}

/** A station on a journey with the train's time there (departure at [TrainBetween.from], arrival at [TrainBetween.to]). */
data class StationTime(val station: Station, val time: LocalTime)

/** A class on a train, with seat availability when the site provides it (NTES doesn't). */
data class ClassOffer(val travelClass: TravelClass, val availability: SeatAvailability? = null)

data class SeatAvailability(val status: Status, val count: Int? = null) {
    enum class Status { AVAILABLE, RAC, WAITLIST, REGRET, NOT_AVAILABLE }
}

enum class ClassGroup { AC, SLEEPER, SEATING, GENERAL }

enum class TravelClass(val code: String, val label: String, val group: ClassGroup) {
    FIRST_AC("1A", "First AC", ClassGroup.AC),
    EXECUTIVE_ANUBHUTI("EA", "Executive Anubhuti", ClassGroup.AC),
    SECOND_AC("2A", "AC 2 Tier", ClassGroup.AC),
    FIRST_CLASS("FC", "First Class", ClassGroup.SLEEPER),
    THIRD_AC("3A", "AC 3 Tier", ClassGroup.AC),
    THIRD_AC_ECONOMY("3E", "AC 3 Economy", ClassGroup.AC),
    EXECUTIVE_CHAIR("EC", "Executive Chair Car", ClassGroup.AC),
    CHAIR_CAR("CC", "AC Chair Car", ClassGroup.AC),
    SLEEPER("SL", "Sleeper", ClassGroup.SLEEPER),
    SECOND_SITTING("2S", "Second Sitting", ClassGroup.SEATING),
    GENERAL("GEN", "General", ClassGroup.GENERAL);

    companion object {
        private val byCode = entries.associateBy { it.code }
        private val aliases = mapOf("GN" to GENERAL, "UR" to GENERAL, "II" to SECOND_SITTING)

        /** null for anything that isn't a travel class (e.g. NTES's "PWD" quota). */
        fun fromCode(code: String): TravelClass? = code.trim().uppercase().let { byCode[it] ?: aliases[it] }
    }
}

enum class TrainType(val label: String) {
    VANDE_BHARAT("Vande Bharat"),
    RAJDHANI("Rajdhani"),
    SHATABDI("Shatabdi"),
    DURONTO("Duronto"),
    GARIB_RATH("Garib Rath"),
    JAN_SHATABDI("Jan Shatabdi"),
    HUMSAFAR("Humsafar"),
    SUPERFAST("Superfast"),
    MAIL_EXPRESS("Mail / Express"),
    MEMU("MEMU"),
    DEMU("DEMU"),
    EMU("EMU"),
    PASSENGER("Passenger"),
    SPECIAL("Special"),
    OTHER("Train"),
}
