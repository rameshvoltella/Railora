package com.ramz.railora.data

import android.content.Context
import com.ramz.railora.data.easemytrip.EaseMyTripCoaches
import com.ramz.railora.data.ntes.NtesLiveStatus
import com.ramz.railora.data.ntes.NtesSession
import com.ramz.railora.data.ntes.NtesStations
import com.ramz.railora.data.ntes.NtesTrainList
import com.ramz.railora.data.ntes.NtesTrainsBetween
import com.ramz.railora.data.railyatri.RailYatri
import com.ramz.railora.data.settings.AppSettings
import com.ramz.railora.data.settings.LiveSource
import com.ramz.railora.data.wimt.WhereIsMyTrain
import com.ramz.railora.domain.source.CoachPositionSource
import com.ramz.railora.domain.source.LiveStatusSource
import com.ramz.railora.domain.source.StationSource
import com.ramz.railora.domain.source.TrainListSource
import com.ramz.railora.domain.source.TrainRouteSource
import com.ramz.railora.domain.source.TrainsBetweenSource
import java.io.File

/**
 * The one place that decides which site backs each feature. To move a feature to another site,
 * write that site's implementation of the source interface and change the line here.
 */
object RailData {

    private lateinit var appContext: Context
    private val ntes by lazy { NtesSession() }
    private val railYatri by lazy { RailYatri() }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val settings: AppSettings by lazy { AppSettings(appContext) }

    val trainsBetween: TrainsBetweenSource by lazy { NtesTrainsBetween(ntes) }

    val stations: StationSource by lazy { NtesStations(ntes, File(appContext.cacheDir, "ntes_stations.js")) }

    val trains: TrainListSource by lazy { NtesTrainList(ntes, File(appContext.cacheDir, "ntes_trains.js")) }

    /** Map positions for every station, whichever site gives the live status. */
    val routes: TrainRouteSource get() = railYatri

    val coaches: CoachPositionSource by lazy { EaseMyTripCoaches() }

    /** Spot My Train: the site the user picked in Settings. */
    fun liveStatus(source: LiveSource): LiveStatusSource = when (source) {
        LiveSource.WHERE_IS_MY_TRAIN -> wimt
        LiveSource.NTES -> ntesLive
        LiveSource.RAILYATRI -> railYatri
    }

    private val wimt by lazy { WhereIsMyTrain() }
    private val ntesLive by lazy { NtesLiveStatus(ntes) }
}
