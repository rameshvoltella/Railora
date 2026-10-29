package com.ramz.railora.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.ramz.railora.data.RailData
import com.ramz.railora.data.saved.SavedTrain

private val Gold = Color(0xFFF5B301)

/** Star that saves [train] to Saved Trains, or removes it; filled when saved. */
@Composable
fun SaveStar(train: SavedTrain, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    val saved by RailData.saved.trains.collectAsState()
    val on = saved.any { it.number == train.number }
    IconButton(onClick = { RailData.saved.toggle(train) }, modifier = modifier) {
        Icon(
            if (on) Icons.Rounded.Star else Icons.Rounded.StarBorder,
            contentDescription = if (on) "Remove from saved trains" else "Save train",
            tint = if (on) Gold else tint,
        )
    }
}
