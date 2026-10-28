package com.ramz.railora

import android.graphics.Color as AndroidColor
import android.app.UiModeManager
import android.os.Build
import android.os.Bundle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import com.ramz.railora.data.RailData
import com.ramz.railora.data.settings.ThemeMode
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.ramz.railora.ui.between.TrainsBetweenScreen
import com.ramz.railora.ui.home.HomeAction
import com.ramz.railora.ui.home.HomeScreen
import com.ramz.railora.ui.pnr.PnrScreen
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
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
                RailoraApp()
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
fun RailoraApp() {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    // A train to open in Spot My Train (from another tab).
    var spotRequest by rememberSaveable { mutableStateOf<String?>(null) }
    // Back from any other tab goes Home; back on Home leaves the app (the default).
    BackHandler(enabled = tab != Tab.HOME) { tab = Tab.HOME }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = t == tab,
                        onClick = { tab = t },
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
        when (tab) {
            Tab.HOME -> HomeScreen(content, onAction = { action ->
                when (action) {
                    HomeAction.PNR -> tab = Tab.PNR
                    HomeAction.SPOT, HomeAction.LIVE_STATUS -> tab = Tab.SPOT
                    HomeAction.BETWEEN_STATIONS, HomeAction.SCHEDULE -> tab = Tab.TRAINS
                    else -> Unit
                }
            })
            Tab.TRAINS -> TrainsBetweenScreen(content, onTrainClick = { train ->
                spotRequest = train.number
                tab = Tab.SPOT
            })
            Tab.SPOT -> SpotScreen(content, request = spotRequest, onRequestHandled = { spotRequest = null })
            Tab.SETTINGS -> SettingsScreen(content)
            Tab.PNR -> PnrScreen(content)
            else -> ComingSoon(tab.label, Modifier.padding(inner))
        }
    }
}

@Composable
private fun ComingSoon(name: String, modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("$name — coming soon", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
