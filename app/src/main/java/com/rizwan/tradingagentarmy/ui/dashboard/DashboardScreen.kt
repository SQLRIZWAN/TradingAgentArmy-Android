package com.rizwan.tradingagentarmy.ui.dashboard

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.rizwan.tradingagentarmy.agents.AgentArmyService
import androidx.hilt.navigation.compose.hiltViewModel
import com.rizwan.tradingagentarmy.domain.model.MarketTicker
import com.rizwan.tradingagentarmy.domain.model.Trade
import com.rizwan.tradingagentarmy.ui.theme.AppFonts
import com.rizwan.tradingagentarmy.ui.theme.Tokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    onOpenSettings: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val armyRunning by AgentArmyService.running.collectAsState()
    val portfolio by viewModel.portfolio.collectAsState()
    val trades by viewModel.trades.collectAsState()
    val openTrades by viewModel.openTrades.collectAsState()
    val circuit by viewModel.circuit.collectAsState()
    val backendAlive by viewModel.backendAlive.collectAsState()
    val noBackend by viewModel.noBackend.collectAsState()
    val wsConnected by viewModel.wsConnected.collectAsState()
    val lastSync by viewModel.lastSync.collectAsState()
    val liveTrading by viewModel.liveTrading.collectAsState()
    val serviceOn by viewModel.serviceOn.collectAsState()
    val agentsOn by viewModel.agentsOn.collectAsState()
    val agentCards by viewModel.agentCards.collectAsState()
    val apiLines by viewModel.apiLines.collectAsState()
    val tradesToday by viewModel.tradesToday.collectAsState()
    val totalTrades by viewModel.totalTrades.collectAsState()
    val armyStatus by viewModel.armyStatus.collectAsState()
    val tickers by viewModel.tickers.collectAsState()

    var confirmArmy by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ---------------- header ----------------
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Dashboard", color = Tokens.TextPrimary, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        if (serviceOn) "⚔️ Army 24/7 chal rahi hai" else "Army band hai — neeche 24/7 ON karein",
                        color = if (serviceOn) Tokens.AccentPrimary else Tokens.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, "Settings", tint = Tokens.TextSecondary)
                }
            }
        }

        // ---------------- circuit breaker ----------------
        if (circuit?.active == true) {
            item {
                val c = circuit!!
                val alpha by rememberInfiniteTransition(label = "pulse").animateFloat(
                    initialValue = 0.75f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
                    label = "a"
                )
                Surface(
                    color = Tokens.AccentDanger,
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(alpha)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("🚨 CIRCUIT BREAKER ACTIVE", color = Tokens.TextPrimary, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Daily loss limit reached. Resets in ${c.resetIn}.",
                            color = Tokens.TextPrimary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        // ---------------- PnL hero ----------------
        item {
            val p = portfolio
            Surface(
                color = Tokens.Surface,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Today P&L", style = MaterialTheme.typography.labelMedium, color = Tokens.TextSecondary)
                            val today = p?.todayPnl ?: 0.0
                            Text(
                                text = (if (today >= 0) "+$" else "-$") + "%.2f".format(kotlin.math.abs(today)),
                                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = AppFonts.Mono),
                                color = if (today >= 0) Tokens.AccentPrimary else Tokens.AccentDanger
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("All-time P&L", style = MaterialTheme.typography.labelMedium, color = Tokens.TextSecondary)
                            val total = p?.totalPnl ?: 0.0
                            Text(
                                text = (if (total >= 0) "+$" else "-$") + "%.2f".format(kotlin.math.abs(total)),
                                style = MaterialTheme.typography.titleLarge.copy(fontFamily = AppFonts.Mono),
                                color = if (total >= 0) Tokens.AccentPrimary else Tokens.AccentDanger
                            )
                        }
                    }
                    HorizontalStats(
                        listOf(
                            "Trades today" to "$tradesToday",
                            "Open" to "${openTrades.size}",
                            "All trades" to "$totalTrades",
                            "Bots" to "${(p?.activeRunning ?: 0) + (p?.activeDemo ?: 0)}"
                        )
                    )
                }
            }
        }

        // ---------------- 24/7 master button ----------------
        item {
            Surface(
                color = if (serviceOn) Tokens.AccentPrimary.copy(alpha = 0.12f) else Tokens.Surface,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (serviceOn) Tokens.AccentPrimary else Tokens.BorderSubtle
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "24/7 Autonomous Army",
                                color = Tokens.TextPrimary,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                if (serviceOn) "Foreground service ON · har minute ek agent kaam kar raha hai"
                                else "Service OFF · agents idle hain",
                                color = if (serviceOn) Tokens.AccentPrimary else Tokens.TextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = serviceOn && agentsOn,
                            onCheckedChange = { want ->
                                if (want) viewModel.toggleArmy(true) else confirmArmy = true
                            },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = Tokens.AccentPrimary,
                                checkedThumbColor = Tokens.Surface,
                                uncheckedTrackColor = Tokens.SurfaceElevated
                            )
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            color = if (serviceOn) Tokens.AccentDanger.copy(alpha = 0.15f) else Tokens.AccentPrimary,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    if (serviceOn) confirmArmy = true else viewModel.toggleArmy(true)
                                }
                        ) {
                            Row(
                                Modifier.padding(vertical = 11.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (serviceOn) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = if (serviceOn) Tokens.AccentDanger else Tokens.BackgroundBase,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer8()
                                Text(
                                    if (serviceOn) "STOP 24/7" else "START 24/7",
                                    color = if (serviceOn) Tokens.AccentDanger else Tokens.BackgroundBase,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Surface(
                            color = Tokens.SurfaceElevated,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.runRoundNow() }
                        ) {
                            Row(
                                Modifier.padding(vertical = 11.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.Bolt,
                                    contentDescription = null,
                                    tint = Tokens.AccentPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer8()
                                Text(
                                    "RUN ROUND",
                                    color = Tokens.TextPrimary,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Text(
                        armyStatus,
                        color = Tokens.TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2
                    )
                }
            }
        }

        // ---------------- agent status ----------------
        item {
            val liveCount = agentCards.count { it.live }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("AI Agents", color = Tokens.TextPrimary, style = MaterialTheme.typography.titleSmall)
                Spacer8()
                Text(
                    "$liveCount/${agentCards.size} active",
                    color = if (liveCount > 0) Tokens.AccentPrimary else Tokens.TextSecondary,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        item {
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(agentCards, key = { it.role.id }) { card -> AgentChip(card) }
            }
        }

        // ---------------- API status ----------------
        item {
            Surface(
                color = Tokens.Surface,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("System status", color = Tokens.TextPrimary, style = MaterialTheme.typography.titleSmall)
                        Spacer8()
                        Text(
                            "sync " + if (lastSync == 0L) "—"
                            else SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(lastSync)),
                            color = Tokens.TextSecondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    apiLines.forEach { line -> ApiRow(line) }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatusDot(
                            backendAlive || noBackend,
                            if (backendAlive) "Backend" else if (noBackend) "On-device" else "No backend"
                        )
                        StatusDot(wsConnected, if (wsConnected) "Live feed" else "Polling")
                        StatusDot(
                            !liveTrading,
                            if (liveTrading) "🔴 LIVE money" else "🟢 PAPER mode"
                        )
                    }
                }
            }
        }

        // ---------------- market strip ----------------
        if (tickers.isNotEmpty()) {
            item {
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(tickers, key = { it.symbol }) { t -> TickerCard(t) }
                }
            }
        }

        // ---------------- open positions ----------------
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Open Positions", color = Tokens.TextPrimary, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${openTrades.size}",
                    color = Tokens.AccentPrimary,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
        if (openTrades.isEmpty()) {
            item {
                Text(
                    "Koi open position nahi — Army/HFT ki trades yahan dikhengi.",
                    color = Tokens.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        } else {
            items(openTrades, key = { "open-${it.id}" }) { t ->
                OpenPositionCard(t) { viewModel.closePosition(t.id) }
            }
        }

        // ---------------- recent trades ----------------
        item {
            Text(
                "Recent Trades",
                style = MaterialTheme.typography.titleSmall,
                color = Tokens.TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        items(trades, key = { it.id }) { t -> TradeRow(t) }
        if (trades.isEmpty()) {
            item {
                Text(
                    "Abhi koi trade nahi — trades yahin record honge (local + Firebase).",
                    color = Tokens.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        item { Box(Modifier.height(10.dp)) }
    }

    if (confirmArmy) {
        AlertDialog(
            onDismissRequest = { confirmArmy = false },
            containerColor = Tokens.Surface,
            title = { Text("24/7 Army band karein?", color = Tokens.TextPrimary) },
            text = {
                Text(
                    "Foreground service stop hoga — agents, scalper aur auto rounds ruk jayenge. " +
                        "Chalte hue positions exchange par safe rehti hain.",
                    color = Tokens.TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmArmy = false
                    viewModel.toggleArmy(false)
                }) { Text("Stop army", color = Tokens.AccentDanger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmArmy = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun Spacer8() = androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))

@Composable
private fun HorizontalStats(items: List<Pair<String, String>>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        items.forEach { (label, value) ->
            Column {
                Text(label, color = Tokens.TextSecondary, style = MaterialTheme.typography.labelSmall)
                Text(
                    value,
                    color = Tokens.TextPrimary,
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = AppFonts.Mono)
                )
            }
        }
    }
}

@Composable
private fun ApiRow(line: ApiLine) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(
                    when (line.state) {
                        0 -> Tokens.AccentPrimary
                        1 -> Tokens.AccentWarning
                        else -> Tokens.AccentDanger
                    }
                )
        )
        Spacer8()
        Text(line.label, color = Tokens.TextSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(74.dp))
        Text(
            line.detail,
            color = Tokens.TextPrimary,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}

@Composable
private fun AgentChip(card: AgentCard) {
    val active = card.live
    Surface(
        color = if (active) Tokens.AccentPrimary.copy(alpha = 0.10f) else Tokens.Surface,
        shape = RoundedCornerShape(9.dp)
    ) {
        Column(
            Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(card.role.emoji, fontSize = 14.sp)
            Text(
                card.role.displayName.take(9),
                color = if (active) Tokens.TextPrimary else Tokens.TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (active) Tokens.AccentPrimary else Tokens.BorderSubtle)
                )
                androidx.compose.foundation.layout.Spacer(Modifier.width(4.dp))
                Text(
                    if (card.runs > 0) "${card.runs} run" else "ready",
                    color = if (active) Tokens.AccentPrimary else Tokens.TextTertiary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun StatusDot(ok: Boolean, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (ok) Tokens.AccentPrimary else Tokens.AccentWarning)
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = Tokens.TextSecondary)
    }
}

@Composable
private fun TickerCard(t: MarketTicker) {
    Surface(color = Tokens.Surface, shape = RoundedCornerShape(10.dp), modifier = Modifier.width(132.dp)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(t.symbol, style = MaterialTheme.typography.labelMedium, color = Tokens.TextSecondary)
            Text(
                "%,.${t.decimals}f".format(t.price),
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = AppFonts.Mono),
                color = Tokens.TextPrimary
            )
            Text(
                text = (if (t.change24h >= 0) "▲ +" else "▼ ") + "%.2f%%".format(t.change24h),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = AppFonts.Mono),
                color = if (t.change24h >= 0) Tokens.AccentPrimary else Tokens.AccentDanger
            )
        }
    }
}

@Composable
private fun TradeRow(t: Trade) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(t.symbol, style = MaterialTheme.typography.bodySmall, color = Tokens.TextPrimary)
        Text(
            t.side,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = AppFonts.Mono),
            color = if (t.side.equals("BUY", true)) Tokens.AccentPrimary else Tokens.AccentDanger
        )
        Text(
            "%.2f → %s".format(t.entry, t.exit?.let { "%.2f".format(it) } ?: "—"),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = AppFonts.Mono),
            color = Tokens.TextSecondary
        )
        Text(
            (if (t.pnl >= 0) "+" else "") + "%.2f".format(t.pnl),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = AppFonts.Mono),
            color = if (t.pnl >= 0) Tokens.AccentPrimary else Tokens.AccentDanger
        )
        Text(
            SimpleDateFormat("HH:mm", Locale.US).format(Date(t.timestamp)),
            style = MaterialTheme.typography.labelSmall,
            color = Tokens.TextSecondary
        )
    }
}

@Composable
private fun OpenPositionCard(trade: Trade, onClose: () -> Unit) {
    val displayPnl = if (trade.unrealizedPnl != 0.0) trade.unrealizedPnl else trade.pnl
    val positive = displayPnl >= 0
    var confirmClose by remember { mutableStateOf(false) }
    Surface(
        color = Tokens.Surface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(trade.symbol, color = Tokens.TextPrimary, style = MaterialTheme.typography.titleSmall)
                Text(
                    " ${trade.side.uppercase()} · ${trade.marketType}",
                    color = if (trade.side.equals("BUY", true) || trade.side.equals("LONG", true)) Tokens.AccentPrimary else Tokens.AccentDanger,
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    trade.botName,
                    color = Tokens.TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
                Text(
                    (if (positive) "+" else "") + "%.2f".format(displayPnl),
                    color = if (positive) Tokens.AccentPrimary else Tokens.AccentDanger,
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = AppFonts.Mono)
                )
                TextButton(onClick = { confirmClose = true }) {
                    Text("Close", color = Tokens.AccentDanger, style = MaterialTheme.typography.labelSmall)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniValue("Qty", if (trade.quantity > 0) "%.4f".format(trade.quantity) else "—")
                MiniValue("Entry", "%.4f".format(trade.entry))
                MiniValue("Current", if (trade.currentPrice > 0) "%.4f".format(trade.currentPrice) else "—")
                MiniValue("SL", trade.stopLoss?.let { "%.4f".format(it) } ?: "—", Tokens.AccentDanger)
                MiniValue("TP", trade.takeProfit?.let { "%.4f".format(it) } ?: "—", Tokens.AccentPrimary)
            }
            Text(
                if (trade.stopLoss != null && trade.takeProfit != null) "Exchange protection: SL + TP configured"
                else "Protection pending — LIVE se pehle SL/TP check karein",
                color = if (trade.stopLoss != null && trade.takeProfit != null) Tokens.AccentPrimary else Tokens.AccentWarning,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text("Close ${trade.symbol} position?") },
            text = { Text("Exchange par market exit jayega. Confirm karne se pehle exchange check karein.") },
            confirmButton = {
                TextButton(onClick = { confirmClose = false; onClose() }) {
                    Text("Confirm close", color = Tokens.AccentDanger)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun MiniValue(label: String, value: String, color: androidx.compose.ui.graphics.Color = Tokens.TextSecondary) {
    Column(Modifier.width(76.dp)) {
        Text(label, color = Tokens.TextSecondary, style = MaterialTheme.typography.labelSmall)
        Text(value, color = color, style = MaterialTheme.typography.bodySmall.copy(fontFamily = AppFonts.Mono))
    }
}
