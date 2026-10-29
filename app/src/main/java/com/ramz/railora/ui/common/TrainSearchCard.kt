package com.ramz.railora.ui.common

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ramz.railora.domain.model.TrainRef
import com.ramz.railora.ui.theme.ActionBlue

/**
 * "Enter train number or name" box with a button, suggesting trains from [trains] as you type.
 * Used by Spot My Train and Coach Position.
 */
@Composable
fun TrainSearchCard(
    query: String,
    trains: List<TrainRef>,
    tracked: String?,
    onQuery: (String) -> Unit,
    onTrack: (String) -> Unit,
    modifier: Modifier,
    /** The button's word: "Track", "Show"… */
    action: String = "Track",
) {
    val focus = LocalFocusManager.current
    var typing by remember { mutableStateOf(false) }
    val suggestions = remember(query, trains) { suggest(trains, query) }
    fun go(text: String) {
        typing = false
        focus.clearFocus()
        onTrack(text)
    }

    Surface(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier
                        .weight(1f)
                        .height(56.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { onQuery(it); typing = true },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(ActionBlue),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { go(query) }),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (query.isEmpty()) Text("Enter train number or name", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            inner()
                        },
                    )
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQuery(""); typing = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = { go(query) },
                    modifier = Modifier.height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ActionBlue, contentColor = Color.White),
                ) { Text(action, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
            }
            if (typing && suggestions.isNotEmpty() && !query.startsWith(tracked ?: "-")) {
                Column(Modifier.padding(top = 6.dp)) {
                    suggestions.forEach { t ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { go(t.number) }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                        ) {
                            Text(t.number, fontWeight = FontWeight.Bold, modifier = Modifier.width(64.dp))
                            Text(t.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

private fun suggest(trains: List<TrainRef>, query: String): List<TrainRef> {
    val q = query.trim()
    if (q.length < 2) return emptyList()
    return if (q.first().isDigit()) trains.filter { it.number.startsWith(q) }.take(6)
    else (trains.filter { it.name.startsWith(q, true) } + trains.filter { it.name.contains(q, true) }).distinct().take(6)
}
