package com.rizwan.tradingagentarmy.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import android.net.Uri
import com.rizwan.tradingagentarmy.ui.army.WarRoomScreen
import com.rizwan.tradingagentarmy.ui.bots.BotDetailScreen
import com.rizwan.tradingagentarmy.ui.bots.BotsScreen
import com.rizwan.tradingagentarmy.ui.chat.ChatScreen
import com.rizwan.tradingagentarmy.ui.dashboard.DashboardScreen
import com.rizwan.tradingagentarmy.ui.settings.SettingsScreen
import com.rizwan.tradingagentarmy.ui.theme.Tokens
import com.rizwan.tradingagentarmy.ui.market.MarketScreen
import com.rizwan.tradingagentarmy.ui.market.ChartScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("dashboard", "Dashboard", Icons.Filled.Dashboard),
    Tab("markets", "Market", Icons.Filled.ShowChart),
    Tab("chat", "Army Chat", Icons.Filled.Forum),
    Tab("bots", "Bots", Icons.Filled.SmartToy)
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun RootApp() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    Scaffold(
        containerColor = Tokens.BackgroundBase,
        topBar = {
            if (current != "settings" && current != "chat" && current?.startsWith("chart/") != true) {
                TopAppBar(title = { Text(when(current) { "dashboard" -> "Trading command center"; "markets" -> "Markets"; "chat" -> "Army Chat"; "bots" -> "Bots & positions"; else -> "Trading Army" }) },
                    actions = { IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Filled.Settings, "Settings") } })
            }
        },
        bottomBar = {
            if (tabs.any { it.route == current } || current?.startsWith("bot_detail") == true) {
                NavigationBar(containerColor = Tokens.Surface) {
                    tabs.forEach { tab ->
                        val selected = if (current?.startsWith("bot_detail") == true)
                            tab.route == "bots" else current == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                nav.navigate(if (tab.route == "chat") "chat" else tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Tokens.AccentPrimary,
                                selectedTextColor = Tokens.AccentPrimary,
                                unselectedIconColor = Tokens.TextSecondary,
                                unselectedTextColor = Tokens.TextSecondary,
                                indicatorColor = Tokens.SurfaceElevated
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "dashboard",
            modifier = Modifier.padding(padding)
        ) {
            composable("chat") { ChatScreen() }
            composable("dashboard") { DashboardScreen() }
            composable("markets") { MarketScreen(onChart = { symbol -> nav.navigate("chart/${Uri.encode(symbol)}") }) }
            composable("chart/{symbol}") { entry -> ChartScreen(Uri.decode(entry.arguments?.getString("symbol").orEmpty()), onBack = { nav.popBackStack() }) }
            composable("bots") {
                BotsScreen(onOpen = { id -> nav.navigate("bot_detail/$id") })
            }
            composable("settings") { SettingsScreen() }
            composable("army") { WarRoomScreen() }
            composable("bot_detail/{botId}") { entry ->
                val id = entry.arguments?.getString("botId") ?: return@composable
                BotDetailScreen(botId = id, onBack = { nav.popBackStack() })
            }
        }
    }
}
