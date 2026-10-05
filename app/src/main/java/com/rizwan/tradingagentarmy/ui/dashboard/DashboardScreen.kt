package com.rizwan.tradingagentarmy.ui.dashboard

import android.annotation.SuppressLint
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.Canvas
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.hilt.navigation.compose.hiltViewModel
import com.rizwan.tradingagentarmy.domain.model.MarketTicker
import com.rizwan.tradingagentarmy.ui.theme.AppFonts
import com.rizwan.tradingagentarmy.ui.theme.Tokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(viewModel: DashboardViewModel = hiltViewModel()) {
    val tickers by viewModel.tickers.collectAsState()
    val portfolio by viewModel.portfolio.collectAsState()
    val trades by viewModel.trades.collectAsState()
    val circuit by viewModel.circuit.collectAsState()
    val backendAlive by viewModel.backendAlive.collectAsState()
    val wsConnected by viewModel.wsConnected.collectAsState()
    val noBackend by viewModel.noBackend.collectAsState()
    val lastSync by viewModel.lastSync.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val chartError by viewModel.chartError.collectAsState()
    val candles by viewModel.candles.collectAsState()
    val chartLoading by viewModel.chartLoading.collectAsState()
    val liveTrading by viewModel.liveTrading.collectAsState()
    val openTrades = trades.filter { it.status.equals("OPEN", true) || it.status.equals("PAPER_OPEN", true) || it.exit == null }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(
                Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    "Trading command center",
                    color = Tokens.TextPrimary,
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    "AI Army · markets · positions · risk",
                    color = Tokens.TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        item {
            Surface(
                color = if (liveTrading) Tokens.AccentDanger.copy(alpha = 0.10f) else Tokens.AccentPrimary.copy(alpha = 0.08f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            ) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatusDot(liveTrading, if (liveTrading) "LIVE TRADING" else "PAPER MODE")
                    Text(
                        if (liveTrading) "Real orders enabled — verify exchange keys and SL/TP"
                        else "Safe virtual mode — no real money is used",
                        color = Tokens.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        // ---- status row ----
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusDot(
                    backendAlive || noBackend,
                    when {
                        backendAlive -> "Backend"
                        noBackend -> "Cloud AI"
                        wsConnected -> "WS"
                        else -> "Offline"
                    }
                )
                StatusDot(wsConnected || !backendAlive, if (wsConnected) "Live feed" else "Polling")
                Text(
                    text = "Sync " + if (lastSync == 0L) "—" else SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(lastSync)),
                    style = MaterialTheme.typography.labelSmall,
                    color = Tokens.TextSecondary,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }

        // ---- circuit breaker ----
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
                            "Daily loss limit reached. All bots halted. Resets in ${c.resetIn}.",
                            color = Tokens.TextPrimary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        // ---- portfolio card ----
        item {
            val p = portfolio
            Surface(
                color = Tokens.Surface,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Today P&L", style = MaterialTheme.typography.labelMedium, color = Tokens.TextSecondary)
                            val today = p?.todayPnl ?: 0.0
                            Text(
                                text = (if (today >= 0) "+" else "") + "%.2f".format(today),
                                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = AppFonts.Mono),
                                color = if (today >= 0) Tokens.AccentPrimary else Tokens.AccentDanger
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Total P&L", style = MaterialTheme.typography.labelMedium, color = Tokens.TextSecondary)
                            val total = p?.totalPnl ?: 0.0
                            Text(
                                text = (if (total >= 0) "+" else "") + "%.2f".format(total),
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = AppFonts.Mono),
                                color = if (total >= 0) Tokens.AccentPrimary else Tokens.AccentDanger
                            )
                        }
                    }
                    Text(
                        text = buildString {
                            append("${p?.activeRunning ?: 0} Running")
                            append(" | ${p?.activeDemo ?: 0} Demo")
                            append(" | ${p?.stopped ?: 0} Stopped")
                            append(if (p?.fromBackend == true) " · backend" else " · local")
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = Tokens.TextSecondary
                    )
                }
            }
        }

        // ---- watchlist ----
        item {
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tickers, key = { it.symbol }) { t ->
                    TickerCard(t) { viewModel.selectSymbol(t.symbol) }
                }
                if (tickers.isEmpty()) {
                    item {
                        Text(
                            "Loading market…",
                            color = Tokens.TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }

        // ---- chart ----
        item {
            Surface(
                color = Tokens.Surface,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                Column {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        var menu by remember { mutableStateOf(false) }
                        TextButton(onClick = { menu = true }) {
                            Text(selected, color = Tokens.TextPrimary, style = MaterialTheme.typography.titleSmall)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            listOf("BTC/USDT", "ETH/USDT", "SOL/USDT", "XAUUSD", "EURUSD", "GBPUSD").forEach { s ->
                                DropdownMenuItem(text = { Text(s) }, onClick = {
                                    viewModel.selectSymbol(s)
                                    menu = false
                                })
                            }
                        }
                        Text("15m · Live", style = MaterialTheme.typography.labelSmall, color = Tokens.TextSecondary)
                    }
                    Box(Modifier.height(240.dp)) {
                        when {
                            chartLoading && candles.isEmpty() -> Box(
                                Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = Tokens.AccentPrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            chartError && candles.isEmpty() -> Box(
                                Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "Chart load nahi hua — network check karein",
                                        color = Tokens.TextSecondary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    TextButton(onClick = { viewModel.loadCandles() }) {
                                        Text("⟳ Retry", color = Tokens.AccentPrimary)
                                    }
                                }
                            }
                            else -> CandleChart(candles, Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }

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
                    "No open position — Army ke live/paper trades yahan dikhenge.",
                    color = Tokens.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        } else {
            items(openTrades, key = { "open-${it.id}" }) { t -> OpenPositionCard(t) }
        }

        // ---- recent trades ----
        item {
            Text(
                "Recent Trades",
                style = MaterialTheme.typography.titleSmall,
                color = Tokens.TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        items(trades, key = { it.id }) { t ->
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
        item { Box(Modifier.height(8.dp)) }
    }
}

@Composable
private fun OpenPositionCard(trade: com.rizwan.tradingagentarmy.domain.model.Trade) {
    val positive = trade.pnl >= 0
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
                    (if (positive) "+" else "") + "%.2f".format(trade.pnl),
                    color = if (positive) Tokens.AccentPrimary else Tokens.AccentDanger,
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = AppFonts.Mono)
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniValue("Qty", if (trade.quantity > 0) "%.4f".format(trade.quantity) else "—")
                MiniValue("Entry", "%.4f".format(trade.entry))
                MiniValue("SL", trade.stopLoss?.let { "%.4f".format(it) } ?: "—", Tokens.AccentDanger)
                MiniValue("TP", trade.takeProfit?.let { "%.4f".format(it) } ?: "—", Tokens.AccentPrimary)
            }
            Text(
                if (trade.stopLoss != null && trade.takeProfit != null) "Exchange protection: SL + TP configured"
                else "Protection pending — do not use LIVE until SL/TP is visible",
                color = if (trade.stopLoss != null && trade.takeProfit != null) Tokens.AccentPrimary else Tokens.AccentWarning,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun MiniValue(label: String, value: String, color: androidx.compose.ui.graphics.Color = Tokens.TextSecondary) {
    Column(Modifier.width(76.dp)) {
        Text(label, color = Tokens.TextSecondary, style = MaterialTheme.typography.labelSmall)
        Text(value, color = color, style = MaterialTheme.typography.bodySmall.copy(fontFamily = AppFonts.Mono))
    }
}

@Composable
private fun StatusDot(ok: Boolean, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (ok) Tokens.AccentPrimary else Tokens.AccentDanger)
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = Tokens.TextSecondary)
    }
}

@Composable
private fun TickerCard(t: MarketTicker, onClick: () -> Unit) {
    Surface(
        color = Tokens.Surface,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.width(140.dp)
    ) {
        Column(
            Modifier
                .padding(10.dp)
                .clip(RoundedCornerShape(10.dp)),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                t.symbol,
                style = MaterialTheme.typography.labelMedium,
                color = Tokens.TextSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    
                    .clickable(onClick = onClick)
            )
            Text(
                text = formatPrice(t.price, t.decimals),
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

private fun formatPrice(p: Double, decimals: Int) = "%,.${decimals}f".format(p)

@Composable
private fun CandleChart(candles: List<Candle>, modifier: Modifier = Modifier) {
    if (candles.isEmpty()) return
    val gridColor = Tokens.BorderSubtle
    val upColor = Tokens.AccentPrimary
    val downColor = Tokens.AccentDanger
    val labelColor = Tokens.TextSecondary
    val lastColor = Tokens.TextPrimary
    Canvas(modifier) {
        val chartW = size.width - 56.dp.toPx()
        val chartH = size.height
        var lo = candles.minOf { it.l }
        var hi = candles.maxOf { it.h }
        if (hi - lo < 0.0001f) { hi += 1f; lo -= 1f }
        val pad = (hi - lo) * 0.06f
        lo -= pad; hi += pad
        fun y(p: Float) = chartH - ((p - lo) / (hi - lo)) * chartH

        val textPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(
                (labelColor.alpha * 255).toInt(),
                (labelColor.red * 255).toInt(),
                (labelColor.green * 255).toInt(),
                (labelColor.blue * 255).toInt()
            )
            textSize = 10.dp.toPx()
            isAntiAlias = true
        }
        val lastPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(
                (lastColor.alpha * 255).toInt(),
                (lastColor.red * 255).toInt(),
                (lastColor.green * 255).toInt(),
                (lastColor.blue * 255).toInt()
            )
            textSize = 10.dp.toPx()
            isAntiAlias = true
            isFakeBoldText = true
        }

        // horizontal grid + right-side price labels
        for (i in 0..4) {
            val price = lo + (hi - lo) * i / 4f
            val yy = y(price)
            drawLine(gridColor, Offset(0f, yy), Offset(chartW, yy), strokeWidth = 1f)
            drawContext.canvas.nativeCanvas.drawText(
                fmtPrice(price), chartW + 6.dp.toPx(), yy + 3.dp.toPx(), textPaint
            )
        }

        val slot = chartW / candles.size
        val bodyW = (slot * 0.62f).coerceAtLeast(1.5f)
        candles.forEachIndexed { i, c ->
            val x = i * slot + slot / 2
            val color = if (c.c >= c.o) upColor else downColor
            drawLine(
                color = color,
                start = Offset(x, y(c.h)),
                end = Offset(x, y(c.l)),
                strokeWidth = 1.2f
            )
            val top = y(maxOf(c.o, c.c))
            val bot = y(minOf(c.o, c.c))
            drawRect(
                color = color,
                topLeft = Offset(x - bodyW / 2, top),
                size = Size(bodyW, maxOf(bot - top, 1.5f))
            )
        }

        // last price marker
        val lastY = y(candles.last().c)
        drawLine(
            color = upColor.copy(alpha = 0.7f),
            start = Offset(0f, lastY),
            end = Offset(chartW, lastY),
            strokeWidth = 1f
        )
        drawContext.canvas.nativeCanvas.drawText(
            fmtPrice(candles.last().c),
            chartW + 6.dp.toPx(),
            lastY - 4.dp.toPx(),
            lastPaint
        )
    }
}

private fun fmtPrice(p: Float): String = when {
    p >= 1000f -> "%,.0f".format(p)
    p >= 10f -> "%.2f".format(p)
    else -> "%.4f".format(p)
}
