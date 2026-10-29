package com.ramz.railora.domain.model

/** The order of a train's coaches, engine first. */
data class CoachPosition(val trainNumber: String, val coaches: List<Coach>)

/**
 * One coach. [position]: 0 for the engine, then 1, 2, … from the engine. [layoutImageUrl]: a
 * picture of the seat layout, when the site has one.
 */
data class Coach(val position: Int, val code: String, val kind: CoachKind, val layoutImageUrl: String? = null)

enum class CoachKind(val label: String) {
    ENGINE("Engine"),
    GENERATOR("Power car"),
    LUGGAGE("Luggage / Guard"),
    AC_FIRST("AC First (1A)"),
    AC_TWO("AC 2 Tier (2A)"),
    AC_THREE("AC 3 Tier (3A)"),
    AC_THREE_ECONOMY("AC 3 Economy (3E)"),
    EXECUTIVE_CHAIR("Executive Chair (EC)"),
    CHAIR_CAR("AC Chair Car (CC)"),
    SLEEPER("Sleeper (SL)"),
    SECOND_SITTING("Second Sitting (2S)"),
    GENERAL("General"),
    PANTRY("Pantry"),
    DIVYANGJAN("Divyangjan"),
    OTHER("Other");

    companion object {
        /** The kind of coach a ticket class travels in ("SL" → sleeper coaches…); null if unknown. */
        fun forClass(classCode: String?): CoachKind? = when (classCode?.trim()?.uppercase()) {
            "1A" -> AC_FIRST
            "2A" -> AC_TWO
            "3A" -> AC_THREE
            "3E" -> AC_THREE_ECONOMY
            "EC" -> EXECUTIVE_CHAIR
            "CC" -> CHAIR_CAR
            "SL" -> SLEEPER
            "2S" -> SECOND_SITTING
            else -> null
        }

        /** From Indian Railways coach codes, which are the same on every site (A1 = 2A, S4 = SL…). */
        fun fromCode(code: String): CoachKind {
            val c = code.trim().uppercase()
            return when {
                c == "ENG" || c == "ENGINE" || c == "LOCO" -> ENGINE
                c == "EOG" || c == "EG" || c == "PWR" || c.startsWith("LWLRRM") -> GENERATOR
                c == "PC" || c == "PANTRY" -> PANTRY
                c in setOf("SLR", "SLRD", "LPR", "LR", "LDR", "VP", "GRD", "RMS") -> LUGGAGE
                c in setOf("HCP", "DIV", "PWD") -> DIVYANGJAN
                c in setOf("GS", "GN", "GEN", "UR") || c.startsWith("GEN") || c.startsWith("UR") -> GENERAL
                c.startsWith("HA") || Regex("""H\d*""").matches(c) -> AC_FIRST
                Regex("""A\d*""").matches(c) -> AC_TWO
                Regex("""[BG]\d*""").matches(c) -> AC_THREE
                Regex("""M\d*""").matches(c) -> AC_THREE_ECONOMY
                Regex("""E\d*""").matches(c) -> EXECUTIVE_CHAIR
                Regex("""[CJ]\d*""").matches(c) -> CHAIR_CAR
                Regex("""S\d*""").matches(c) -> SLEEPER
                Regex("""D\d*""").matches(c) -> SECOND_SITTING
                else -> OTHER
            }
        }
    }
}
