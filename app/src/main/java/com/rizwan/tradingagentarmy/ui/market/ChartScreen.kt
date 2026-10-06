package com.rizwan.tradingagentarmy.ui.market

import android.annotation.SuppressLint
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.rizwan.tradingagentarmy.ui.theme.Tokens
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume

private val TIMEFRAMES = listOf("1m", "5m", "15m", "1h", "4h", "1D")

@Composable
fun ChartScreen(
    symbol: String,
    timeframe: String,
    onBack: () -> Unit,
    viewModel: ChartViewModel = hiltViewModel()
) {
    val tf by viewModel.timeframe.collectAsState()
    val candles by viewModel.candles.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val offline by viewModel.offline.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(symbol, timeframe) { viewModel.open(symbol, timeframe) }
    LaunchedEffect(offline) { if (offline) viewModel.watchOffline() }

    Column(
        Modifier
            .fillMaxSize()
            .background(Tokens.BackgroundBase)
    ) {
        Surface(color = Tokens.Surface) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Tokens.TextPrimary)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        ChartViewModel.tvSymbol(symbol),
                        color = Tokens.TextPrimary,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        if (offline) "Offline chart · app data feed" else "Real TradingView chart · full tools",
                        color = if (offline) Tokens.AccentWarning else Tokens.AccentPrimary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                IconButton(onClick = { viewModel.retry() }) {
                    Icon(Icons.Filled.Refresh, "Reload", tint = Tokens.TextSecondary)
                }
            }
        }

        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(TIMEFRAMES.size) { i ->
                val frame = TIMEFRAMES[i]
                TextButton(onClick = { viewModel.setTimeframe(frame) }) {
                    Text(
                        frame,
                        color = if (tf == frame) Tokens.AccentPrimary else Tokens.TextSecondary,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        Box(Modifier.weight(1f)) {
            if (offline) {
                OfflineChart(candles)
            } else {
                TradingViewWebView(
                    symbol = ChartViewModel.tvSymbol(symbol),
                    interval = ChartViewModel.tvInterval(tf),
                    onTimedOut = { viewModel.useOfflineChart() }
                )
            }

            if (loading && candles.isEmpty() && offline) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Tokens.AccentPrimary, modifier = Modifier.size(26.dp))
                }
            }

            if (error != null && offline) {
                Surface(
                    color = Tokens.Surface,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp)
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(error ?: "", color = Tokens.AccentWarning, style = MaterialTheme.typography.labelSmall)
                        TextButton(onClick = { viewModel.load() }) { Text("⟳ Retry", color = Tokens.AccentPrimary) }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun TradingViewWebView(symbol: String, interval: String, onTimedOut: () -> Unit) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var settled by remember(symbol, interval) { mutableStateOf(false) }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.mediaPlaybackRequiresUserGesture = false
                webViewClient = WebViewClient()
                setBackgroundColor(android.graphics.Color.rgb(16, 23, 34))
                loadUrl(
                    "file:///android_asset/tradingview_chart.html" +
                        "?symbol=${java.net.URLEncoder.encode(symbol, "UTF-8")}" +
                        "&interval=$interval"
                )
                webView = this
            }
        },
        update = { view -> webView = view }
    )

    LaunchedEffect(symbol, interval) {
        settled = false
        var waited = 0
        while (waited < 14_000) {
            delay(1_500)
            waited += 1_500
            val ready = webView?.let { w ->
                kotlinx.coroutines.suspendCancellableCoroutine<String?> { cont ->
                    w.post {
                        w.evaluateJavascript("(window.__tvReady===true)?'1':'0'") { v ->
                            cont.resume(v?.trim('"') ?: "0")
                        }
                    }
                }
            } ?: "0"
            if (ready == "1") {
                settled = true
                return@LaunchedEffect
            }
        }
        if (!settled) onTimedOut()
    }
}

@Composable
private fun OfflineChart(candles: List<com.rizwan.tradingagentarmy.data.remote.CandleBar>) {
    val payload = remember(candles) {
        JSONArray().apply {
            candles.forEach { c ->
                put(JSONObject().apply {
                    put("ts", c.ts)
                    put("o", c.o.toDouble())
                    put("h", c.h.toDouble())
                    put("l", c.l.toDouble())
                    put("c", c.c.toDouble())
                    put("v", c.volume.toDouble())
                })
            }
        }.toString()
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                setBackgroundColor(android.graphics.Color.rgb(16, 23, 34))
                webViewClient = WebViewClient()
                loadUrl("file:///android_asset/trading_chart.html")
            }
        },
        update = { w ->
            w.post { runCatching { w.evaluateJavascript("setBars($payload);", null) } }
        }
    )
}
