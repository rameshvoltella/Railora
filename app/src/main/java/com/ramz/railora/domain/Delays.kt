package com.ramz.railora.domain

import com.ramz.railora.domain.model.LiveStop

/**
 * Delay now, for sites that don't state one: the next forecast's delay (the site's current
 * estimate), else the latest actual time's. The last actual can be hours old on long runs
 * between halts, so the forecast comes first.
 */
fun delayFromStops(stops: List<LiveStop>): Int? {
    val times = stops.flatMap { listOfNotNull(it.arrival, it.departure) }
    return times.firstOrNull { !it.isActual && it.delayMinutes != null }?.delayMinutes
        ?: times.lastOrNull { it.isActual && it.delayMinutes != null }?.delayMinutes
}
