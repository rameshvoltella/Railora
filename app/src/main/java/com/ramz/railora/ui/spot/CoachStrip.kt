package com.ramz.railora.ui.spot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.ramz.railora.domain.model.Coach
import com.ramz.railora.domain.model.CoachKind
import com.ramz.railora.domain.model.CoachPosition

/** One colour per kind of coach: the label tab and the body share it, and the legend explains it. */
private fun CoachKind.color(): Color = when (this) {
    CoachKind.ENGINE -> Color(0xFFD32F2F)
    CoachKind.GENERATOR -> Color(0xFF455A64)
    CoachKind.LUGGAGE -> Color(0xFF78909C)
    CoachKind.AC_FIRST -> Color(0xFFB8860B)
    CoachKind.AC_TWO -> Color(0xFF7B1FA2)
    CoachKind.AC_THREE -> Color(0xFF1565C0)
    CoachKind.AC_THREE_ECONOMY -> Color(0xFF00897B)
    CoachKind.EXECUTIVE_CHAIR -> Color(0xFFAD1457)
    CoachKind.CHAIR_CAR -> Color(0xFF0288D1)
    CoachKind.SLEEPER -> Color(0xFF2E7D32)
    CoachKind.SECOND_SITTING -> Color(0xFF6D4C41)
    CoachKind.GENERAL -> Color(0xFFEF6C00)
    CoachKind.PANTRY -> Color(0xFFF9A825)
    CoachKind.DIVYANGJAN -> Color(0xFF5E35B1)
    CoachKind.OTHER -> Color(0xFF9E9E9E)
}

/**
 * Coach order as a strip, engine on the left, like EaseMyTrip shows it: a label tab over each
 * coach and its number from the engine. Tapping a coach with a seat layout shows the layout.
 */
@Composable
fun CoachCard(
    coaches: CoachPosition,
    modifier: Modifier = Modifier,
    /** Coach codes to mark as the user's ("S4"), e.g. from a PNR. */
    yours: Set<String> = emptySet(),
    /** Coaches of this kind get a lighter mark, e.g. the ticket's class while it's waitlisted. */
    yourClass: CoachKind? = null,
) {
    var layoutOf by remember { mutableStateOf<Coach?>(null) }
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    // Bring the first marked coach into view.
    LaunchedEffect(coaches, yours, yourClass) {
        val i = coaches.coaches.indexOfFirst { it.code in yours }.takeIf { it >= 0 }
            ?: coaches.coaches.indexOfFirst { it.kind == yourClass }.takeIf { it >= 0 } ?: return@LaunchedEffect
        scroll.animateScrollTo(with(density) { ((i - 1).coerceAtLeast(0) * 71).dp.roundToPx() })
    }
    Surface(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.padding(vertical = 16.dp)) {
            Text(
                "Coach Position",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Text(
                "${coaches.coaches.size - 1} coaches · engine first · tap a coach for its seat layout",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .horizontalScroll(scroll)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                coaches.coaches.forEach { c ->
                    CoachItem(
                        c,
                        mark = when {
                            c.code in yours -> Mark.YOURS
                            yourClass != null && c.kind == yourClass -> Mark.CLASS
                            else -> Mark.NONE
                        },
                        onClick = { if (c.layoutImageUrl != null) layoutOf = c },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            FlowRow(
                Modifier.padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                coaches.coaches.map { it.kind }.distinct().forEach { kind ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(kind.color())
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(kind.label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text(
                "Coach order from EaseMyTrip. It can change on the day, so check the display board at the station.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp),
            )
        }
    }

    layoutOf?.let { coach ->
        Dialog(onDismissRequest = { layoutOf = null }) {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(16.dp)) {
                    Text("${coach.code} · ${coach.kind.label}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(460.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        AsyncImage(
                            model = coach.layoutImageUrl,
                            contentDescription = "Seat layout of ${coach.code}",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    TextButton(onClick = { layoutOf = null }, modifier = Modifier.align(Alignment.End)) { Text("Close") }
                }
            }
        }
    }
}

private enum class Mark { NONE, CLASS, YOURS }

@Composable
private fun CoachItem(coach: Coach, mark: Mark, onClick: () -> Unit) {
    val color = coach.kind.color()
    Column(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .then(
                when (mark) {
                    Mark.YOURS -> Modifier.border(2.dp, Color(0xFFF5B301), RoundedCornerShape(8.dp))
                    Mark.CLASS -> Modifier.border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    Mark.NONE -> Modifier
                }
            )
            .clickable(onClick = onClick)
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (mark == Mark.YOURS) "You" else "",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFE6A100),
        )
        Text(
            if (coach.kind == CoachKind.ENGINE) "Engine" else coach.code,
            modifier = Modifier
                .width(if (coach.kind == CoachKind.ENGINE) 84.dp else 64.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(color)
                .padding(vertical = 5.dp),
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Canvas(
            Modifier
                .width(if (coach.kind == CoachKind.ENGINE) 84.dp else 64.dp)
                .height(34.dp)
        ) {
            if (coach.kind == CoachKind.ENGINE) drawEngine(color) else drawCoach(color)
        }
        Text(
            if (coach.position == 0) "" else coach.position.toString(),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A coach side-on: roof, coloured body with windows, a pale stripe, and two bogies. */
private fun DrawScope.drawCoach(body: Color) {
    val w = size.width
    val h = size.height
    val bodyTop = h * 0.12f
    val bodyH = h * 0.66f
    drawRoundRect(Color(0xFFB0B7C0), Offset(w * 0.02f, 0f), Size(w * 0.96f, bodyTop + 4f), CornerRadius(6f))
    drawRoundRect(body, Offset(0f, bodyTop), Size(w, bodyH), CornerRadius(5f))
    drawRect(Color.White.copy(alpha = 0.55f), Offset(0f, bodyTop + bodyH * 0.68f), Size(w, bodyH * 0.1f))
    val winW = w * 0.11f
    val gap = (w - 6 * winW) / 7
    repeat(6) { i ->
        drawRoundRect(
            Color(0xFFE3F2FD),
            Offset(gap + i * (winW + gap), bodyTop + bodyH * 0.18f),
            Size(winW, bodyH * 0.34f),
            CornerRadius(2f),
        )
    }
    drawBogies(w, bodyTop + bodyH, h)
}

/** The engine, nose to the left: sloped cab front, headlight, cream band and pantograph. */
private fun DrawScope.drawEngine(body: Color) {
    val w = size.width
    val h = size.height
    val bodyTop = h * 0.14f
    val bodyBottom = h * 0.78f
    // Pantograph.
    drawLine(Color(0xFF424242), Offset(w * 0.45f, bodyTop), Offset(w * 0.55f, 1f), strokeWidth = 2f)
    drawLine(Color(0xFF424242), Offset(w * 0.65f, bodyTop), Offset(w * 0.55f, 1f), strokeWidth = 2f)
    drawLine(Color(0xFF424242), Offset(w * 0.47f, 1f), Offset(w * 0.63f, 1f), strokeWidth = 2.5f)
    val shape = Path().apply {
        moveTo(w * 0.14f, bodyTop)
        lineTo(w, bodyTop)
        lineTo(w, bodyBottom)
        lineTo(0f, bodyBottom)
        lineTo(0f, bodyTop + (bodyBottom - bodyTop) * 0.45f)
        close()
    }
    drawPath(shape, body)
    drawRect(Color(0xFFFFF3C4), Offset(0f, bodyTop + (bodyBottom - bodyTop) * 0.62f), Size(w, (bodyBottom - bodyTop) * 0.14f))
    // Cab window and side windows.
    drawRoundRect(Color(0xFF1A237E), Offset(w * 0.1f, bodyTop + 4f), Size(w * 0.14f, (bodyBottom - bodyTop) * 0.32f), CornerRadius(2f))
    repeat(3) { i ->
        drawRoundRect(
            Color(0xFF1A237E),
            Offset(w * (0.4f + i * 0.16f), bodyTop + 4f),
            Size(w * 0.09f, (bodyBottom - bodyTop) * 0.28f),
            CornerRadius(2f),
        )
    }
    drawCircle(Color(0xFFFFEB3B), radius = 3f, center = Offset(w * 0.04f, bodyTop + (bodyBottom - bodyTop) * 0.55f))
    drawBogies(w, bodyBottom, h)
}

private fun DrawScope.drawBogies(w: Float, top: Float, h: Float) {
    val dark = Color(0xFF37474F)
    listOf(0.12f, 0.62f).forEach { x ->
        drawRect(dark, Offset(w * x, top), Size(w * 0.26f, (h - top) * 0.45f))
        drawCircle(Color(0xFF212121), radius = (h - top) * 0.32f, center = Offset(w * (x + 0.06f), h - (h - top) * 0.32f))
        drawCircle(Color(0xFF212121), radius = (h - top) * 0.32f, center = Offset(w * (x + 0.2f), h - (h - top) * 0.32f))
    }
}
