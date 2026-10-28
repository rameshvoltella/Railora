package com.ramz.railora.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ramz.railora.data.RailData
import com.ramz.railora.data.settings.AppSettings
import com.ramz.railora.data.settings.LiveSource
import com.ramz.railora.data.settings.PnrSite
import com.ramz.railora.data.settings.ThemeMode
import com.ramz.railora.ui.theme.ToolPurple
import com.ramz.railora.ui.common.ScreenHeader
import com.ramz.railora.ui.theme.ActionBlue
import com.ramz.railora.ui.theme.ActionGreen

/** What each site is like, in plain words, so people can choose. */
private fun LiveSource.description(): String = when (this) {
    LiveSource.WHERE_IS_MY_TRAIN ->
        "Usually the most accurate position and delay, with platform numbers. Best for most trains."
    LiveSource.NTES ->
        "Official Indian Railways status. Updates when the train reports at a station, so it can lag " +
            "a few minutes behind between stations. Slowest to load."
    LiveSource.RAILYATRI ->
        "Quick to load, and shows the distance covered. Some positions are RailYatri's own estimates, " +
            "so they can be a little off."
}

private fun ThemeMode.description(): String = when (this) {
    ThemeMode.SYSTEM -> "Light or dark, the same as the rest of your phone. Changes when your phone does."
    ThemeMode.LIGHT -> "Always light, with white backgrounds."
    ThemeMode.DARK -> "Always dark. Easier on the eyes at night and saves battery on OLED screens."
}

private fun PnrSite.description(): String = when (this) {
    PnrSite.CONFIRMTKT ->
        "Fast, with coach and berth for each passenger and the chance a waitlisted ticket gets confirmed."
    PnrSite.RAILMITRA ->
        "Another source for the same railway data. Try it if ConfirmTkt doesn't answer."
}

@Composable
fun SettingsScreen(modifier: Modifier = Modifier, settings: AppSettings = RailData.settings) {
    val liveSource by settings.liveSource.collectAsState()
    val pnrSite by settings.pnrSite.collectAsState()
    val themeMode by settings.themeMode.collectAsState()

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        Box {
            ScreenHeader("Settings", "Choose how Railora works for you", height = 170.dp)
            Column(
                Modifier.padding(top = 128.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ChoiceSetting(
                    title = "Appearance",
                    icon = Icons.Rounded.DarkMode,
                    tint = ToolPurple,
                    intro = "Choose how Railora looks. System default follows your phone's dark mode setting.",
                    options = ThemeMode.entries,
                    selected = themeMode,
                    label = { it.label },
                    description = { it.description() },
                    recommended = null,
                    onSelect = settings::setThemeMode,
                    summary = themeMode.label,
                )
                ChoiceSetting(
                    title = "Spot My Train",
                    icon = Icons.Rounded.LocationOn,
                    tint = ActionGreen,
                    intro = "Spot My Train asks one website where your train is. Pick the one you trust most. " +
                        "If a train doesn't load or looks wrong, try another.",
                    options = LiveSource.entries,
                    selected = liveSource,
                    label = { it.label },
                    description = { it.description() },
                    recommended = LiveSource.WHERE_IS_MY_TRAIN,
                    onSelect = settings::setLiveSource,
                    footnote = "The map always uses RailYatri's route, whichever site you pick.",
                )
                ChoiceSetting(
                    title = "PNR Status",
                    icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                    tint = ActionBlue,
                    intro = "PNR Status asks one website for your ticket's status. Both read the same " +
                        "Indian Railways data; if one doesn't answer, try the other.",
                    options = PnrSite.entries,
                    selected = pnrSite,
                    label = { it.label },
                    description = { it.description() },
                    recommended = PnrSite.CONFIRMTKT,
                    onSelect = settings::setPnrSite,
                    footnote = "Indian Railways' PNR system is down every night for about an hour from 11:30 pm.",
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * A setting that shows the current choice; tap to open the options, each with what it's like,
 * and pick one.
 */
@Composable
private fun <T> ChoiceSetting(
    title: String,
    icon: ImageVector,
    tint: Color,
    intro: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    description: (T) -> String,
    recommended: T?,
    onSelect: (T) -> Unit,
    footnote: String? = null,
    /** What the collapsed row says about the current choice. */
    summary: String = "Data from ${label(selected)}",
) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { open = !open }
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(tint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, contentDescription = null, tint = tint) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(summary, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(
                    if (open) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (open) "Hide choices" else "Show choices",
                )
            }

            AnimatedVisibility(open) {
                Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 18.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Text(
                        intro,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 14.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        options.forEach { o ->
                            Option(label(o), description(o), selected = o == selected, recommended = o == recommended) { onSelect(o) }
                        }
                    }
                    footnote?.let {
                        Text(
                            it,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Option(label: String, description: String, selected: Boolean, recommended: Boolean, onSelect: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) ActionBlue else MaterialTheme.colorScheme.outlineVariant,
                shape,
            )
            .background(if (selected) ActionBlue.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onSelect)
            .padding(start = 4.dp, end = 14.dp, top = 10.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, onClick = onSelect, colors = RadioButtonDefaults.colors(selectedColor = ActionBlue))
        Column(Modifier.padding(top = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                if (recommended) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Recommended",
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ActionGreen.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        color = ActionGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
