package com.ramz.railora.data.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ramz.railora.MainActivity
import com.ramz.railora.R
import com.ramz.railora.data.RailData
import com.ramz.railora.domain.AlertPlanner
import com.ramz.railora.domain.AlertStep
import com.ramz.railora.domain.ArrivalAlert
import com.ramz.railora.domain.model.IST
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Turns arrival alerts on and off, and runs their checks in the background (WorkManager). */
object ArrivalAlerts {

    const val EXTRA_TRAIN = "open_train"
    private const val CHANNEL = "arrival_alerts"
    private const val KEY_ID = "alert_id"
    private val CLOCK = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

    fun start(context: Context, alert: ArrivalAlert) {
        RailData.alerts.put(alert)
        schedule(context, alert.id, LocalDateTime.now(IST))   // first check now
    }

    fun cancel(context: Context, id: String) {
        RailData.alerts.remove(id)
        WorkManager.getInstance(context).cancelUniqueWork(work(id))
    }

    internal fun schedule(context: Context, id: String, at: LocalDateTime) {
        val delay = Duration.between(LocalDateTime.now(IST), at).coerceAtLeast(Duration.ZERO)
        val request = OneTimeWorkRequestBuilder<AlertWorker>()
            .setInitialDelay(delay)
            .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(KEY_ID to id))
            .addTag("arrival_alert")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(work(id), ExistingWorkPolicy.REPLACE, request)
    }

    private fun work(id: String) = "arrival-$id"

    internal fun notify(context: Context, alert: ArrivalAlert, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Arrival alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "When your train is about to reach your station"
                }
            )
        }
        val open = PendingIntent.getActivity(
            context,
            alert.id.hashCode(),
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_TRAIN, alert.trainNumber)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification_train)
            .setColor(0xFF1F6FF2.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(alert.id.hashCode(), n)
    }

    internal fun trainLabel(a: ArrivalAlert) = listOfNotNull(a.trainNumber, a.trainName).joinToString(" ")

    internal fun etaText(eta: LocalDateTime?, delay: Int?): String = buildString {
        if (eta != null) append("Expected ${eta.format(CLOCK)}")
        when {
            delay == null -> Unit
            delay <= 0 -> append(if (isEmpty()) "On time" else " (on time)")
            else -> append(if (isEmpty()) "$delay min late" else " ($delay min late)")
        }
    }
}

/** One check of an alert: asks the live-status site, then alerts or books the next check. */
class AlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString("alert_id") ?: return Result.success()
        val alert = RailData.alerts.get(id) ?: return Result.success()   // cancelled
        val now = LocalDateTime.now(IST)
        val status = runCatching {
            RailData.liveStatusOrNtes(RailData.settings.liveSource.value, alert.trainNumber, now, alert.startDate)
        }.getOrNull()
        val station = alert.station

        when (val step = AlertPlanner.next(status, station.code, now, alert.eta)) {
            is AlertStep.Approaching -> {
                ArrivalAlerts.notify(
                    applicationContext, alert,
                    "${ArrivalAlerts.trainLabel(alert)} is about to reach ${station.name}",
                    "${ArrivalAlerts.etaText(step.eta, step.delayMinutes)} at ${station.name} (${station.code}). Get ready to board.",
                )
                RailData.alerts.remove(id)
            }
            is AlertStep.Arrived -> {
                ArrivalAlerts.notify(
                    applicationContext, alert,
                    "${ArrivalAlerts.trainLabel(alert)} has reached ${station.name}",
                    "The train is at or has left ${station.name} (${station.code}).",
                )
                RailData.alerts.remove(id)
            }
            is AlertStep.CheckAgain -> {
                RailData.alerts.put(alert.copy(eta = step.eta ?: alert.eta, delayMinutes = step.delayMinutes ?: alert.delayMinutes, lastChecked = now))
                ArrivalAlerts.schedule(applicationContext, id, step.at)
            }
            is AlertStep.GiveUp -> {
                ArrivalAlerts.notify(applicationContext, alert, "Alert stopped for ${ArrivalAlerts.trainLabel(alert)}", step.reason)
                RailData.alerts.remove(id)
            }
        }
        return Result.success()
    }
}
