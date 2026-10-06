package com.rizwan.tradingagentarmy.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rizwan.tradingagentarmy.ui.army.ArmyChatScreen
import com.rizwan.tradingagentarmy.ui.bots.BotDetailScreen
import com.rizwan.tradingagentarmy.ui.chat.ChatScreen
import com.rizwan.tradingagentarmy.ui.dashboard.DashboardScreen
import com.rizwan.tradingagentarmy.ui.fleet.FleetScreen
import com.rizwan.tradingagentarmy.ui.market.ChartScreen
import com.rizwan.tradingagentarmy.ui.market.MarketScreen
import com.rizwan.tradingagentarmy.ui.settings.SettingsScreen
import com.rizwan.tradingagentarmy.ui.theme.Tokens

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("dashboard", "Dashboard", Icons.Filled.Home),
    Tab("market", "Market", Icons.Filled.CandlestickChart),
    Tab("army", "Army Chat", Icons.Filled.Forum),
    Tab("fleet", "Bots", Icons.Filled.SmartToy)
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun RootApp() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    val showBar = current == null || tabs.any { it.route == current } ||
        current.startsWith("bot_detail")

    Scaffold(
        containerColor = Tokens.BackgroundBase,
        topBar = {
            if (showBar) {
                TopAppBar(
                    title = {
                        Text(
                            when (current) {
                                "dashboard" -> "Trading command center"
                                "market" -> "Markets"
                                "army" -> "Army Chat"
                                "fleet" -> "Bots & positions"
                                else -> "Trading Army"
                            },
                            color = Tokens.TextPrimary
                        )
                    },
                    actions = {
                        IconButton(onClick = { nav.navigate("settings") }) {
                            Icon(Icons.Filled.Settings, "Settings", tint = Tokens.TextSecondary)
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = Tokens.Surface) {
                    tabs.forEach { tab ->
                        val selected = if (current?.startsWith("bot_detail") == true)
                            tab.route == "fleet" else current == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                nav.navigate(tab.route) {
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
            composable("dashboard") {
                DashboardScreen(onOpenSettings = { nav.navigate("settings") })
            }
            composable("market") {
                MarketScreen(onOpenChart = { symbol -> nav.navigate("chart/${Uri.encode(symbol)}") })
            }
            composable("army") { ArmyChatScreen() }
            composable("fleet") {
                FleetScreen(onOpenBot = { id -> nav.navigate("bot_detail/$id") })
            }
            composable("chat") { ChatScreen() }
            composable("settings") { SettingsScreen() }
            composable(
                "chart/{symbol}",
                arguments = listOf(navArgument("symbol") { type = NavType.StringType })
            ) { entry ->
                val symbol = Uri.decode(entry.arguments?.getString("symbol") ?: "BTCUSDT")
                ChartScreen(symbol = symbol, timeframe = "5m", onBack = { nav.popBackStack() })
            }
            composable("bot_detail/{botId}") { entry ->
                val id = entry.arguments?.getString("botId") ?: return@composable
                BotDetailScreen(botId = id, onBack = { nav.popBackStack() })
            }
        }
    }
}
