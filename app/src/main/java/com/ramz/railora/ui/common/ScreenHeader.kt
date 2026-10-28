package com.ramz.railora.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ramz.railora.ui.home.rememberDayPart
import com.ramz.railora.ui.theme.ActionBlue
import com.ramz.railora.ui.theme.ActionBlueEnd

/**
 * Blue header for inner screens: title and subtitle on the left, and the time-of-day train
 * picture on the right fading into the blue. Content below overlaps its bottom edge.
 */
@Composable
fun ScreenHeader(title: String, subtitle: String, height: Dp = 190.dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(Brush.linearGradient(listOf(ActionBlue, ActionBlueEnd)))
    ) {
        Image(
            painter = painterResource(rememberDayPart().hero),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(0.62f),
            contentScale = ContentScale.Crop,
            alignment = BiasAlignment(0.5f, 0.1f),
        )
        // Fade the picture into the blue so the title stays readable.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0.35f to ActionBlue,
                        0.75f to ActionBlue.copy(alpha = 0.35f),
                        1f to Color.Transparent,
                    )
                )
        )
        Column(
            Modifier
                .statusBarsPadding()
                .padding(start = 24.dp, top = 18.dp, end = 24.dp)
        ) {
            Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color.White.copy(alpha = 0.92f), fontSize = 16.sp)
        }
    }
}
