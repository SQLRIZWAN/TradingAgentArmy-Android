package com.rizwan.tradingagentarmy.trading

import com.rizwan.tradingagentarmy.data.local.SecurePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BitgetClient @Inject constructor(private val prefs: SecurePreferences) {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()

    data class BitgetResult(val ok: Boolean, val message: String)
    data class OrderResult(val ok: Boolean, val message: String, val orderId: String = "", val clientOid: String = "")
    data class CfdQuote(val symbol: String, val bid: Double, val ask: Double, val high: Double, val low: Double, val ts: Long)
    data class CfdCandle(val ts: Long, val open: Double, val high: Double, val low: Double, val close: Double)
    data class CfdPosition(
        val positionId: String,
        val symbol: String,
        val side: String,
        val quantity: Double,
        val openPrice: Double,
        val takeProfit: Double?,
        val stopLoss: Double?,
        val unrealizedPnl: Double,
        val totalProfit: Double
    )

    val configured: Boolean
        get() = prefs.bitgetKey.isNotBlank() && prefs.bitgetSecret.isNotBlank() && prefs.bitgetPassphrase.isNotBlank()

    private fun sign(ts: String, method: String, path: String, body: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(prefs.bitgetSecret.toByteArray(), "HmacSHA256"))
        return Base64.getEncoder().encodeToString(mac.doFinal((ts + method + path + body).toByteArray()))
    }

    private suspend fun call(method: String, path: String, body: String = ""): Pair<Int, String> = withContext(Dispatchers.IO) {
        val ts = System.currentTimeMillis().toString()
        val req = Request.Builder().url("https://api.bitget.com$path")
            .addHeader("ACCESS-KEY", prefs.bitgetKey)
            .addHeader("ACCESS-SIGN", sign(ts, method, path, body))
            .addHeader("ACCESS-TIMESTAMP", ts)
            .addHeader("ACCESS-PASSPHRASE", prefs.bitgetPassphrase)
            .addHeader("Content-Type", "application/json")
            .apply { if (method == "POST") post(body.toRequestBody("application/json".toMediaType())) else get() }
            .build()
        http.newCall(req).execute().use { it.code to (it.body?.string().orEmpty()) }
    }

    private fun parse(raw: String): BitgetResult = runCatching {
        val obj = json.parseToJsonElement(raw).jsonObject
        val code = obj["code"]?.jsonPrimitive?.content ?: "?"
        val msg = obj["msg"]?.jsonPrimitive?.content ?: raw.take(200)
        BitgetResult(code == "00000" || code == "0", if (code == "00000" || code == "0") "OK" else "[$code] $msg")
    }.getOrElse { BitgetResult(false, it.message ?: "parse error") }

    private fun result(raw: String, ok: Boolean, message: String): OrderResult = runCatching {
        val data = json.parseToJsonElement(raw).jsonObject["data"]?.jsonObject
        OrderResult(
            ok,
            message,
            data?.get("orderId")?.jsonPrimitive?.content
                ?: data?.get("trxId")?.jsonPrimitive?.content.orEmpty(),
            data?.get("clientOid")?.jsonPrimitive?.content.orEmpty()
        )
    }.getOrElse { OrderResult(false, "Invalid exchange response: ${it.message}") }

    suspend fun testConnection(): BitgetResult {
        if (!configured) return BitgetResult(false, "Bitget API key/secret/passphrase missing")
        val (code, body) = call("GET", "/api/v2/spot/account/info")
        val r = parse(body)
        return if (r.ok) r else BitgetResult(false, "HTTP $code · ${r.message}")
    }

    suspend fun spotPrice(symbol: String): String {
        val (code, body) = call("GET", "/api/v2/spot/market/tickers?symbol=${symbol.uppercase()}")
        if (code != 200) return "?"
        return runCatching {
            val data = json.parseToJsonElement(body).jsonObject["data"]
            val row = if (data is kotlinx.serialization.json.JsonArray) data.firstOrNull()?.jsonObject else data?.jsonObject
            row?.get("lastPr")?.jsonPrimitive?.content ?: "?"
        }.getOrDefault("?")
    }

    suspend fun placeSpotMarket(symbol: String, side: String, quoteUsd: Double): BitgetResult =
        placeSpotMarketProtected(symbol, side, quoteUsd, null, null, "").let { BitgetResult(it.ok, it.message) }

    /** Exchange-owned Spot TP/SL. Bitget market-buy size is quote currency. */
    suspend fun placeSpotMarketProtected(symbol: String, side: String, quoteUsd: Double, stopLoss: Double?, takeProfit: Double?, clientOid: String): OrderResult {
        if (!configured) return OrderResult(false, "Bitget keys missing — enable Paper mode or configure keys")
        val payload = buildString {
            append("{\"symbol\":\"${symbol.uppercase()}\",\"side\":\"${side.lowercase()}\",\"orderType\":\"market\",\"force\":\"gtc\",\"size\":\"${"%.2f".format(Locale.US, quoteUsd)}\"")
            if (clientOid.isNotBlank()) append(",\"clientOid\":\"$clientOid\"")
            if (takeProfit != null) append(",\"presetTakeProfitPrice\":\"${price(takeProfit)}\"")
            if (stopLoss != null) append(",\"presetStopLossPrice\":\"${price(stopLoss)}\"")
            append("}")
        }
        val (code, body) = call("POST", "/api/v2/spot/trade/place-order", payload)
        val r = parse(body)
        return result(body, r.ok, if (r.ok) "OK" else "HTTP $code · ${r.message}")
    }

    /** Futures market order with exchange-owned preset TP/SL. */
    suspend fun placeFuturesMarketProtected(symbol: String, side: String, quoteUsd: Double, referencePrice: Double, stopLoss: Double?, takeProfit: Double?, clientOid: String): OrderResult {
        if (!configured) return OrderResult(false, "Bitget keys missing — enable Paper mode or configure keys")
        if (referencePrice <= 0.0) return OrderResult(false, "Invalid reference price for futures sizing")
        val baseSize = quoteUsd / referencePrice
        val product = prefs.getString("bitget_product_type", "USDT-FUTURES")
        val margin = prefs.getString("bitget_margin_mode", "isolated")
        val payload = buildString {
            append("{\"symbol\":\"${symbol.uppercase()}\",\"productType\":\"$product\",\"marginMode\":\"$margin\",\"marginCoin\":\"USDT\",\"size\":\"${"%.8f".format(Locale.US, baseSize)}\",\"side\":\"${side.lowercase()}\",\"tradeSide\":\"open\",\"orderType\":\"market\",\"force\":\"ioc\"")
            if (clientOid.isNotBlank()) append(",\"clientOid\":\"$clientOid\"")
            if (takeProfit != null) append(",\"presetStopSurplusPrice\":\"${price(takeProfit)}\"")
            if (stopLoss != null) append(",\"presetStopLossPrice\":\"${price(stopLoss)}\"")
            append("}")
        }
        val (code, body) = call("POST", "/api/v2/mix/order/place-order", payload)
        val r = parse(body)
        return result(body, r.ok, if (r.ok) "OK" else "HTTP $code · ${r.message}")
    }

    suspend fun placeProtected(marketType: String, symbol: String, side: String, quoteUsd: Double, referencePrice: Double, stopLoss: Double?, takeProfit: Double?, clientOid: String): OrderResult =
        if (marketType == "FUTURES") placeFuturesMarketProtected(symbol, side, quoteUsd, referencePrice, stopLoss, takeProfit, clientOid)
        else if (marketType == "CFD") placeCfdMarketProtected(symbol, side, quoteUsd, referencePrice, stopLoss, takeProfit, clientOid)
        else placeSpotMarketProtected(symbol, side, quoteUsd, stopLoss, takeProfit, clientOid)

    suspend fun closeFuturesMarket(symbol: String, side: String, size: String, clientOid: String): OrderResult {
        val product = prefs.getString("bitget_product_type", "USDT-FUTURES")
        val margin = prefs.getString("bitget_margin_mode", "isolated")
        val payload = """{"symbol":"${symbol.uppercase()}","productType":"$product","marginMode":"$margin","marginCoin":"USDT","size":"$size","side":"${side.lowercase()}","tradeSide":"close","orderType":"market","force":"ioc","reduceOnly":"YES","clientOid":"$clientOid"}"""
        val (code, body) = call("POST", "/api/v2/mix/order/place-order", payload)
        val r = parse(body)
        return result(body, r.ok, if (r.ok) "OK" else "HTTP $code · ${r.message}")
    }

    suspend fun closeSpotMarket(symbol: String, side: String, baseSize: Double, clientOid: String): OrderResult {
        if (!configured) return OrderResult(false, "Bitget keys missing")
        val payload = """{"symbol":"${symbol.uppercase()}","side":"${side.lowercase()}","orderType":"market","force":"gtc","size":"${"%.8f".format(Locale.US, baseSize)}","clientOid":"$clientOid"}"""
        val (code, body) = call("POST", "/api/v2/spot/trade/place-order", payload)
        val r = parse(body)
        return result(body, r.ok, if (r.ok) "OK" else "HTTP $code · ${r.message}")
    }

    /** Raw signed snapshots used by the restart reconciler. */
    suspend fun currentSpotPlans(symbol: String): Pair<Boolean, String> {
        val (code, body) = call("GET", "/api/v2/spot/trade/current-plan-order?symbol=${symbol.uppercase()}")
        return (code in 200..299 && parse(body).ok) to body
    }

    suspend fun currentFuturesPositions(): Pair<Boolean, String> {
        val product = prefs.getString("bitget_product_type", "USDT-FUTURES")
        val (code, body) = call("GET", "/api/v2/mix/position/all-position?productType=$product&marginCoin=USDT")
        return (code in 200..299 && parse(body).ok) to body
    }

    /** Bitget CFD/MT5 account Open API: direct XAUUSD/forex execution. */
    suspend fun placeCfdMarketProtected(symbol: String, side: String, quoteUsd: Double, referencePrice: Double, stopLoss: Double?, takeProfit: Double?, clientOid: String): OrderResult {
        if (!configured) return OrderResult(false, "Bitget API keys missing")
        if (referencePrice <= 0.0) return OrderResult(false, "Invalid CFD reference price")
        val qty = quoteUsd / referencePrice
        val payload = buildString {
            append("{\"symbol\":\"${symbol.uppercase()}\",\"orderType\":\"market\",\"side\":\"${side.lowercase()}\",\"qty\":\"${"%.8f".format(Locale.US, qty)}\"")
            if (takeProfit != null) append(",\"takeProfit\":\"${price(takeProfit)}\"")
            if (stopLoss != null) append(",\"stopLoss\":\"${price(stopLoss)}\"")
            append("}")
        }
        val (code, body) = call("POST", "/api/v3/cfd/trade/place-order", payload)
        val r = parse(body)
        return result(body, r.ok, if (r.ok) "OK" else "HTTP $code · ${r.message}")
    }

    suspend fun closeCfdPosition(positionId: String, quantity: Double): OrderResult {
        if (positionId.isBlank() || quantity <= 0.0) return OrderResult(false, "CFD positionId/quantity missing")
        val payload = """{"positionId":"$positionId","qty":"${"%.8f".format(Locale.US, quantity)}"}"""
        val (code, body) = call("POST", "/api/v3/cfd/trade/close-positions", payload)
        val r = parse(body)
        return result(body, r.ok, if (r.ok) "OK" else "HTTP $code · ${r.message}")
    }

    suspend fun currentCfdPositions(symbol: String = ""): Pair<Boolean, String> {
        val query = if (symbol.isBlank()) "" else "?symbol=${symbol.uppercase()}"
        val (code, body) = call("GET", "/api/v3/cfd/trade/current-positions$query")
        return (code in 200..299 && parse(body).ok) to body
    }

    suspend fun cfdPositionList(symbol: String = ""): Result<List<CfdPosition>> = runCatching {
        val (code, body) = call("GET", "/api/v3/cfd/trade/current-positions" + if (symbol.isBlank()) "" else "?symbol=${symbol.uppercase()}")
        if (code !in 200..299 || !parse(body).ok) error("CFD positions HTTP $code: ${parse(body).message}")
        val data = json.parseToJsonElement(body).jsonObject["data"] as? JsonArray ?: JsonArray(emptyList())
        data.mapNotNull { row ->
            val o = row.jsonObject
            val id = o["positionId"]?.jsonPrimitive?.content.orEmpty()
            val sym = o["symbol"]?.jsonPrimitive?.content.orEmpty()
            val qty = o["qty"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
            if (id.isBlank() || sym.isBlank() || qty <= 0.0) return@mapNotNull null
            CfdPosition(
                positionId = id,
                symbol = sym,
                side = o["side"]?.jsonPrimitive?.content.orEmpty().uppercase(),
                quantity = qty,
                openPrice = o["openPrice"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                takeProfit = o["takeProfit"]?.jsonPrimitive?.content?.toDoubleOrNull(),
                stopLoss = o["stopLoss"]?.jsonPrimitive?.content?.toDoubleOrNull(),
                unrealizedPnl = o["unrealizedPnl"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                totalProfit = o["totalProfit"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
            )
        }
    }

    suspend fun cfdQuote(symbol: String): Result<CfdQuote> = runCatching {
        val (code, body) = call("GET", "/api/v3/cfd/market/tickers?symbol=${symbol.uppercase()}")
        if (code !in 200..299 || !parse(body).ok) error("CFD ticker HTTP $code: ${parse(body).message}")
        val data = json.parseToJsonElement(body).jsonObject["data"] as? JsonArray
            ?: error("CFD ticker has no data")
        val o = data.firstOrNull()?.jsonObject ?: error("CFD ticker empty")
        val bid = o["bid1"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
        val ask = o["ask1"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
        if (bid <= 0.0 || ask <= 0.0) error("CFD ticker has invalid bid/ask")
        CfdQuote(
            symbol = o["symbol"]?.jsonPrimitive?.content ?: symbol.uppercase(),
            bid = bid,
            ask = ask,
            high = o["highPrice"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
            low = o["lowPrice"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
            ts = o["quoteTime"]?.jsonPrimitive?.content?.toLongOrNull() ?: System.currentTimeMillis()
        )
    }

    suspend fun cfdPrice(symbol: String, side: String = "buy"): Result<Double> =
        cfdQuote(symbol).map { if (side.equals("sell", true)) it.bid else it.ask }

    suspend fun cfdCandles(symbol: String, interval: String = "1m", side: String = "buy", limit: Int = 100): Result<List<CfdCandle>> = runCatching {
        val path = "/api/v3/cfd/market/history-candlestick?symbol=${symbol.uppercase()}&interval=$interval&side=$side&limit=${limit.coerceIn(1, 100)}"
        val (code, body) = call("GET", path)
        if (code !in 200..299 || !parse(body).ok) error("CFD candles HTTP $code: ${parse(body).message}")
        val data = json.parseToJsonElement(body).jsonObject["data"] as? JsonArray ?: JsonArray(emptyList())
        data.mapNotNull { row ->
            val a = row as? JsonArray ?: return@mapNotNull null
            if (a.size < 5) return@mapNotNull null
            CfdCandle(
                ts = a[0].jsonPrimitive.content.toLongOrNull() ?: return@mapNotNull null,
                open = a[1].jsonPrimitive.content.toDoubleOrNull() ?: return@mapNotNull null,
                high = a[2].jsonPrimitive.content.toDoubleOrNull() ?: return@mapNotNull null,
                low = a[3].jsonPrimitive.content.toDoubleOrNull() ?: return@mapNotNull null,
                close = a[4].jsonPrimitive.content.toDoubleOrNull() ?: return@mapNotNull null
            )
        }
    }

    private fun price(value: Double): String = "%.8f".format(Locale.US, value).trimEnd('0').trimEnd('.')
}
