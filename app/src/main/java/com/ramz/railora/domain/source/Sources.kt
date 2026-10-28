package com.ramz.railora.domain.source

import com.ramz.railora.domain.model.CoachPosition
import com.ramz.railora.domain.model.LiveTrainStatus
import com.ramz.railora.domain.model.Station
import com.ramz.railora.domain.model.TrainBetween
import com.ramz.railora.domain.model.TrainRef
import com.ramz.railora.domain.model.TrainRoute
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * What the app needs from a site, in the app's own models. Each site implements these in
 * `data/<site>/`; `data/RailData` picks which implementation the app uses.
 */
interface TrainsBetweenSource {
    /**
     * Trains from [fromCode] to [toCode]. [date] is a hint: sites that know dates may use it,
     * others (NTES) return every train and the caller filters with [TrainBetween.runsOn].
     */
    suspend fun trainsBetween(fromCode: String, toCode: String, date: LocalDate): List<TrainBetween>
}

interface StationSource {
    /** Every station the site knows, for search. */
    suspend fun allStations(): List<Station>
}

interface TrainListSource {
    /** Every train the site knows, for search. */
    suspend fun allTrains(): List<TrainRef>
}

interface LiveStatusSource {
    /**
     * Where one run of the train is. [startDate] picks the run by the day it left its origin
     * (long journeys have several runs on the way at once); null = the current run: the one on
     * its way now, else the next to start, else the last one. Null if the site doesn't know the
     * train, or can't show that run. [now] is IST.
     */
    suspend fun liveStatus(trainNumber: String, now: LocalDateTime, startDate: LocalDate? = null): LiveTrainStatus?
}

interface CoachPositionSource {
    /** The train's coach order; null if the site doesn't have it. */
    suspend fun coachPosition(trainNumber: String): CoachPosition?
}

interface TrainRouteSource {
    /** The train's full path with map positions; null if unknown. */
    suspend fun route(trainNumber: String): TrainRoute?
}
