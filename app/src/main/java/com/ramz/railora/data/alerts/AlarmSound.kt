package com.ramz.railora.data.alerts

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.ramz.railora.R

/**
 * The "train approaching" alarm: the app's sound (`res/raw/train_alarm.mp3`; the phone's alarm
 * tone if that can't play), on the alarm stream (so it sounds even with media muted), with
 * vibration, for [PLAY_MS], then stops by itself. [stop] ends it early (Stop button, pop-up).
 */
object AlarmSound {

    const val PLAY_MS = 15_000L

    private val main = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val stopLater = Runnable { stop() }

    fun play(context: Context) = main.post {
        stop()
        val app = context.applicationContext
        fun start(uri: Uri) = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setDataSource(app, uri)
            isLooping = true
            prepare()
            start()
        }
        player = runCatching { start(soundUri(app)) }
            .recoverCatching { start(fallbackUri(app)) }
            .getOrNull()

        vibrator = (if (Build.VERSION.SDK_INT >= 31) {
            app.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION") app.getSystemService(Vibrator::class.java)
        })?.also { v ->
            val pattern = longArrayOf(0, 700, 500)
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(pattern, 0))
            else @Suppress("DEPRECATION") v.vibrate(pattern, 0)
        }
        main.postDelayed(stopLater, PLAY_MS)
    }

    fun stop() {
        main.removeCallbacks(stopLater)
        player?.runCatching { stop(); release() }
        player = null
        vibrator?.cancel()
        vibrator = null
    }

    /** The app's own alarm (`res/raw/train_alarm.mp3`). */
    private fun soundUri(context: Context): Uri =
        Uri.parse("android.resource://${context.packageName}/${R.raw.train_alarm}")

    /** The phone's alarm tone (or ringtone), if the app's sound can't be played. */
    private fun fallbackUri(context: Context): Uri =
        RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
}
