package com.rizwan.tradingagentarmy.trading

import com.rizwan.tradingagentarmy.data.local.SecurePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MT5 cannot be embedded as an Android trading terminal. This client speaks to
 * a user-hosted MT5 EA/bridge (usually on a VPS) which owns the MT5 login and
 * sends orders through MetaTrader5. No bridge URL means LIVE MT5 is blocked.
 */
@Singleton
class Mt5BridgeClient @Inject constructor(private val prefs: SecurePreferences) {
    private val http = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    val configured: Boolean get() = prefs.mt5BridgeUrl.isNotBlank() && prefs.mt5Login.isNotBlank() && prefs.mt5Server.isNotBlank()

    suspend fun placeMarketProtected(symbol: String, side: String, volume: Double, stopLoss: Double, takeProfit: Double, clientOid: String): BitgetClient.OrderResult =
        post("/api/mt5/order", """{"symbol":"${symbol.uppercase()}","side":"${side.uppercase()}","volume":$volume,"stopLoss":$stopLoss,"takeProfit":$takeProfit,"clientOid":"$clientOid","login":"${prefs.mt5Login}","password":"${prefs.mt5Password}","server":"${prefs.mt5Server}"}""")

    suspend fun close(symbol: String, side: String, volume: Double, clientOid: String): BitgetClient.OrderResult =
        post("/api/mt5/close", """{"symbol":"${symbol.uppercase()}","side":"${side.uppercase()}","volume":$volume,"clientOid":"$clientOid","login":"${prefs.mt5Login}","password":"${prefs.mt5Password}","server":"${prefs.mt5Server}"}""")

    private suspend fun post(path: String, body: String): BitgetClient.OrderResult = withContext(Dispatchers.IO) {
        if (!configured) return@withContext BitgetClient.OrderResult(false, "MT5 bridge/login/server not configured")
        runCatching {
            val url = prefs.mt5BridgeUrl.trimEnd('/') + path
            val request = Request.Builder().url(url)
                .addHeader("Authorization", "Bearer ${prefs.bearerToken}")
                .post(body.toRequestBody("application/json".toMediaType())).build()
            http.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                val obj = json.parseToJsonElement(raw).jsonObject
                val ok = response.isSuccessful && (obj["ok"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false)
                BitgetClient.OrderResult(ok, obj["message"]?.jsonPrimitive?.content ?: raw.take(200), obj["orderId"]?.jsonPrimitive?.content.orEmpty(), obj["clientOid"]?.jsonPrimitive?.content.orEmpty())
            }
        }.getOrElse { BitgetClient.OrderResult(false, "MT5 bridge error: ${it.message}") }
    }
}
