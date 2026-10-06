package com.rizwan.tradingagentarmy.ui.fleet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rizwan.tradingagentarmy.domain.model.Bot
import com.rizwan.tradingagentarmy.domain.model.BotStatus
import com.rizwan.tradingagentarmy.domain.model.Trade
import com.rizwan.tradingagentarmy.ui.theme.Tokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FleetScreen(
    onOpenBot: (String) -> Unit = {},
    vm: FleetViewModel = hiltViewModel()
) {
    val tab by vm.tab.collectAsState()
    val bots by vm.filteredBots.collectAsState()
    val filter by vm.botFilter.collectAsState()
    val futures by vm.futuresTrades.collectAsState()
    val forex by vm.forexTrades.collectAsState()
    val scalpOn by vm.scalpOn.collectAsState()
    val hftOn by vm.hftActive.collectAsState()
    val hftPos by vm.hftPosition.collectAsState()
    val hftSig by vm.hftSignal.collectAsState()

    Column(
        Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🤖 Bots & Positions", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Tokens.TextPrimary)
            Spacer(Modifier.weight(1f))
            Text(
                "open ${futures.count { it.status != "CLOSED" } + forex.count { it.status != "CLOSED" }}",
                fontSize = 11.sp, color = Tokens.TextSecondary
            )
        }

        // top level tabs
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FleetTabChip("Bots (${bots.size})", tab == FleetTab.BOTS) { vm.setTab(FleetTab.BOTS) }
            FleetTabChip("Futures", tab == FleetTab.FUTURES) { vm.setTab(FleetTab.FUTURES) }
            FleetTabChip("Forex / Gold", tab == FleetTab.FOREX) { vm.setTab(FleetTab.FOREX) }
        }

        // scalp engine card
        Surface(color = Tokens.Surface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 11.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("⚡", fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Scalp Engine", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Tokens.TextPrimary)
                    Text(
                        if (scalpOn) "${if (hftOn) "RUNNING" else "armed"} · $hftPos · $hftSig" else "off",
                        fontSize = 10.sp, color = Tokens.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(
                    checked = scalpOn, onCheckedChange = { vm.toggleScalp(it) },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = Tokens.AccentPrimary,
                        checkedThumbColor = androidx.compose.ui.graphics.Color.White
                    )
                )
            }
        }

        when (tab) {
            FleetTab.BOTS -> {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = filter == BotFilter.ALL,
                        onClick = { vm.setBotFilter(BotFilter.ALL) },
                        label = { Text("All", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = filter == BotFilter.FUTURE_BOT,
                        onClick = { vm.setBotFilter(BotFilter.FUTURE_BOT) },
                        label = { Text("Future Bot", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = filter == BotFilter.FOREX_BOT,
                        onClick = { vm.setBotFilter(BotFilter.FOREX_BOT) },
                        label = { Text("Forex Bot", fontSize = 11.sp) }
                    )
                }
                if (bots.isEmpty()) {
                    EmptyState("🤖", "Koi bot nahi hai", "Chat me bolo: “Create a BTC scalper bot”")
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(bots, key = { it.id }) { b -> BotRow(b, onOpenBot, vm) }
                    }
                }
            }
            FleetTab.FUTURES -> {
                Text("Derivatives (USDT-M / futures) trades", fontSize = 11.sp, color = Tokens.TextSecondary)
                if (futures.isEmpty()) EmptyState("📈", "No futures trades yet", "Settings → Bitget market type = FUTURES")
                else TradeList(futures)
            }
            FleetTab.FOREX -> {
                Text("Forex & Gold (CFD / MT5 account) trades", fontSize = 11.sp, color = Tokens.TextSecondary)
                if (forex.isEmpty()) EmptyState("🥇", "No forex/gold trades yet", "Settings → Bitget CFD demo keys + XAUUSD")
                else TradeList(forex)
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun RowScope.FleetTabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) Tokens.AccentPrimary.copy(alpha = 0.16f) else Tokens.Surface,
        shape = RoundedCornerShape(9.dp),
        border = BorderStroke(1.dp, if (selected) Tokens.AccentPrimary else Tokens.BorderSubtle),
        modifier = Modifier.weight(1f),
        onClick = onClick
    ) {
        Box(Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(
                label, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) Tokens.AccentPrimary else Tokens.TextSecondary, maxLines = 1
            )
        }
    }
}

@Composable
private fun BotRow(b: Bot, onOpen: (String) -> Unit, vm: FleetViewModel) {
    val running = b.status == BotStatus.RUNNING
    Surface(
        color = Tokens.Surface, shape = RoundedCornerShape(11.dp),
        onClick = { onOpen(b.id) }, modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 11.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🤖", fontSize = 17.sp)
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(b.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Tokens.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${b.market} · ${b.strategy}", fontSize = 10.sp, color = Tokens.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    GateDot(b.gate1); GateDot(b.gate2); GateDot(b.gate3)
                    Text("gates", fontSize = 9.sp, color = Tokens.TextTertiary)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (b.pnlTotal >= 0) "+$%.2f".format(b.pnlTotal) else "-$%.2f".format(kotlin.math.abs(b.pnlTotal)),
                    fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    color = if (b.pnlTotal >= 0) Tokens.SuccessGreen else Tokens.ErrorRed
                )
                Text("today $${"%.2f".format(b.pnlToday)}", fontSize = 9.sp, color = Tokens.TextTertiary)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(b.status.name, running)
                    IconButton(
                        onClick = { vm.setStatus(b.id, if (running) BotStatus.STOPPED else BotStatus.RUNNING) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            if (running) Icons.Filled.PauseCircle else Icons.Filled.PlayCircle,
                            contentDescription = null,
                            tint = if (running) Tokens.WarningAmber else Tokens.SuccessGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GateDot(on: Boolean) {
    Box(
        Modifier
            .padding(end = 3.dp)
            .size(7.dp)
            .background(if (on) Tokens.SuccessGreen else Tokens.BorderSubtle, RoundedCornerShape(4.dp))
    )
}

@Composable
private fun StatusPill(text: String, good: Boolean) {
    Surface(
        color = (if (good) Tokens.SuccessGreen else Tokens.TextTertiary).copy(alpha = 0.16f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text, fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
            color = if (good) Tokens.SuccessGreen else Tokens.TextSecondary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun ColumnScope.TradeList(list: List<Trade>) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.weight(1f)) {
        items(list, key = { it.id }) { t -> TradeRow(t) }
    }
}

@Composable
private fun TradeRow(t: Trade) {
    val open = t.status != "CLOSED" && t.status != "FAILED"
    val pnl = if (open) t.unrealizedPnl else t.pnl
    Surface(color = Tokens.Surface, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 11.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        t.symbol, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Tokens.TextPrimary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        t.side.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        color = if (t.side.equals("BUY", true) || t.side == "LONG") Tokens.SuccessGreen else Tokens.ErrorRed
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(t.marketType, fontSize = 9.sp, color = Tokens.TextTertiary)
                }
                Text(
                    "@ ${t.entry} · SL ${t.stopLoss ?: "-"} · TP ${t.takeProfit ?: "-"} · ${t.botName}",
                    fontSize = 10.sp, color = Tokens.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    SimpleDateFormat("dd MMM HH:mm", Locale.US).format(Date(t.timestamp)),
                    fontSize = 9.sp, color = Tokens.TextTertiary
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    (if (pnl >= 0) "+" else "-") + "$" + "%.2f".format(kotlin.math.abs(pnl)),
                    fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = if (pnl >= 0) Tokens.SuccessGreen else Tokens.ErrorRed
                )
                Spacer(Modifier.height(3.dp))
                StatusPill(t.status, !open)
            }
        }
    }
}

@Composable
private fun EmptyState(icon: String, title: String, sub: String) {
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(icon, fontSize = 34.sp)
        Spacer(Modifier.height(8.dp))
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Tokens.TextSecondary)
        Text(sub, fontSize = 11.sp, color = Tokens.TextTertiary)
    }
}
