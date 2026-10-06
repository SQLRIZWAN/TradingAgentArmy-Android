package com.rizwan.tradingagentarmy.agents

import com.rizwan.tradingagentarmy.data.local.TradeDao
import com.rizwan.tradingagentarmy.data.remote.MarketApi
import com.rizwan.tradingagentarmy.data.repository.LocalDatabaseContext
import com.rizwan.tradingagentarmy.trading.BitgetClient
import com.rizwan.tradingagentarmy.domain.model.MarketTicker
import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentTools @Inject constructor(
    private val marketApi: MarketApi,
    private val tradeDao: TradeDao,
    private val bitget: BitgetClient,
    private val databaseContext: LocalDatabaseContext
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    suspend fun webSearch(query: String): String = withContext(Dispatchers.IO) {
        runCatching {
            val q = URLEncoder.encode(query, "UTF-8")
            val request = Request.Builder()
                .url("https://html.duckduckgo.com/html/?q=$q")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Mobile Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml")
                .get()
                .build()
            val (code, html) = http.newCall(request).execute().use { response ->
                response.code to response.body?.string().orEmpty()
            }
            if (code !in 200..299) error("search provider HTTP $code")
            val anchors = Regex(
                "<a[^>]+class=[\"'][^\"']*(?:result-link|result__a)[^\"']*[\"'][^>]*href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ).findAll(html).take(8).toList()
            val snippets = Regex(
                "<(?:td|div)[^>]+class=[\"'][^\"']*(?:result-snippet|result__snippet)[^\"']*[\"'][^>]*>(.*?)</(?:td|div)>",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ).findAll(html).map { cleanHtml(it.groupValues[1]) }.toList()
            anchors.mapIndexedNotNull { index, match ->
                val title = cleanHtml(match.groupValues[2])
                val link = match.groupValues[1].replace("&amp;", "&")
                if (title.isBlank()) null else buildString {
                    append("- ").append(title)
                    if (snippets.getOrNull(index).orEmpty().isNotBlank()) append(" — ").append(snippets[index])
                    append(" (").append(link).append(')')
                }
            }.joinToString("\n").ifBlank { "NO WEB RESULTS returned by search provider (HTTP 200, no result links)." }
        }.getOrElse { "SEARCH FAILED: ${it.message}" }
    }

    private fun cleanHtml(fragment: String): String = Html.fromHtml(
        fragment.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), " "),
        Html.FROM_HTML_MODE_LEGACY
    ).toString().replace(Regex("\\s+"), " ").trim()

    suspend fun appDatabaseContext(): String = databaseContext.build()

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
            if (isCfdSymbol(symbol)) {
                val candles = bitget.cfdCandles(
                    symbol = symbol.replace("/", "").uppercase(),
                    interval = interval,
                    limit = limit.coerceAtMost(100)
                ).getOrElse { return@withContext "CFD CANDLE DATA FAILED: ${it.message}" }
                return@withContext candles.joinToString("\n") {
                    "T=${it.ts} O=${it.open} H=${it.high} L=${it.low} C=${it.close}"
                }.ifBlank { "NO CFD CANDLE DATA" }
            }
            runCatching {
                val sym = symbol.lowercase().replace("/", "")
                val url = "https://api.binance.com/api/v3/klines?symbol=${sym.uppercase()}&interval=$interval&limit=$limit"
                val body = http.newCall(Request.Builder().url(url).build())
                    .execute().use { it.body?.string().orEmpty() }
                val rows = Json { isLenient = true }.parseToJsonElement(body) as? JsonArray
                    ?: return@runCatching "CANDLE DATA FAILED: exchange response was not an array"
                rows.takeLast(30).mapNotNull { row ->
                    val values = row as? JsonArray ?: return@mapNotNull null
                    if (values.size < 6) return@mapNotNull null
                    val open = values[1].jsonPrimitive.content
                    val high = values[2].jsonPrimitive.content
                    val low = values[3].jsonPrimitive.content
                    val close = values[4].jsonPrimitive.content
                    val volume = values[5].jsonPrimitive.content
                    "O=$open H=$high L=$low C=$close V=$volume"
                }.joinToString("\n").ifBlank { "NO CANDLE DATA" }
            }.getOrElse { "CANDLE DATA FAILED: ${it.message}" }
        }

    suspend fun lastPrice(symbol: String, side: String = "buy"): Double = withContext(Dispatchers.IO) {
        if (isCfdSymbol(symbol)) {
            return@withContext bitget.cfdPrice(symbol.replace("/", ""), side).getOrDefault(0.0)
        }
        runCatching {
            val sym = symbol.replace("/", "").uppercase()
            val body = http.newCall(
                Request.Builder().url("https://api.binance.com/api/v3/ticker/price?symbol=$sym").build()
            ).execute().use { it.body?.string().orEmpty() }
            Regex("\"price\":\"([0-9.]+)\"").find(body)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        }.getOrDefault(0.0)
    }

    fun isCfdSymbol(symbol: String): Boolean =
        symbol.uppercase().replace("/", "") in setOf(
            "XAUUSD", "XAUUSD.S", "XAUUSD.PRO", "XAGUSD", "XAGUSD.S", "XAGUSD.PRO",
            "EURUSD", "EURUSD.S", "EURUSD.PRO", "GBPUSD", "GBPUSD.S", "GBPUSD.PRO",
            "USDJPY", "USDJPY.S", "USDJPY.PRO", "AUDUSD", "AUDUSD.S", "USDCAD", "USDCHF"
        )

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
