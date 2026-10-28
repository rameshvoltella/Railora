package com.ramz.railora.domain

import com.ramz.railora.domain.model.LiveTrainStatus
import com.ramz.railora.domain.model.RoutePoint
import com.ramz.railora.domain.model.TrainRoute
import java.time.LocalDateTime

/** A station the train runs through without stopping, with when it's due there. */
data class PassingStation(val point: RoutePoint, val expected: LocalDateTime?, val passed: Boolean)

/**
 * The stations between halt [stopIndex] and the next halt, from the route (which every site's
 * status can use). [expected] = schedule + the current delay; [passed] = the train is beyond it.
 */
fun stationsBetween(status: LiveTrainStatus, route: TrainRoute?, stopIndex: Int, kmNow: Double?): List<PassingStation> {
    if (route == null || stopIndex !in 0 until status.stops.lastIndex) return emptyList()
    val routeKm = route.points.associate { it.station.code to it.kmFromOrigin }
    val from = status.stops[stopIndex]
    val to = status.stops[stopIndex + 1]
    val kmA = from.kmFromOrigin ?: routeKm[from.station.code] ?: return emptyList()
    val kmB = to.kmFromOrigin ?: routeKm[to.station.code] ?: return emptyList()
    val halts = status.stops.map { it.station.code }.toSet()
    val start = status.startDate?.atStartOfDay()
    val delay = (status.delayMinutes ?: 0).toLong()
    return route.points
        .filter { it.kmFromOrigin > kmA && it.kmFromOrigin < kmB && it.station.code !in halts }
        .map { p ->
            PassingStation(
                point = p,
                expected = if (start != null && p.scheduledMinutes != null) start.plusMinutes(p.scheduledMinutes + delay) else null,
                passed = kmNow != null && p.kmFromOrigin <= kmNow,
            )
        }
}
