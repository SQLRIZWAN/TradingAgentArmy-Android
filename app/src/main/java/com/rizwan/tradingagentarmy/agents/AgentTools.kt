package com.rizwan.tradingagentarmy.agents

import com.rizwan.tradingagentarmy.data.local.TradeDao
import com.rizwan.tradingagentarmy.data.remote.MarketApi
import com.rizwan.tradingagentarmy.domain.model.MarketTicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentTools @Inject constructor(
    private val marketApi: MarketApi,
    private val tradeDao: TradeDao
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    suspend fun webSearch(query: String): String = withContext(Dispatchers.IO) {
        runCatching {
            val q = URLEncoder.encode(query, "UTF-8")
            val html = http.newCall(
                Request.Builder().url("https://lite.duckduckgo.com/lite/?q=$q").build()
            ).execute().use { it.body?.string().orEmpty() }
            val results = mutableListOf<String>()
            val linkIter = Regex("result-link[^>]*href=\"([^\"]+)\"").findAll(html)
            val titleIter = Regex("result-snippet\"[^>]*>(.*?)</td>", RegexOption.DOT_MATCHES_ALL).findAll(html)
            val links = linkIter.map { it.groupValues[1] }.toList()
            val titles = titleIter.map { it.groupValues[1].replace(Regex("<[^>]+>"), "").trim() }.toList()
            for (i in titles.indices.take(6)) {
                results += "- ${titles[i]} ${links.getOrNull(i)?.let { "($it)" } ?: ""}"
            }
            if (results.isEmpty()) {
                Regex("<a[^>]*class=\"result-link\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
                    .findAll(html).take(6).forEach { results += "- " + it.groupValues[1].replace(Regex("<[^>]+>"), "") }
            }
            results.joinToString("\n").ifBlank { "NO RESULTS" }
        }.getOrElse { "SEARCH FAILED: ${it.message}" }
    }

    suspend fun marketSnapshot(limit: Int = 14): String = runCatching {
        val tickers = marketApi.fullWatchlist()
        renderTickers(tickers, limit)
    }.getOrElse { "MARKET DATA FAILED: ${it.message}" }

    suspend fun topMovers(): String = runCatching {
        val tickers = marketApi.fullWatchlist()
        val sorted = tickers.sortedByDescending { kotlin.math.abs(it.change24h) }.take(6)
        renderTickers(sorted, 6)
    }.getOrElse { "MARKET DATA FAILED: ${it.message}" }

    private fun renderTickers(tickers: List<MarketTicker>, limit: Int): String =
        tickers.take(limit).joinToString("\n") {
            val sign = if (it.change24h >= 0) "+" else ""
            "${it.symbol}: ${"%,.${it.decimals}f".format(it.price)} (${sign}${"%.2f".format(it.change24h)}%)"
        }

    suspend fun klines(symbol: String, interval: String = "1h", limit: Int = 48): String =
        withContext(Dispatchers.IO) {
            runCatching {
                val sym = symbol.lowercase().replace("/", "")
                val url = "https://api.binance.com/api/v3/klines?symbol=${sym.uppercase()}&interval=$interval&limit=$limit"
                val body = http.newCall(Request.Builder().url(url).build())
                    .execute().use { it.body?.string().orEmpty() }
                val rows = Regex("\\[(.*?)\\]").findAll(body).map { it.groupValues[1] }.toList()
                if (rows.isEmpty()) return@runCatching "NO CANDLE DATA"
                rows.takeLast(30).joinToString("\n") { r ->
                    val p = r.split(",").mapNotNull { it.trim('"').toDoubleOrNull() }
                    if (p.size >= 5) "O=${p[0]} H=${p[2]} L=${p[3]} C=${p[4]} V=${p[5]}" else r
                }
            }.getOrElse { "CANDLE DATA FAILED: ${it.message}" }
        }

    suspend fun lastPrice(symbol: String): Double = withContext(Dispatchers.IO) {
        runCatching {
            val sym = symbol.replace("/", "").uppercase()
            val body = http.newCall(
                Request.Builder().url("https://api.binance.com/api/v3/ticker/price?symbol=$sym").build()
            ).execute().use { it.body?.string().orEmpty() }
            Regex("\"price\":\"([0-9.]+)\"").find(body)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        }.getOrDefault(0.0)
    }

    suspend fun pastTradesSummary(): String = runCatching {
        val trades = tradeDao.all()
        if (trades.isEmpty()) return@runCatching "TRADE DB: empty (no past trades yet)"
        val recent = trades.take(20)
        val wins = trades.count { it.pnl > 0 }
        val losses = trades.count { it.pnl < 0 }
        val totalPnl = trades.sumOf { it.pnl }
        val lines = recent.joinToString("\n") {
            "${it.timestamp.toShortString()} ${it.side} ${it.symbol} pnl=${"%.2f".format(it.pnl)} [${it.mode}/${it.botName}]"
        }
        "TRADE DB: total=${trades.size} wins=$wins losses=$losses totalPnl=${"%.2f".format(totalPnl)}\nRecent:\n$lines"
    }.getOrElse { "TRADE DB ERROR: ${it.message}" }

    fun appActivity(): String =
        com.rizwan.tradingagentarmy.agents.AppEvents.recentLog().ifBlank { "APP: idle, nothing unusual" }

    private fun Long.toShortString(): String {
        val sdf = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.US)
        return sdf.format(java.util.Date(this))
    }
}
