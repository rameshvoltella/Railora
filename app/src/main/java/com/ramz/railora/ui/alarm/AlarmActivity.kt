package com.ramz.railora.ui.alarm

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ramz.railora.MainActivity
import com.ramz.railora.R
import com.ramz.railora.data.alerts.AlarmSound
import com.ramz.railora.data.alerts.ArrivalAlerts
import com.ramz.railora.ui.theme.ActionBlue
import com.ramz.railora.ui.theme.ActionBlueEnd

/**
 * Full-screen "your train is approaching" pop-up, shown by the alert notification even over
 * the lock screen with the screen off. The alarm sound plays meanwhile; Stop or Open train
 * ends it.
 */
class AlarmActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TEXT = "text"
        private const val EXTRA_NOTIFICATION = "notification"

        fun intent(context: Context, title: String, text: String, train: String, notificationId: Int): Intent =
            Intent(context, AlarmActivity::class.java)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_TEXT, text)
                .putExtra(ArrivalAlerts.EXTRA_TRAIN, train)
                .putExtra(EXTRA_NOTIFICATION, notificationId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
        val train = intent.getStringExtra(ArrivalAlerts.EXTRA_TRAIN)

        setContent {
            val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
                0.94f, 1.06f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "scale",
            )
            Column(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(ActionBlue, ActionBlueEnd, Color(0xFF0B2A66))))
                    .safeDrawingPadding()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Train approaching", color = Color.White.copy(alpha = 0.85f), fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(28.dp))
                Image(
                    painterResource(R.drawable.map_train),
                    contentDescription = null,
                    modifier = Modifier
                        .width(220.dp)
                        .scale(pulse),
                )
                Spacer(Modifier.height(28.dp))
                Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Text(text, color = Color.White.copy(alpha = 0.9f), fontSize = 17.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(48.dp))
                Button(
                    onClick = { finishAlarm(openTrain = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = ActionBlue),
                ) { Text("Stop", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { finishAlarm(openTrain = train) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                ) { Text("Open train", fontSize = 17.sp) }
            }
        }
    }

    private fun finishAlarm(openTrain: String?) {
        AlarmSound.stop()
        getSystemService(NotificationManager::class.java)?.cancel(intent.getIntExtra(EXTRA_NOTIFICATION, 0))
        if (openTrain != null) {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra(ArrivalAlerts.EXTRA_TRAIN, openTrain)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }
        finish()
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= 27) {
            // Over the lock screen without unlocking it: no PIN needed just to stop the alarm.
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
