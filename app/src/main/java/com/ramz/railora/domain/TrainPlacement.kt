package com.ramz.railora.domain

import com.ramz.railora.domain.model.GeoPoint
import com.ramz.railora.domain.model.LiveStop
import com.ramz.railora.domain.model.LiveTrainStatus
import com.ramz.railora.domain.model.PositionEvent
import com.ramz.railora.domain.model.RunState
import com.ramz.railora.domain.model.TrainRoute
import java.time.Duration
import java.time.LocalDateTime

/** Where the train is now, worked out from any site's [LiveTrainStatus] plus the route. */
data class Placement(
    /** Km from origin now, or null if neither the site nor the route says. */
    val kmNow: Double?,
    val location: GeoPoint?,
    val nextStop: LiveStop?,
    val kmToNextStop: Double?,
)

/**
 * Works the same for every site: km comes from the site when it gives one, else from [route]
 * by station code. Between stations the train is moved on by time towards the next stop's
 * expected arrival, so the marker keeps moving between reports.
 */
object TrainPlacement {

    fun place(status: LiveTrainStatus, route: TrainRoute?, now: LocalDateTime): Placement {
        val routeKm = route?.points?.associate { it.station.code to it.kmFromOrigin }.orEmpty()
        fun km(stop: LiveStop) = stop.kmFromOrigin ?: routeKm[stop.station.code]

        val nextStop = when (status.state) {
            RunState.REACHED -> null
            else -> status.stops.firstOrNull { !it.passed }
        }

        val pos = status.position
        val kmAt = pos?.let { it.kmFromOrigin ?: routeKm[it.station.code] ?: status.stops.firstOrNull { s -> s.station.code == it.station.code }?.let(::km) }
        val kmNow = when {
            status.state == RunState.NOT_STARTED -> status.stops.firstOrNull()?.let(::km) ?: 0.0
            status.state == RunState.REACHED -> status.stops.lastOrNull()?.let(::km)
            kmAt == null -> null
            pos!!.event == PositionEvent.ARRIVED || nextStop == null -> kmAt
            else -> {
                // Moving: advance by time towards the next stop.
                val nextKm = km(nextStop)
                val from = pos.at
                val to = nextStop.arrival?.best
                if (nextKm != null && from != null && to != null && to.isAfter(from) && nextKm > kmAt) {
                    val f = (Duration.between(from, now).seconds.toDouble() / Duration.between(from, to).seconds)
                        .coerceIn(0.0, 0.97)
                    kmAt + (nextKm - kmAt) * f
                } else kmAt
            }
        }

        return Placement(
            kmNow = kmNow,
            location = kmNow?.let { route?.let { r -> locate(r, it) } },
            nextStop = nextStop,
            kmToNextStop = nextStop?.let(::km)?.let { k -> kmNow?.let { (k - it).coerceAtLeast(0.0) } },
        )
    }

    /** The point [km] along the route, between the two located points around it. */
    fun locate(route: TrainRoute, km: Double): GeoPoint? {
        val pts = route.points.filter { it.location != null }
        if (pts.isEmpty()) return null
        val after = pts.indexOfFirst { it.kmFromOrigin >= km }
        if (after <= 0) return pts[if (after == 0) 0 else pts.lastIndex].location
        val a = pts[after - 1]
        val b = pts[after]
        val span = b.kmFromOrigin - a.kmFromOrigin
        val f = if (span <= 0) 0.0 else (km - a.kmFromOrigin) / span
        val la = a.location!!
        val lb = b.location!!
        return GeoPoint(la.lat + (lb.lat - la.lat) * f, la.lng + (lb.lng - la.lng) * f)
    }
}
