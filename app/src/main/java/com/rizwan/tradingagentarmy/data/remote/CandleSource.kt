package com.rizwan.tradingagentarmy.data.remote

import com.rizwan.tradingagentarmy.trading.BitgetClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

data class CandleBar(
    val ts: Long = 0L,
    val o: Float,
    val h: Float,
    val l: Float,
    val c: Float,
    val volume: Float = 0f
)

/**
 * Candle pipeline used by the offline chart fallback.
 * Crypto → Binance, CFD (gold/forex) → Bitget CFD, FX → ECB series,
 * gold → PAXG proxy on Binance. TradingView is the primary chart source.
 */
object CandleSource {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val cfdSymbols = setOf(
        "XAUUSD", "XAUUSD.S", "XAUUSD.PRO", "XAGUSD", "XAGUSD.S", "XAGUSD.PRO",
        "XPTUSD", "XPDUSD", "XPTUSD.S", "XPDUSD.S",
        "EURUSD", "EURUSD.S", "EURUSD.PRO", "GBPUSD", "GBPUSD.S", "GBPUSD.PRO",
        "USDJPY", "USDJPY.S", "USDJPY.PRO", "AUDUSD", "AUDUSD.S", "AUDUSD.PRO",
        "USDCAD", "USDCAD.S", "USDCHF", "USDCHF.S", "NZDUSD", "EURGBP", "EURJPY"
    )

    fun isCfdSymbol(symbol: String): Boolean =
        symbol.uppercase().replace("/", "") in cfdSymbols

    suspend fun fetch(symbol: String, timeframe: String, bitget: BitgetClient): List<CandleBar> {
        val compact = symbol.uppercase().replace("/", "")
        return when {
            isCfdSymbol(compact) -> bitgetCandles(bitget, compact, timeframe)
            isFxPair(symbol) -> ecbCandles(symbol, timeframe)
            compact == "XAUUSD" || symbol.equals("GOLD", true) -> binanceCandles("PAXGUSDT", timeframe)
            else -> binanceCandles(compact, timeframe)
        }
    }

    private fun isFxPair(symbol: String): Boolean {
        val s = symbol.replace("/", "").uppercase()
        if (s.length != 6) return false
        val currencies = MarketApi().fxCurrencies
        return s.substring(0, 3) in currencies && s.substring(3, 6) in currencies
    }

    private suspend fun bitgetCandles(bitget: BitgetClient, symbol: String, timeframe: String): List<CandleBar> {
        val interval = if (timeframe == "1D") "1D" else timeframe
        val raw = bitget.cfdCandles(symbol = symbol, interval = interval, limit = 200)
            .getOrElse { return emptyList() }
        var bars = raw.map { CandleBar(it.ts, it.open.toFloat(), it.high.toFloat(), it.low.toFloat(), it.close.toFloat()) }
        if (timeframe == "5m") bars = aggregate5m(bars)
        return bars.asReversed()
    }

    private suspend fun binanceCandles(pair: String, timeframe: String): List<CandleBar> {
        val tf = timeframe.lowercase()
        val url = "https://api.binance.com/api/v3/klines?symbol=$pair&interval=$tf&limit=200"
        val body = http.newCall(Request.Builder().url(url).build()).execute().use { it.body?.string().orEmpty() }
        val arr = json.parseToJsonElement(body).jsonArray
        return arr.mapNotNull { row ->
            val a = row.jsonArray
            CandleBar(
                ts = a[0].jsonPrimitive.content.toLongOrNull() ?: return@mapNotNull null,
                o = a[1].jsonPrimitive.doubleOrNull?.toFloat() ?: return@mapNotNull null,
                h = a[2].jsonPrimitive.doubleOrNull?.toFloat() ?: return@mapNotNull null,
                l = a[3].jsonPrimitive.doubleOrNull?.toFloat() ?: return@mapNotNull null,
                c = a[4].jsonPrimitive.doubleOrNull?.toFloat() ?: return@mapNotNull null,
                volume = a[5].jsonPrimitive.doubleOrNull?.toFloat() ?: 0f
            )
        }
    }

    /** ECB daily reference rates → OHLC bars for any FX cross. */
    private suspend fun ecbCandles(symbol: String, timeframe: String): List<CandleBar> {
        val base = symbol.substring(0, 3)
        val quote = symbol.substring(4, 7)
        val days = if (timeframe == "1m" || timeframe == "5m" || timeframe == "15m") 30 else 180
        val from = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(System.currentTimeMillis() - days * 86_400_000L))
        val to = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val url = "https://api.frankfurter.dev/v1/$from..$to?base=$base"
        val body = http.newCall(Request.Builder().url(url).build()).execute().use { it.body?.string().orEmpty() }
        val rates = json.parseToJsonElement(body).jsonObject["rates"]?.jsonObject ?: return emptyList()
        return rates.keys.sorted().mapNotNull { date ->
            val row = rates[date]?.jsonObject ?: return@mapNotNull null
            val q = row[quote]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val close = (1.0 / q).toFloat()
            CandleBar(
                ts = runCatching {
                    SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)?.time ?: 0L
                }.getOrDefault(0L),
                o = close, h = close, l = close, c = close
            )
        }
    }

    private fun aggregate5m(bars: List<CandleBar>): List<CandleBar> {
        if (bars.size < 2) return bars
        val bucket = 5L * 60_000L
        return bars.groupBy { (it.ts / bucket) * bucket }.toSortedMap().values.map { g ->
            CandleBar(
                ts = g.first().ts,
                o = g.first().o,
                h = g.maxOf { it.h },
                l = g.minOf { it.l },
                c = g.last().c,
                volume = g.sumOf { it.volume.toDouble() }.toFloat()
            )
        }
    }
}
