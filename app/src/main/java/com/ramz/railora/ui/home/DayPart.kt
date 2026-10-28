package com.ramz.railora.ui.home

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ramz.railora.R
import kotlinx.coroutines.delay
import java.util.Calendar

/** Part of the day, which picks the home greeting and the hero picture of the train. */
enum class DayPart(val greeting: String, @DrawableRes val hero: Int) {
    MORNING("Good Morning", R.drawable.hero_morning),     // 05:00–11:59
    NOON("Good Afternoon", R.drawable.hero_noon),         // 12:00–15:59
    EVENING("Good Evening", R.drawable.hero_evening),     // 16:00–18:59
    NIGHT("Good Night", R.drawable.hero_night);           // 19:00–04:59

    companion object {
        fun at(hour: Int): DayPart = when (hour) {
            in 5..11 -> MORNING
            in 12..15 -> NOON
            in 16..18 -> EVENING
            else -> NIGHT
        }

        fun now(): DayPart = at(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
    }
}

/** The current [DayPart], rechecked every minute so an open screen changes over on its own. */
@Composable
fun rememberDayPart(): DayPart {
    var part by remember { mutableStateOf(DayPart.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            part = DayPart.now()
        }
    }
    return part
}
