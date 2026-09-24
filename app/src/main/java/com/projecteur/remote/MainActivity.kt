package com.projecteur.remote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.projecteur.remote.ui.screens.BluetoothScreen
import com.projecteur.remote.ui.screens.ConfigScreen
import com.projecteur.remote.ui.screens.DiagnosticScreen
import com.projecteur.remote.ui.screens.DiscoveryScreen
import com.projecteur.remote.ui.screens.IrScreen
import com.projecteur.remote.ui.screens.LearnScreen
import com.projecteur.remote.ui.screens.NetworkScreen
import com.projecteur.remote.ui.screens.RemoteScreen
import com.projecteur.remote.ui.theme.ProjecteurTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = (application as ProjecteurApp).container
        setContent { ProjecteurTheme { AppNavigation(app) } }
    }

    override fun onResume() {
        super.onResume()
        // Un accessoire USB a pu être branché pendant que l'application était en arrière-plan.
        (application as ProjecteurApp).container.let { it.irHardware.refresh(); it.network.refresh() }
    }
}

private val titles = mapOf(
    "remote" to "Télécommande",
    "discovery" to "Rechercher le vidéoprojecteur",
    "config" to "Configuration",
    "diagnostic" to "🧪 Diagnostic",
    "ir" to "🔴 Infrarouge",
    "learn" to "📡 Apprendre une commande IR",
    "bluetooth" to "🔵 Bluetooth",
    "network" to "Réseau local",
)
private val tabs = listOf("remote" to "🎛", "discovery" to "🔎", "config" to "⚙", "diagnostic" to "🧪")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppNavigation(app: AppContainer) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "remote"
    val navigate: (String) -> Unit = { target ->
        if (tabs.any { it.first == target }) {
            nav.navigate(target) {
                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        } else nav.navigate(target) { launchSingleTop = true }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titles[route] ?: "Projecteur Remote") },
                navigationIcon = {
                    if (tabs.none { it.first == route }) IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEach { (r, icon) ->
                    NavigationBarItem(
                        selected = route == r,
                        onClick = { navigate(r) },
                        icon = { Text(icon) },
                        label = { Text(titles.getValue(r).substringAfter(' ').let { if (r == "discovery") "Rechercher" else it }) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "remote", modifier = Modifier.padding(padding)) {
            composable("remote") { RemoteScreen(app, navigate) }
            composable("discovery") { DiscoveryScreen(app, navigate) }
            composable("config") { ConfigScreen(app, navigate) }
            composable("diagnostic") { DiagnosticScreen(app) }
            composable("ir") { IrScreen(app, navigate) }
            composable("learn") { LearnScreen(app) }
            composable("bluetooth") { BluetoothScreen(app, navigate) }
            composable("network") { NetworkScreen(app, navigate) }
        }
    }
}
