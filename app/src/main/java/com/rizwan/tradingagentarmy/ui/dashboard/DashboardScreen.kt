package com.rizwan.tradingagentarmy.ui.dashboard

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.viewinterop.AndroidView
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
    val lastSync by viewModel.lastSync.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val chartError by viewModel.chartError.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ---- status row ----
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusDot(backendAlive, if (backendAlive) "Backend" else if (wsConnected) "WS" else "Offline")
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
                        Text("TradingView", style = MaterialTheme.typography.labelSmall, color = Tokens.TextSecondary)
                    }
                    Box(Modifier.height(240.dp)) {
                        if (chartError) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "Chart offline — network check karein",
                                    color = Tokens.TextSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        } else {
                            MiniChart(url = viewModel.chartUrl(), onError = viewModel::onChartError)
                        }
                    }
                }
            }
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MiniChart(url: String, onError: () -> Unit) {
    var loading by remember { mutableStateOf(true) }
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun onReceivedError(
                            view: WebView?, request: android.webkit.WebResourceRequest?,
                            error: android.webkit.WebResourceError?
                        ) {
                            onError()
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            loading = false
                        }
                    }
                    loadUrl(url)
                }
            },
            update = { wv -> if (wv.url != url) wv.loadUrl(url) },
            modifier = Modifier.fillMaxSize()
        )
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Tokens.AccentPrimary, modifier = Modifier.size(28.dp))
            }
        }
    }
}
