package com.rizwan.tradingagentarmy.trading

import com.rizwan.tradingagentarmy.data.local.SecurePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BitgetClient @Inject constructor(private val prefs: SecurePreferences) {

    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    data class BitgetResult(val ok: Boolean, val message: String)

    val configured: Boolean
        get() = prefs.bitgetKey.isNotBlank() && prefs.bitgetSecret.isNotBlank() && prefs.bitgetPassphrase.isNotBlank()

    private fun sign(ts: String, method: String, pathWithQuery: String, body: String): String {
        val payload = ts + method + pathWithQuery + body
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(prefs.bitgetSecret.toByteArray(), "HmacSHA256"))
        return Base64.getEncoder().encodeToString(mac.doFinal(payload.toByteArray()))
    }

    private suspend fun call(method: String, pathWithQuery: String, body: String? = null): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            val ts = System.currentTimeMillis().toString()
            val b = body.orEmpty()
            val req = Request.Builder()
                .url("https://api.bitget.com$pathWithQuery")
                .addHeader("ACCESS-KEY", prefs.bitgetKey)
                .addHeader("ACCESS-SIGN", sign(ts, method, pathWithQuery, b))
                .addHeader("ACCESS-TIMESTAMP", ts)
                .addHeader("ACCESS-PASSPHRASE", prefs.bitgetPassphrase)
                .addHeader("Content-Type", "application/json")
                .apply {
                    if (method == "POST") post(b.toRequestBody("application/json".toMediaType()))
                    else get()
                }
                .build()
            http.newCall(req).execute().use {
                it.code to (it.body?.string().orEmpty())
            }
        }

    private fun parse(raw: String): BitgetResult = runCatching {
        val obj = json.parseToJsonElement(raw).jsonObject
        val code = obj["code"]?.jsonPrimitive?.content ?: "?"
        val msg = obj["msg"]?.jsonPrimitive?.content ?: raw.take(200)
        BitgetResult(code == "00000" || code == "0", if (code == "00000" || code == "0") "OK" else "[$code] $msg")
    }.getOrElse { BitgetResult(false, it.message ?: "parse error") }

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
            val data = json.parseToJsonElement(body).jsonObject["data"]?.let {
                if (it is kotlinx.serialization.json.JsonArray) it.firstOrNull() else it
            }?.jsonObject
            data?.get("lastPr")?.jsonPrimitive?.content ?: "?"
        }.getOrDefault("?")
    }

    suspend fun placeSpotMarket(symbol: String, side: String, quoteUsd: Double): BitgetResult {
        if (!configured) return BitgetResult(false, "Bitget keys missing — enable keys or use Paper mode")
        val payload = buildString {
            append("{")
            append("\"symbol\":\"${symbol.uppercase()}\",")
            append("\"side\":\"${side.lowercase()}\",")
            append("\"orderType\":\"market\",")
            append("\"quoteOrderSize\":\"${"%.2f".format(quoteUsd)}\"")
            append("}")
        }
        val (code, body) = call("POST", "/api/v2/spot/trade/place-order", payload)
        val r = parse(body)
        return if (r.ok) r else BitgetResult(false, "HTTP $code · ${r.message}")
    }
}
