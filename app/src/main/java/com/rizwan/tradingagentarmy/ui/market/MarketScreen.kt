package com.rizwan.tradingagentarmy.ui.market

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rizwan.tradingagentarmy.domain.model.MarketTicker
import com.rizwan.tradingagentarmy.ui.theme.AppFonts
import com.rizwan.tradingagentarmy.ui.theme.Tokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MarketScreen(
    onOpenChart: (symbol: String) -> Unit,
    viewModel: MarketViewModel = hiltViewModel()
) {
    val tab by viewModel.tab.collectAsState()
    val coins by viewModel.coins.collectAsState()
    val forex by viewModel.forex.collectAsState()
    val metals by viewModel.metals.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val lastSync by viewModel.lastSync.collectAsState()
    val selected by viewModel.selected.collectAsState()

    val chartSymbol = when (tab) {
        MarketTab.CRYPTO -> coins.firstOrNull()?.pair ?: selected
        MarketTab.FOREX -> forex.firstOrNull()?.symbol ?: selected
        MarketTab.METAL -> metals.firstOrNull()?.symbol ?: selected
    }

    Column(
        Modifier
            .fillMaxSize()
    ) {
        Surface(color = Tokens.Surface) {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Market", color = Tokens.TextPrimary, style = MaterialTheme.typography.titleLarge)
                        Text(
                            buildString {
                                append("${coins.size + forex.size + metals.size} instruments")
                                if (lastSync > 0) append(
                                    " · " + SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(lastSync))
                                )
                            },
                            color = Tokens.TextSecondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, "Refresh", tint = Tokens.TextSecondary)
                    }
                    // candle button → real TradingView chart with all tools
                    Surface(
                        color = Tokens.AccentPrimary.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            Modifier
                                .clickable { onOpenChart(chartSymbol) }
                                .padding(horizontal = 9.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                Icons.Filled.CandlestickChart,
                                contentDescription = "Open chart",
                                tint = Tokens.AccentPrimary,
                                modifier = Modifier.size(17.dp)
                            )
                            Text("Chart", color = Tokens.AccentPrimary, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MarketTab.entries.forEach { t ->
                        TabPill(
                            label = t.label,
                            selected = tab == t,
                            modifier = Modifier.weight(1f)
                        ) { viewModel.selectTab(t) }
                    }
                }
            }
        }

        if (loading && (coins.isEmpty() && forex.isEmpty() && metals.isEmpty())) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Tokens.AccentPrimary, modifier = Modifier.size(26.dp))
            }
        } else if (error != null && tabAssetsEmpty(tab, coins, forex, metals)) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(error ?: "", color = Tokens.AccentWarning, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { viewModel.refresh() }) { Text("⟳ Retry", color = Tokens.AccentPrimary) }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                when (tab) {
                    MarketTab.CRYPTO -> items(coins, key = { "c-${it.id}" }) { coin ->
                        CoinRowItem(coin) {
                            viewModel.select(coin.pair)
                            onOpenChart(coin.pair)
                        }
                    }
                    MarketTab.FOREX -> items(forex, key = { "f-${it.symbol}" }) { pair ->
                        TickerRowItem(pair) {
                            viewModel.select(pair.symbol)
                            onOpenChart(pair.symbol)
                        }
                    }
                    MarketTab.METAL -> items(metals, key = { "m-${it.symbol}" }) { metal ->
                        TickerRowItem(metal) {
                            viewModel.select(metal.symbol)
                            onOpenChart(metal.symbol)
                        }
                    }
                }
                item { Box(Modifier.height(10.dp)) }
            }
        }
    }
}

private fun tabAssetsEmpty(
    tab: MarketTab,
    coins: List<com.rizwan.tradingagentarmy.data.remote.MarketApi.CoinRow>,
    forex: List<MarketTicker>,
    metals: List<MarketTicker>
): Boolean = when (tab) {
    MarketTab.CRYPTO -> coins.isEmpty()
    MarketTab.FOREX -> forex.isEmpty()
    MarketTab.METAL -> metals.isEmpty()
}

@Composable
private fun TabPill(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) Tokens.AccentPrimary else Tokens.SurfaceElevated)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) Color0() else Tokens.TextSecondary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun Color0() = androidx.compose.ui.graphics.Color.White

@Composable
private fun CoinRowItem(coin: com.rizwan.tradingagentarmy.data.remote.MarketApi.CoinRow, onClick: () -> Unit) {
    Surface(
        color = Tokens.Surface,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Tokens.SurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    coin.symbol.take(1),
                    color = Tokens.AccentPrimary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 9.dp)
            ) {
                Text(coin.name, color = Tokens.TextPrimary, style = MaterialTheme.typography.bodyMedium)
                Text(
                    coin.pair + (if (coin.rank > 0) " · #${coin.rank}" else ""),
                    color = Tokens.TextSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatPrice(coin.price),
                    color = Tokens.TextPrimary,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = AppFonts.Mono)
                )
                Text(
                    changeText(coin.change24h),
                    color = if (coin.change24h >= 0) Tokens.AccentPrimary else Tokens.AccentDanger,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = AppFonts.Mono)
                )
            }        }
    }
}

@Composable
private fun TickerRowItem(t: MarketTicker, onClick: () -> Unit) {
    Surface(
        color = Tokens.Surface,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(t.symbol, color = Tokens.TextPrimary, style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (t.symbol.startsWith("XAU") || t.symbol.startsWith("XAG") ||
                        t.symbol.startsWith("XPT") || t.symbol.startsWith("XPD") ||
                        t.symbol == "COPPER"
                    ) "Metal · spot" else "Forex · cross",
                    color = Tokens.TextSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatPrice(t.price),
                    color = Tokens.TextPrimary,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = AppFonts.Mono)
                )
                Text(
                    changeText(t.change24h),
                    color = if (t.change24h >= 0) Tokens.AccentPrimary else Tokens.AccentDanger,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = AppFonts.Mono)
                )
            }
        }
    }
}

private fun changeText(change: Double): String =
    if (change == 0.0) "—" else (if (change >= 0) "▲ +" else "▼ ") + "%.2f%%".format(change)

private fun formatPrice(p: Double): String = when {
    p >= 1000 -> "%,.2f".format(p)
    p >= 1 -> "%,.4f".format(p)
    else -> "%,.5f".format(p)}
