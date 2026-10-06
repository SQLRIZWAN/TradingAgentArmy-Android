package com.rizwan.tradingagentarmy.data.remote

import com.rizwan.tradingagentarmy.domain.model.MarketTicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Key-less public market data sources with graceful degradation. */
@Singleton
class MarketApi @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true }
    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    private fun get(url: String): String? = try {
        http.newCall(Request.Builder().url(url).get().build()).execute().use {
            if (it.isSuccessful) it.body?.string() else null
        }
    } catch (e: Exception) {
        null
    }

    data class CoinRow(
        val id: String,
        val symbol: String,
        val name: String,
        val image: String,
        val price: Double,
        val change24h: Double,
        val rank: Int,
        val volume: Double,
        val marketCap: Double
    ) {
        val pair: String get() = "${symbol.uppercase()}/USDT"
    }

    /** Top [limit] coins by market cap — CoinGecko free endpoint. */
    suspend fun topCoins(limit: Int = 100): List<CoinRow> = withContext(Dispatchers.IO) {
        val body = get(
            "https://api.coingecko.com/api/v3/coins/markets?vs_currency=usd&order=market_cap_desc" +
                "&per_page=$limit&page=1&sparkline=false"
        ) ?: return@withContext emptyList()
        runCatching {
            json.parseToJsonElement(body).jsonArray.mapNotNull { row ->
                val o = row.jsonObject
                val price = o["current_price"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                CoinRow(
                    id = o["id"]?.jsonPrimitive?.content.orEmpty(),
                    symbol = o["symbol"]?.jsonPrimitive?.content.orEmpty().uppercase(),
                    name = o["name"]?.jsonPrimitive?.content.orEmpty(),
                    image = o["image"]?.jsonPrimitive?.content.orEmpty(),
                    price = price,
                    change24h = o["price_change_percentage_24h"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    rank = o["market_cap_rank"]?.jsonPrimitive?.doubleOrNull?.toInt() ?: 0,
                    volume = o["total_volume"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    marketCap = o["market_cap"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                )
            }
        }.getOrDefault(emptyList())
    }

    /** 30 currencies → every major/minor/exotic cross used by retail FX desks. */
    val fxCurrencies = listOf(
        "USD", "EUR", "GBP", "JPY", "CHF", "AUD", "CAD", "NZD", "CNY", "HKD",
        "SGD", "SEK", "NOK", "DKK", "PLN", "CZK", "HUF", "TRY", "ZAR", "MXN",
        "BRL", "INR", "KRW", "IDR", "THB", "PHP", "MYR", "ILS", "RON", "ISK"
    )

    /** Hand-picked tradable crosses rendered on the Forex tab (order matters for UI). */
    val fxPairs = listOf(
        "EUR/USD", "GBP/USD", "USD/JPY", "USD/CHF", "AUD/USD", "USD/CAD", "NZD/USD",
        "EUR/GBP", "EUR/JPY", "GBP/JPY", "AUD/JPY", "EUR/AUD", "EUR/CHF", "EUR/CAD",
        "GBP/CHF", "GBP/CAD", "GBP/AUD", "AUD/CAD", "AUD/CHF", "AUD/NZD",
        "CAD/CHF", "CAD/JPY", "CHF/JPY", "NZD/JPY", "NZD/CAD", "NZD/CHF",
        "EUR/NZD", "EUR/SEK", "EUR/NOK", "EUR/PLN", "EUR/TRY", "EUR/ZAR",
        "GBP/NZD", "GBP/SEK", "GBP/PLN", "GBP/TRY", "USD/SEK", "USD/NOK",
        "USD/PLN", "USD/CZK", "USD/HUF", "USD/TRY", "USD/ZAR", "USD/MXN",
        "USD/BRL", "USD/INR", "USD/KRW", "USD/IDR", "USD/THB", "USD/PHP",
        "USD/MYR", "USD/ILS", "USD/CNH", "USD/SGD", "USD/HKD"
    )

    /**
     * All forex crosses with a 24h change computed from the ECB time series.
     * One request covers the whole tab: /v1/{from}..{to}?base=USD
     */
    suspend fun forexTickersFull(): List<MarketTicker> = withContext(Dispatchers.IO) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val weekAgo = SimpleDateFormat(
            "yyyy-MM-dd", Locale.US
        ).format(Date(System.currentTimeMillis() - 7L * 86_400_000L))
        val body = get("https://api.frankfurter.dev/v1/$weekAgo..$today?base=USD")
            ?: return@withContext emptyList()
        runCatching<List<MarketTicker>> {
            val days = json.parseToJsonElement(body).jsonObject["rates"]?.jsonObject
                ?: return@runCatching emptyList()
            val ordered = days.keys.sorted()
            if (ordered.isEmpty()) return@runCatching emptyList()
            val first = days[ordered.first()]!!.jsonObject
            val last = days[ordered.last()]!!.jsonObject
            fun rate(o: kotlinx.serialization.json.JsonObject, c: String): Double? =
                if (c == "USD") 1.0 else o[c]?.jsonPrimitive?.doubleOrNull
            fxPairs.mapNotNull { pair ->
                val base = pair.substring(0, 3)
                val quote = pair.substring(4, 7)
                val now = rate(last, quote)?.let { q -> rate(last, base)?.div(q) } ?: return@mapNotNull null
                val before = rate(first, quote)?.let { q -> rate(first, base)?.div(q) } ?: now
                val change = if (before == 0.0) 0.0 else ((now - before) / before) * 100.0
                MarketTicker(pair, now, change, decimalsFor(now))
            }
        }.getOrDefault(emptyList())
    }

    /** Gold / silver / platinum / palladium spot (gold-api.com, no key). */
    suspend fun metalTickers(): List<MarketTicker> = withContext(Dispatchers.IO) {
        val out = java.util.ArrayList<MarketTicker>()
        for (code in listOf("XAU", "XAG", "XPT", "XPD")) {
            val ticker = runCatching {
                val body = get("https://api.gold-api.com/price/$code") ?: return@runCatching null
                val o = json.parseToJsonElement(body).jsonObject
                val price = o["price"]?.jsonPrimitive?.doubleOrNull ?: return@runCatching null
                MarketTicker("${code}USD", price, 0.0, decimalsFor(price))
            }.getOrNull()
            if (ticker != null) out.add(ticker)
        }
        copperTicker()?.let { out.add(it) }
        out
    }

    private suspend fun copperTicker(): MarketTicker? = withContext(Dispatchers.IO) {
        runCatching {
            val body = get(
                "https://query2.finance.yahoo.com/v8/finance/chart/HG%3DF?interval=1d&range=5d"
            ) ?: return@runCatching null
            val chart = json.parseToJsonElement(body).jsonObject["chart"]?.jsonObject
                ?.get("result")?.jsonArray?.firstOrNull()?.jsonObject ?: return@runCatching null
            val meta = chart["meta"]?.jsonObject ?: return@runCatching null
            val price = meta["regularMarketPrice"]?.jsonPrimitive?.doubleOrNull ?: return@runCatching null
            val prev = meta["chartPreviousClose"]?.jsonPrimitive?.doubleOrNull
                ?: meta["previousClose"]?.jsonPrimitive?.doubleOrNull ?: price
            val change = if (prev == 0.0) 0.0 else ((price - prev) / prev) * 100.0
            MarketTicker("COPPER", price, change, decimalsFor(price))
        }.getOrNull()
    }

    private fun decimalsFor(p: Double): Int = when {
        p >= 1000 -> 2
        p >= 100 -> 2
        p >= 1 -> 4
        else -> 5
    }

    suspend fun cryptoTickers(): List<MarketTicker> = withContext(Dispatchers.IO) {
        val body = get(
            "https://api.coingecko.com/api/v3/simple/price" +
                "?ids=bitcoin,ethereum,solana&vs_currencies=usd&include_24hr_change=true"
        ) ?: return@withContext emptyList()
        runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            val ids = listOf("bitcoin" to "BTC/USDT", "ethereum" to "ETH/USDT", "solana" to "SOL/USDT")
            ids.mapNotNull { (id, sym) ->
                val o = root[id]?.jsonObject ?: return@mapNotNull null
                val price = o["usd"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                val chg = o["usd_24h_change"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                MarketTicker(sym, price, chg, if (price > 1000) 0 else 2)
            }
        }.getOrDefault(emptyList())
    }

    suspend fun fxTickers(): List<MarketTicker> = withContext(Dispatchers.IO) {
        val body = get("https://api.frankfurter.app/latest?from=USD&to=EUR,GBP") ?: return@withContext emptyList()
        runCatching {
            val rates = json.parseToJsonElement(body).jsonObject["rates"]?.jsonObject ?: return@runCatching emptyList()
            val eur = rates["EUR"]?.jsonPrimitive?.doubleOrNull
            val gbp = rates["GBP"]?.jsonPrimitive?.doubleOrNull
            buildList {
                eur?.let { add(MarketTicker("EURUSD", 1.0 / it, 0.0, 4)) }
                gbp?.let { add(MarketTicker("GBPUSD", 1.0 / it, 0.0, 4)) }
            }
        }.getOrDefault(emptyList())
    }

    suspend fun goldTicker(): MarketTicker? = withContext(Dispatchers.IO) {
        val body = get("https://api.gold-api.com/price/XAU") ?: return@withContext null
        runCatching {
            val price = json.parseToJsonElement(body).jsonObject["price"]?.jsonPrimitive?.doubleOrNull
            price?.let { MarketTicker("XAUUSD", it, 0.0, 2) }
        }.getOrNull()
    }

    suspend fun fullWatchlist(): List<MarketTicker> {
        val crypto = cryptoTickers()
        val fx = fxTickers()
        val gold = goldTicker()
        return crypto + fx + listOfNotNull(gold)
    }

    companion object {
        /** CoinGecko ids that map to a Binance spot pair for the chart screen. */
        fun binancePairFor(symbol: String): String =
            symbol.replace("/", "").uppercase()
    }
}
