package com.rizwan.tradingagentarmy.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.rizwan.tradingagentarmy.ui.army.WarRoomScreen
import com.rizwan.tradingagentarmy.ui.bots.BotDetailScreen
import com.rizwan.tradingagentarmy.ui.bots.BotsScreen
import com.rizwan.tradingagentarmy.ui.chat.ChatScreen
import com.rizwan.tradingagentarmy.ui.dashboard.DashboardScreen
import com.rizwan.tradingagentarmy.ui.settings.SettingsScreen
import com.rizwan.tradingagentarmy.ui.theme.Tokens

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("chat", "Chat", Icons.Filled.Forum),
    Tab("dashboard", "Markets", Icons.Filled.Dashboard),
    Tab("bots", "Bots", Icons.Filled.SmartToy),
    Tab("army", "Army", Icons.Filled.Groups),
    Tab("settings", "Settings", Icons.Filled.Tune)
)

@Composable
fun RootApp() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    Scaffold(
        containerColor = Tokens.BackgroundBase,
        bottomBar = {
            if (tabs.any { it.route == current } || current?.startsWith("bot_detail") == true) {
                NavigationBar(containerColor = Tokens.Surface) {
                    tabs.forEach { tab ->
                        val selected = if (current?.startsWith("bot_detail") == true)
                            tab.route == "bots" else current == tab.route
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
            composable("chat") { ChatScreen() }
            composable("dashboard") { DashboardScreen() }
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
