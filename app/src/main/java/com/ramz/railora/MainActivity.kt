package com.ramz.railora

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
        super.onCreate(savedInstanceState)
        // The home picture runs under the status bar, so its icons stay light.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT))
        setContent {
            RailoraTheme {
                RailoraApp()
            }
        }
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
