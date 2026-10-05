package com.rizwan.tradingagentarmy.data.remote

import com.rizwan.tradingagentarmy.domain.model.MarketTicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
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
}
