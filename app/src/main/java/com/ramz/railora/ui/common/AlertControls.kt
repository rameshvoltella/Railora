package com.ramz.railora.ui.common

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ramz.railora.data.alerts.ArrivalAlerts
import com.ramz.railora.domain.ArrivalAlert
import com.ramz.railora.ui.theme.ToolPurple
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Clock = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private val Day = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

/**
 * Starts arrival alerts the same way everywhere: asks for notification permission first
 * (Android 13+), then, if Android 14+ won't let the alarm pop up full screen, offers the
 * setting. Returns the function to call with an alert.
 */
@Composable
fun rememberAlertStarter(): (ArrivalAlert) -> Unit {
    val context = LocalContext.current
    var waiting by remember { mutableStateOf<ArrivalAlert?>(null) }
    var askPopUp by remember { mutableStateOf(false) }
    fun begin(alert: ArrivalAlert) {
        ArrivalAlerts.start(context, alert)
        Toast.makeText(context, "We'll ring when it's about to reach ${alert.station.name}", Toast.LENGTH_LONG).show()
        if (!ArrivalAlerts.canPopUp(context)) askPopUp = true
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val alert = waiting ?: return@rememberLauncherForActivityResult
        waiting = null
        if (granted) begin(alert)
        else Toast.makeText(context, "Allow notifications for Railora to get arrival alerts", Toast.LENGTH_LONG).show()
    }

    if (askPopUp) {
        AlertDialog(
            onDismissRequest = { askPopUp = false },
            title = { Text("Show the alarm on your screen?") },
            text = {
                Text(
                    "So you don't miss it, Railora can pop up a full-screen alarm, even on the lock screen, " +
                        "when your train is about to arrive. Android asks you to allow this once."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    askPopUp = false
                    context.startActivity(ArrivalAlerts.popUpSettings(context))
                }) { Text("Allow") }
            },
            dismissButton = { TextButton(onClick = { askPopUp = false }) { Text("Not now") } },
        )
    }

    return { alert ->
        val needsPermission = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            waiting = alert
            askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else begin(alert)
    }
}

/** An alert that's on: where, the latest expected time, and a way to turn it off. */
@Composable
fun AlertCard(alert: ArrivalAlert, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = ToolPurple.copy(alpha = 0.10f),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = ToolPurple)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Alert on for ${alert.station.name} (${alert.station.code})", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    listOfNotNull(
                        alert.eta?.let {
                            "Expected " + (if (it.toLocalDate() != LocalDate.now()) "${it.format(Day)}, " else "") + it.format(Clock)
                        },
                        alert.delayMinutes?.let { if (it <= 0) "on time" else "$it min late" },
                        alert.lastChecked?.let { "checked ${it.format(Clock)}" },
                    ).joinToString(" · ").ifEmpty { "Checking the train…" },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onCancel) { Text("Cancel", color = ToolPurple) }
        }
    }
}
