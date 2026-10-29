package com.ramz.railora.domain

import com.ramz.railora.domain.model.LiveTrainStatus
import com.ramz.railora.domain.model.RunState
import com.ramz.railora.domain.model.Station
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/** "Tell me when this train is about to reach my station." [eta] etc. are updated by each check. */
data class ArrivalAlert(
    val trainNumber: String,
    val trainName: String?,
    val station: Station,
    /** The run, by the day it left its origin; null = the current run. */
    val startDate: LocalDate?,
    val eta: LocalDateTime?,
    val delayMinutes: Int?,
    val lastChecked: LocalDateTime?,
) {
    val id: String get() = "$trainNumber-${startDate ?: "current"}-${station.code}"
}

/** What to do after a check. */
sealed interface AlertStep {
    /** The train has left the stop before [ArrivalAlert.station], or is minutes away: tell the user. */
    data class Approaching(val eta: LocalDateTime?, val delayMinutes: Int?) : AlertStep
    /** Already at or past the station (e.g. the alert was set late): tell the user. */
    data class Arrived(val at: LocalDateTime?) : AlertStep
    /** Not close yet: look again at [at]. */
    data class CheckAgain(val at: LocalDateTime, val eta: LocalDateTime?, val delayMinutes: Int?) : AlertStep
    /** Can't follow it any more (station not on this run, or long overdue with no news). */
    data class GiveUp(val reason: String) : AlertStep
}

/**
 * Decides, from any site's status, whether to alert now or when to look again. Checks start
 * about [START_BEFORE] ahead of the expected arrival and get closer together as it nears, so
 * a growing delay just pushes the next check later.
 */
object AlertPlanner {

    val START_BEFORE: Duration = Duration.ofMinutes(30)
    /** "About to reach": this close, or the train has left the stop before. */
    private val NEAR: Duration = Duration.ofMinutes(12)
    private val CLOSE_CHECKS: Duration = Duration.ofMinutes(3)
    private val FAR_CHECKS_MAX: Duration = Duration.ofHours(2)
    private val EARLY_LOOK: Duration = Duration.ofHours(3)

    fun next(status: LiveTrainStatus?, stationCode: String, now: LocalDateTime, previousEta: LocalDateTime?): AlertStep {
        if (status == null) {
            // No answer: give up long after it should have arrived.
            if (previousEta != null && now.isAfter(previousEta.plusHours(4))) return AlertStep.GiveUp("No news of the train")
            // An alert set days ahead (e.g. from a PNR): the run isn't on the sites yet, so wait
            // until a few hours before; it will have started by then unless boarding at its origin.
            if (previousEta != null && Duration.between(now, previousEta) > EARLY_LOOK) {
                return AlertStep.CheckAgain(previousEta.minus(EARLY_LOOK), previousEta, null)
            }
            return AlertStep.CheckAgain(now.plusMinutes(5), previousEta, null)
        }
        val index = status.stops.indexOfFirst { it.station.code == stationCode }
        if (index < 0) return AlertStep.GiveUp("The train doesn't stop at $stationCode on this run")
        val stop = status.stops[index]
        val eta = stop.arrival?.best ?: stop.departure?.best
        val delay = stop.arrival?.delayMinutes ?: status.delayMinutes

        if (stop.passed || status.state == RunState.REACHED) {
            return AlertStep.Arrived(stop.arrival?.expected ?: stop.departure?.expected)
        }
        // Every site marks an actual departure the same way.
        val left = status.stops.getOrNull(index - 1)?.departure?.isActual == true
        val untilEta = eta?.let { Duration.between(now, it) }
        if (status.state == RunState.RUNNING && (left || (untilEta != null && untilEta <= NEAR))) {
            return AlertStep.Approaching(eta, delay)
        }
        if (eta == null) return AlertStep.CheckAgain(now.plusMinutes(10), null, delay)

        val checkAt = when {
            untilEta!! <= START_BEFORE + CLOSE_CHECKS -> now.plus(CLOSE_CHECKS)
            // Far off: look again when it's START_BEFORE away, but at least every 2 h, since delays change.
            else -> minOf(eta.minus(START_BEFORE), now.plus(FAR_CHECKS_MAX))
        }
        return AlertStep.CheckAgain(checkAt, eta, delay)
    }
}
