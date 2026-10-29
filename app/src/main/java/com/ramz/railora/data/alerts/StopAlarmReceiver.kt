package com.ramz.railora.data.alerts

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** The alarm notification's Stop button, and swiping it away: silence the alarm. */
class StopAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_NOTIFICATION = "notification"
    }

    override fun onReceive(context: Context, intent: Intent) {
        AlarmSound.stop()
        context.getSystemService(NotificationManager::class.java)?.cancel(intent.getIntExtra(EXTRA_NOTIFICATION, 0))
    }
}
