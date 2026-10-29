package com.ramz.railora

import android.app.UiModeManager
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.ramz.railora.data.RailData
import com.ramz.railora.data.alerts.ArrivalAlerts
import com.ramz.railora.data.settings.ThemeMode
import com.ramz.railora.ui.alerts.AlertsScreen
import com.ramz.railora.ui.between.TrainsBetweenScreen
import com.ramz.railora.ui.coach.CoachScreen
import com.ramz.railora.ui.home.HomeAction
import com.ramz.railora.ui.home.HomeScreen
import com.ramz.railora.ui.pnr.PnrScreen
import com.ramz.railora.ui.saved.SavedScreen
import com.ramz.railora.ui.settings.SettingsScreen
import com.ramz.railora.ui.spot.SpotScreen
import com.ramz.railora.ui.theme.RailoraTheme

enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Rounded.Home),
    PNR("PNR", Icons.AutoMirrored.Rounded.ReceiptLong),
    SPOT("Spot", Icons.Rounded.LocationOn),
    TRAINS("Trains", Icons.Rounded.Train),
    SETTINGS("Settings", Icons.Rounded.Settings),
}

/** Pages opened from Home rather than from the bottom bar. */
enum class Page { SAVED, COACH, ALERTS }

class MainActivity : ComponentActivity() {

    /** A train to open in Spot My Train, from an arrival alert notification. */
    private val openTrain = mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(ArrivalAlerts.EXTRA_TRAIN)?.let { openTrain.value = it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) openTrain.value = intent.getStringExtra(ArrivalAlerts.EXTRA_TRAIN)
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            val mode by RailData.settings.themeMode.collectAsState()
            val dark = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            LaunchedEffect(mode, dark) {
                // Every screen's header runs under the status bar, so its icons stay light;
                // the navigation bar's icons follow the app's light/dark choice.
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
                )
                applyNightModeToSystem(mode)
            }
            RailoraTheme(darkTheme = dark) {
                RailoraApp(openTrain = openTrain.value, onOpened = { openTrain.value = null })
            }
        }
    }

    /**
     * Android 12+: tell the system the app's choice too, so the splash screen (drawn before
     * the app runs) uses the same light or dark background next time.
     */
    private fun applyNightModeToSystem(mode: ThemeMode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        getSystemService(UiModeManager::class.java)?.setApplicationNightMode(
            when (mode) {
                ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
                ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
                ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
            }
        )
    }
}

@Composable
fun RailoraApp(openTrain: String? = null, onOpened: () -> Unit = {}) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    // A page opened from Home's Quick Tools, shown over the Home tab.
    var page by rememberSaveable { mutableStateOf<Page?>(null) }
    // A train to open in Spot My Train (from another screen, or a notification).
    var spotRequest by rememberSaveable { mutableStateOf<String?>(null) }
    // Where links inside screens came from ("TAB/PAGE", newest last), so back returns there,
    // e.g. a train tapped in Trains Between Stations → Spot My Train → back to the list.
    var history by rememberSaveable { mutableStateOf(listOf<String>()) }
    val alerts by RailData.alerts.alerts.collectAsState()
    // Keeps each screen's scroll position (and other saved UI state) while it's not shown.
    val screens = rememberSaveableStateHolder()

    fun go(toTab: Tab, toPage: Page? = null) {
        history = history + "${tab.name}/${page?.name.orEmpty()}"
        tab = toTab
        page = toPage
    }

    LaunchedEffect(openTrain) {
        if (openTrain != null) {
            spotRequest = openTrain
            tab = Tab.SPOT
            page = null
            history = emptyList()
            onOpened()
        }
    }
    // Back: to the screen a link came from, else close a page, else from any tab go Home;
    // back on Home leaves the app (the default).
    BackHandler(enabled = history.isNotEmpty() || page != null || tab != Tab.HOME) {
        val last = history.lastOrNull()
        when {
            last != null -> {
                history = history.dropLast(1)
                tab = Tab.valueOf(last.substringBefore('/'))
                page = last.substringAfter('/').takeIf { it.isNotEmpty() }?.let(Page::valueOf)
            }
            page != null -> page = null
            else -> tab = Tab.HOME
        }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = t == tab,
                        onClick = { tab = t; page = null; history = emptyList() },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        ),
                    )
                }
            }
        },
    ) { inner ->
        // Home draws under the status bar itself; only keep clear of the bottom bar.
        val content = Modifier.padding(bottom = inner.calculateBottomPadding())
        screens.SaveableStateProvider(page?.name ?: tab.name) {
            when {
                page == Page.SAVED -> SavedScreen(content, onOpen = { number ->
                    spotRequest = number
                    go(Tab.SPOT)
                })
                page == Page.COACH -> CoachScreen(content)
                page == Page.ALERTS -> AlertsScreen(content, onOpen = { number ->
                    spotRequest = number
                    go(Tab.SPOT)
                })
                else -> when (tab) {
                    Tab.HOME -> HomeScreen(content, alertCount = alerts.size, onAction = { action ->
                        when (action) {
                            HomeAction.PNR -> go(Tab.PNR)
                            HomeAction.SPOT, HomeAction.LIVE_STATUS -> go(Tab.SPOT)
                            HomeAction.BETWEEN_STATIONS, HomeAction.SCHEDULE -> go(Tab.TRAINS)
                            HomeAction.SAVED -> go(Tab.HOME, Page.SAVED)
                            HomeAction.COACH_POSITION -> go(Tab.HOME, Page.COACH)
                            HomeAction.ALERTS -> go(Tab.HOME, Page.ALERTS)
                        }
                    })
                    Tab.TRAINS -> TrainsBetweenScreen(content, onTrainClick = { train ->
                        spotRequest = train.number
                        go(Tab.SPOT)
                    })
                    Tab.SPOT -> SpotScreen(content, request = spotRequest, onRequestHandled = { spotRequest = null })
                    Tab.SETTINGS -> SettingsScreen(content)
                    Tab.PNR -> PnrScreen(content)
                }
            }
        }
    }
}
