package com.ramz.railora.domain.model

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Indian Railways times are IST; every LocalDateTime in the models is IST wall-clock time. */
val IST: ZoneId = ZoneId.of("Asia/Kolkata")

/** A train in a list or search box. */
data class TrainRef(val number: String, val name: String)

/**
 * Where one run of a train is, as reported by a site. Fields the site doesn't give are null;
 * [stops] are halts only, in travel order.
 */
data class LiveTrainStatus(
    val number: String,
    val name: String?,
    val origin: Station?,
    val destination: Station?,
    /** The date this run left its origin. */
    val startDate: LocalDate?,
    val state: RunState,
    /** Minutes late now (negative = early, 0 = on time). */
    val delayMinutes: Int?,
    /** When the site last had news of the train. */
    val lastUpdated: LocalDateTime?,
    val speedKmph: Int?,
    /** Last place the train was reported; null before it starts or after it reaches. */
    val position: TrainPosition?,
    val stops: List<LiveStop>,
)

enum class RunState { NOT_STARTED, RUNNING, REACHED }

/** The last station the train was reported at. It may be one it runs through without stopping. */
data class TrainPosition(
    val station: Station,
    val event: PositionEvent,
    val at: LocalDateTime?,
    /** Km from origin at [at], when the site knows it; otherwise the app works it out from the route. */
    val kmFromOrigin: Double?,
)

enum class PositionEvent { ARRIVED, DEPARTED, PASSED }

data class LiveStop(
    val station: Station,
    val kmFromOrigin: Double?,
    val platform: String?,
    /** Null at the origin. */
    val arrival: EventTime?,
    /** Null at the destination. */
    val departure: EventTime?,
    /** The train has reached (or left) this stop. */
    val passed: Boolean,
)

/** [expected] is the actual time when [isActual], otherwise the site's forecast. */
data class EventTime(val scheduled: LocalDateTime?, val expected: LocalDateTime?, val isActual: Boolean) {
    val delayMinutes: Int?
        get() = if (scheduled != null && expected != null) Duration.between(scheduled, expected).toMinutes().toInt() else null

    /** The best time we have: actual/expected, else scheduled. */
    val best: LocalDateTime? get() = expected ?: scheduled
}

data class GeoPoint(val lat: Double, val lng: Double)

/** Every station a train passes, stopping or not, in order: for drawing and placing it on a map. */
data class TrainRoute(val trainNumber: String, val points: List<RoutePoint>)

/** [scheduledMinutes]: when the train is due here, in minutes after midnight of the run's start date. */
data class RoutePoint(
    val station: Station,
    val kmFromOrigin: Double,
    val location: GeoPoint?,
    val isStop: Boolean,
    val scheduledMinutes: Int? = null,
)
