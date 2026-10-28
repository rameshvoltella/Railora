package com.ramz.railora.ui.spot

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val RailPassed = Color(0xFF546E7A)
private val SleeperPassed = Color(0xFF8D6E63)
private val RailAhead = Color(0xFFB0BEC5)
private val SleeperAhead = Color(0xFFD7CCC8)
private val SleeperGap = 7.dp

/**
 * Where the train sits on a piece of track: [at] 0 = at [from] (e.g. the station dot) … 1 = the
 * end of the piece.
 */
data class TrainOnTrack(val at: Float, val moving: Boolean, val from: Dp = 0.dp)

/**
 * A vertical piece of railway (two rails on sleepers) for the station list, from [top] down to
 * [end] (null = the bottom). Track the train has covered is darker. With [train], a top-down
 * train is drawn on it, heading down the list; while it's moving the sleepers slide up under
 * it and it rocks slightly, so it looks like it's running.
 */
@Composable
fun RailTrack(
    modifier: Modifier,
    passed: Boolean,
    top: Dp = 0.dp,
    end: Dp? = null,
    train: TrainOnTrack? = null,
) {
    val moving = train?.moving == true
    val motion = rememberInfiniteTransition(label = "rail")
    val slide by motion.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(450, easing = LinearEasing), RepeatMode.Restart),
        label = "sleepers",
    )
    val rock by motion.animateFloat(
        -1f, 1f,
        infiniteRepeatable(tween(260, easing = LinearEasing), RepeatMode.Reverse),
        label = "rock",
    )

    Canvas(modifier) {
        val y0 = top.toPx()
        val y1 = end?.toPx()?.coerceAtMost(size.height) ?: size.height
        if (y1 <= y0) return@Canvas
        val trainY = train?.let { t -> t.from.toPx() + (y1 - t.from.toPx()) * t.at.coerceIn(0f, 1f) }
        // Covered track runs up to the train when it's on this piece.
        val split = trainY ?: if (passed) y1 else y0
        // Sleepers slide only around a running train; elsewhere the track stands still.
        val offset = if (moving) slide * SleeperGap.toPx() else 0f
        clipRect(top = y0, bottom = split) { drawTrack(y0, split, RailPassed, SleeperPassed, -offset) }
        clipRect(top = split, bottom = y1) { drawTrack(split, y1, RailAhead, SleeperAhead, -offset) }

        if (trainY != null) drawTrain(centerY = trainY, sway = if (moving) rock * 0.6f else 0f)
    }
}

private fun DrawScope.drawTrack(from: Float, to: Float, rail: Color, sleeper: Color, shift: Float) {
    val cx = size.width / 2
    val gauge = 4.dp.toPx()
    val gap = SleeperGap.toPx()
    var y = from - gap + ((shift % gap) + gap) % gap
    while (y < to + gap) {
        drawRoundRect(
            sleeper,
            Offset(cx - gauge - 3.dp.toPx(), y),
            Size((gauge + 3.dp.toPx()) * 2, 2.5.dp.toPx()),
            CornerRadius(1.dp.toPx()),
        )
        y += gap
    }
    val w = 1.6.dp.toPx()
    drawLine(rail, Offset(cx - gauge, from), Offset(cx - gauge, to), strokeWidth = w)
    drawLine(rail, Offset(cx + gauge, from), Offset(cx + gauge, to), strokeWidth = w)
}

/**
 * Seen from above, heading down: the engine (nose and headlight at the bottom) and one coach,
 * red with a cream roof stripe, centred on [centerY].
 */
private fun DrawScope.drawTrain(centerY: Float, sway: Float) {
    val cx = size.width / 2 + sway.dp.toPx()
    val w = 13.dp.toPx()
    val car = 17.dp.toPx()
    val gap = 2.dp.toPx()
    val left = cx - w / 2
    val total = car * 2 + gap
    val top = centerY - total / 2
    val red = Color(0xFFD32F2F)
    val cream = Color(0xFFFFF3C4)
    val shadow = Color.Black.copy(alpha = 0.18f)

    // Coach (behind), then engine (in front, lower).
    drawRoundRect(shadow, Offset(left + 1.dp.toPx(), top + 1.dp.toPx()), Size(w, total), CornerRadius(4.dp.toPx()))
    drawRoundRect(red, Offset(left, top), Size(w, car), CornerRadius(3.dp.toPx()))
    drawRect(cream, Offset(cx - 1.5.dp.toPx(), top + 2.dp.toPx()), Size(3.dp.toPx(), car - 4.dp.toPx()))
    val engineTop = top + car + gap
    drawRoundRect(red, Offset(left, engineTop), Size(w, car), CornerRadius(3.dp.toPx(), 6.dp.toPx()))
    drawRect(Color(0xFF9E9E9E), Offset(left + 2.dp.toPx(), engineTop + 3.dp.toPx()), Size(w - 4.dp.toPx(), 6.dp.toPx()))
    drawLine(Color(0xFF424242), Offset(left + 3.dp.toPx(), engineTop + 6.dp.toPx()), Offset(left + w - 3.dp.toPx(), engineTop + 6.dp.toPx()), strokeWidth = 1.2.dp.toPx())
    drawRect(Color(0xFF1A237E), Offset(left + 2.dp.toPx(), engineTop + car - 5.dp.toPx()), Size(w - 4.dp.toPx(), 2.5.dp.toPx()))
    drawCircle(Color(0xFFFFEB3B), radius = 1.6.dp.toPx(), center = Offset(cx, engineTop + car - 1.2.dp.toPx()))
}
