package com.ramz.railora.ui.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ramz.railora.data.RailData
import com.ramz.railora.data.alerts.ArrivalAlerts
import com.ramz.railora.domain.ArrivalAlert
import com.ramz.railora.ui.common.AlertCard
import com.ramz.railora.ui.common.ScreenHeader
import com.ramz.railora.ui.theme.ActionBlue
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Day = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)

/** Every arrival alert that's on, soonest first; tap a train to see it live, or cancel an alert. */
@Composable
fun AlertsScreen(modifier: Modifier = Modifier, onOpen: (String) -> Unit) {
    val alerts by RailData.alerts.alerts.collectAsState()
    val context = LocalContext.current
    LazyColumn(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Box(Modifier.height(150.dp)) { ScreenHeader("Train Alerts", "Alarms waiting for your trains", height = 150.dp) } }
        if (alerts.isEmpty()) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Rounded.NotificationsNone, contentDescription = null, tint = ActionBlue, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("No alerts on", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Use Set Alert in Spot My Train, or Arrival alert on a PNR, to get an alarm before your train arrives.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(alerts.sortedBy { it.eta }, key = { it.id }) { a ->
            AlertRow(a, onOpen = { onOpen(a.trainNumber) }, onCancel = { ArrivalAlerts.cancel(context, a.id) })
        }
    }
}

@Composable
private fun AlertRow(alert: ArrivalAlert, onOpen: () -> Unit, onCancel: () -> Unit) {
    Surface(
        onClick = onOpen,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.padding(start = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    alert.trainNumber,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(ActionBlue)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(alert.trainName ?: "Train ${alert.trainNumber}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    alert.startDate?.let {
                        Text("Run that started ${it.format(Day)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Open train", tint = ActionBlue)
            }
            AlertCard(alert, onCancel = onCancel)
        }
    }
}
