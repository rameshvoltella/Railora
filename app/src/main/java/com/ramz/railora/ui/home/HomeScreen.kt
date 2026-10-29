package com.ramz.railora.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AirlineSeatReclineNormal
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.Tram
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ramz.railora.ui.theme.ActionBlue
import com.ramz.railora.ui.theme.ActionBlueEnd
import com.ramz.railora.ui.theme.ActionGreen
import com.ramz.railora.ui.theme.ActionGreenEnd
import com.ramz.railora.ui.theme.ActionPurple
import com.ramz.railora.ui.theme.ActionPurpleEnd
import com.ramz.railora.ui.theme.RailoraTheme
import com.ramz.railora.ui.theme.ToolGreen
import com.ramz.railora.ui.theme.ToolOrange
import com.ramz.railora.ui.theme.ToolPink
import com.ramz.railora.ui.theme.ToolPurple
import com.ramz.railora.ui.theme.ToolRed

/** Where each home shortcut leads; MainActivity routes them. */
enum class HomeAction { PNR, SPOT, BETWEEN_STATIONS, LIVE_STATUS, SCHEDULE, COACH_POSITION, SAVED, ALERTS }

private val HeroHeight = 340.dp
private val SheetOverlap = 32.dp

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    dayPart: DayPart = rememberDayPart(),
    /** Arrival alerts that are on; when any, an Alerts tile shows in Quick Tools. */
    alertCount: Int = 0,
    onAction: (HomeAction) -> Unit = {},
) {
    val scroll = rememberScrollState()
    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scroll)
    ) {
        Hero(dayPart, scrolled = { scroll.value })

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = HeroHeight - SheetOverlap),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
                ActionCard(
                    title = "PNR Status",
                    subtitle = "Check live PNR & berth details",
                    icon = Icons.Rounded.Search,
                    decoration = Icons.Rounded.Train,
                    colors = listOf(ActionBlue, ActionBlueEnd),
                    onClick = { onAction(HomeAction.PNR) },
                )
                Spacer(Modifier.height(10.dp))
                ActionCard(
                    title = "Spot My Train",
                    subtitle = "See live train location",
                    icon = Icons.Rounded.LocationOn,
                    decoration = Icons.Rounded.Map,
                    colors = listOf(ActionGreen, ActionGreenEnd),
                    onClick = { onAction(HomeAction.SPOT) },
                )
                Spacer(Modifier.height(10.dp))
                ActionCard(
                    title = "Trains Between Stations",
                    subtitle = "Find trains & schedule",
                    icon = Icons.Rounded.SwapHoriz,
                    decoration = Icons.Rounded.Tram,
                    colors = listOf(ActionPurple, ActionPurpleEnd),
                    onClick = { onAction(HomeAction.BETWEEN_STATIONS) },
                )

                Text(
                    "Quick Tools",
                    modifier = Modifier.padding(top = 28.dp, bottom = 14.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val tile = Modifier.weight(1f)
                    QuickTool("Live\nStatus", Icons.Rounded.Schedule, ToolOrange, tile) { onAction(HomeAction.LIVE_STATUS) }
                    QuickTool("Train\nSchedule", Icons.Rounded.Train, ToolPink, tile) { onAction(HomeAction.SCHEDULE) }
                    QuickTool("Coach\nPosition", Icons.Rounded.AirlineSeatReclineNormal, ToolPurple, tile) { onAction(HomeAction.COACH_POSITION) }
                    QuickTool("Saved\nTrains", Icons.Rounded.Star, ToolGreen, tile) { onAction(HomeAction.SAVED) }
                }
                // Only while an arrival alert is on: a tile listing them, with how many.
                if (alertCount > 0) {
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val tile = Modifier.weight(1f)
                        QuickTool("Train\nAlerts", Icons.Rounded.NotificationsActive, ToolRed, tile, badge = alertCount) {
                            onAction(HomeAction.ALERTS)
                        }
                        repeat(3) { Spacer(tile) }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/**
 * The time-of-day picture with the greeting over its sky.
 *
 * Parallax: as the page scrolls up by [scrolled] px, the picture moves up at half that speed and
 * zooms in slightly, so the sheet slides over it; the greeting drifts a little faster and fades out.
 * [scrolled] is read only inside graphicsLayer, so scrolling redraws without recomposing.
 */
@Composable
private fun Hero(dayPart: DayPart, scrolled: () -> Int) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(HeroHeight)
            .graphicsLayer {
                val y = scrolled().toFloat()
                translationY = y * 0.5f
                val zoom = 1f + (y / size.height).coerceIn(0f, 1f) * 0.15f
                scaleX = zoom
                scaleY = zoom
            }
    ) {
        Crossfade(dayPart, animationSpec = tween(800), label = "hero") { part ->
            Image(
                painter = painterResource(part.hero),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                // The pictures are wide; keep the engine, which sits right of centre, in frame.
                alignment = BiasAlignment(0.45f, 0f),
            )
        }
        // Darken the sky a little so the white greeting reads on every picture.
        Box(
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent)))
        )

        val textShadow = Shadow(Color.Black.copy(alpha = 0.35f), blurRadius = 8f)
        Row(
            Modifier
                .graphicsLayer {
                    val y = scrolled().toFloat()
                    translationY = -y * 0.25f
                    alpha = 1f - (y / (size.height * 2.5f)).coerceIn(0f, 1f)
                }
                .statusBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 20.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    dayPart.greeting,
                    style = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, shadow = textShadow),
                    color = Color.White,
                )
                Text(
                    "Your journey companion",
                    style = TextStyle(fontSize = 18.sp, shadow = textShadow),
                    color = Color.White.copy(alpha = 0.92f),
                )
            }
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    decoration: ImageVector,
    colors: List<Color>,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .shadow(8.dp, shape, ambientColor = colors[0], spotColor = colors[0])
            .clip(shape)
            .background(Brush.horizontalGradient(colors))
            .clickable(onClick = onClick)
    ) {
        // Faint artwork in the corner, as on the design.
        Icon(
            decoration,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.12f),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = (-36).dp, y = 10.dp)
                .size(72.dp),
        )
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun QuickTool(label: String, icon: ImageVector, tint: Color, modifier: Modifier, badge: Int? = null, onClick: () -> Unit) {
    Box(modifier.aspectRatio(0.88f)) {
        Column(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(18.dp))
                .background(tint.copy(alpha = 0.10f))
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(8.dp))
            Text(
                label,
                textAlign = TextAlign.Center,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        badge?.let {
            Text(
                "$it",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(tint)
                    .padding(horizontal = 7.dp, vertical = 1.dp),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 860)
@Composable
private fun HomeMorningPreview() {
    RailoraTheme { HomeScreen(dayPart = DayPart.MORNING) }
}

@Preview(showBackground = true, heightDp = 860)
@Composable
private fun HomeNightPreview() {
    RailoraTheme(darkTheme = true) { HomeScreen(dayPart = DayPart.NIGHT) }
}
